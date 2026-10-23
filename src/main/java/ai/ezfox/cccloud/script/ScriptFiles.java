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

    // ---- Script Store records (docs/SPEC.md "Script Store – server side") ----------------------------------------------

    /** Installed store files of this computer: {@code {path: {id, version, sha256}}}. */
    public static final String STORE_FILE = RESERVED + "/store.json";
    public static final int MAX_STORE_RECORDS = 200;
    private static final Pattern STORE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern STORE_VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,31}");

    public record StoreRecord(String id, String version, String sha256) {
    }

    /** {@code id@version} → {id, version}, or null. */
    public static String @Nullable [] storeRef(@Nullable String ref) {
        if (ref == null) return null;
        int at = ref.indexOf('@');
        if (at <= 0) return null;
        String id = ref.substring(0, at), version = ref.substring(at + 1);
        return STORE_ID.matcher(id).matches() && STORE_VERSION.matcher(version).matches() ? new String[]{id, version} : null;
    }

    public static java.util.Map<String, StoreRecord> storeRecords(Store store) throws IOException {
        var map = new java.util.LinkedHashMap<String, StoreRecord>();
        if (!store.exists(STORE_FILE) || store.isDirectory(STORE_FILE)) return map;
        try {
            var json = com.google.gson.JsonParser.parseString(new String(store.read(STORE_FILE, 64 * 1024), java.nio.charset.StandardCharsets.UTF_8));
            if (!json.isJsonObject()) return map;
            for (var e : json.getAsJsonObject().entrySet()) {
                if (map.size() >= MAX_STORE_RECORDS || validate(e.getKey()) == null || !e.getValue().isJsonObject()) continue;
                var o = e.getValue().getAsJsonObject();
                String id = o.has("id") ? o.get("id").getAsString() : null, version = o.has("version") ? o.get("version").getAsString() : null;
                String sha = o.has("sha256") ? o.get("sha256").getAsString() : null;
                if (storeRef(id + "@" + version) == null || sha == null || !sha.matches("[0-9a-f]{64}")) continue;
                map.put(e.getKey(), new StoreRecord(id, version, sha));
            }
        } catch (RuntimeException e) {
            // a broken file (edited on the computer) counts as no records
        }
        return map;
    }

    public static void saveStoreRecords(Store store, java.util.Map<String, StoreRecord> records) throws IOException {
        var json = new com.google.gson.JsonObject();
        for (var e : records.entrySet()) {
            var o = new com.google.gson.JsonObject();
            o.addProperty("id", e.getValue().id());
            o.addProperty("version", e.getValue().version());
            o.addProperty("sha256", e.getValue().sha256());
            json.add(e.getKey(), o);
        }
        if (records.isEmpty()) {
            if (store.exists(STORE_FILE)) store.delete(STORE_FILE);
            return;
        }
        write(store, STORE_FILE, json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
    }

    /** Full SHA-256 (hex) like the store index. */
    public static String sha256(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Records under {@code path} (the file itself or a folder's content) are removed or moved to {@code to} (null = removed). */
    public static void moveStoreRecords(Store store, String path, @Nullable String to) throws IOException {
        var records = storeRecords(store);
        var out = new java.util.LinkedHashMap<String, StoreRecord>();
        boolean changed = false;
        for (var e : records.entrySet()) {
            String p = e.getKey();
            if (p.equals(path) || p.startsWith(path + "/")) {
                changed = true;
                if (to != null) out.put(to + p.substring(path.length()), e.getValue());
            } else {
                out.put(p, e.getValue());
            }
        }
        if (changed) saveStoreRecords(store, out);
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
