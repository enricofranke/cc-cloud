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
 * A Cloud Computer in a running server starts a WebAssembly program from its shell: {@code greet.wasm} on its drive
 * (built from {@code wasm/assemblyscript/tests/computer-args.ts}) runs as {@code greet} with arguments, Ctrl+T ends
 * it at once and the shell goes on, a second run gets a typed character, then the shell finishes its startup.
 */
public final class WasmComputerGameTests {
    private WasmComputerGameTests() {
    }

    private static String screen(Computer c) {
        var t = c.terminal();
        var sb = new StringBuilder();
        for (int y = 0; y < t.height(); y++) sb.append(t.line(y).stripTrailing()).append('\n');
        return sb.toString();
    }

    public static void shellRunsWasm(GameTestHelper helper) {
        CloudGameTests.throttleForComputers(helper);
        var level = helper.getLevel();
        var registry = ComputerRegistry.get(level.getServer());
        var owner = helper.makeMockPlayer(GameType.SURVIVAL).getUUID();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        int id = registry.create();
        var computer = (Computer) registry.load(id, host(level, pos, owner, new RedstoneIo.Memory()), false);
        byte[] program;
        try (var in = WasmComputerGameTests.class.getResourceAsStream("/cccloud_gametest/wasm/computer-args.wasm")) {
            if (in == null) throw helper.assertionException("missing computer-args.wasm");
            program = in.readAllBytes();
        } catch (java.io.IOException e) {
            throw helper.assertionException("cannot read computer-args.wasm: " + e);
        }
        computer.fs().writeFile("greet.wasm", program);
        computer.fs().writeFile("startup.lua", """
            shell.run("greet first")
            local f = fs.open("after.txt", "w") f.close()
            shell.run("greet second")
            f = fs.open("done.txt", "w") f.close()
            """.getBytes(StandardCharsets.ISO_8859_1));
        helper.onEachTick(() -> {
            if (registry.get(id) != null) computer.tick();
        });
        helper.startSequence()
            .thenExecute(computer::turnOn)
            .thenWaitUntil(() -> helper.assertTrue(screen(computer).contains("args greet first"),
                "the shell started greet.wasm with its argument: " + screen(computer) + " error: " + computer.lastError()))
            .thenExecute(() -> computer.input(InputEvent.control("terminate"), "player"))
            .thenWaitUntil(() -> helper.assertTrue(computer.fs().exists("after.txt") && screen(computer).contains("Terminated"),
                "Ctrl+T ended it and the shell went on: " + screen(computer)))
            .thenWaitUntil(() -> helper.assertTrue(screen(computer).contains("args greet second"), "the second run: " + screen(computer)))
            .thenExecute(() -> computer.input(InputEvent.character('k'), "player"))
            .thenWaitUntil(() -> helper.assertTrue(computer.fs().exists("done.txt") && screen(computer).contains("got k"),
                "the program got the character and ended: " + screen(computer)))
            .thenExecute(() -> {
                helper.assertTrue(computer.lastError() == null, "no error: " + computer.lastError());
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
