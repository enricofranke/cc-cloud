package ai.ezfox.cccloud.script;

import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.cc.CcComputers;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.WritableMount;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The CC computer behind a Cloud Uplink card (the computer the uplink is placed on) and its own drive, through CC's
 * writable save-dir mount ({@link ComputerCraftAPI#createSaveDirMount}) with the computer's configured capacity.
 * Server thread only.
 */
public final class ComputerTarget {
    private final CcComputers.Handle computer;
    private final String saveDir;

    private ComputerTarget(CcComputers.Handle computer, String saveDir) {
        this.computer = computer;
        this.saveDir = saveDir;
    }

    /** The computer an uplink sits on, or null (no computer there, not loaded, no id yet). */
    public static @Nullable ComputerTarget of(CloudUplinkBlockEntity uplink) {
        if (!(uplink.getLevel() instanceof ServerLevel level)) return null;
        var handle = CcComputers.find(level, uplink.getBlockPos().relative(uplink.getBlockState().getValue(ai.ezfox.cccloud.block.CloudUplinkBlock.FACING)));
        if (handle == null) return null;
        String dir = handle.saveDir();
        return dir == null ? null : new ComputerTarget(handle, dir);
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

    /** CC's disk space per computer (config {@code computerSpaceLimit}), 1 MB if it cannot be read. */
    static long capacity() {
        try {
            return dan200.computercraft.shared.config.ConfigSpec.computerSpaceLimit.get();
        } catch (RuntimeException | LinkageError e) {
            return 1_000_000L;
        }
    }

    /** The computer's drive as a {@link ScriptFiles.Store}. */
    public ScriptFiles.Store store(MinecraftServer server) {
        WritableMount mount = ComputerCraftAPI.createSaveDirMount(server, saveDir, capacity());
        return new ScriptFiles.Store() {
            @Override
            public boolean exists(String path) throws IOException {
                return mount.exists(path);
            }

            @Override
            public boolean isDirectory(String path) throws IOException {
                return mount.isDirectory(path);
            }

            @Override
            public List<String> list(String dir) throws IOException {
                var list = new ArrayList<String>();
                if (mount.exists(dir) && mount.isDirectory(dir)) mount.list(dir, list);
                return list;
            }

            @Override
            public long size(String path) throws IOException {
                return mount.getSize(path);
            }

            @Override
            public byte[] read(String path, int max) throws IOException {
                try (var channel = mount.openForRead(path)) {
                    var buffer = ByteBuffer.allocate((int) Math.min(max, Math.max(0, channel.size())));
                    while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                    }
                    return java.util.Arrays.copyOf(buffer.array(), buffer.position());
                }
            }

            @Override
            public void writeNew(String path, byte[] data) throws IOException {
                try (var channel = mount.openFile(path, Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                    var buffer = ByteBuffer.wrap(data);
                    while (buffer.hasRemaining()) channel.write(buffer);
                }
            }

            @Override
            public void delete(String path) throws IOException {
                mount.delete(path);
            }

            @Override
            public void rename(String from, String to) throws IOException {
                mount.rename(from, to);
            }

            @Override
            public void makeDirectory(String path) throws IOException {
                mount.makeDirectory(path);
            }

            @Override
            public long remaining() throws IOException {
                return mount.getRemainingSpace();
            }

            @Override
            public long capacity() {
                return mount.getCapacity();
            }
        };
    }
}
