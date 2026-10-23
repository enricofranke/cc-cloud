package ai.ezfox.cccloud.script;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Path validation, overwrite flag, size and quota limits, atomic writes of the script import. */
class ScriptFilesTest {
    /** An in-memory drive with a capacity. */
    static final class MemoryStore implements ScriptFiles.Store {
        final Map<String, byte[]> files = new TreeMap<>();
        final java.util.Set<String> dirs = new java.util.TreeSet<>();
        final long capacity;
        final List<String> ops = new ArrayList<>();

        MemoryStore(long capacity) {
            this.capacity = capacity;
        }

        @Override
        public boolean exists(String path) {
            return files.containsKey(path) || dirs.contains(path);
        }

        @Override
        public boolean isDirectory(String path) {
            return dirs.contains(path);
        }

        @Override
        public List<String> list(String dir) {
            var out = new ArrayList<String>();
            String prefix = dir.isEmpty() ? "" : dir + "/";
            for (var p : files.keySet()) if (p.startsWith(prefix) && !p.substring(prefix.length()).contains("/")) out.add(p.substring(prefix.length()));
            for (var p : dirs) if (p.startsWith(prefix) && !p.substring(prefix.length()).contains("/")) out.add(p.substring(prefix.length()));
            return out;
        }

        @Override
        public long size(String path) {
            return files.get(path).length;
        }

        @Override
        public byte[] read(String path, int max) {
            return files.get(path);
        }

        @Override
        public void writeNew(String path, byte[] data) throws IOException {
            if (exists(path)) throw new IOException("exists");
            ops.add("write " + path);
            files.put(path, data);
        }

        @Override
        public void delete(String path) {
            ops.add("delete " + path);
            files.remove(path);
            dirs.remove(path);
        }

        @Override
        public void rename(String from, String to) {
            ops.add("rename " + from + " " + to);
            files.put(to, files.remove(from));
        }

        @Override
        public void makeDirectory(String path) {
            dirs.add(path);
        }

        @Override
        public long remaining() {
            long used = 0;
            for (var f : files.values()) used += f.length;
            return capacity - used;
        }

        @Override
        public long capacity() {
            return capacity;
        }
    }

    @Test
    void traversalAndMountsAreRefused() {
        for (String bad : new String[]{"..", "../x.lua", "a/../b", "/startup.lua", "rom/programs/x.lua", "rom", "disk/x.lua", "disk2/x.lua",
            "DISK/x.lua", ".cloud/run_next", "a/b/c.lua", "a\\b.lua", "", "x/", "./x", "with space.lua", "ümlaut.lua", "a".repeat(65)}) {
            assertNull(ScriptFiles.validate(bad), bad);
        }
        assertEquals("startup.lua", ScriptFiles.validate("startup.lua"));
        assertEquals("programs/reactor.lua", ScriptFiles.validate("programs/reactor.lua"));
    }

    @Test
    void overwriteQuotaAndAtomicWrites() throws IOException {
        var store = new MemoryStore(10_000);
        var data = "print('hi')".getBytes(StandardCharsets.UTF_8);
        assertNull(ScriptFiles.write(store, "hello.lua", data, false).error());
        assertEquals("exists", ScriptFiles.write(store, "hello.lua", data, false).error(), "no overwrite without the flag");
        store.ops.clear();
        assertNull(ScriptFiles.write(store, "hello.lua", "print('v2')".getBytes(StandardCharsets.UTF_8), true).error());
        assertEquals(List.of("write .cloud/upload.tmp", "delete hello.lua", "rename .cloud/upload.tmp hello.lua"), store.ops, "temp file, then replace");
        assertEquals("print('v2')", new String(store.files.get("hello.lua"), StandardCharsets.UTF_8));

        assertEquals("too_large", ScriptFiles.write(store, "big.lua", new byte[ScriptFiles.MAX_FILE + 1], true).error());
        var result = ScriptFiles.write(store, "full.lua", new byte[9_800], true);
        assertEquals("quota", result.error(), "the computer's free space");
        assertEquals(507, result.status());
        assertNull(ScriptFiles.write(store, "lib/util.lua", data, false).error(), "one sub folder");
        var listed = ScriptFiles.list(store).stream().map(ScriptFiles.Entry::path).toList();
        assertTrue(listed.contains("lib/util.lua") && listed.contains("hello.lua") && !listed.contains(".cloud"), listed.toString());
        assertEquals("not_found", ScriptFiles.delete(store, "nope.lua").error());
        assertNull(ScriptFiles.delete(store, "hello.lua").error());
        assertEquals("shell.run(\"x.lua\")", new String(ScriptFiles.startupFor("x.lua"), StandardCharsets.UTF_8).lines().toList().get(1));
    }
}
