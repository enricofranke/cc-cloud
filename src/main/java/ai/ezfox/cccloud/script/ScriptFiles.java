package ai.ezfox.cccloud.script;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * File operations of the script import (docs/SPEC.md "Script import – HTTP API") on a computer's own drive: strict path
 * validation, size and quota limits, atomic writes. Pure Java over a {@link Store} (the game adapts CC's writable
 * save-dir mount), unit tested.
 */
public final class ScriptFiles {
    public static final int MAX_FILE = 256 * 1024;
    public static final int MAX_LIST = 500;
    public static final String RESERVED = ".cloud";
    private static final Pattern PART = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern DISK = Pattern.compile("disk[0-9]*");

    /** The computer's drive (paths are already validated, relative, '/'-separated). */
    public interface Store {
        boolean exists(String path) throws IOException;

        boolean isDirectory(String path) throws IOException;

        List<String> list(String dir) throws IOException;

        long size(String path) throws IOException;

        byte[] read(String path, int max) throws IOException;

        /** Writes the bytes to a new file (fails if it exists). */
        void writeNew(String path, byte[] data) throws IOException;

        void delete(String path) throws IOException;

        void rename(String from, String to) throws IOException;

        void makeDirectory(String path) throws IOException;

        long remaining() throws IOException;

        long capacity();
    }

    public record Entry(String path, long size, boolean dir) {
    }

    /** Result of an operation: null error = ok. */
    public record Result(@Nullable String error, int status, long size) {
        static Result ok(long size) {
            return new Result(null, 200, size);
        }

        static Result fail(int status, String error) {
            return new Result(error, status, 0);
        }
    }

    private ScriptFiles() {
    }

    /**
     * Normalizes a user path: {@code name} or {@code folder/name}; no {@code ..}, no absolute paths, no {@code rom/},
     * disk mounts or the reserved {@code .cloud} folder. Null if invalid.
     */
    public static @Nullable String validate(@Nullable String path) {
        if (path == null || path.isEmpty() || path.length() > 130 || path.startsWith("/") || path.contains("\\") || path.endsWith("/")) return null;
        String[] parts = path.split("/", -1);
        if (parts.length > 2) return null;
        for (String part : parts) {
            if (!PART.matcher(part).matches() || part.equals(".") || part.equals("..")) return null;
        }
        String first = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (parts.length == 2 || parts.length == 1) {
            if (first.equals("rom") || DISK.matcher(first).matches() || first.equals(RESERVED)) return null;
        }
        return path;
    }

    /** Files and folders two levels deep (at most {@link #MAX_LIST}), the reserved folder hidden. */
    public static List<Entry> list(Store store) throws IOException {
        var result = new ArrayList<Entry>();
        for (String name : sorted(store.list(""))) {
            if (result.size() >= MAX_LIST) break;
            if (name.equals(RESERVED)) continue;
            boolean dir = store.isDirectory(name);
            result.add(new Entry(name, dir ? 0 : store.size(name), dir));
            if (!dir) continue;
            for (String child : sorted(store.list(name))) {
                if (result.size() >= MAX_LIST) break;
                String path = name + "/" + child;
                boolean childDir = store.isDirectory(path);
                result.add(new Entry(path, childDir ? 0 : store.size(path), childDir));
            }
        }
        return result;
    }

    private static List<String> sorted(List<String> names) {
        var list = new ArrayList<>(names);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public static byte @Nullable [] read(Store store, String path) throws IOException {
        if (!store.exists(path) || store.isDirectory(path)) return null;
        if (store.size(path) > MAX_FILE) return null;
        return store.read(path, MAX_FILE);
    }

    /**
     * Writes a file atomically: the data goes to a temporary file in {@code .cloud/}, then replaces the target.
     * Checks the size limit, the overwrite flag and the computer's free space (the old file's size counts as free).
     */
    public static Result write(Store store, String path, byte[] data, boolean overwrite) throws IOException {
        if (data.length > MAX_FILE) return Result.fail(413, "too_large");
        boolean exists = store.exists(path);
        if (exists && store.isDirectory(path)) return Result.fail(409, "is_directory");
        if (exists && !overwrite) return Result.fail(409, "exists");
        long old = exists ? store.size(path) : 0;
        // Each file costs at least a little space in CC's accounting; keep a margin for the temporary copy.
        if (store.remaining() + old < data.length + 512L) return Result.fail(507, "quota");
        if (!store.exists(RESERVED)) store.makeDirectory(RESERVED);
        int slash = path.indexOf('/');
        if (slash > 0) {
            String dir = path.substring(0, slash);
            if (store.exists(dir) && !store.isDirectory(dir)) return Result.fail(409, "not_a_directory");
            if (!store.exists(dir)) store.makeDirectory(dir);
        }
        String temp = RESERVED + "/upload.tmp";
        if (store.exists(temp)) store.delete(temp);
        store.writeNew(temp, data);
        if (exists) store.delete(path);
        store.rename(temp, path);
        return Result.ok(data.length);
    }

    public static Result delete(Store store, String path) throws IOException {
        if (!store.exists(path)) return Result.fail(404, "not_found");
        store.delete(path);
        return Result.ok(0);
    }

    /** {@code startup.lua} that runs the uploaded file. The path is validated, so it needs no escaping beyond quotes. */
    public static byte[] startupFor(String path) {
        return ("-- Written by CC: Cloud (Script import): runs " + path + " at startup.\nshell.run(\"" + path + "\")\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
