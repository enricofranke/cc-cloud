package ai.ezfox.cccloud.compat.cct;

import ai.ezfox.cccloud.script.ScriptFiles;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.WritableMount;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/**
 * A computer's own drive for the script import ({@link ai.ezfox.cccloud.script.ComputerTarget}): CC's writable
 * save-dir mount ({@link ComputerCraftAPI#createSaveDirMount}) with the computer's configured capacity.
 */
final class ComputerDrive {
    private ComputerDrive() {
    }

    /** CC's disk space per computer (config {@code computerSpaceLimit}), 1 MB if it cannot be read. */
    static long capacity() {
        try {
            return dan200.computercraft.shared.config.ConfigSpec.computerSpaceLimit.get();
        } catch (RuntimeException | LinkageError e) {
            return 1_000_000L;
        }
    }

    /** The drive of the computer saved under {@code saveDir} as a {@link ScriptFiles.Store}. */
    static ScriptFiles.Store of(MinecraftServer server, String saveDir) {
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
