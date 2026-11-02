package ai.ezfox.cccloud.script;

import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.Cct;
import ai.ezfox.cccloud.compat.ComputerHandle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The CC computer behind a Cloud Uplink card (the computer the uplink is placed on) and its own drive, through CC's
 * writable save-dir mount with the computer's configured capacity ({@link ComputerHandle#drive}). Without CC: Tweaked
 * there is never a target. Server thread only.
 */
public final class ComputerTarget {
    private final ComputerHandle computer;

    private ComputerTarget(ComputerHandle computer) {
        this.computer = computer;
    }

    /** The computer an uplink sits on, or null (no computer there, not loaded, no id yet). */
    public static @Nullable ComputerTarget of(CloudUplinkBlockEntity uplink) {
        if (!(uplink.getLevel() instanceof ServerLevel level)) return null;
        if (blocked(uplink)) return null;
        var handle = Cct.computer(level, uplink.getBlockPos().relative(uplink.getBlockState().getValue(ai.ezfox.cccloud.block.CloudUplinkBlock.FACING)));
        if (handle == null) return null;
        return handle.saveDir() == null ? null : new ComputerTarget(handle);
    }

    /** Claim guard: the uplink is blocked, or the computer is in a chunk another team claimed for its owners. */
    public static boolean blocked(CloudUplinkBlockEntity uplink) {
        if (ai.ezfox.cccloud.claims.ClaimGuard.check(uplink) != null) return true;
        return uplink.getLevel() instanceof ServerLevel level && ai.ezfox.cccloud.claims.ClaimGuard.blockedAt(level, uplink.attachedPos(), uplink.guardOwners());
    }

    public int id() {
        return computer.id();
    }

    public String title() {
        return computer.title();
    }

    public @Nullable String label() {
        return computer.label();
    }

    public boolean isOn() {
        return computer.isOn();
    }

    public boolean advanced() {
        return computer.advanced();
    }

    /** Queues {@code cloud_run <path>} (picked up by the ROM helper's background tab or a running cloud.ui). */
    public void queueRun(String path) {
        if (!computer.isOn()) computer.turnOn();
        computer.queueEvent("cloud_run", path);
    }

    public void reboot() {
        if (computer.isOn()) computer.reboot();
        else computer.turnOn();
    }

    /** The computer's drive as a {@link ScriptFiles.Store} (CC's save-dir mount with the computer's disk space limit). */
    public ScriptFiles.Store store(MinecraftServer server) {
        var drive = computer.drive(server);
        if (drive == null) throw new IllegalStateException("computer #" + computer.id() + " has no drive");
        return drive;
    }
}
