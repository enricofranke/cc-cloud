package ai.ezfox.cccloud.computer;

import static ai.ezfox.cccloud.computer.TestComputers.screen;
import static ai.ezfox.cccloud.computer.TestComputers.shows;
import static ai.ezfox.cccloud.computer.TestComputers.until;
import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.runtime.Limits;
import ai.ezfox.cccloud.code.wasm.WasmEngine;
import ai.ezfox.cccloud.code.wasm.WasmSource;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cloud Computers running a WebAssembly program (modules from {@code wasm/assemblyscript/tests}, the computer SDK in
 * {@code sdk/computer.ts}): the program writes to the terminal, waits for a timer and a typed character, sets redstone;
 * the same budget as Lua programs stops an endless loop ("too long without yielding") and a memory hog.
 */
class WasmComputerTest {
    @TempDir
    Path tmp;
    private TestComputers computers;

    @AfterEach
    void close() {
        if (computers != null) computers.close();
    }

    private final RedstoneIo.Memory redstone = new RedstoneIo.Memory();

    private Computer computer(String program) {
        byte[] module;
        try (var in = WasmComputerTest.class.getResourceAsStream("/wasm/" + program + ".wasm")) {
            assertNotNull(in, "missing test module " + program + " (npm run build in wasm/assemblyscript)");
            module = in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        computers = new TestComputers(tmp, new WasmEngine(() -> Limits.DEFAULT), new WasmSource(module, null, null).encode());
        return computers.create(redstone);
    }

    @Test
    void programWritesToTheTerminalAndWaitsForEvents() {
        var c = computer("computer-hello");
        c.turnOn();
        until(c, 20_000, "the greeting", () -> shows(c, "Hello from WebAssembly #" + c.id()));
        until(c, 20_000, "the timer", () -> shows(c, "timer ok"));
        assertEquals(15, redstone.output(ComputerSide.TOP), "redstone set by the program");
        assertTrue(c.isOn(), "it waits for a character: " + c.lastError());
        assertTrue(c.input(InputEvent.character('q'), "player"));
        until(c, 20_000, "the character", () -> shows(c, "got q"));
        until(c, 10_000, "the program to end", () -> !c.isOn());
        assertNull(c.lastError(), screen(c));
    }

    @Test
    void endlessLoopIsStoppedLikeInLua() {
        var c = computer("computer-loop");
        computers.limits = new ComputerLimits(50, 150, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        long start = System.nanoTime();
        c.turnOn();
        until(c, 15_000, "the stop", () -> !c.isOn());
        assertNotNull(c.lastError());
        assertTrue(c.lastError().contains("too long without yielding"), c.lastError());
        assertTrue(System.nanoTime() - start > 1_500_000_000L, "the per-second share held it back in between");
    }

    @Test
    void memoryHogIsStopped() {
        var c = computer("computer-memhog");
        computers.limits = new ComputerLimits(500, 5000, 4L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        c.turnOn();
        until(c, 20_000, "the stop", () -> !c.isOn());
        assertNotNull(c.lastError());
        assertTrue(c.lastError().contains("out of memory"), c.lastError());
    }

    @Test
    void shutdownStopsAWaitingProgram() {
        var c = computer("computer-hello");
        c.turnOn();
        until(c, 20_000, "the timer", () -> shows(c, "timer ok"));
        c.shutdown();
        assertFalse(c.isOn());
        assertEquals(0, redstone.output(ComputerSide.TOP), "outputs off after shutdown");
        TestComputers.until(c, 5000, "the program thread to end", () -> computers.scheduler.threadCount() == 0);
    }
}
