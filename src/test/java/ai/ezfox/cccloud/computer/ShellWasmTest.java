package ai.ezfox.cccloud.computer;

import static ai.ezfox.cccloud.computer.TestComputers.file;
import static ai.ezfox.cccloud.computer.TestComputers.screen;
import static ai.ezfox.cccloud.computer.TestComputers.shows;
import static ai.ezfox.cccloud.computer.TestComputers.until;
import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.runtime.Limits;
import ai.ezfox.cccloud.code.wasm.WasmEngine;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * WebAssembly programs started from the Lua shell of a Cloud Computer ({@code name} or {@code name.wasm}): arguments,
 * events while it runs, Ctrl+T and shutdown end it at once, errors in the terminal, the shell goes on afterwards, at
 * most one at a time.
 */
class ShellWasmTest {
    @TempDir
    Path tmp;
    private TestComputers computers;

    @BeforeEach
    void setUp() {
        computers = new TestComputers(tmp);
        computers.wasm = new WasmEngine(() -> Limits.DEFAULT);
    }

    @AfterEach
    void close() {
        computers.close();
    }

    private static byte[] module(String name) {
        try (var in = ShellWasmTest.class.getResourceAsStream("/wasm/" + name + ".wasm")) {
            assertNotNull(in, name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private Computer withProgram(String file, String module) {
        var c = computers.create();
        c.fs().writeFile(file, module(module));
        return c;
    }

    @Test
    void runsByNameWithArgumentsAndGetsTheEvents() {
        var c = withProgram("greet.wasm", "computer-args");
        file(c, "startup.lua", """
            shell.run("greet one \\"two words\\"")
            local f = fs.open("done", "w") f.write("x") f.close()
            """);
        c.turnOn();
        until(c, 20_000, "the program's arguments", () -> shows(c, "args greet one two words"));
        assertTrue(c.input(InputEvent.character('k'), "player"));
        until(c, 20_000, "the character in the program", () -> shows(c, "got k"));
        until(c, 20_000, "the shell to go on", () -> c.fs().exists("done"));
        assertNull(c.lastError(), screen(c));
    }

    @Test
    void runsWithItsFullNameToo() {
        var c = withProgram("greet.wasm", "computer-args");
        file(c, "startup.lua", "shell.run(\"greet.wasm x\") local f = fs.open(\"done\", \"w\") f.close()");
        c.turnOn();
        until(c, 20_000, "the program", () -> shows(c, "args greet.wasm x"));
        c.input(InputEvent.character('z'), "p");
        until(c, 20_000, "the shell to go on", () -> c.fs().exists("done"));
    }

    @Test
    void ctrlTEndsTheProgramAtOnce() {
        var c = withProgram("wait.wasm", "computer-forever");
        file(c, "startup.lua", "shell.run(\"wait\") local f = fs.open(\"done\", \"w\") f.close()");
        c.turnOn();
        until(c, 20_000, "the program", () -> shows(c, "waiting"));
        c.input(InputEvent.control("terminate"), "player");
        until(c, 5_000, "Ctrl+T to end it", () -> c.fs().exists("done"));
        assertTrue(shows(c, "Terminated"), screen(c));
        assertTrue(c.isOn(), "the computer stays on, the shell goes on");
        until(c, 5_000, "the program thread to end", () -> computers.scheduler.threadCount() == 0);
    }

    @Test
    void shutdownEndsTheProgram() {
        var c = withProgram("wait.wasm", "computer-forever");
        file(c, "startup.lua", "shell.run(\"wait\")");
        c.turnOn();
        until(c, 20_000, "the program", () -> shows(c, "waiting"));
        assertEquals(1, computers.scheduler.threadCount());
        c.shutdown();
        until(c, 5_000, "the program thread to end", () -> computers.scheduler.threadCount() == 0);
        c.turnOn();
        until(c, 20_000, "it runs again after a reboot", () -> shows(c, "waiting"));
        c.reboot();
        until(c, 20_000, "rebooted into it again", () -> shows(c, "waiting") && computers.scheduler.threadCount() == 1);
    }

    @Test
    void errorsAndLimitsShowInTheTerminalAndTheShellGoesOn() {
        var c = computers.create();
        c.fs().writeFile("loop.wasm", module("computer-loop"));
        c.fs().writeFile("broken.wasm", new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0, 1, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f});
        c.fs().writeFile("wasi.wasm", module("wasi"));
        computers.limits = new ComputerLimits(200, 300, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);
        file(c, "startup.lua", """
            for _, p in ipairs({ "broken", "wasi", "loop" }) do shell.run(p) end
            local f = fs.open("done", "w") f.close()
            """);
        c.turnOn();
        until(c, 30_000, "all three to fail", () -> c.fs().exists("done"));
        String screen = screen(c);
        assertTrue(screen.contains("not a valid WebAssembly module"), screen);
        assertTrue(screen.contains("WASI"), screen);
        assertTrue(screen.contains("too long without yielding"), screen);
        assertTrue(c.isOn());
    }

    @Test
    void onlyOneProgramAtATime() {
        var c = withProgram("wait.wasm", "computer-forever");
        // a second start from inside the first is impossible (the shell waits); from Lua directly it is refused
        file(c, "startup.lua", "shell.run(\"wait\")");
        c.turnOn();
        until(c, 20_000, "the program", () -> shows(c, "waiting"));
        var e = assertThrows(ai.ezfox.cccloud.code.api.ApiError.class,
            () -> c.runtimeForTest().startWasm(computers.wasm, "x", "{}", java.util.List.of()));
        assertTrue(e.getMessage().contains("already running"), e.getMessage());
    }
}
