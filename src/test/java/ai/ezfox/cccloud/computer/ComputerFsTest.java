package ai.ezfox.cccloud.computer;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.api.ApiError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The computer file system: path rules (no way out of the drive), quota, entry limit, rom, the dashboard store. */
class ComputerFsTest {
    @TempDir
    Path tmp;

    private ComputerFs fs(long capacity) {
        return new ComputerFs(tmp.resolve("drive"), capacity, ClasspathRom.standard());
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void normalizeStaysInsideTheRoot() {
        assertEquals("", ComputerFs.normalize("/"));
        assertEquals("", ComputerFs.normalize(".."));
        assertEquals("b", ComputerFs.normalize("../../a/../b"));
        assertEquals("etc/passwd", ComputerFs.normalize("../../../etc/passwd"));
        assertEquals("a/b", ComputerFs.normalize("\\a\\.\\b\\"));
        assertEquals("a/b", ComputerFs.normalize("//a//b"));
    }

    @Test
    void badNamesAreRefused() {
        for (String bad : List.of("...", "a/..../b", "a\u0000b", "con:", "a*", "x?", "a|b", "<x>", "a\"b", "dot.", "space ", "a\nb", "€")) {
            assertThrows(ApiError.class, () -> ComputerFs.normalize(bad), bad);
        }
        assertThrows(ApiError.class, () -> ComputerFs.normalize("a/".repeat(40)));
        assertThrows(ApiError.class, () -> ComputerFs.normalize("x".repeat(300)));
    }

    @Test
    void traversalNeverReachesTheHost() throws IOException {
        Files.writeString(tmp.resolve("secret.txt"), "secret");
        var fs = fs(10_000);
        fs.writeFile("../../secret.txt", bytes("mine"));
        assertEquals("secret", Files.readString(tmp.resolve("secret.txt")), "the file outside was not touched");
        assertEquals("mine", new String(fs.readFile("secret.txt"), StandardCharsets.ISO_8859_1));
        assertTrue(Files.exists(tmp.resolve("drive/secret.txt")));
    }

    @Test
    void symbolicLinksAreNotFollowed() throws IOException {
        Files.createDirectories(tmp.resolve("drive"));
        Files.writeString(tmp.resolve("outside.txt"), "outside");
        try {
            Files.createSymbolicLink(tmp.resolve("drive/link"), tmp.resolve("outside.txt"));
        } catch (UnsupportedOperationException | IOException e) {
            return; // no links on this system
        }
        var fs = fs(10_000);
        assertThrows(ApiError.class, () -> fs.readFile("link"));
        assertThrows(ApiError.class, () -> fs.writeFile("link", bytes("x")));
        assertFalse(fs.list("").contains("link"));
        assertEquals("outside", Files.readString(tmp.resolve("outside.txt")));
    }

    @Test
    void quotaCountsFilesAndFolders() {
        var fs = fs(2000);
        fs.writeFile("a.txt", new byte[1000]);
        assertEquals(1000, fs.freeSpace());
        fs.writeFile("a.txt", new byte[1500]);
        assertEquals(500, fs.freeSpace(), "an overwrite only counts the difference");
        var full = assertThrows(ApiError.class, () -> fs.writeFile("b.txt", new byte[600]));
        assertTrue(full.getMessage().contains("Out of space"), full.getMessage());
        assertFalse(fs.exists("b.txt"), "nothing written when it does not fit");
        assertThrows(ApiError.class, () -> fs.makeDir("x"), "a folder costs " + ComputerFs.DIR_COST);
        fs.delete("a.txt");
        assertEquals(2000, fs.freeSpace());
        fs.makeDir("x/y");
        assertEquals(2000 - 2 * ComputerFs.DIR_COST, fs.freeSpace());
    }

    @Test
    void usageIsMeasuredAgainAfterARestart() {
        var fs = fs(5000);
        fs.writeFile("dir/a", new byte[1000]);
        var again = fs(5000);
        assertEquals(5000 - 1000 - ComputerFs.DIR_COST, again.freeSpace());
    }

    @Test
    void romIsReadOnlyAndListedAtTheTop() {
        var fs = fs(10_000);
        assertTrue(fs.list("").contains("rom"));
        assertTrue(fs.isDir("rom/programs"));
        assertTrue(fs.isReadOnly("/rom/programs/shell.lua"));
        assertTrue(new String(fs.readFile("rom/programs/shell.lua"), StandardCharsets.ISO_8859_1).contains("startup"));
        assertThrows(ApiError.class, () -> fs.writeFile("rom/x", bytes("x")));
        assertThrows(ApiError.class, () -> fs.delete("rom"));
        assertThrows(ApiError.class, () -> fs.move("rom/programs", "p"));
        fs.copy("rom/programs/cat.lua", "cat.lua");
        assertTrue(fs.exists("cat.lua"));
        assertFalse(fs.isReadOnly("cat.lua"));
    }

    @Test
    void moveAndCopyRefuseToGoInsideThemselves() {
        var fs = fs(10_000);
        fs.makeDir("a/b");
        assertThrows(ApiError.class, () -> fs.move("a", "a/b/c"));
        assertThrows(ApiError.class, () -> fs.copy("a", "a/c"));
        fs.move("a", "z");
        assertTrue(fs.isDir("z/b"));
        assertThrows(ApiError.class, () -> fs.move("nothing", "q"));
        fs.writeFile("f", bytes("1"));
        assertThrows(ApiError.class, () -> fs.move("f", "z"), "the target exists");
    }

    @Test
    void entryLimitStopsFileSpam() {
        var fs = fs(Long.MAX_VALUE / 4);
        for (int i = 0; i < ComputerFs.MAX_ENTRIES; i++) fs.writeFile("f" + i, new byte[0]);
        var error = assertThrows(ApiError.class, () -> fs.writeFile("one-more", new byte[0]));
        assertTrue(error.getMessage().contains("Too many files"));
    }

    @Test
    void writesAreAtomicAndLeaveNoTemporaryFiles() {
        var fs = fs(10_000);
        fs.writeFile("x.txt", bytes("one"));
        fs.writeFile("x.txt", bytes("two"));
        assertEquals(List.of("rom", "x.txt"), fs.list(""));
    }

    @Test
    void dashboardStoreHidesTheRom() throws IOException {
        var fs = fs(10_000);
        var store = fs.store();
        store.writeNew("prog.lua", bytes("print(1)"));
        assertThrows(IOException.class, () -> store.writeNew("prog.lua", bytes("again")));
        assertEquals(List.of("prog.lua"), store.list(""));
        assertFalse(store.exists("rom"));
        assertEquals(10_000 - 8, store.remaining());
    }

    @Test
    void combineNameAndDir() {
        assertEquals("a/b/c", ComputerFs.combine("a/b", "c"));
        assertEquals("c", ComputerFs.combine("a", "../c"));
        assertEquals("c.lua", ComputerFs.name("/a/b/c.lua"));
        assertEquals("a/b", ComputerFs.dir("a/b/c"));
        assertEquals("", ComputerFs.dir("x"));
    }
}
