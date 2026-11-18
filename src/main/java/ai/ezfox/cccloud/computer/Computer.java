package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.script.ScriptFiles;
import java.util.ArrayDeque;
import org.jspecify.annotations.Nullable;

/**
 * The {@link CloudComputer} the registry hands out. Interface stage: life cycle, label, terminal and event queue; the
 * program runtime follows.
 */
final class Computer implements CloudComputer {
    static final int MAX_EVENTS = 256;

    private final ComputerRegistry registry;
    private final int id;
    private final ComputerTerminal terminal = new ComputerTerminal();
    private final ArrayDeque<Object[]> events = new ArrayDeque<>();
    private volatile ComputerHost host;
    private volatile boolean on;
    private volatile @Nullable String lastError;

    Computer(ComputerRegistry registry, int id, ComputerHost host) {
        this.registry = registry;
        this.id = id;
        this.host = host;
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

    @Override
    public boolean isOn() {
        return on;
    }

    @Override
    public synchronized void turnOn() {
        if (on) return;
        on = true;
        lastError = null;
        terminal.reset();
        host.changed();
    }

    @Override
    public synchronized void shutdown() {
        if (!on) return;
        on = false;
        events.clear();
        terminal.reset();
        if (host.redstone() instanceof RedstoneIo.Memory m) m.clearOutputs();
        host.changed();
    }

    @Override
    public synchronized void reboot() {
        shutdown();
        turnOn();
    }

    @Override
    public synchronized boolean queueEvent(String name, @Nullable Object... args) {
        if (!on || events.size() >= MAX_EVENTS) return false;
        var event = new Object[(args == null ? 0 : args.length) + 1];
        event[0] = name;
        if (args != null) System.arraycopy(args, 0, event, 1, args.length);
        events.add(event);
        return true;
    }

    @Override
    public void input(InputEvent event) {
        switch (event.name()) {
            case "turn_on" -> turnOn();
            case "shutdown" -> shutdown();
            case "reboot" -> reboot();
            default -> queueEvent(event.name(), event.args());
        }
    }

    @Override
    public ComputerTerminal terminal() {
        return terminal;
    }

    @Override
    public RedstoneIo redstone() {
        return host.redstone();
    }

    @Override
    public ScriptFiles.Store drive() {
        throw new UnsupportedOperationException("drive not built yet");
    }

    @Override
    public void tick() {
    }

    @Override
    public @Nullable String lastError() {
        return lastError;
    }

    synchronized void close() {
        on = false;
        events.clear();
    }
}
