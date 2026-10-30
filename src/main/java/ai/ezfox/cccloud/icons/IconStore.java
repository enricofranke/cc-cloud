package ai.ezfox.cccloud.icons;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;

/**
 * Persistent item icon store: {@code <dir>/<namespace>.zip} with one {@code <path>.png} per item, plus
 * {@code manifest.json} mapping item id → version (mod id + mod version) and source, so only new or changed items are
 * rendered again after a modpack update. New icons are kept in memory and written per namespace by {@link #flush()}
 * (the namespace zip is rewritten atomically). Reads go through a small LRU. Thread-safe; pure Java, unit tested.
 * <p>
 * Sources: {@link Source#CLIENT} icons come from a game client's renderer (the real inventory icon),
 * {@link Source#SERVER} icons from {@link ServerIconRenderer} (flat items and plain cubes only). Client renders always
 * replace server renders; a server render never replaces a client render, not even an outdated one. Manifest format
 * 2: {@code {"version":2,"items":{"<id>":{"version":"<ns>@<mod version>","source":"client"|"server","failed":true?}}}};
 * format 1 ({@code "<id>":"<version>"}, {@code "!<version>"} = could not render) is read as client entries.
 */
public final class IconStore implements AutoCloseable {
    public static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
    public static final Pattern PATH = Pattern.compile("[a-z0-9_.-]{1,128}(/[a-z0-9_.-]{1,128}){0,7}");
    private static final String FAILED_V1 = "!";
    private static final int LRU = 2000;

    /** Who rendered a stored icon. */
    public enum Source {
        CLIENT("client"), SERVER("server");

        public final String id;

        Source(String id) {
            this.id = id;
        }
    }

    /** {@code stored} = client + server icons; {@code failed} = items no client could render (for their version). */
    public record Status(int stored, int failed, long bytes, int client, int server) {
    }

    /** A manifest entry: the stored icon's version and source, or a client's "could not render" marker. */
    private record Entry(String version, Source source, boolean failed) {
    }

    private final Path dir;
    /** id → entry. */
    private final Map<String, Entry> manifest = new HashMap<>();
    /** namespace → path → png (not yet flushed). */
    private final Map<String, Map<String, byte[]>> pending = new HashMap<>();
    private final Map<String, ZipFile> zips = new HashMap<>();
    private final LinkedHashMap<String, byte[]> lru = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > LRU;
        }
    };
    private boolean manifestDirty;
    private long hits, misses;

    public IconStore(Path dir) {
        this.dir = dir;
        loadManifest();
    }

    public Path dir() {
        return dir;
    }

    /** Valid item id with a safe namespace and path (no traversal, no uppercase, no backslashes). */
    public static boolean isValidId(@Nullable String id) {
        if (id == null) return false;
        int colon = id.indexOf(':');
        if (colon <= 0) return false;
        String ns = id.substring(0, colon), path = id.substring(colon + 1);
        if (!NAMESPACE.matcher(ns).matches() || !PATH.matcher(path).matches()) return false;
        for (var segment : path.split("/")) if (segment.equals(".") || segment.equals("..")) return false;
        return !ns.equals(".") && !ns.equals("..");
    }

    private void loadManifest() {
        var file = dir.resolve("manifest.json");
        if (!Files.isRegularFile(file)) return;
        try {
            var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : json.getAsJsonObject("items").entrySet()) {
                if (!isValidId(e.getKey())) continue;
                if (e.getValue() instanceof JsonObject o) {
                    // format 2
                    if (o.get("version") == null || !o.get("version").isJsonPrimitive()) continue;
                    var source = o.get("source") != null && o.get("source").isJsonPrimitive() && o.get("source").getAsString().equals(Source.SERVER.id)
                        ? Source.SERVER : Source.CLIENT;
                    boolean failed = o.get("failed") != null && o.get("failed").isJsonPrimitive() && o.get("failed").getAsBoolean();
                    manifest.put(e.getKey(), new Entry(o.get("version").getAsString(), source, failed && source == Source.CLIENT));
                } else if (e.getValue().isJsonPrimitive()) {
                    // format 1: only client renders existed
                    String v = e.getValue().getAsString();
                    boolean failed = v.startsWith(FAILED_V1);
                    manifest.put(e.getKey(), new Entry(failed ? v.substring(1) : v, Source.CLIENT, failed));
                }
            }
        } catch (IOException | RuntimeException e) {
            manifest.clear();
        }
    }

    /**
     * True if a game client already handled this version: its icon or its "could not render" marker. Server renders
     * do not count, so those items stay queued for clients.
     */
    public synchronized boolean isCurrent(String id, String version) {
        var e = manifest.get(id);
        return e != null && e.source() == Source.CLIENT && e.version().equals(version);
    }

    /** True if an icon (from any source) is stored. */
    public synchronized boolean hasIcon(String id) {
        var e = manifest.get(id);
        return e != null && !e.failed();
    }

    /** Who rendered the stored icon; null if there is none. */
    public synchronized @Nullable Source source(String id) {
        var e = manifest.get(id);
        return e == null || e.failed() ? null : e.source();
    }

    /**
     * True if the server renderer should draw this item: nothing stored, an outdated server render, or an outdated
     * client failure. Never for a client icon (of any version) or a client failure of this version.
     */
    public synchronized boolean wantsServerRender(String id, String version) {
        var e = manifest.get(id);
        if (e == null) return true;
        if (e.source() == Source.CLIENT) return e.failed() && !e.version().equals(version);
        return !e.version().equals(version);
    }

    /** Stores a client render (it replaces a server render of the same item). */
    public synchronized void put(String id, String version, byte[] png) {
        store(id, new Entry(version, Source.CLIENT, false), png);
    }

    /**
     * Stores a server render unless a client icon (any version) or a client failure for this version is stored.
     *
     * @return true if stored
     */
    public synchronized boolean putServer(String id, String version, byte[] png) {
        var e = manifest.get(id);
        if (e != null && e.source() == Source.CLIENT && (!e.failed() || e.version().equals(version))) return false;
        store(id, new Entry(version, Source.SERVER, false), png);
        return true;
    }

    private void store(String id, Entry entry, byte[] png) {
        if (!isValidId(id)) throw new IllegalArgumentException("bad id " + id);
        int colon = id.indexOf(':');
        pending.computeIfAbsent(id.substring(0, colon), k -> new HashMap<>()).put(id.substring(colon + 1), png);
        manifest.put(id, entry);
        lru.put(id, png);
        manifestDirty = true;
    }

    /**
     * Remembers that no client could render this item (for this version), so it is not queued again. A server render
     * stays (a client cannot wipe it); the item is offered to clients again after a restart.
     */
    public synchronized void markFailed(String id, String version) {
        if (!isValidId(id)) return;
        var e = manifest.get(id);
        if (e != null && e.source() == Source.SERVER) return;
        manifest.put(id, new Entry(version, Source.CLIENT, true));
        lru.remove(id);
        manifestDirty = true;
    }

    /** The icon, or null if none is stored. */
    public synchronized byte @Nullable [] get(String id) {
        if (!isValidId(id) || !hasIcon(id)) return null;
        var cached = lru.get(id);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        int colon = id.indexOf(':');
        String ns = id.substring(0, colon), path = id.substring(colon + 1);
        var p = pending.get(ns);
        if (p != null && p.containsKey(path)) return p.get(path);
        try {
            var zip = zip(ns);
            if (zip == null) return null;
            var entry = zip.getEntry(path + ".png");
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                var bytes = in.readNBytes(PngCodec.MAX_BYTES + 1);
                if (bytes.length > PngCodec.MAX_BYTES) return null;
                lru.put(id, bytes);
                return bytes;
            }
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized long hits() {
        return hits;
    }

    public synchronized long misses() {
        return misses;
    }

    private @Nullable ZipFile zip(String ns) throws IOException {
        var zip = zips.get(ns);
        if (zip != null) return zip;
        var file = dir.resolve(ns + ".zip");
        if (!Files.isRegularFile(file)) return null;
        zip = new ZipFile(file.toFile());
        zips.put(ns, zip);
        return zip;
    }

    /** Writes pending icons into their namespace zips and the manifest. Call from a background thread. */
    public void flush() throws IOException {
        Map<String, Map<String, byte[]>> snapshot;
        boolean writeManifest;
        synchronized (this) {
            snapshot = new HashMap<>();
            for (var e : pending.entrySet()) snapshot.put(e.getKey(), new HashMap<>(e.getValue()));
            writeManifest = manifestDirty || !snapshot.isEmpty();
        }
        if (!writeManifest) return;
        Files.createDirectories(dir);
        for (var e : snapshot.entrySet()) {
            String ns = e.getKey();
            var file = dir.resolve(ns + ".zip");
            var temp = dir.resolve(ns + ".zip.tmp");
            var written = new HashSet<String>();
            try (var out = new ZipOutputStream(Files.newOutputStream(temp))) {
                out.setLevel(0); // PNGs are already compressed
                for (var icon : e.getValue().entrySet()) {
                    out.putNextEntry(new ZipEntry(icon.getKey() + ".png"));
                    out.write(icon.getValue());
                    out.closeEntry();
                    written.add(icon.getKey() + ".png");
                }
                if (Files.isRegularFile(file)) {
                    try (var old = new ZipFile(file.toFile())) {
                        var entries = old.entries();
                        while (entries.hasMoreElements()) {
                            var entry = entries.nextElement();
                            if (written.contains(entry.getName())) continue;
                            out.putNextEntry(new ZipEntry(entry.getName()));
                            try (var in = old.getInputStream(entry)) {
                                in.transferTo(out);
                            }
                            out.closeEntry();
                        }
                    }
                }
            }
            synchronized (this) {
                var old = zips.remove(ns);
                if (old != null) old.close();
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                var p = pending.get(ns);
                if (p != null) {
                    for (var icon : e.getValue().entrySet()) p.remove(icon.getKey(), icon.getValue());
                    if (p.isEmpty()) pending.remove(ns);
                }
            }
        }
        String json;
        synchronized (this) {
            var root = new JsonObject();
            root.addProperty("version", 2);
            var items = new JsonObject();
            manifest.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                var o = new JsonObject();
                o.addProperty("version", e.getValue().version());
                o.addProperty("source", e.getValue().source().id);
                if (e.getValue().failed()) o.addProperty("failed", true);
                items.add(e.getKey(), o);
            });
            root.add("items", items);
            json = new GsonBuilder().create().toJson(root);
            manifestDirty = false;
        }
        var temp = dir.resolve("manifest.json.tmp");
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        Files.move(temp, dir.resolve("manifest.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Deletes every icon (also on disk). */
    public synchronized void clear() throws IOException {
        for (var zip : zips.values()) zip.close();
        zips.clear();
        pending.clear();
        lru.clear();
        manifest.clear();
        manifestDirty = false;
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            for (var file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".zip") || name.equals("manifest.json") || name.endsWith(".tmp")) Files.deleteIfExists(file);
            }
        }
    }

    public synchronized Status status() {
        int client = 0, server = 0, failed = 0;
        for (var e : manifest.values()) {
            if (e.failed()) failed++;
            else if (e.source() == Source.SERVER) server++;
            else client++;
        }
        long bytes = 0;
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (var file : files.toList()) if (file.getFileName().toString().endsWith(".zip")) bytes += Files.size(file);
            } catch (IOException ignored) {
            }
        }
        for (var p : pending.values()) for (var b : p.values()) bytes += b.length;
        return new Status(client + server, failed, bytes, client, server);
    }

    /** Ids whose stored version differs (or that are missing) for the given id → version map. */
    public synchronized Set<String> outdated(Map<String, String> wanted) {
        var set = new java.util.LinkedHashSet<String>();
        for (var e : wanted.entrySet()) if (!isCurrent(e.getKey(), e.getValue())) set.add(e.getKey());
        return set;
    }

    @Override
    public synchronized void close() throws IOException {
        for (var zip : zips.values()) zip.close();
        zips.clear();
    }
}
