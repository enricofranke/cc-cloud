package ai.ezfox.cccloud.computer;

import static org.junit.jupiter.api.Assertions.fail;

import ai.ezfox.cccloud.code.engine.ScriptEngine;
import ai.ezfox.cccloud.code.lua.LuaEngine;
import ai.ezfox.cccloud.code.runtime.CpuClock;
import ai.ezfox.cccloud.code.runtime.ScriptScheduler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/** Computers on real script threads and the real rom, with the test thread acting as the server thread. */
final class TestComputers implements AutoCloseable {
    final ScriptScheduler scheduler;
    final ScriptEngine engine;
    /** The engine of WebAssembly programs started from the shell (null: none). */
    @Nullable ScriptEngine wasm;
    final ComputerRegistry registry;
    /** More CPU than the default so functional tests boot fast on a busy machine; limit tests set their own. */
    ComputerLimits limits = new ComputerLimits(500, 2000, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);

    TestComputers(Path root) {
        this(root, CpuClock.system());
    }

    /** {@code clock}: what the computers' CPU budget is measured with. */
    TestComputers(Path root, CpuClock clock) {
        this(root, new LuaEngine(ZoneId::systemDefault), null, clock);
    }

    /** Computers whose program runs on {@code engine}; {@code boot} null = the rom's boot code. */
    TestComputers(Path root, ScriptEngine engine, @Nullable String boot) {
        this(root, engine, boot, CpuClock.system());
    }

    TestComputers(Path root, ScriptEngine engine, @Nullable String boot, CpuClock clock) {
        this.scheduler = new ScriptScheduler(2, clock);
        this.engine = engine;
        registry = new ComputerRegistry(new ComputerData(), root, new ComputerRegistry.Environment() {
            @Override
            public ScriptEngine engine() {
                return engine;
            }

            @Override
            public @Nullable ScriptScheduler scheduler() {
                return scheduler;
            }

            @Override
            public ComputerLimits limits() {
                return limits;
            }

            @Override
            public @Nullable ScriptEngine wasm() {
                return wasm;
            }

            @Override
            public String boot() {
                return boot != null ? boot : ClasspathRom.standard().boot();
            }
        });
    }

    /** A new computer (off) with plain redstone. */
    Computer create(RedstoneIo redstone) {
        int id = registry.create();
        return (Computer) registry.load(id, ComputerHost.detached(redstone), false);
    }

    Computer create() {
        return create(new RedstoneIo.Memory());
    }

    /** Writes a file onto the computer's drive. */
    static void file(Computer c, String path, String text) {
        c.fs().writeFile(path, text.getBytes(StandardCharsets.ISO_8859_1));
    }

    static String read(Computer c, String path) {
        return new String(c.fs().readFile(path), StandardCharsets.ISO_8859_1);
    }

    /** Ticks the computer (as the server would) until the condition holds; fails after {@code millis}. */
    static void until(Computer c, long millis, String what, BooleanSupplier condition) {
        long end = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < end) {
            c.tick();
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }
        }
        fail("timed out waiting for " + what + "\nscreen:\n" + screen(c) + "\nlast error: " + c.lastError());
    }

    static String screen(Computer c) {
        var t = c.terminal();
        var sb = new StringBuilder();
        for (int y = 0; y < t.height(); y++) sb.append(t.line(y).stripTrailing()).append('\n');
        return sb.toString();
    }

    static boolean shows(Computer c, String text) {
        return screen(c).contains(text);
    }

    /** Boots a computer whose startup program is {@code source} and waits until the file {@code done} exists. */
    static void runStartup(Computer c, String source, long millis) {
        file(c, "startup.lua", source);
        c.turnOn();
        until(c, millis, "startup to finish", () -> c.fs().exists("done"));
    }

    @Override
    public void close() {
        registry.unloadAll();
        scheduler.close();
    }
}
