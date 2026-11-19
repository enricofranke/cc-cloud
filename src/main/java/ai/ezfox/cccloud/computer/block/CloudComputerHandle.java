package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.ComputerHandle;
import ai.ezfox.cccloud.computer.CloudComputer;
import ai.ezfox.cccloud.computer.ComputerRegistry;
import ai.ezfox.cccloud.computer.ComputerSide;
import ai.ezfox.cccloud.computer.InputEvent;
import ai.ezfox.cccloud.script.ScriptFiles;
import ai.ezfox.cccloud.terminal.TerminalFrame;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * A Cloud Computer as the dashboard sees it ({@link ai.ezfox.cccloud.compat.Cct#computer}): the automatic Computer card,
 * the browser terminal and the script editor work with it exactly as with a CC: Tweaked computer. Server thread only.
 * The dashboard checks its own rights before it calls in; a computer blocked by a claim refuses everything here too.
 */
public final class CloudComputerHandle implements ComputerHandle {
    private final CloudComputerBlockEntity be;

    CloudComputerHandle(CloudComputerBlockEntity be) {
        this.be = be;
    }

    private @Nullable CloudComputer computer() {
        return be.isRemoved() ? null : be.computer();
    }

    @Override
    public int id() {
        return be.computerId();
    }

    @Override
    public String title() {
        var c = computer();
        return c == null ? "Computer #" + be.computerId() : c.title();
    }

    @Override
    public boolean isOn() {
        var c = computer();
        return c != null && c.isOn();
    }

    @Override
    public void turnOn() {
        var c = computer();
        if (c != null && !be.blocked()) c.turnOn();
    }

    @Override
    public void shutdown() {
        var c = computer();
        if (c != null) c.shutdown();
    }

    @Override
    public void reboot() {
        var c = computer();
        if (c != null && !be.blocked()) c.reboot();
    }

    /**
     * The browser's events (already checked by {@link ai.ezfox.cccloud.terminal.TerminalInput}) go through
     * {@link CloudComputer#input}, where the limits are; anything else (e.g. {@code cloud_action}, {@code cloud_run})
     * is queued as is.
     */
    @Override
    public void queueEvent(String name, @Nullable Object... args) {
        var c = computer();
        if (c == null || be.blocked()) return;
        Object[] a = args == null ? new Object[0] : args;
        if (InputEvent.CONTROLS.contains(name) || InputEvent.EVENTS.contains(name)) c.input(new InputEvent(name, a), "dashboard:" + be.computerId());
        else c.queueEvent(name, a);
    }

    @Override
    public @Nullable TerminalFrame frame() {
        var c = computer();
        return c == null || !c.isOn() ? null : c.terminal().frame();
    }

    @Override
    public boolean advanced() {
        return be.advanced();
    }

    /** {@code cccloud/computers/<id>} under the world save. */
    @Override
    public @Nullable String saveDir() {
        return be.computerId() < 0 ? null : ComputerRegistry.FOLDER + "/" + be.computerId();
    }

    @Override
    public @Nullable String label() {
        var c = computer();
        return c == null ? null : c.label();
    }

    @Override
    public void setLabel(@Nullable String label) {
        var c = computer();
        if (c != null) c.setLabel(label);
    }

    @Override
    public int input(String side) {
        var s = ComputerSide.byId(side);
        return s == null ? 0 : be.redstone().input(s);
    }

    @Override
    public int output(String side) {
        var s = ComputerSide.byId(side);
        return s == null || !isOn() ? 0 : be.redstone().output(s);
    }

    @Override
    public boolean setOutput(String side, int level) {
        var s = ComputerSide.byId(side);
        if (s == null || be.blocked()) return false;
        be.redstone().setOutput(s, level);
        return true;
    }

    @Override
    public boolean canSetOutputs() {
        return !be.blocked();
    }

    @Override
    public ScriptFiles.@Nullable Store drive(MinecraftServer server) {
        var c = computer();
        if (c == null) return null;
        try {
            return c.drive();
        } catch (UnsupportedOperationException e) {
            CCCloud.LOGGER.debug("[cccloud] computer #{} has no drive yet", be.computerId());
            return null;
        }
    }
}
