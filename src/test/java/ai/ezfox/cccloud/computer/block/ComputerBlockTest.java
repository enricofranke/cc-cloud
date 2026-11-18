package ai.ezfox.cccloud.computer.block;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.computer.ComputerSide;
import ai.ezfox.cccloud.terminal.TerminalFrame;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The pure parts of the Cloud Computer block: terminal mirror, input parsing, rights, hold shortcuts, sides. */
class ComputerBlockTest {
    static TerminalFrame frame(int cx, String... rows) {
        int h = rows.length, w = rows[0].length();
        var fg = new String[h];
        var bg = new String[h];
        Arrays.fill(fg, "0".repeat(w));
        Arrays.fill(bg, "f".repeat(w));
        var palette = new int[16];
        for (int i = 0; i < 16; i++) palette[i] = i * 0x111111;
        return new TerminalFrame(w, h, true, rows, fg, bg, cx, 0, true, palette);
    }

    static JsonObject diff(TerminalFrame a, TerminalFrame b) {
        return TerminalFrame.diff(a, b, "computer:1");
    }

    @Test
    void mirrorFollowsDiffs() {
        var mirror = new TerminalMirror();
        var a = frame(1, "hello", "     ");
        assertFalse(mirror.apply(diff(frame(0, "xxxxx", "     "), a)), "a delta before the first full frame is ignored");
        assertTrue(mirror.apply(diff(null, a)));
        assertEquals('h', mirror.charAt(0, 0));
        var b = frame(3, "hello", "> ok ");
        assertTrue(mirror.apply(diff(a, b)));
        var got = mirror.frame();
        assertArrayEquals(b.text(), got.text());
        assertEquals(3, got.cursorX());
        assertEquals(0x111111 * 15, mirror.background(0, 0), "palette by blit digit");
        // a resize is a full frame
        var c = frame(0, "ab", "cd", "ef");
        assertTrue(mirror.apply(diff(b, c)));
        assertEquals(2, mirror.width());
        assertEquals(3, mirror.height());
    }

    @Test
    void mirrorRejectsBrokenMessages() {
        var mirror = new TerminalMirror();
        assertTrue(mirror.apply(diff(null, frame(0, "abc"))));
        var before = mirror.frame();
        String[] broken = {
            "{\"t\":\"term\",\"full\":false,\"rows\":[{\"y\":5,\"text\":\"abc\",\"fg\":\"000\",\"bg\":\"fff\"}]}",
            "{\"t\":\"term\",\"full\":false,\"rows\":[{\"y\":0,\"text\":\"abcd\",\"fg\":\"0000\",\"bg\":\"ffff\"}]}",
            "{\"t\":\"term\",\"full\":false,\"rows\":[{\"y\":0,\"text\":\"abc\",\"fg\":\"0z0\",\"bg\":\"fff\"}]}",
            "{\"t\":\"term\",\"full\":true,\"w\":0,\"h\":1,\"rows\":[]}",
            "{\"t\":\"term\",\"full\":true,\"w\":999,\"h\":1,\"rows\":[]}",
            "{\"t\":\"term\",\"full\":false,\"palette\":[\"#ffffff\"]}",
            "{\"t\":\"term\",\"full\":false,\"rows\":\"x\"}",
            "{\"t\":\"other\"}",
        };
        for (var json : broken) assertFalse(mirror.apply(JsonParser.parseString(json).getAsJsonObject()), json);
        assertArrayEquals(before.text(), mirror.frame().text(), "nothing changed");
        assertFalse(mirror.apply(null));
        // control characters in a row are shown as spaces
        assertTrue(mirror.apply(JsonParser.parseString("{\"t\":\"term\",\"full\":false,\"rows\":[{\"y\":0,\"text\":\"a\\u0007c\",\"fg\":\"000\",\"bg\":\"fff\"}]}")
            .getAsJsonObject()));
        assertEquals(' ', mirror.charAt(1, 0));
    }

    @Test
    void inputIsParsedStrictly() {
        var ok = ComputerScreens.parseInput("[{\"type\":\"key\",\"key\":257,\"held\":false},{\"type\":\"paste\",\"text\":\"ls\"},{\"type\":\"terminate\"}]",
            false, 51, 19);
        assertNotNull(ok);
        assertEquals(3, ok.size());
        assertTrue(ok.get(2).control());
        assertNull(ComputerScreens.parseInput("[{\"type\":\"mouse_scroll\",\"dir\":1,\"x\":1,\"y\":1}]", false, 51, 19), "mouse only on advanced");
        assertNotNull(ComputerScreens.parseInput("[{\"type\":\"mouse_scroll\",\"dir\":1,\"x\":1,\"y\":1}]", true, 51, 19));
        assertNull(ComputerScreens.parseInput("[{\"type\":\"mouse_click\",\"button\":1,\"x\":52,\"y\":1}]", true, 51, 19), "outside the terminal");
        assertNull(ComputerScreens.parseInput("[]", true, 51, 19), "empty");
        assertNull(ComputerScreens.parseInput("[{\"type\":\"char\",\"c\":\"ab\"}]", true, 51, 19), "one character per char event");
        assertNull(ComputerScreens.parseInput("[{\"type\":\"key\",\"key\":9999,\"held\":false}]", true, 51, 19), "key code range");
        assertNull(ComputerScreens.parseInput("{", true, 51, 19), "garbage");
        var many = new StringBuilder("[");
        for (int i = 0; i < 33; i++) many.append(i == 0 ? "" : ",").append("{\"type\":\"char\",\"c\":\"a\"}");
        assertNull(ComputerScreens.parseInput(many.append("]").toString(), true, 51, 19), "more than 32 events");
        assertNull(ComputerScreens.parseInput("[" + "{\"type\":\"char\",\"c\":\"a\"},".repeat(1000) + "]", true, 51, 19), "oversized");
    }

    @Test
    void rights() {
        var owner = UUID.randomUUID();
        var friend = UUID.randomUUID();
        var stranger = UUID.randomUUID();
        assertTrue(ComputerAccess.mayUse(owner, owner, false, (a, b) -> false));
        assertTrue(ComputerAccess.mayUse(owner, friend, false, (a, b) -> b.equals(friend)), "same team");
        assertFalse(ComputerAccess.mayUse(owner, stranger, false, (a, b) -> b.equals(friend)));
        assertTrue(ComputerAccess.mayUse(owner, stranger, true, (a, b) -> false), "admin");
        assertTrue(ComputerAccess.mayUse(null, stranger, false, (a, b) -> false), "no owner");
        assertFalse(ComputerAccess.mayUse(owner, stranger, false, (a, b) -> {
            throw new IllegalStateException("team mod broke");
        }), "a failing team lookup denies");

        assertTrue(ComputerAccess.mayRestore(true, false, owner, owner));
        assertFalse(ComputerAccess.mayRestore(true, false, owner, stranger), "someone else's computer");
        assertFalse(ComputerAccess.mayRestore(true, true, owner, owner), "already running in another block");
        assertFalse(ComputerAccess.mayRestore(false, false, null, owner), "an id this world never handed out");
    }

    @Test
    void holdShortcuts() {
        var hold = new HoldShortcut();
        hold.press(HoldShortcut.KEY_T, true, 0);
        assertNull(hold.poll(999), "not before a second");
        assertEquals("terminate", hold.poll(1000));
        assertNull(hold.poll(5000), "once per hold");
        hold.release(HoldShortcut.KEY_T);
        hold.press(HoldShortcut.KEY_R, true, 100);
        hold.press(HoldShortcut.KEY_R, true, 600); // key repeat keeps the hold
        assertEquals("reboot", hold.poll(1100));
        hold.press(HoldShortcut.KEY_S, false, 0);
        assertNull(hold.poll(5000), "without Ctrl it is a plain key");
        hold.press(HoldShortcut.KEY_S, true, 0);
        hold.release(341); // left Ctrl let go
        assertNull(hold.poll(5000));
        hold.press(65, true, 0);
        assertNull(hold.poll(5000), "Ctrl+A is no shortcut");
        assertEquals(-1f, hold.progress(0));
    }

    @Test
    void sidesFollowTheFacing() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (var side : ComputerSide.all()) {
                assertEquals(side, CloudComputerBlockEntity.local(facing, CloudComputerBlockEntity.world(facing, side)), facing + " " + side);
            }
        }
        assertEquals(ComputerSide.FRONT, CloudComputerBlockEntity.local(Direction.NORTH, Direction.NORTH));
        assertEquals(ComputerSide.LEFT, CloudComputerBlockEntity.local(Direction.NORTH, Direction.WEST), "the computer's own left");
        assertEquals(ComputerSide.RIGHT, CloudComputerBlockEntity.local(Direction.SOUTH, Direction.WEST));
        assertEquals(ComputerSide.TOP, CloudComputerBlockEntity.local(Direction.EAST, Direction.UP));
    }
}
