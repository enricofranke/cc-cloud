package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReportStackTest {
    private static final String CRASH_FILE = """
        ---- Minecraft Crash Report ----
        // Hi. I'm Minecraft, and I'm a crashaholic.

        Time: 2026-11-01 12:00:00
        Description: Exception in server tick loop

        java.lang.IllegalStateException: Could not read /home/steve/world/level.dat
        \tat TRANSFORMER/cccloud@0.17.0/ai.ezfox.cccloud.web.WebHub.tick(WebHub.java:120) ~[cccloud-0.17.0.jar%23266!/:?]
        \tat MC-BOOTSTRAP/net.minecraft@26.1.2/net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:900) ~[?:?]
        \tat MC-BOOTSTRAP/net.minecraft@26.1.2/net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:901) ~[?:?]
        \tat MC-BOOTSTRAP/cc.tweaked@1.116/dan200.computercraft.shared.Foo.bar(Foo.java:7) ~[?:?]
        \tat java.base/java.lang.Thread.run(Thread.java:1583) [?:?]
        Caused by: java.io.IOException: Disk of Steve12 is full
        \tat java.base/java.io.FileOutputStream.write(FileOutputStream.java:1) ~[?:?]
        \tat TRANSFORMER/cccloud@0.17.0/ai.ezfox.cccloud.web.Store.save(Store.java:33) ~[?:?]


        A detailed walkthrough of the error, its code path and all known details is as follows:
        ---------------------------------------------------------------------------------------

        -- Head --
        Thread: Server thread
        Stacktrace:
        \tat TRANSFORMER/cccloud@0.17.0/ai.ezfox.cccloud.secret.Below.never(Below.java:1) ~[?:?]
        -- System Details --
        Player Count: 1 / 20; [Steve12]
        JVM Flags: -Duser.home=/home/steve
        """;

    private static Throwable failure(String message, StackTraceElement... frames) {
        var e = new IllegalStateException(message);
        e.setStackTrace(frames);
        return e;
    }

    private static StackTraceElement own(String method, int line) {
        return new StackTraceElement("ai.ezfox.cccloud.web.WebHub", method, "WebHub.java", line);
    }

    private static StackTraceElement foreign(String cls, int line) {
        return new StackTraceElement(cls, "secretMethod", "X.java", line);
    }

    @Test
    void ownFramesKeepTheirDetail() {
        var cause = ReportStack.of(failure("x", own("tick", 5))).causes().getFirst();
        assertEquals("ai.ezfox.cccloud.web.WebHub.tick:5", cause.frames().getFirst().text());
    }

    @Test
    void knownModsKeepTheirDetail() {
        var cause = ReportStack.of(failure("x", foreign("dan200.computercraft.core.Foo", 3))).causes().getFirst();
        assertEquals("dan200.computercraft.core.Foo.secretMethod:3", cause.frames().getFirst().text());
    }

    @Test
    void foreignFramesAreOnlyAClassName() {
        var cause = ReportStack.of(failure("x", foreign("org.example.Other", 3), own("tick", 5))).causes().getFirst();
        assertEquals("org.example.Other", cause.frames().getFirst().text());
    }

    @Test
    void runsOfTheSameForeignClassCollapse() {
        var cause = ReportStack.of(failure("x", foreign("a.B", 1), foreign("a.B", 2), foreign("a.B", 3))).causes().getFirst();
        assertEquals(1, cause.frames().size());
    }

    @Test
    void framesAndCausesAreBounded() {
        var frames = new StackTraceElement[500];
        Arrays.setAll(frames, i -> own("m" + i, i));
        Throwable chain = failure("root", frames);
        for (int i = 0; i < 20; i++) chain = new RuntimeException("wrap", chain);
        var stack = ReportStack.of(chain);
        assertEquals(ReportStack.MAX_CAUSES, stack.causes().size());
        var deep = ReportStack.of(failure("x", frames));
        assertEquals(ReportStack.MAX_FRAMES, deep.causes().getFirst().frames().size());
    }

    @Test
    void aCycleOfCausesEnds() {
        var a = new RuntimeException("a");
        var b = new RuntimeException("b", a);
        a.initCause(b);
        assertEquals(2, ReportStack.of(a).causes().size());
    }

    @Test
    void foreignFailuresAreNotOurs() {
        assertFalse(ReportStack.of(failure("x", foreign("org.example.Other", 3))).ours());
        assertTrue(ReportStack.of(failure("x", foreign("org.example.Other", 3), own("tick", 1))).ours());
    }

    @Test
    void anOwnFrameInACauseIsOurs() {
        var outer = new RuntimeException("o", failure("x", own("tick", 1)));
        outer.setStackTrace(new StackTraceElement[] {foreign("org.example.Other", 1)});
        assertTrue(ReportStack.of(outer).ours());
    }

    @Test
    void luaMessagesAreNeverKept() {
        var stack = ReportStack.parse("Description: x\n\ndan200.computercraft.api.lua.LuaException: print('Steve12 lives at home')\n\tat ai.ezfox.cccloud.Foo.run(Foo.java:1)\n");
        assertNotNull(stack);
        assertNull(stack.causes().getFirst().message());
    }

    @Test
    void theFingerprintIgnoresLineNumbersAndMessages() {
        var one = ReportStack.of(failure("/home/steve", own("tick", 5), own("run", 9)));
        var two = ReportStack.of(failure("/home/anna", own("tick", 6), own("run", 11)));
        assertEquals(one.fingerprint(), two.fingerprint());
        assertTrue(one.fingerprint().matches("[0-9a-f]{16}"), one.fingerprint());
    }

    @Test
    void theFingerprintFollowsTheCode() {
        var one = ReportStack.of(failure("x", own("tick", 5)));
        assertNotEquals(one.fingerprint(), ReportStack.of(failure("x", own("run", 5))).fingerprint());
        var other = new IllegalArgumentException("x");
        other.setStackTrace(new StackTraceElement[] {own("tick", 5)});
        assertNotEquals(one.fingerprint(), ReportStack.of(other).fingerprint());
    }

    @Test
    void aWrapperDoesNotChangeTheFingerprintOfItsCause() {
        var plain = failure("x", own("tick", 5));
        var wrapper = new RuntimeException("wrapped", plain);
        wrapper.setStackTrace(new StackTraceElement[0]);
        assertEquals(ReportStack.of(plain).fingerprint(), ReportStack.of(wrapper).fingerprint());
    }

    @Test
    void readsTheTopOfACrashFile() {
        var stack = ReportStack.parse(CRASH_FILE);
        assertNotNull(stack);
        assertEquals(2, stack.causes().size());
        var top = stack.causes().getFirst();
        assertEquals("java.lang.IllegalStateException", top.cls());
        assertEquals("Could not read /home/steve/world/level.dat", top.message());
        assertEquals("ai.ezfox.cccloud.web.WebHub.tick:120", top.frames().getFirst().text());
        assertEquals("net.minecraft.server.MinecraftServer", top.frames().get(1).text());
        assertEquals("dan200.computercraft.shared.Foo.bar:7", top.frames().get(2).text());
        assertEquals("java.lang.Thread", top.frames().get(3).text());
        assertTrue(stack.ours());
    }

    @Test
    void neverReadsBelowTheStackTrace() {
        var stack = ReportStack.parse(CRASH_FILE);
        assertNotNull(stack);
        assertFalse(stack.causes().toString().contains("Below"));
        assertFalse(stack.causes().toString().contains("user.home"));
    }

    @Test
    void aCrashFileWithoutACrashIsNothing() {
        assertNull(ReportStack.parse(""));
        assertNull(ReportStack.parse("---- Minecraft Crash Report ----\nDescription: x\n\nA detailed walkthrough"));
    }

    @Test
    void foreignCrashFilesAreNotOurs() {
        var stack = ReportStack.parse("Description: x\n\njava.lang.Error: boom\n\tat org.example.Other.run(Other.java:1)\n");
        assertNotNull(stack);
        assertFalse(stack.ours());
    }

    @Test
    void theSameBugGetsOneFingerprintLiveAndFromAFile() {
        var live = new java.io.IOException("x");
        live.setStackTrace(new StackTraceElement[] {new StackTraceElement("ai.ezfox.cccloud.web.Store", "save", "Store.java", 33)});
        var file = ReportStack.parse("Description: x\n\njava.io.IOException: y\n\tat TRANSFORMER/cccloud@1/ai.ezfox.cccloud.web.Store.save(Store.java:40)\n");
        assertNotNull(file);
        assertEquals(ReportStack.of(live).fingerprint(), file.fingerprint());
    }
}
