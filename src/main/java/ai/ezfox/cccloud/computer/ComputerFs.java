package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.script.ScriptFiles;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The file system a computer program sees: its own drive (a folder in the world save, with a size quota and an entry
 * limit) and the read-only {@code rom} folder from the mod. Paths are program paths ({@code /a/b}, {@code a/../b});
 * {@link #normalize} resolves them inside the root: {@code ..} never leaves it, there are no absolute host paths, no
 * symbolic links are followed, and names with characters that are special on some host systems are refused.
 *
 * <p>Writes go through {@link #writeFile} as whole files (open handles keep their content in memory and write it on
 * flush and close), so the quota is checked before anything reaches the disk. Thread-safe (one lock per drive).
 */
public final class ComputerFs {
    public static final int MAX_PATH = 255;
    public static final int MAX_NAME = 128;
    public static final int MAX_DEPTH = 32;
    /** Files and folders on one drive. */
    public static final int MAX_ENTRIES = 2048;
    /** What a folder costs from the quota (so empty folders cannot fill a disk for free). */
    public static final long DIR_COST = 512;
    public static final String ROM = "rom";
    private static final String BAD_CHARS = "\"*:<>?|\\";

    private final Path root;
    private final long capacity;
    private final Rom rom;
    private final Object lock = new Object();
    private long used = -1;
    private int entries;

    public ComputerFs(Path root, long capacity, Rom rom) {
        this.root = root.toAbsolutePath().normalize();
        this.capacity = capacity;
        this.rom = rom;
    }

    public long capacity() {
        return capacity;
    }

    // ---- paths ---------------------------------------------------------------------------------------------------------

    /**
     * A program path as a clean relative path ({@code ""} = the root): '/' and '\' separate, empty parts and {@code .}
     * vanish, {@code ..} goes up but never above the root. Throws {@link ApiError} for a bad name.
     */
    public static String normalize(String path) {
        if (path.length() > 4 * MAX_PATH) throw new ApiError("/" + cut(path) + ": Path too long");
        var parts = new ArrayList<String>();
        for (String part : path.split("[/\\\\]")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!parts.isEmpty()) parts.removeLast();
                continue;
            }
            // "..." and similar would be ".." on some host systems: only dots is not a name
            if (part.chars().allMatch(c -> c == '.')) throw new ApiError("/" + cut(path) + ": Invalid Path");
            if (part.length() > MAX_NAME) throw new ApiError("/" + cut(path) + ": Name too long");
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < 0x20 || c == 0x7f || c > 0xff || BAD_CHARS.indexOf(c) >= 0) throw new ApiError("/" + cut(path) + ": Invalid Path");
            }
            if (part.endsWith(".") || part.endsWith(" ") || windowsDevice(part)) throw new ApiError("/" + cut(path) + ": Invalid Path");
            parts.add(part);
            if (parts.size() > MAX_DEPTH) throw new ApiError("/" + cut(path) + ": Path too deep");
        }
        String out = String.join("/", parts);
        if (out.length() > MAX_PATH) throw new ApiError("/" + cut(path) + ": Path too long");
        return out;
    }

    /**
     * Names Windows treats as devices, with any extension and trailing spaces ({@code con}, {@code NUL.txt},
     * {@code com1 .log}, {@code LPT¹}): a world on a Windows host must never open one (the {@code .~tmp} file of an atomic
     * write would be one too).
     */
    static boolean windowsDevice(String name) {
        int dot = name.indexOf('.');
        String base = (dot < 0 ? name : name.substring(0, dot)).stripTrailing().toUpperCase(java.util.Locale.ROOT);
        return switch (base) {
            case "CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$" -> true;
            default -> base.length() == 4 && (base.startsWith("COM") || base.startsWith("LPT")) && "0123456789\u00b9\u00b2\u00b3".indexOf(base.charAt(3)) >= 0;
        };
    }

    public static String combine(String a, String b) {
        return normalize(a + "/" + b);
    }

    public static String name(String path) {
        String p = normalize(path);
        int slash = p.lastIndexOf('/');
        return p.isEmpty() ? "root" : p.substring(slash + 1);
    }

    public static String dir(String path) {
        String p = normalize(path);
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    private static boolean inRom(String p) {
        return p.equals(ROM) || p.startsWith(ROM + "/");
    }

    private static String romPath(String p) {
        return p.equals(ROM) ? "" : p.substring(ROM.length() + 1);
    }

    /** The host file of a normalized drive path; never outside the root (checked again after resolving). */
    private Path host(String p) {
        Path file = p.isEmpty() ? root : root.resolve(p).normalize();
        if (!file.startsWith(root)) throw new ApiError("/" + p + ": Invalid Path");
        // a symbolic link anywhere on the way (made outside the game) is not followed
        for (Path at = file; !at.equals(root) && at.startsWith(root); at = at.getParent()) {
            if (Files.isSymbolicLink(at)) throw new ApiError("/" + p + ": Access denied");
        }
        return file;
    }

    private static String cut(String path) {
        return path.length() > 64 ? path.substring(0, 64) + "..." : path;
    }

    // ---- reading -------------------------------------------------------------------------------------------------------

    public boolean exists(String path) {
        String p = normalize(path);
        if (p.isEmpty()) return true;
        if (inRom(p)) return rom.exists(romPath(p));
        return Files.exists(host(p), LinkOption.NOFOLLOW_LINKS);
    }

    public boolean isDir(String path) {
        String p = normalize(path);
        if (p.isEmpty()) return true;
        if (inRom(p)) return rom.isDir(romPath(p));
        return Files.isDirectory(host(p), LinkOption.NOFOLLOW_LINKS);
    }

    public boolean isReadOnly(String path) {
        String p = normalize(path);
        return inRom(p);
    }

    /** Names in a folder, sorted; the root also lists {@code rom}. */
    public List<String> list(String path) {
        String p = normalize(path);
        if (inRom(p)) {
            if (!rom.isDir(romPath(p))) throw new ApiError("/" + p + ": Not a directory");
            return rom.list(romPath(p));
        }
        Path dir = host(p);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            if (p.isEmpty()) return List.of(ROM);
            throw new ApiError("/" + p + ": Not a directory");
        }
        var names = new ArrayList<String>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                String n = child.getFileName().toString();
                if (Files.isSymbolicLink(child) || !validName(n)) continue;
                if (p.isEmpty() && n.equalsIgnoreCase(ROM)) continue;
                names.add(n);
                if (names.size() > MAX_ENTRIES) break;
            }
        } catch (IOException e) {
            throw new ApiError("/" + p + ": Access denied");
        }
        if (p.isEmpty()) names.add(ROM);
        Collections.sort(names);
        return names;
    }

    public long size(String path) {
        String p = normalize(path);
        if (inRom(p)) {
            if (!rom.exists(romPath(p))) throw new ApiError("/" + p + ": No such file");
            return rom.isDir(romPath(p)) ? 0 : rom.read(romPath(p)).length;
        }
        Path file = host(p);
        try {
            var attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isDirectory() ? 0 : attrs.size();
        } catch (IOException e) {
            throw new ApiError("/" + p + ": No such file");
        }
    }

    /** The bytes of a file (a drive is small, so files are read whole). */
    public byte[] readFile(String path) {
        String p = normalize(path);
        if (inRom(p)) {
            if (!rom.exists(romPath(p)) || rom.isDir(romPath(p))) throw new ApiError("/" + p + ": No such file");
            return rom.read(romPath(p));
        }
        Path file = host(p);
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) || !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new ApiError("/" + p + ": No such file");
        try {
            if (Files.size(file) > capacity + DIR_COST) throw new ApiError("/" + p + ": File too large");
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ApiError("/" + p + ": Access denied");
        }
    }

    public long freeSpace() {
        synchronized (lock) {
            return Math.max(0, capacity - used());
        }
    }

    // ---- writing -------------------------------------------------------------------------------------------------------

    /** Writes a whole file (creating its folders); refused when the drive would be over its quota. */
    public void writeFile(String path, byte[] data) {
        String p = writable(path);
        if (p.isEmpty()) throw new ApiError("/: Cannot write to directory");
        synchronized (lock) {
            Path file = host(p);
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) throw new ApiError("/" + p + ": Cannot write to directory");
            long old = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? sizeOf(file) : -1;
            int newDirs = missingDirs(dir(p));
            long need = data.length - Math.max(0, old) + newDirs * DIR_COST;
            if (used() + need > capacity) throw new ApiError("/" + p + ": Out of space");
            if (entries + newDirs + (old < 0 ? 1 : 0) > MAX_ENTRIES) throw new ApiError("/" + p + ": Too many files");
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".~tmp");
                Files.write(tmp, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                used = -1;
                throw new ApiError("/" + p + ": Access denied");
            }
            used += need;
            entries += newDirs + (old < 0 ? 1 : 0);
        }
    }

    public void makeDir(String path) {
        String p = writable(path);
        if (p.isEmpty()) return;
        synchronized (lock) {
            Path dir = host(p);
            if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) return;
                throw new ApiError("/" + p + ": File exists");
            }
            int newDirs = missingDirs(p);
            if (used() + newDirs * DIR_COST > capacity) throw new ApiError("/" + p + ": Out of space");
            if (entries + newDirs > MAX_ENTRIES) throw new ApiError("/" + p + ": Too many files");
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                used = -1;
                throw new ApiError("/" + p + ": Access denied");
            }
            used += newDirs * DIR_COST;
            entries += newDirs;
        }
    }

    /** Deletes a file or a folder with everything in it; nothing if it does not exist. */
    public void delete(String path) {
        String p = writable(path);
        if (p.isEmpty()) throw new ApiError("/: Access denied");
        synchronized (lock) {
            Path file = host(p);
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
            try {
                deleteTree(file);
            } catch (IOException e) {
                throw new ApiError("/" + p + ": Access denied");
            } finally {
                used = -1;
            }
        }
    }

    public void move(String from, String to) {
        String a = writable(from), b = writable(to);
        if (a.isEmpty() || b.isEmpty()) throw new ApiError("/: Access denied");
        if (b.equals(a) || b.startsWith(a + "/")) throw new ApiError("/" + b + ": Can't move a directory inside itself");
        synchronized (lock) {
            Path src = host(a), dst = host(b);
            if (!Files.exists(src, LinkOption.NOFOLLOW_LINKS)) throw new ApiError("/" + a + ": No such file");
            if (Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) throw new ApiError("/" + b + ": File exists");
            int newDirs = missingDirs(dir(b));
            if (used() + newDirs * DIR_COST > capacity) throw new ApiError("/" + b + ": Out of space");
            try {
                Files.createDirectories(dst.getParent());
                Files.move(src, dst);
            } catch (IOException e) {
                throw new ApiError("/" + b + ": Access denied");
            } finally {
                used = -1;
            }
        }
    }

    /** Copies a file or folder (also out of {@code rom}); refused when it does not fit. */
    public void copy(String from, String to) {
        String a = normalize(from), b = writable(to);
        if (b.isEmpty()) throw new ApiError("/: Access denied");
        // the top folder holds everything, so any target is inside it
        if (a.isEmpty() || b.equals(a) || b.startsWith(a + "/")) throw new ApiError("/" + b + ": Can't copy a directory inside itself");
        if (!exists(a)) throw new ApiError("/" + a + ": No such file");
        if (exists(b)) throw new ApiError("/" + b + ": File exists");
        copyTree(a, b, 0);
    }

    private void copyTree(String a, String b, int depth) {
        if (depth > MAX_DEPTH) throw new ApiError("/" + a + ": Path too deep");
        if (isDir(a)) {
            makeDir(b);
            for (String n : list(a)) {
                if (a.isEmpty() && n.equals(ROM)) continue;
                copyTree(a.isEmpty() ? n : a + "/" + n, b + "/" + n, depth + 1);
            }
        } else {
            writeFile(b, readFile(a));
        }
    }

    private String writable(String path) {
        String p = normalize(path);
        if (inRom(p)) throw new ApiError("/" + p + ": Access denied");
        return p;
    }

    private int missingDirs(String dir) {
        int n = 0;
        for (String d = dir; !d.isEmpty(); d = dir(d)) {
            Path h = host(d);
            if (Files.exists(h, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(h, LinkOption.NOFOLLOW_LINKS)) throw new ApiError("/" + d + ": Not a directory");
                break;
            }
            n++;
        }
        return n;
    }

    /** Bytes in use (files plus {@link #DIR_COST} per folder), measured once and then kept up to date. Under lock. */
    private long used() {
        if (used >= 0) return used;
        long[] sum = {0, 0};
        if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
                    @Override
                    public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(root)) {
                            sum[0] += DIR_COST;
                            sum[1]++;
                        }
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        sum[0] += attrs.isRegularFile() ? attrs.size() : 0;
                        sum[1]++;
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                // count what was seen
            }
        }
        used = sum[0];
        entries = (int) Math.min(Integer.MAX_VALUE, sum[1]);
        return used;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    private static void deleteTree(Path file) throws IOException {
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(file)) {
                for (Path child : stream) deleteTree(child);
            }
        }
        try {
            Files.delete(file);
        } catch (NoSuchFileException ignored) {
            // gone already
        }
    }

    private static boolean validName(String n) {
        try {
            return normalize(n).equals(n);
        } catch (ApiError e) {
            return false;
        }
    }

    // ---- the drive for the dashboard (script import, editor) -----------------------------------------------------------

    /** The drive (without {@code rom}) as the dashboard's file store. Paths there are already validated. */
    public ScriptFiles.Store store() {
        return new ScriptFiles.Store() {
            @Override
            public boolean exists(String path) {
                return !inRom(normalize(path)) && ComputerFs.this.exists(path);
            }

            @Override
            public boolean isDirectory(String path) {
                return !inRom(normalize(path)) && isDir(path);
            }

            @Override
            public List<String> list(String dir) {
                var names = new ArrayList<>(ComputerFs.this.list(dir));
                if (normalize(dir).isEmpty()) names.remove(ROM);
                return names;
            }

            @Override
            public long size(String path) {
                return ComputerFs.this.size(path);
            }

            @Override
            public byte[] read(String path, int max) throws IOException {
                byte[] data = readFile(path);
                if (data.length > max) throw new IOException("file too large");
                return data;
            }

            @Override
            public void writeNew(String path, byte[] data) throws IOException {
                if (ComputerFs.this.exists(path)) throw new IOException("exists");
                wrap(() -> writeFile(path, data));
            }

            @Override
            public void delete(String path) throws IOException {
                wrap(() -> ComputerFs.this.delete(path));
            }

            @Override
            public void rename(String from, String to) throws IOException {
                wrap(() -> move(from, to));
            }

            @Override
            public void makeDirectory(String path) throws IOException {
                wrap(() -> makeDir(path));
            }

            @Override
            public long remaining() {
                return freeSpace();
            }

            @Override
            public long capacity() {
                return capacity;
            }

            private void wrap(Runnable r) throws IOException {
                try {
                    r.run();
                } catch (ApiError e) {
                    throw new IOException(e.getMessage());
                }
            }
        };
    }

    // ---- rom -----------------------------------------------------------------------------------------------------------

    /** The read-only files every computer has under {@code rom}: the mod's own programs. */
    public interface Rom {
        boolean exists(String path);

        boolean isDir(String path);

        List<String> list(String dir);

        byte[] read(String path);

        /** No rom (tests). */
        Rom EMPTY = new Rom() {
            @Override
            public boolean exists(String path) {
                return path.isEmpty();
            }

            @Override
            public boolean isDir(String path) {
                return path.isEmpty();
            }

            @Override
            public List<String> list(String dir) {
                return List.of();
            }

            @Override
            public byte[] read(String path) {
                throw new ApiError("/rom/" + path + ": No such file");
            }
        };
    }
}
