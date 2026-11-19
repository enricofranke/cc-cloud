package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.engine.ScriptError;
import ai.ezfox.cccloud.script.ScriptFiles;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * The {@link CloudComputer} the registry hands out. Power changes happen on the server thread: {@link #turnOn},
 * {@link #shutdown} and {@link #reboot} directly, everything from other threads (a program's {@code os.shutdown}, a
 * browser's {@code reboot}, a program that ended) as a request handled by the next {@link #tick}.
 */
final class Computer implements CloudComputer {
    enum Power { TURN_ON, SHUTDOWN, REBOOT }

    private final ComputerRegistry registry;
    private final int id;
    private final ComputerTerminal terminal;
    private final InputLimiter limiter = new InputLimiter(System::nanoTime);
    private final AtomicReference<@Nullable Power> power = new AtomicReference<>();
    private final int[] lastInputs = new int[6];
    private final ComputerPeripherals peripherals = new ComputerPeripherals();
    private volatile ComputerHost host;
    private volatile @Nullable ComputerFs fs;
    private volatile boolean on;
    private volatile @Nullable ComputerRuntime runtime;
    private volatile @Nullable FileHandles files;
    private volatile @Nullable String lastError;
    /** A program ended: the next tick finishes the shutdown ({@code error} null: it ended normally). */
    private volatile boolean ended;
    private volatile boolean notify;
    private volatile long gameTimeOfDay;
    private volatile long gameDay;
    private long seenOutputs;
    /** Redstone output tokens (rate limit), refilled per second. */
    private double redstoneTokens;
    private long redstoneAt = System.nanoTime();

    Computer(ComputerRegistry registry, int id, ComputerHost host) {
        this.registry = registry;
        this.id = id;
        this.host = host;
        this.terminal = new ComputerTerminal(ComputerTerminal.DEFAULT_WIDTH, ComputerTerminal.DEFAULT_HEIGHT, host.advanced());
        this.redstoneTokens = 2.0 * registry.env().limits().redstonePerSecond();
        for (var side : ComputerSide.all()) lastInputs[side.ordinal()] = host.redstone().input(side);
    }

    void setHost(ComputerHost host) {
        this.host = host;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public @Nullable String label() {
        return registry.label(id);
    }

    @Override
    public void setLabel(@Nullable String label) {
        registry.setLabel(id, label);
        host.changed();
    }

    /** From a program (worker thread): the registry is thread-safe, the block hears about it on the next tick. */
    void setLabelFromProgram(@Nullable String label) {
        registry.setLabel(id, label);
        notify = true;
    }

    @Override
    public boolean isOn() {
        return on;
    }

    @Override
    public synchronized void turnOn() {
        if (on) return;
        var env = registry.env();
        var scheduler = env.scheduler();
        String problem = env.problem();
        if (scheduler == null || problem != null) {
            lastError = problem != null ? problem : "programs cannot run right now (the server is starting or stopping)";
            return;
        }
        on = true;
        ended = false;
        lastError = null;
        terminal.reset();
        terminal.setBlink(true);
        var handles = new FileHandles(fs(), env.limits().maxOpenFiles());
        files = handles;
        var rt = new ComputerRuntime("computer " + id, env.engine(), scheduler, env.limits(), env.boot(),
            r -> ComputerApis.modules(this, r, handles), this::runEnded);
        runtime = rt;
        host.changed();
        rt.start();
    }

    @Override
    public synchronized void shutdown() {
        if (!on) return;
        stopRun();
        terminal.reset();
        host.changed();
    }

    @Override
    public synchronized void reboot() {
        shutdown();
        turnOn();
    }

    private void stopRun() {
        on = false;
        ended = false;
        var rt = runtime;
        runtime = null;
        if (rt != null) rt.stop();
        var f = files;
        files = null;
        if (f != null) f.closeAll();
        if (host.redstone() instanceof RedstoneIo.Memory m) m.clearOutputs();
        else for (var side : ComputerSide.all()) host.redstone().setOutput(side, 0);
    }

    /** Worker or timer thread: the program ended (null error: normally) or failed. */
    private void runEnded(ComputerRuntime rt, @Nullable ScriptError error) {
        if (rt != runtime) return; // stopped on purpose
        if (error != null) {
            lastError = error.getMessage();
            showError(error.getMessage());
        }
        ended = true;
    }

    private void showError(String message) {
        terminal.setTextColour(14); // red
        terminal.setBackgroundColour(ComputerTerminal.BLACK);
        int y = Math.min(terminal.height() - 1, Math.max(0, terminal.cursorY() + 1));
        if (terminal.cursorX() == 0) y = Math.max(0, terminal.cursorY());
        if (y >= terminal.height() - 1) {
            terminal.scroll(1);
            y = terminal.height() - 1;
        }
        terminal.setCursor(0, y);
        String text = message.length() > 200 ? message.substring(0, 200) + "..." : message;
        terminal.write(text);
        terminal.setBlink(false);
    }

    /** Any thread: a power change for the next tick. */
    void requestPower(Power p) {
        power.set(p);
    }

    @Override
    public boolean queueEvent(String name, @Nullable Object... args) {
        var rt = runtime;
        return on && rt != null && rt.queue(name, args);
    }

    @Override
    public boolean input(InputEvent event, String source) {
        if (!limiter.allow(source)) return false;
        if (event.name().startsWith("mouse_") && !terminal.colour()) return false;
        switch (event.name()) {
            case "turn_on" -> requestPower(Power.TURN_ON);
            case "shutdown" -> requestPower(Power.SHUTDOWN);
            case "reboot" -> requestPower(Power.REBOOT);
            case "paste" -> {
                String text = InputEvent.cleanPaste((String) event.args()[0]);
                return !text.isEmpty() && queueEvent("paste", text);
            }
            default -> {
                return queueEvent(event.name(), event.args());
            }
        }
        return true;
    }

    @Override
    public ComputerTerminal terminal() {
        return terminal;
    }

    @Override
    public RedstoneIo redstone() {
        return host.redstone();
    }

    /** A program sets an output: rate limited ({@link ComputerLimits#redstonePerSecond}, burst twice that). */
    void setOutput(ComputerSide side, int level) {
        var io = host.redstone();
        if (io.output(side) == level) return;
        int perSecond = registry.env().limits().redstonePerSecond();
        synchronized (this) {
            long now = System.nanoTime();
            redstoneTokens = Math.min(2.0 * perSecond, redstoneTokens + (now - redstoneAt) / 1e9 * perSecond);
            redstoneAt = now;
            if (redstoneTokens < 1) throw new ApiError("too many redstone changes (at most " + perSecond + " a second)");
            redstoneTokens -= 1;
        }
        io.setOutput(side, level);
    }

    ComputerFs fs() {
        var f = fs;
        if (f == null) {
            synchronized (this) {
                f = fs;
                if (f == null) {
                    var env = registry.env();
                    f = new ComputerFs(registry.folder(id), env.limits().diskBytes(), env.rom());
                    fs = f;
                }
            }
        }
        return f;
    }

    @Override
    public ScriptFiles.Store drive() {
        return fs().store();
    }

    long gameTimeOfDay() {
        return gameTimeOfDay;
    }

    long gameDay() {
        return gameDay;
    }

    @Override
    public void tick() {
        var level = host.level();
        if (level != null) {
            long t = level.getServer().overworld().getOverworldClockTime();
            gameTimeOfDay = Math.floorMod(t, 24000L);
            gameDay = Math.floorDiv(t, 24000L) + 1;
        }
        var p = power.getAndSet(null);
        if (p != null) {
            switch (p) {
                case TURN_ON -> turnOn();
                case SHUTDOWN -> shutdown();
                case REBOOT -> reboot();
            }
        }
        if (ended) {
            synchronized (this) {
                if (ended) {
                    boolean failed = lastError != null;
                    stopRun();
                    if (!failed) terminal.reset();
                    host.changed();
                }
            }
        }
        peripherals.tick(host, on, this::queueEvent);
        if (on) {
            var io = host.redstone();
            boolean changed = false;
            for (var side : ComputerSide.all()) {
                int v = io.input(side);
                if (v != lastInputs[side.ordinal()]) {
                    lastInputs[side.ordinal()] = v;
                    changed = true;
                }
            }
            if (changed) queueEvent("redstone");
        }
        long outputs = host.redstone().outputs();
        if (outputs != seenOutputs) {
            seenOutputs = outputs;
            notify = true;
        }
        if (notify) {
            notify = false;
            host.changed();
        }
    }

    @Override
    public @Nullable String lastError() {
        return lastError;
    }

    ComputerPeripherals peripherals() {
        return peripherals;
    }

    /** Unloaded: the program stops, open files are written. */
    synchronized void close() {
        if (on) stopRun();
        peripherals.clear();
    }
}
