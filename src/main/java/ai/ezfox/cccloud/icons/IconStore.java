package ai.ezfox.cccloud.icons;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * rendered again after a modpack update. Reads go through a small LRU. Thread-safe; pure Java, unit tested.
 * <p>
 * Writing survives a hard kill (hosting panels often kill the server instead of stopping it): every change is first
 * written by {@link #checkpoint()} as a small journal segment {@code journal-<n>.zip} (the new icons plus their
 * manifest entries, written to a temp file and renamed into place, so a segment is either complete or absent), at most
 * about a second after an upload. {@link #flush()} compacts: it rewrites the touched namespace zips and
 * {@code manifest.json} (both atomically) and deletes the segments it covered. Loading reads {@code manifest.json} and
 * then the segments in order, so a kill at any moment loses at most what came after the last checkpoint.
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
    private static final Pattern JOURNAL = Pattern.compile("journal-(\\d{1,18})\\.zip");
    private static final String JOURNAL_ENTRIES = "entries.json";
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
        JsonObject json() {
            var o = new JsonObject();
            o.addProperty("version", version);
            o.addProperty("source", source.id);
            if (failed) o.addProperty("failed", true);
            return o;
        }

        /** Format 2 object or format 1 string; null if neither. */
        static @Nullable Entry parse(JsonElement value) {
            if (value instanceof JsonObject o) {
                if (o.get("version") == null || !o.get("version").isJsonPrimitive()) return null;
                var source = o.get("source") != null && o.get("source").isJsonPrimitive() && o.get("source").getAsString().equals(Source.SERVER.id)
                    ? Source.SERVER : Source.CLIENT;
                boolean failed = o.get("failed") != null && o.get("failed").isJsonPrimitive() && o.get("failed").getAsBoolean();
                return new Entry(o.get("version").getAsString(), source, failed && source == Source.CLIENT);
            }
            if (value.isJsonPrimitive()) {
                String v = value.getAsString(); // format 1: only client renders existed
                boolean failed = v.startsWith(FAILED_V1);
                return new Entry(failed ? v.substring(1) : v, Source.CLIENT, failed);
            }
            return null;
        }
    }

    private final Path dir;
    /** id → entry. */
    private final Map<String, Entry> manifest = new HashMap<>();
    /** namespace → path → png (not yet compacted into the namespace zips). */
    private final Map<String, Map<String, byte[]>> pending = new HashMap<>();
    /** Ids changed since the last checkpoint. */
    private final Set<String> unjournaled = new LinkedHashSet<>();
    private final Map<String, ZipFile> zips = new HashMap<>();
    private final LinkedHashMap<String, byte[]> lru = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > LRU;
        }
    };
    /** Checkpoints and compaction never run at the same time (they share the journal sequence and files). */
    private final Object writeLock = new Object();
    private boolean manifestDirty;
    private long nextSegment;
    private long hits, misses;

    public IconStore(Path dir) {
        this.dir = dir;
        loadManifest();
        loadJournal();
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

    // ---- loading -----------------------------------------------------------------------------------------------------

    private void loadManifest() {
        var file = dir.resolve("manifest.json");
        if (!Files.isRegularFile(file)) return;
        JsonObject items;
        try {
            items = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("items");
        } catch (IOException | RuntimeException e) {
            return; // unreadable: start empty (written atomically, so this means someone edited it)
        }
        if (items == null) return;
        for (var e : items.entrySet()) {
            if (!isValidId(e.getKey())) continue;
            try {
                var entry = Entry.parse(e.getValue());
                if (entry != null) manifest.put(e.getKey(), entry);
            } catch (RuntimeException ignored) {
                // one odd entry does not cost the others
            }
        }
    }

    /** Applies the journal segments a kill left behind, in order; their icons wait in memory for the next compaction. */
    private void loadJournal() {
        if (!Files.isDirectory(dir)) return;
        var segments = new ArrayList<long[]>();
        try (var files = Files.list(dir)) {
            for (var file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    Files.deleteIfExists(file); // a write a kill interrupted: never renamed into place, so never used
                    continue;
                }
                var m = JOURNAL.matcher(name);
                if (m.matches()) segments.add(new long[]{Long.parseLong(m.group(1))});
            }
        } catch (IOException | RuntimeException e) {
            return;
        }
        segments.sort((a, b) -> Long.compare(a[0], b[0]));
        for (var seq : segments) {
            nextSegment = Math.max(nextSegment, seq[0] + 1);
            try (var zip = new ZipFile(segment(seq[0]).toFile())) {
                var entriesFile = zip.getEntry(JOURNAL_ENTRIES);
                if (entriesFile == null) continue;
                JsonObject entries;
                try (var in = zip.getInputStream(entriesFile)) {
                    entries = JsonParser.parseString(new String(in.readNBytes(64 * 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
                }
                for (var e : entries.entrySet()) {
                    String id = e.getKey();
                    if (!isValidId(id)) continue;
                    var entry = Entry.parse(e.getValue());
                    if (entry == null) continue;
                    int colon = id.indexOf(':');
                    String ns = id.substring(0, colon), path = id.substring(colon + 1);
                    if (!entry.failed()) {
                        var icon = zip.getEntry("icons/" + ns + "/" + path + ".png");
                        if (icon == null) continue;
                        byte[] png;
                        try (var in = zip.getInputStream(icon)) {
                            png = in.readNBytes(PngCodec.MAX_BYTES + 1);
                        }
                        if (png.length > PngCodec.MAX_BYTES) continue;
                        pending.computeIfAbsent(ns, k -> new HashMap<>()).put(path, png);
                    }
                    manifest.put(id, entry);
                    manifestDirty = true;
                }
            } catch (IOException | RuntimeException ignored) {
                // a damaged segment: skip it, the others still count
            }
        }
    }

    private Path segment(long seq) {
        return dir.resolve(String.format(java.util.Locale.ROOT, "journal-%012d.zip", seq));
    }

    // ---- queries -----------------------------------------------------------------------------------------------------

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

    // ---- changes -----------------------------------------------------------------------------------------------------

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
        unjournaled.add(id);
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
        unjournaled.add(id);
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

    // ---- writing -----------------------------------------------------------------------------------------------------

    /** True if changes wait for a {@link #checkpoint()}. */
    public synchronized boolean hasUnjournaled() {
        return !unjournaled.isEmpty();
    }

    /**
     * Writes every change since the last checkpoint as one journal segment (temp file + atomic rename). Cheap: only
     * the new icons. Call from a background thread, about once a second while icons arrive.
     *
     * @return the number of entries written
     */
    public int checkpoint() throws IOException {
        synchronized (writeLock) {
            var entries = new LinkedHashMap<String, Entry>();
            var icons = new LinkedHashMap<String, byte[]>();
            long seq;
            synchronized (this) {
                if (unjournaled.isEmpty()) return 0;
                for (var id : unjournaled) {
                    var entry = manifest.get(id);
                    if (entry == null) continue;
                    if (!entry.failed()) {
                        int colon = id.indexOf(':');
                        var p = pending.get(id.substring(0, colon));
                        byte[] png = p == null ? null : p.get(id.substring(colon + 1));
                        if (png == null) continue; // already compacted into its namespace zip
                        icons.put(id, png);
                    }
                    entries.put(id, entry);
                }
                unjournaled.clear();
                seq = nextSegment++;
            }
            if (entries.isEmpty()) return 0;
            try {
                Files.createDirectories(dir);
                var file = segment(seq);
                var temp = dir.resolve(file.getFileName() + ".tmp");
                try (var out = new ZipOutputStream(Files.newOutputStream(temp))) {
                    out.setLevel(0); // PNGs are already compressed
                    for (var icon : icons.entrySet()) {
                        int colon = icon.getKey().indexOf(':');
                        out.putNextEntry(new ZipEntry("icons/" + icon.getKey().substring(0, colon) + "/" + icon.getKey().substring(colon + 1) + ".png"));
                        out.write(icon.getValue());
                        out.closeEntry();
                    }
                    var json = new JsonObject();
                    entries.forEach((id, entry) -> json.add(id, entry.json()));
                    out.putNextEntry(new ZipEntry(JOURNAL_ENTRIES));
                    out.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    out.closeEntry();
                }
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | RuntimeException e) {
                synchronized (this) {
                    unjournaled.addAll(entries.keySet()); // try again with the next checkpoint
                }
                throw e;
            }
            return entries.size();
        }
    }

    /**
     * Compacts: writes pending icons into their namespace zips, then the manifest, then deletes the journal segments
     * (everything in them is in the zips and the manifest now). Each file is replaced atomically, so a kill in between
     * leaves a consistent store (segments are applied again on the next load). Call from a background thread.
     */
    public void flush() throws IOException {
        synchronized (writeLock) {
            Map<String, Map<String, byte[]>> snapshot;
            Map<String, Entry> entries;
            boolean write;
            synchronized (this) {
                // Icons and manifest from the same moment: the manifest never names an icon the zips do not have.
                snapshot = new HashMap<>();
                for (var e : pending.entrySet()) snapshot.put(e.getKey(), new HashMap<>(e.getValue()));
                entries = new HashMap<>(manifest);
                write = manifestDirty || !snapshot.isEmpty();
                manifestDirty = false;
            }
            var segments = journalFiles();
            if (!write && segments.isEmpty()) return;
            try {
                Files.createDirectories(dir);
                for (var e : snapshot.entrySet()) writeNamespace(e.getKey(), e.getValue());
                var root = new JsonObject();
                root.addProperty("version", 2);
                var items = new JsonObject();
                entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> items.add(e.getKey(), e.getValue().json()));
                root.add("items", items);
                var temp = dir.resolve("manifest.json.tmp");
                Files.writeString(temp, new GsonBuilder().create().toJson(root), StandardCharsets.UTF_8);
                Files.move(temp, dir.resolve("manifest.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | RuntimeException e) {
                synchronized (this) {
                    manifestDirty = true;
                }
                throw e;
            }
            // Only now: everything the segments held is in the zips and the manifest.
            for (var file : segments) Files.deleteIfExists(file);
        }
    }

    private void writeNamespace(String ns, Map<String, byte[]> icons) throws IOException {
        var file = dir.resolve(ns + ".zip");
        var temp = dir.resolve(ns + ".zip.tmp");
        var written = new HashSet<String>();
        try (var out = new ZipOutputStream(Files.newOutputStream(temp))) {
            out.setLevel(0); // PNGs are already compressed
            for (var icon : icons.entrySet()) {
                out.putNextEntry(new ZipEntry(icon.getKey() + ".png"));
                out.write(icon.getValue());
                out.closeEntry();
                written.add(icon.getKey() + ".png");
            }
            if (Files.isRegularFile(file)) {
                try (var old = new ZipFile(file.toFile())) {
                    var oldEntries = old.entries();
                    while (oldEntries.hasMoreElements()) {
                        var entry = oldEntries.nextElement();
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
                for (var icon : icons.entrySet()) p.remove(icon.getKey(), icon.getValue());
                if (p.isEmpty()) pending.remove(ns);
            }
        }
    }

    private List<Path> journalFiles() throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (var files = Files.list(dir)) {
            return files.filter(f -> JOURNAL.matcher(f.getFileName().toString()).matches()).sorted().toList();
        }
    }

    /** Number of journal segments on disk (tests, status). */
    public int journalSegments() {
        try {
            return journalFiles().size();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Deletes every icon (also on disk). */
    public void clear() throws IOException {
        synchronized (writeLock) {
            synchronized (this) {
                for (var zip : zips.values()) zip.close();
                zips.clear();
                pending.clear();
                lru.clear();
                manifest.clear();
                unjournaled.clear();
                manifestDirty = false;
                if (!Files.isDirectory(dir)) return;
                try (var files = Files.list(dir)) {
                    for (var file : files.toList()) {
                        String name = file.getFileName().toString();
                        if (name.endsWith(".zip") || name.equals("manifest.json") || name.endsWith(".tmp")) Files.deleteIfExists(file);
                    }
                }
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
                for (var file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".zip") && !JOURNAL.matcher(name).matches()) bytes += Files.size(file);
                }
            } catch (IOException ignored) {
            }
        }
        for (var p : pending.values()) for (var b : p.values()) bytes += b.length;
        return new Status(client + server, failed, bytes, client, server);
    }

    /** Ids whose stored version differs (or that are missing) for the given id → version map. */
    public synchronized Set<String> outdated(Map<String, String> wanted) {
        var set = new LinkedHashSet<String>();
        for (var e : wanted.entrySet()) if (!isCurrent(e.getKey(), e.getValue())) set.add(e.getKey());
        return set;
    }

    @Override
    public synchronized void close() throws IOException {
        for (var zip : zips.values()) zip.close();
        zips.clear();
    }
}
