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
    /** Folder levels of a path (editor file tree). */
    public static final int MAX_DEPTH = 8;
    public static final int MAX_PATH = 255;
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
     * Normalizes a user path: up to {@link #MAX_DEPTH} levels ({@code a/b/c.lua}); no {@code ..}, no absolute paths, no
     * {@code rom/}, disk mounts or the reserved {@code .cloud} folder. Null if invalid.
     */
    public static @Nullable String validate(@Nullable String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_PATH || path.startsWith("/") || path.contains("\\") || path.endsWith("/")) return null;
        String[] parts = path.split("/", -1);
        if (parts.length > MAX_DEPTH) return null;
        for (String part : parts) {
            if (!PART.matcher(part).matches() || part.equals(".") || part.equals("..")) return null;
        }
        String first = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (first.equals("rom") || DISK.matcher(first).matches() || first.equals(RESERVED)) return null;
        return path;
    }

    /** Files and folders two levels deep (at most {@link #MAX_LIST}), the reserved folder hidden. */
    public static List<Entry> list(Store store) throws IOException {
        return list(store, 2);
    }

    /**
     * Files and folders up to {@code depth} levels (editor tree: {@link #MAX_DEPTH}), depth first and sorted, at most
     * {@link #MAX_LIST} entries; the reserved folder, {@code rom} and disk mounts are hidden.
     */
    public static List<Entry> list(Store store, int depth) throws IOException {
        var result = new ArrayList<Entry>();
        walk(store, "", 1, Math.max(1, Math.min(MAX_DEPTH, depth)), result);
        return result;
    }

    private static void walk(Store store, String dir, int level, int depth, List<Entry> result) throws IOException {
        for (String name : sorted(store.list(dir))) {
            if (result.size() >= MAX_LIST) return;
            String path = dir.isEmpty() ? name : dir + "/" + name;
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (dir.isEmpty() && (lower.equals(RESERVED) || lower.equals("rom") || DISK.matcher(lower).matches())) continue;
            boolean isDir = store.isDirectory(path);
            result.add(new Entry(path, isDir ? 0 : store.size(path), isDir));
            if (isDir && level < depth) walk(store, path, level + 1, depth, result);
        }
    }

    /** Content hash for the editor's "changed meanwhile" check: the first 16 hex characters of SHA-256. */
    public static String hash(byte[] data) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            return java.util.HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Creates a folder (and its parents). */
    public static Result makeDirectory(Store store, String path) throws IOException {
        if (store.exists(path)) return store.isDirectory(path) ? Result.ok(0) : Result.fail(409, "exists");
        String error = parents(store, path);
        if (error != null) return Result.fail(409, error);
        store.makeDirectory(path);
        return Result.ok(0);
    }

    /** Creates the missing parent folders of a path; an error if one of them is a file. */
    private static @Nullable String parents(Store store, String path) throws IOException {
        String[] parts = path.split("/");
        var dir = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) dir.append('/');
            dir.append(parts[i]);
            String d = dir.toString();
            if (store.exists(d) && !store.isDirectory(d)) return "not_a_directory";
            if (!store.exists(d)) store.makeDirectory(d);
        }
        return null;
    }

    /** Renames/moves a file or folder; refuses to overwrite unless asked, and moving a folder into itself. */
    public static Result move(Store store, String from, String to, boolean overwrite) throws IOException {
        if (!store.exists(from)) return Result.fail(404, "not_found");
        if (from.equals(to)) return Result.ok(0);
        if (to.startsWith(from + "/")) return Result.fail(409, "into_itself");
        if (store.exists(to)) {
            if (!overwrite || store.isDirectory(to) || store.isDirectory(from)) return Result.fail(409, "exists");
            store.delete(to);
        }
        String error = parents(store, to);
        if (error != null) return Result.fail(409, error);
        store.rename(from, to);
        return Result.ok(0);
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
        String parentError = parents(store, path);
        if (parentError != null) return Result.fail(409, parentError);
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
