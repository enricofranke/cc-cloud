package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.code.api.ApiError;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Open files of one computer run. A read handle holds the file's bytes; a write handle collects bytes in memory (at
 * most the drive's capacity) and writes the whole file on flush and close, so the quota is checked by
 * {@link ComputerFs#writeFile}. Handles are numbers; the rom's {@code fs.open} wraps them in a table. One run at a
 * time uses it (the program's worker thread).
 */
final class FileHandles {
    private final ComputerFs fs;
    private final int max;
    private final Map<Integer, Handle> open = new HashMap<>();
    private int next = 1;

    private static final class Handle {
        final String path;
        final boolean write;
        final boolean binary;
        byte[] data;
        int pos;
        @Nullable ByteArrayOutputStream out;
        boolean dirty;

        Handle(String path, boolean write, boolean binary) {
            this.path = path;
            this.write = write;
            this.binary = binary;
            this.data = new byte[0];
        }
    }

    FileHandles(ComputerFs fs, int max) {
        this.fs = fs;
        this.max = max;
    }

    /** {@code r}, {@code w}, {@code a}, each optionally with {@code b}. Returns the handle id. */
    synchronized int open(String path, String mode) {
        String m = mode.replace("b", "");
        boolean binary = mode.contains("b");
        if (!(m.equals("r") || m.equals("w") || m.equals("a")) || mode.length() - m.length() > 1) throw new ApiError("Unsupported mode");
        if (open.size() >= max) throw new ApiError("Too many files already open");
        String p = ComputerFs.normalize(path);
        Handle h;
        if (m.equals("r")) {
            if (!fs.exists(p) || fs.isDir(p)) throw new ApiError("/" + p + ": No such file");
            h = new Handle(p, false, binary);
            h.data = fs.readFile(p);
        } else {
            if (fs.isReadOnly(p)) throw new ApiError("/" + p + ": Access denied");
            if (fs.isDir(p)) throw new ApiError("/" + p + ": Cannot write to directory");
            h = new Handle(p, true, binary);
            h.out = new ByteArrayOutputStream();
            if (m.equals("a") && fs.exists(p)) h.out.writeBytes(fs.readFile(p));
            if (m.equals("w")) fs.writeFile(p, new byte[0]); // like any file system: opening for writing truncates now
            else if (!fs.exists(p)) fs.writeFile(p, new byte[0]);
        }
        int id = next++;
        open.put(id, h);
        return id;
    }

    /** {@code count} bytes (null: one character, or one byte as a number in binary mode); null at the end. */
    synchronized @Nullable Object read(int id, @Nullable Long count) {
        var h = reader(id);
        if (count == null) {
            if (h.pos >= h.data.length) return null;
            int b = h.data[h.pos++] & 0xff;
            return h.binary ? (Object) (long) b : (Object) String.valueOf((char) b);
        }
        if (count < 0) throw new ApiError("Cannot read a negative number of bytes");
        if (h.pos >= h.data.length) return count == 0 ? "" : null;
        int n = (int) Math.min(count, h.data.length - h.pos);
        String s = new String(h.data, h.pos, n, java.nio.charset.StandardCharsets.ISO_8859_1);
        h.pos += n;
        return s;
    }

    synchronized @Nullable String readLine(int id, boolean withEnd) {
        var h = reader(id);
        if (h.pos >= h.data.length) return null;
        int start = h.pos, end = start;
        while (end < h.data.length && h.data[end] != '\n') end++;
        int next = Math.min(h.data.length, end + 1);
        int stop = withEnd ? next : end;
        if (!withEnd && stop > start && h.data[stop - 1] == '\r') stop--;
        h.pos = next;
        return new String(h.data, start, stop - start, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    synchronized @Nullable String readAll(int id) {
        var h = reader(id);
        if (h.pos >= h.data.length) return h.data.length == 0 && h.pos == 0 ? "" : null;
        String s = new String(h.data, h.pos, h.data.length - h.pos, java.nio.charset.StandardCharsets.ISO_8859_1);
        h.pos = h.data.length;
        return s;
    }

    synchronized void write(int id, String text) {
        var h = writer(id);
        var out = h.out;
        if (out == null) throw new ApiError("attempt to use a closed file");
        if (out.size() + (long) text.length() > fs.capacity()) throw new ApiError("Out of space");
        for (int i = 0; i < text.length(); i++) out.write(text.charAt(i) & 0xff);
        h.dirty = true;
    }

    synchronized void flush(int id) {
        var h = writer(id);
        flush(h);
    }

    private void flush(Handle h) {
        if (!h.dirty || h.out == null) return;
        fs.writeFile(h.path, h.out.toByteArray());
        h.dirty = false;
    }

    /** Moves a read handle ({@code set}, {@code cur}, {@code end}); returns the new position. */
    synchronized long seek(int id, String whence, long offset) {
        var h = reader(id);
        long base = switch (whence) {
            case "set" -> 0;
            case "cur" -> h.pos;
            case "end" -> h.data.length;
            default -> throw new ApiError("bad argument #1 to 'seek' (invalid option '" + whence + "')");
        };
        long p = base + offset;
        if (p < 0) throw new ApiError("Position is negative");
        h.pos = (int) Math.min(p, Integer.MAX_VALUE);
        return p;
    }

    synchronized void close(int id) {
        var h = open.remove(id);
        if (h == null) return;
        if (h.write) {
            try {
                flush(h);
            } finally {
                h.out = null;
            }
        }
    }

    /** End of the run: everything still open is written and closed (errors are dropped). */
    synchronized void closeAll() {
        for (var h : open.values()) {
            if (!h.write) continue;
            try {
                flush(h);
            } catch (ApiError ignored) {
                // out of space: the last writes are lost, like a full disk
            }
        }
        open.clear();
    }

    synchronized int openCount() {
        return open.size();
    }

    private Handle reader(int id) {
        var h = open.get(id);
        if (h == null) throw new ApiError("attempt to use a closed file");
        if (h.write) throw new ApiError("file is not open for reading");
        return h;
    }

    private Handle writer(int id) {
        var h = open.get(id);
        if (h == null) throw new ApiError("attempt to use a closed file");
        if (!h.write) throw new ApiError("file is not open for writing");
        return h;
    }
}
