package ai.ezfox.cccloud.voxel;

import ai.ezfox.cccloud.CloudLog;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

/**
 * The voxel view's disk cache (docs/SPEC.md "Voxel view – last known terrain"): what the view shows of chunks that are
 * not loaded. One file per mainframe and chunk column, {@code <root>/<mainframe>/<dimension>/<cx>_<cz>.bin}, so a chunk
 * unload is one write and a base of 500 sections a few dozen small files:
 * <pre>
 *   "CCVD"  version (1 byte)  logic version (i32)  gzip of:
 *     sections (u16), each: cy (i16)  self (i64)  content (i64)  known (u8)  palette size (u16)
 *                           palette entries (id, color i32, flags u8)  runs length (i32)  runs
 * </pre>
 * Other versions and damaged files count as empty and are replaced; the content hash and the exposed count are computed
 * again when a file is read. Reads may run on any thread: files are replaced atomically, so a read sees a whole file or
 * none. Everything that writes runs on one background thread, in the order it was asked for, so a column's
 * read-merge-write and the purge need no locks; the queue is bounded and drops when busy (a dropped section just stays
 * as it was). Modification times mean "last written or read" for the age cap, and the oldest files go first when a size
 * cap is reached. Never follows links. Pure Java, unit tested.
 */
public final class VoxelDisk {
    static final byte[] MAGIC = {'C', 'C', 'V', 'D'};
    static final int VERSION = 1;
    /** Sections waiting for the writer at most; more are dropped. */
    static final int MAX_PENDING = 8192;
    /** The largest file or decompressed body that is read (tallest modded worlds, a full palette per section). */
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SECTIONS = 1024;
    private static final int MAX_PALETTE = 4096;
    private static final long HOUR_MS = 3_600_000L;
    /** A file read from disk that is older than this gets a new modification time ("last used"). */
    static final long TOUCH_AFTER_MS = 24 * HOUR_MS;
    private static final String COLUMN = ".bin", TEMP = ".tmp", AREA = "area.gz";

    /**
     * A section on disk: its encoding plus what decides whether a newer build replaces it. {@code self} hashes the
     * section's own blocks, {@code content} also the layers of its neighbors that touch it, {@code known} has bit f set
     * when neighbor f was loaded for that build.
     */
    public record Stored(long self, long content, int known, VoxelSection.Encoded section) {
    }

    /** Age cap ({@code 0} = none), size caps in bytes per mainframe and in total ({@code 0} total = nothing on disk). */
    public record Limits(long maxAgeMs, long mainframeBytes, long totalBytes) {
    }

    private record Column(UUID mainframe, String dim, int cx, int cz) {
    }

    private record Item(Path file, long size, long time, @Nullable UUID mainframe) {
    }

    private final Path root;
    private final int logic;
    private final Supplier<Limits> limits;
    /** Sections waiting for the writer, per column; a newer build of a section replaces the waiting one. */
    private final Map<Column, Map<Integer, Stored>> pending = new LinkedHashMap<>();
    private int pendingSections;
    private boolean draining;
    private final Set<UUID> purged = ConcurrentHashMap.newKeySet();
    /** Columns whose modification time was checked since the last sweep. */
    private final Set<Column> touched = ConcurrentHashMap.newKeySet();
    /** Writer thread only: bytes per mainframe (rounded up to file system blocks) and in total. */
    private final Map<UUID, Long> sizes = new HashMap<>();
    private volatile long total;
    private boolean scanned;
    private @Nullable ExecutorService writer;

    /** @param logic the build logic's version: files of another version count as empty */
    public VoxelDisk(Path root, int logic, Supplier<Limits> limits) {
        this.root = root;
        this.logic = logic;
        this.limits = limits;
    }

    private Path mainframeDir(UUID mainframe) {
        return root.resolve(mainframe.toString());
    }

    /** One folder per dimension; encoded, so {@code a_b:c} and {@code a:b_c} never share one. */
    private Path dimDir(UUID mainframe, String dim) {
        return mainframeDir(mainframe).resolve(URLEncoder.encode(dim, StandardCharsets.UTF_8));
    }

    private Path file(Column c) {
        return dimDir(c.mainframe(), c.dim()).resolve(c.cx() + "_" + c.cz() + COLUMN);
    }

    // ---- reading (any thread) ------------------------------------------------------------------------------------------

    /** The stored sections of a chunk column by section Y; empty when there are none (or the file is damaged). */
    public Map<Integer, Stored> column(UUID mainframe, String dim, int cx, int cz) {
        if (purged.contains(mainframe)) return Map.of();
        return read(file(new Column(mainframe, dim, cx, cz)));
    }

    private Map<Integer, Stored> read(Path file) {
        try {
            if (Files.size(file) > MAX_BYTES) return Map.of();
            return decode(Files.readAllBytes(file), logic);
        } catch (NoSuchFileException e) {
            return Map.of();
        } catch (IOException | RuntimeException e) {
            CloudLog.LOGGER.debug("[cccloud] Damaged voxel cache file {}", file, e);
            return Map.of();
        }
    }

    /** The last view area saved with {@link #saveArea}, per mainframe (decompressed); damaged ones are left out. */
    public Map<UUID, byte[]> areas() {
        var out = new HashMap<UUID, byte[]>();
        for (var dir : mainframeDirs()) {
            try {
                var file = dir.resolve(AREA);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES) continue;
                out.put(UUID.fromString(dir.getFileName().toString()), gunzip(Files.readAllBytes(file), 0));
            } catch (IOException | RuntimeException e) {
                CloudLog.LOGGER.debug("[cccloud] Damaged voxel area in {}", dir, e);
            }
        }
        return out;
    }

    private List<Path> mainframeDirs() {
        var out = new ArrayList<Path>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return out;
        try (var dirs = Files.list(root)) {
            for (var dir : (Iterable<Path>) dirs::iterator) {
                if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) && parse(dir.getFileName().toString()) != null) out.add(dir);
            }
        } catch (IOException | RuntimeException e) {
            CloudLog.LOGGER.debug("[cccloud] Could not list the voxel cache", e);
        }
        return out;
    }

    private static @Nullable UUID parse(String name) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- the format ----------------------------------------------------------------------------------------------------

    static byte[] encode(Map<Integer, Stored> sections, int logic) {
        try {
            var body = new ByteArrayOutputStream(1024);
            var out = new DataOutputStream(body);
            out.writeShort(sections.size());
            for (var e : sections.entrySet()) {
                var s = e.getValue();
                out.writeShort(e.getKey());
                out.writeLong(s.self());
                out.writeLong(s.content());
                out.writeByte(s.known());
                var palette = s.section().palette();
                out.writeShort(palette.size());
                for (var kind : palette) {
                    out.writeUTF(kind.id());
                    out.writeInt(kind.color());
                    out.writeByte(kind.flags());
                }
                out.writeInt(s.section().runs().length);
                out.write(s.section().runs());
            }
            var file = new ByteArrayOutputStream(body.size() / 2 + 16);
            var head = new DataOutputStream(file);
            head.write(MAGIC);
            head.writeByte(VERSION);
            head.writeInt(logic);
            head.write(VoxelBatch.gzip(body.toByteArray()));
            return file.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Strict: anything malformed, or another version, throws or gives an empty map. */
    static Map<Integer, Stored> decode(byte[] data, int logic) throws IOException {
        if (data.length < 9 || data[0] != MAGIC[0] || data[1] != MAGIC[1] || data[2] != MAGIC[2] || data[3] != MAGIC[3]) throw new IOException("not a voxel column");
        if (data[4] != VERSION) return Map.of();
        var head = new DataInputStream(new ByteArrayInputStream(data, 5, 4));
        if (head.readInt() != logic) return Map.of();
        var in = new DataInputStream(new ByteArrayInputStream(gunzip(data, 9)));
        int count = in.readUnsignedShort();
        if (count > MAX_SECTIONS) throw new IOException("too many sections");
        var out = new TreeMap<Integer, Stored>();
        for (int i = 0; i < count; i++) {
            int cy = in.readShort();
            long self = in.readLong(), content = in.readLong();
            int known = in.readUnsignedByte();
            int size = in.readUnsignedShort();
            if (size > MAX_PALETTE) throw new IOException("palette too large");
            var palette = new ArrayList<VoxelSection.Kind>(size);
            for (int k = 0; k < size; k++) palette.add(new VoxelSection.Kind(in.readUTF(), in.readInt(), in.readUnsignedByte()));
            int length = in.readInt();
            if (length < 0 || length > in.available()) throw new IOException("bad runs length");
            byte[] runs = in.readNBytes(length);
            int exposed = 0;
            for (int cell : VoxelSection.decode(runs)) {
                if (cell > size) throw new IOException("index out of palette");
                if (cell > 0) exposed++;
            }
            var list = List.copyOf(palette);
            out.put(cy, new Stored(self, content, known, new VoxelSection.Encoded(list, runs, exposed, VoxelSection.hash(list, runs))));
        }
        if (in.read() != -1) throw new IOException("trailing bytes");
        return out;
    }

    private static byte[] gunzip(byte[] data, int from) throws IOException {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(data, from, data.length - from))) {
            byte[] out = in.readNBytes(MAX_BYTES + 1);
            if (out.length > MAX_BYTES) throw new IOException("too large");
            return out;
        }
    }

    /**
     * True if {@code old} stays although a new build came in: nothing changed, or the blocks are the same and the new
     * build knows fewer neighbors. Chunks unload one after the other, so most unload builds miss a neighbor that the
     * section on disk still knows; that must neither cost a write nor bring back the seam at the chunk border.
     */
    static boolean keeps(Stored old, long self, long content, int known) {
        if (old.content() == content) return true;
        return old.self() == self && (known & ~old.known()) == 0 && known != old.known();
    }

    static boolean keeps(Stored old, Stored update) {
        return keeps(old, update.self(), update.content(), update.known());
    }

    // ---- writing (queued, one background thread) -------------------------------------------------------------------------

    private synchronized ExecutorService writer() {
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor(r -> {
                var t = new Thread(r, "cccloud-voxel-disk");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        return writer;
    }

    /** Queues sections of a chunk column. Any thread. @return false if dropped (busy, purged, cache switched off) */
    public boolean put(UUID mainframe, String dim, int cx, int cz, Map<Integer, Stored> sections) {
        if (sections.isEmpty() || purged.contains(mainframe) || limits.get().totalBytes() <= 0) return false;
        synchronized (pending) {
            var column = new Column(mainframe, dim, cx, cz);
            var waiting = pending.get(column);
            int added = sections.size();
            if (waiting != null) for (var cy : sections.keySet()) if (waiting.containsKey(cy)) added--;
            if (pendingSections + added > MAX_PENDING) return false;
            if (waiting == null) pending.put(column, waiting = new HashMap<>());
            waiting.putAll(sections);
            pendingSections += added;
            if (draining) return true;
            draining = true;
        }
        writer().execute(this::drain);
        return true;
    }

    private void drain() {
        while (true) {
            Column column;
            Map<Integer, Stored> sections;
            synchronized (pending) {
                var it = pending.entrySet().iterator();
                if (!it.hasNext()) {
                    draining = false;
                    return;
                }
                var e = it.next();
                it.remove();
                column = e.getKey();
                sections = e.getValue();
                pendingSections -= sections.size();
            }
            try {
                write(column, sections);
            } catch (RuntimeException e) {
                CloudLog.LOGGER.debug("[cccloud] Could not cache voxel column {}", column, e);
            }
        }
    }

    /** Merges the sections into the column's file. Writer thread. */
    private void write(Column column, Map<Integer, Stored> sections) {
        if (purged.contains(column.mainframe())) return;
        var file = file(column);
        var old = read(file);
        var merged = new TreeMap<>(old);
        boolean changed = false;
        for (var e : sections.entrySet()) {
            var have = old.get(e.getKey());
            if (have != null && keeps(have, e.getValue())) continue;
            merged.put(e.getKey(), e.getValue());
            changed = true;
        }
        try {
            if (!changed) {
                touchFile(file);
                return;
            }
            scan();
            long before = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? blocks(Files.size(file)) : 0;
            byte[] data = encode(merged, logic);
            atomicWrite(file, data);
            account(column.mainframe(), blocks(data.length) - before);
            enforceCaps(column.mainframe());
        } catch (IOException | RuntimeException e) {
            CloudLog.LOGGER.debug("[cccloud] Could not cache voxel column {}", column, e);
        }
    }

    private static void atomicWrite(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.getParent());
        var temp = file.resolveSibling(file.getFileName() + TEMP);
        Files.write(temp, data);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Bytes a file takes on most file systems. */
    private static long blocks(long size) {
        return (size + 4095) & ~4095L;
    }

    private void account(UUID mainframe, long delta) {
        sizes.merge(mainframe, delta, Long::sum);
        total += delta;
    }

    /**
     * A section was read: its column counts as used, at most once between two sweeps (a daily look at the file's time).
     * Any thread.
     */
    public void touch(UUID mainframe, String dim, int cx, int cz) {
        var column = new Column(mainframe, dim, cx, cz);
        if (!touched.add(column)) return;
        writer().execute(() -> touchFile(file(column)));
    }

    private static void touchFile(Path file) {
        try {
            long now = System.currentTimeMillis();
            if (now - Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis() >= TOUCH_AFTER_MS) Files.setLastModifiedTime(file, FileTime.fromMillis(now));
        } catch (IOException | RuntimeException e) {
            CloudLog.LOGGER.debug("[cccloud] Could not touch voxel column {}", file, e);
        }
    }

    /** Saves a mainframe's view area ({@code gzip} runs on the writer thread and may return null for "nothing to save"). */
    public void saveArea(UUID mainframe, Supplier<byte @Nullable []> gzip) {
        writer().execute(() -> {
            if (purged.contains(mainframe)) return;
            try {
                byte[] data = gzip.get();
                if (data != null) atomicWrite(mainframeDir(mainframe).resolve(AREA), data);
            } catch (IOException | RuntimeException e) {
                CloudLog.LOGGER.debug("[cccloud] Could not save the voxel area of {}", mainframe, e);
            }
        });
    }

    // ---- housekeeping (writer thread) ------------------------------------------------------------------------------------

    /** Deletes a purged mainframe's folder; nothing of it is written or read any more. */
    public void purge(UUID mainframe) {
        purged.add(mainframe);
        synchronized (pending) {
            for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (!e.getKey().mainframe().equals(mainframe)) continue;
                pendingSections -= e.getValue().size();
                it.remove();
            }
        }
        writer().execute(() -> {
            var dir = mainframeDir(mainframe);
            if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) return;
            int deleted = 0;
            try {
                deleted = deleteTree(dir);
            } catch (IOException | RuntimeException e) {
                CloudLog.LOGGER.warn("[cccloud] Could not delete the voxel cache of {}", mainframe, e);
            }
            var size = sizes.remove(mainframe);
            if (size != null) total -= size;
            if (deleted > 0) CloudLog.LOGGER.info("[cccloud] Deleted the voxel cache of purged mainframe {}: {} file(s)", mainframe, deleted);
        });
    }

    private static int deleteTree(Path dir) throws IOException {
        int[] deleted = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                deleted[0]++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, @Nullable IOException e) throws IOException {
                Files.deleteIfExists(d);
                return FileVisitResult.CONTINUE;
            }
        });
        return deleted[0];
    }

    /** Queues a sweep with the current limits (server start, once a day, config reload). */
    public void sweep() {
        writer().execute(() -> {
            try {
                int deleted = sweepNow(System.currentTimeMillis());
                if (deleted > 0) CloudLog.LOGGER.info("[cccloud] Deleted {} voxel cache file(s) nobody used or over the size caps", deleted);
            } catch (RuntimeException e) {
                CloudLog.LOGGER.warn("[cccloud] Could not clean up the voxel cache", e);
            }
        });
    }

    /**
     * Deletes left-over temp files and columns not written or read within the age cap, then the folders that became
     * empty (a mainframe's area goes with its last column once it is that old too), then enforces the size caps.
     *
     * @return files deleted
     */
    int sweepNow(long now) {
        var l = limits.get();
        long cutoff = l.maxAgeMs() > 0 ? now - l.maxAgeMs() : Long.MIN_VALUE;
        int deleted = 0;
        for (var dir : mainframeDirs()) {
            for (var item : items(dir, null)) {
                String name = item.file().getFileName().toString();
                boolean old = name.endsWith(TEMP) ? item.time() < now - HOUR_MS : name.endsWith(COLUMN) && item.time() < cutoff;
                if (old && delete(item.file())) deleted++;
            }
            deleteEmptyDims(dir);
            var area = dir.resolve(AREA);
            try {
                if (hasOnlyArea(dir) && Files.getLastModifiedTime(area, LinkOption.NOFOLLOW_LINKS).toMillis() < cutoff && delete(area)) deleted++;
            } catch (IOException | RuntimeException ignored) {
                // no area or still in use: kept
            }
            deleteIfEmpty(dir);
        }
        scanned = false;
        scan();
        deleted += enforceTotal(l);
        for (var mainframe : List.copyOf(sizes.keySet())) deleted += enforceMainframe(mainframe, l);
        touched.clear();
        return deleted;
    }

    private static boolean hasOnlyArea(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.allMatch(p -> p.getFileName().toString().equals(AREA));
        }
    }

    private void deleteEmptyDims(Path mainframeDir) {
        try (var dims = Files.list(mainframeDir)) {
            for (var dim : (Iterable<Path>) dims::iterator) {
                if (Files.isDirectory(dim, LinkOption.NOFOLLOW_LINKS)) deleteIfEmpty(dim);
            }
        } catch (IOException | RuntimeException ignored) {
            // the sweep comes back tomorrow
        }
    }

    private static void deleteIfEmpty(Path dir) {
        try (var entries = Files.list(dir)) {
            if (entries.findAny().isEmpty()) Files.deleteIfExists(dir);
        } catch (IOException | RuntimeException ignored) {
            // still in use or not empty: kept
        }
    }

    private static boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Files under a folder, without following links; {@code mainframe} goes into the items. */
    private static List<Item> items(Path dir, @Nullable UUID mainframe) {
        var out = new ArrayList<Item>();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) out.add(new Item(file, attrs.size(), attrs.lastModifiedTime().toMillis(), mainframe));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            CloudLog.LOGGER.debug("[cccloud] Could not list {}", dir, e);
        }
        return out;
    }

    /** Sizes of the columns on disk, once per run (afterwards every write keeps them current). */
    private void scan() {
        if (scanned) return;
        scanned = true;
        sizes.clear();
        total = 0;
        for (var dir : mainframeDirs()) {
            var id = parse(dir.getFileName().toString());
            if (id == null) continue;
            long size = 0;
            for (var item : items(dir, id)) if (item.file().getFileName().toString().endsWith(COLUMN)) size += blocks(item.size());
            if (size > 0) sizes.put(id, size);
            total += size;
        }
    }

    private void enforceCaps(UUID mainframe) {
        var l = limits.get();
        enforceMainframe(mainframe, l);
        enforceTotal(l);
    }

    private int enforceMainframe(UUID mainframe, Limits l) {
        if (sizes.getOrDefault(mainframe, 0L) <= l.mainframeBytes()) return 0;
        return evict(columns(List.of(mainframeDir(mainframe))), sizes.get(mainframe) - l.mainframeBytes() * 9 / 10);
    }

    private int enforceTotal(Limits l) {
        if (total <= l.totalBytes()) return 0;
        return evict(columns(mainframeDirs()), total - l.totalBytes() * 9 / 10);
    }

    private static List<Item> columns(List<Path> dirs) {
        var out = new ArrayList<Item>();
        for (var dir : dirs) {
            var id = parse(dir.getFileName().toString());
            for (var item : items(dir, id)) if (item.file().getFileName().toString().endsWith(COLUMN)) out.add(item);
        }
        return out;
    }

    /** Deletes the least recently used columns until {@code bytes} are free (down to 90 % of a cap, so this stays rare). */
    private int evict(List<Item> columns, long bytes) {
        columns.sort(Comparator.comparingLong(Item::time));
        int deleted = 0;
        long freed = 0;
        for (var item : columns) {
            if (freed >= bytes) break;
            if (!delete(item.file())) continue;
            deleted++;
            long size = blocks(item.size());
            freed += size;
            if (item.mainframe() != null) account(item.mainframe(), -size);
            deleteIfEmpty(item.file().getParent());
        }
        return deleted;
    }

    /** Bytes of the columns on disk (as of the last write or sweep). */
    public long bytesOnDisk() {
        return total;
    }

    /** Waits until everything asked for so far is done. @return false if that took longer than {@code millis} */
    public boolean flush(long millis) {
        ExecutorService w;
        synchronized (this) {
            w = writer;
        }
        if (w == null) return true;
        try {
            w.submit(() -> {
            }).get(millis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | ExecutionException | java.util.concurrent.RejectedExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Stops the writer after what is queued, waiting at most {@code millis}. */
    public void close(long millis) {
        flush(millis);
        synchronized (this) {
            if (writer != null) writer.shutdownNow();
            writer = null;
        }
    }
}
