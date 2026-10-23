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
            if (dirs.contains(from)) {
                for (var d : new ArrayList<>(dirs)) if (d.equals(from) || d.startsWith(from + "/")) {
                    dirs.remove(d);
                    dirs.add(to + d.substring(from.length()));
                }
                for (var f : new ArrayList<>(files.keySet())) if (f.startsWith(from + "/")) files.put(to + f.substring(from.length()), files.remove(f));
                return;
            }
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
            "DISK/x.lua", ".cloud/run_next", "a/b/c/d/e/f/g/h/i.lua", "a\\b.lua", "", "x/", "./x", "with space.lua", "ümlaut.lua", "a".repeat(65)}) {
            assertNull(ScriptFiles.validate(bad), bad);
        }
        assertEquals("startup.lua", ScriptFiles.validate("startup.lua"));
        assertEquals("programs/reactor.lua", ScriptFiles.validate("programs/reactor.lua"));
        assertEquals("a/b/c/d/e/f/g/h.lua", ScriptFiles.validate("a/b/c/d/e/f/g/h.lua"), "editor: 8 levels");
        assertNull(ScriptFiles.validate("x/rom/../y"));
    }

    @Test
    void editorTreeFoldersMoveAndHash() throws IOException {
        var store = new MemoryStore(100_000);
        assertNull(ScriptFiles.write(store, "lib/net/http.lua", "a".getBytes(StandardCharsets.UTF_8), false).error(), "parents are created");
        assertTrue(store.isDirectory("lib") && store.isDirectory("lib/net"));
        assertNull(ScriptFiles.makeDirectory(store, "data/logs").error());
        assertEquals(409, ScriptFiles.makeDirectory(store, "lib/net/http.lua").status(), "a file is in the way");
        store.makeDirectory("rom");
        store.makeDirectory("disk");
        var tree = ScriptFiles.list(store, ScriptFiles.MAX_DEPTH).stream().map(ScriptFiles.Entry::path).toList();
        assertEquals(List.of("data", "data/logs", "lib", "lib/net", "lib/net/http.lua"), tree, "rom, disk and .cloud are hidden");
        assertEquals(List.of("data", "data/logs", "lib", "lib/net"), ScriptFiles.list(store).stream().map(ScriptFiles.Entry::path).toList(), "two levels");
        assertNull(ScriptFiles.move(store, "lib/net/http.lua", "lib/http.lua", false).error());
        assertTrue(store.exists("lib/http.lua") && !store.exists("lib/net/http.lua"));
        assertEquals("into_itself", ScriptFiles.move(store, "lib", "lib/sub/lib", false).error());
        ScriptFiles.write(store, "x.lua", "x".getBytes(StandardCharsets.UTF_8), false);
        assertEquals("exists", ScriptFiles.move(store, "x.lua", "lib/http.lua", false).error());
        assertNull(ScriptFiles.move(store, "x.lua", "lib/http.lua", true).error(), "overwrite on request");
        assertNull(ScriptFiles.move(store, "lib", "src/lib", false).error(), "folders move with their files");
        assertTrue(store.exists("src/lib/http.lua"));
        assertEquals(404, ScriptFiles.move(store, "nope", "a", false).status());
        assertEquals(16, ScriptFiles.hash(new byte[0]).length());
        assertEquals("e3b0c44298fc1c14", ScriptFiles.hash(new byte[0]), "SHA-256 prefix, same as the browser computes");
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
