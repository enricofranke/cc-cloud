package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.gametest.CloudGameTests;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * Cloud Computers in a running server without CC: Tweaked, through the registry (the block comes separately): a new
 * computer gets an id and its drive under the world save, boots the rom's shell on the script threads (never the
 * server thread), runs its startup program, which writes a file, sets a label and a redstone output and reads the
 * in-game time; then it is unloaded and keeps its label and files.
 */
public final class ComputerGameTests {
    private ComputerGameTests() {
    }

    public static void bootsAndRunsStartup(GameTestHelper helper) {
        CloudGameTests.throttleForComputers(helper); // programs run on their own threads in wall-clock time
        var level = helper.getLevel();
        var registry = ComputerRegistry.get(level.getServer());
        var owner = helper.makeMockPlayer(GameType.SURVIVAL).getUUID();
        var io = new RedstoneIo.Memory();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var host = host(level, pos, owner, io);
        int id = registry.create();
        var computer = (Computer) registry.load(id, host, false);
        computer.fs().writeFile("startup.lua", """
            local f = fs.open("hello.txt", "w")
            f.write("id " .. os.getComputerID() .. " time " .. tostring(type(os.time()) == "number") .. " day " .. tostring(os.day() >= 1))
            f.write(" peripherals " .. #peripheral.getNames())
            f.close()
            os.setComputerLabel("Test Rig")
            redstone.setAnalogOutput("front", 11)
            """.getBytes(StandardCharsets.ISO_8859_1));
        helper.onEachTick(() -> {
            if (registry.get(id) != null) computer.tick();
        });
        helper.startSequence()
            .thenExecute(computer::turnOn)
            .thenWaitUntil(() -> helper.assertTrue(computer.fs().exists("hello.txt") && io.output(ComputerSide.FRONT) == 11,
                "the startup program ran (error: " + computer.lastError() + ")"))
            .thenExecute(() -> {
                String text = new String(computer.fs().readFile("hello.txt"), StandardCharsets.ISO_8859_1);
                helper.assertTrue(text.equals("id " + id + " time true day true peripherals 0"), "file: " + text);
                helper.assertTrue("Test Rig".equals(registry.label(id)), "label: " + registry.label(id));
                var folder = registry.folder(id);
                helper.assertTrue(java.nio.file.Files.exists(folder.resolve("hello.txt")), "the drive is in the world save");
                registry.unload(id);
                helper.assertTrue(!computer.isOn() && registry.get(id) == null, "unloaded and stopped");
                helper.assertTrue(io.output(ComputerSide.FRONT) == 0, "outputs off");
                var again = (Computer) registry.load(id, host(level, pos, owner, new RedstoneIo.Memory()), false);
                helper.assertTrue("Test Rig".equals(again.label()) && again.fs().exists("hello.txt"), "label and files stay");
                registry.unload(id);
            })
            .thenSucceed();
    }

    private static ComputerHost host(ServerLevel level, BlockPos pos, UUID owner, RedstoneIo io) {
        return new ComputerHost() {
            @Override
            public ServerLevel level() {
                return level;
            }

            @Override
            public BlockPos pos() {
                return pos;
            }

            @Override
            public @Nullable UUID owner() {
                return owner;
            }

            @Override
            public RedstoneIo redstone() {
                return io;
            }

            @Override
            public @Nullable Direction direction(ComputerSide side) {
                return switch (side) {
                    case FRONT -> Direction.NORTH;
                    case BACK -> Direction.SOUTH;
                    case LEFT -> Direction.WEST;
                    case RIGHT -> Direction.EAST;
                    case TOP -> Direction.UP;
                    case BOTTOM -> Direction.DOWN;
                };
            }
        };
    }
}
