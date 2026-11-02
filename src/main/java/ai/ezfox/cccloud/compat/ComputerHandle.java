package ai.ezfox.cccloud.compat;

import ai.ezfox.cccloud.script.ScriptFiles;
import ai.ezfox.cccloud.terminal.TerminalFrame;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * A computer block next to an uplink, without CC: Tweaked types (from {@link Cct#computer}): the automatic Computer card
 * (label, power, redstone per side), the browser terminal and the script import. Server thread only.
 */
public interface ComputerHandle {
    int id();

    /** The label, or "Computer #id". */
    String title();

    boolean isOn();

    void turnOn();

    void shutdown();

    void reboot();

    /** Queues an event on the computer (it must be on). */
    void queueEvent(String name, @Nullable Object... args);

    /** A copy of the computer's terminal, or null while it is off. */
    @Nullable TerminalFrame frame();

    /** Advanced and command computers have colour and multishell. */
    boolean advanced();

    /** The computer's folder in the world save ({@code computer/<id>}), or null before it got an id. */
    @Nullable String saveDir();

    @Nullable String label();

    void setLabel(@Nullable String label);

    /** Redstone input on a computer-local side (works while the computer is off). */
    int input(String side);

    /** The output the computer currently emits on a side (0 while it is off). */
    int output(String side);

    /** Sets an output like {@code redstone.setAnalogOutput}. Returns false if not possible. */
    boolean setOutput(String side, int level);

    boolean canSetOutputs();

    /** The computer's own drive with its disk space limit, or null before it got an id. */
    ScriptFiles.@Nullable Store drive(MinecraftServer server);
}
