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
 * {@code manifest.json} mapping item id → version (mod id + mod version), so only new or changed items are rendered
 * again after a modpack update. New icons are kept in memory and written per namespace by {@link #flush()} (the
 * namespace zip is rewritten atomically). Reads go through a small LRU. Thread-safe; pure Java, unit tested.
 */
public final class IconStore implements AutoCloseable {
    public static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
    public static final Pattern PATH = Pattern.compile("[a-z0-9_.-]{1,128}(/[a-z0-9_.-]{1,128}){0,7}");
    private static final String FAILED = "!";
    private static final int LRU = 2000;

    public record Status(int stored, int failed, long bytes) {
    }

    private final Path dir;
    /** id → version ("!" + version for items no client could render). */
    private final Map<String, String> manifest = new HashMap<>();
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
                if (isValidId(e.getKey())) manifest.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (IOException | RuntimeException e) {
            manifest.clear();
        }
    }

    /** True if the stored icon (or the "could not render" marker) is for this version. */
    public synchronized boolean isCurrent(String id, String version) {
        var v = manifest.get(id);
        return v != null && (v.equals(version) || v.equals(FAILED + version));
    }

    public synchronized boolean hasIcon(String id) {
        var v = manifest.get(id);
        return v != null && !v.startsWith(FAILED);
    }

    public synchronized void put(String id, String version, byte[] png) {
        if (!isValidId(id)) throw new IllegalArgumentException("bad id " + id);
        int colon = id.indexOf(':');
        pending.computeIfAbsent(id.substring(0, colon), k -> new HashMap<>()).put(id.substring(colon + 1), png);
        manifest.put(id, version);
        lru.put(id, png);
        manifestDirty = true;
    }

    /** Remembers that no client could render this item (for this version), so it is not queued again. */
    public synchronized void markFailed(String id, String version) {
        if (!isValidId(id)) return;
        manifest.put(id, FAILED + version);
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
            root.addProperty("version", 1);
            var items = new JsonObject();
            manifest.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> items.addProperty(e.getKey(), e.getValue()));
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
        int stored = 0, failed = 0;
        for (var v : manifest.values()) {
            if (v.startsWith(FAILED)) failed++;
            else stored++;
        }
        long bytes = 0;
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (var file : files.toList()) if (file.getFileName().toString().endsWith(".zip")) bytes += Files.size(file);
            } catch (IOException ignored) {
            }
        }
        for (var p : pending.values()) for (var b : p.values()) bytes += b.length;
        return new Status(stored, failed, bytes);
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
