package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.script.ScriptFiles;
import org.jspecify.annotations.Nullable;

/**
 * A Cloud Computer: one program instance on the Cloud Scripts runtime (never on the server thread), a terminal, a
 * drive with a quota and redstone. Got from {@link ComputerRegistry#load}; the block entity calls {@link #tick} every
 * server tick and {@link ComputerRegistry#unload} when it goes away. All methods are server thread unless noted.
 */
public interface CloudComputer {
    /** The world-wide id (ComputerRegistry), stable over restarts; also the drive folder name. */
    int id();

    @Nullable String label();

    /** Sets or clears (null/blank) the label; at most {@link ComputerRegistry#MAX_LABEL} characters, control characters dropped. */
    void setLabel(@Nullable String label);

    /** The label, or "Computer #id". */
    default String title() {
        String label = label();
        return label != null ? label : "Computer #" + id();
    }

    boolean isOn();

    /** Boots the computer (runs {@code startup} after the shell starts). Nothing if it is on. */
    void turnOn();

    /** Stops the program, clears the terminal and the redstone outputs. */
    void shutdown();

    void reboot();

    /** Queues an event for the program (any thread). Dropped while off or when the queue is full; false then. */
    boolean queueEvent(String name, @Nullable Object... args);

    /** Input from the screen or browser: controls act, the rest is queued (any thread). */
    void input(InputEvent event);

    /** The terminal (thread-safe). Kept while off; cleared on boot and shutdown. */
    ComputerTerminal terminal();

    RedstoneIo redstone();

    /** The computer's own folder with its quota. */
    ScriptFiles.Store drive();

    /** Server tick: applies queued redstone outputs, timers due on the server side, host notifications. */
    void tick();

    /** The last error that stopped the program (shown on the screen), or null. */
    @Nullable String lastError();
}
