package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.claims.ClaimGuard;
import ai.ezfox.cccloud.compat.Cct;
import ai.ezfox.cccloud.computer.ComputerSide;
import ai.ezfox.cccloud.computer.InputEvent;
import com.google.gson.JsonParser;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * GameTests of the Cloud Computer block (docs/SPEC.md "Cloud Computers"): placing it gives an id and a running computer,
 * the screen gets the terminal as diffs and input is parsed strictly, redstone out and in on computer-local sides,
 * breaking keeps id and label on the item (and an edited or copied item cannot take over another computer), rights
 * (owner, stranger, claim block) and the dashboard's handle through {@link Cct#computer}.
 */
@SuppressWarnings("removal") // GameTestHelper#makeMockServerPlayerInLevel: the only mock player that is a ServerPlayer
public final class ComputerGameTests {
    private ComputerGameTests() {
    }

    private static CloudComputerBlockEntity place(GameTestHelper helper, BlockPos rel, boolean advanced, @Nullable ServerPlayer owner) {
        var block = advanced ? ComputerBlocks.ADVANCED_CLOUD_COMPUTER.get() : ComputerBlocks.CLOUD_COMPUTER.get();
        helper.setBlock(rel, block.defaultBlockState().setValue(CloudComputerBlock.FACING, Direction.NORTH));
        var be = at(helper, rel);
        be.placed(owner, null);
        return be;
    }

    private static CloudComputerBlockEntity at(GameTestHelper helper, BlockPos rel) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof CloudComputerBlockEntity be)) {
            throw helper.assertionException("no Cloud Computer at " + rel);
        }
        return be;
    }

    public static void placeAndBoot(GameTestHelper helper) {
        var owner = helper.makeMockServerPlayerInLevel();
        var rel = new BlockPos(2, 1, 2);
        var be = place(helper, rel, false, owner);
        helper.assertTrue(be.computerId() >= 0, "placing gives an id");
        helper.assertTrue(be.computer() != null, "the computer is loaded");
        helper.assertTrue(owner.getUUID().equals(be.owner()), "the placer owns it");
        helper.assertTrue(!be.advanced() && be.computer().terminal().width() == 51 && be.computer().terminal().height() == 19, "51x19 terminal");
        be.computer().turnOn();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertBlockProperty(rel, CloudComputerBlock.ON, true))
            .thenExecute(() -> be.computer().shutdown())
            .thenWaitUntil(() -> helper.assertBlockProperty(rel, CloudComputerBlock.ON, false))
            .thenSucceed();
    }

    /** The screen's sync on a computer that is off (no program writes in between): exact diffs. */
    public static void terminalSync(GameTestHelper helper) {
        var be = place(helper, new BlockPos(2, 1, 2), true, null);
        var computer = be.computer();
        var menu = new ComputerMenu(1, be.getBlockPos(), true);
        var first = ComputerScreens.updates(menu, computer);
        helper.assertTrue(first.size() == 2, "first sync: state and the full terminal, got " + first.size());
        helper.assertTrue(first.get(0).contains("\"on\":false"), "state says off");
        var mirror = new TerminalMirror();
        helper.assertTrue(mirror.apply(JsonParser.parseString(first.get(1)).getAsJsonObject()), "full frame applies");
        helper.assertTrue(ComputerScreens.updates(menu, computer).isEmpty(), "nothing changed, nothing sent");
        computer.terminal().setCursor(0, 3);
        computer.terminal().write("hello");
        var delta = ComputerScreens.updates(menu, computer);
        helper.assertTrue(delta.size() == 1 && delta.get(0).contains("\"full\":false"), "a write sends a delta");
        helper.assertTrue(mirror.apply(JsonParser.parseString(delta.get(0)).getAsJsonObject()), "delta applies");
        helper.assertTrue("hello".equals(row(mirror, 3, 5)), "the screen shows what was written: " + row(mirror, 3, 5));

        // input: the browser's format, strict
        var ok = ComputerScreens.parseInput("[{\"type\":\"char\",\"c\":\"a\"},{\"type\":\"mouse_click\",\"button\":1,\"x\":3,\"y\":4}]", true, 51, 19);
        helper.assertTrue(ok != null && ok.size() == 2 && ok.get(1).name().equals("mouse_click"), "advanced takes mouse input");
        helper.assertTrue(ComputerScreens.parseInput("[{\"type\":\"mouse_click\",\"button\":1,\"x\":3,\"y\":4}]", false, 51, 19) == null,
            "a basic computer refuses mouse input");
        helper.assertTrue(ComputerScreens.parseInput("[{\"type\":\"exec\",\"cmd\":\"x\"}]", true, 51, 19) == null, "unknown events drop the message");
        helper.assertTrue(ComputerScreens.parseInput("not json", true, 51, 19) == null, "garbage drops the message");
        helper.succeed();
    }

    /** A real program: the computer boots its shell, which writes its prompt; the screen gets it; shutdown reaches the screen. */
    public static void programWrites(GameTestHelper helper) {
        var be = place(helper, new BlockPos(2, 1, 2), true, null);
        var computer = be.computer();
        var menu = new ComputerMenu(1, be.getBlockPos(), true);
        var mirror = new TerminalMirror();
        computer.input(InputEvent.control("turn_on"), "test");
        helper.startSequence()
            .thenWaitUntil(() -> {
                for (String json : ComputerScreens.updates(menu, computer)) {
                    var msg = JsonParser.parseString(json).getAsJsonObject();
                    if (!msg.get("t").getAsString().equals("state")) mirror.apply(msg);
                }
                helper.assertTrue(mirror.ready() && !screenText(mirror).isBlank(), "the booted shell wrote to the screen");
            })
            .thenExecute(() -> {
                helper.assertTrue(computer.isOn(), "on");
                for (String json : ComputerScreens.updates(menu, computer)) {
                    var msg = JsonParser.parseString(json).getAsJsonObject();
                    if (!msg.get("t").getAsString().equals("state")) mirror.apply(msg);
                }
                var frame = Cct.computer(helper.getLevel(), be.getBlockPos()).frame();
                helper.assertTrue(frame != null && String.join("", frame.text()).equals(String.join("", mirror.frame().text())),
                    "the browser terminal sees the same picture");
                computer.input(InputEvent.control("shutdown"), "test");
            })
            .thenWaitUntil(() -> {
                ComputerScreens.updates(menu, computer);
                helper.assertTrue(menu.sentState != null && menu.sentState.contains("\"on\":false"), "the screen learns it is off");
            })
            .thenSucceed();
    }

    /**
     * The screen with a real program, as evidence: the colour test boots, "ls" is typed; the client mirror's picture is
     * drawn with the real font texture into {@code computer-screen-<kind>.png} (folder: {@code -Dcccloud.terminalShot},
     * default {@code computer-screenshots} in the run folder). Checks the program's text and that colours arrived.
     */
    public static void screenPicture(GameTestHelper helper) {
        var advanced = place(helper, new BlockPos(2, 1, 2), true, null);
        var basic = place(helper, new BlockPos(4, 1, 2), false, null);
        var shots = new java.util.ArrayList<CloudComputerBlockEntity>(java.util.List.of(advanced, basic));
        for (var be : shots) {
            try {
                ComputerDemo.install(be.computer());
            } catch (java.io.IOException e) {
                throw helper.assertionException("cannot write startup.lua: " + e);
            }
            be.computer().turnOn();
        }
        helper.startSequence()
            .thenWaitUntil(() -> {
                for (var be : shots) helper.assertTrue(TerminalImage.text(be.computer().terminal().frame()).contains("colour test"), "the colour test ran");
            })
            .thenIdle(20)
            .thenExecute(() -> {
                for (var be : shots) ComputerDemo.type(be.computer(), "ls", "test");
            })
            .thenWaitUntil(() -> {
                for (var be : shots) helper.assertTrue(TerminalImage.text(be.computer().terminal().frame()).contains("reactor.lua"), "ls listed the files");
            })
            .thenExecute(() -> {
                try {
                    var font = TerminalImage.font();
                    var dir = java.nio.file.Path.of(System.getProperty("cccloud.terminalShot", "computer-screenshots"));
                    java.nio.file.Files.createDirectories(dir);
                    for (var be : shots) {
                        var menu = new ComputerMenu(1, be.getBlockPos(), be.advanced());
                        var mirror = new TerminalMirror();
                        for (String json : ComputerScreens.updates(menu, be.computer())) {
                            var msg = JsonParser.parseString(json).getAsJsonObject();
                            if (!msg.get("t").getAsString().equals("state")) mirror.apply(msg);
                        }
                        var frame = mirror.frame();
                        helper.assertTrue(frame != null && TerminalImage.text(frame).contains("reactor.lua"), "the screen's mirror shows the program");
                        var image = TerminalImage.render(frame, font, be.advanced() && frame.colour(), 2);
                        int colours = TerminalImage.colours(image);
                        helper.assertTrue(be.advanced() ? colours >= 12 : colours >= 4, "colours on the screen: " + colours);
                        var file = dir.resolve("computer-screen-" + (be.advanced() ? "advanced" : "basic") + ".png");
                        javax.imageio.ImageIO.write(image, "png", file.toFile());
                        ai.ezfox.cccloud.CCCloud.LOGGER.info("[cccloud] Computer screen picture: {} ({} colours)\n{}", file.toAbsolutePath(), colours,
                            TerminalImage.text(frame));
                    }
                } catch (java.io.IOException e) {
                    throw helper.assertionException("cannot write the picture: " + e);
                }
            })
            .thenSucceed();
    }

    private static String row(TerminalMirror m, int y, int n) {
        var line = new StringBuilder();
        for (int x = 0; x < n; x++) line.append(m.charAt(x, y));
        return line.toString();
    }

    private static String screenText(TerminalMirror m) {
        var all = new StringBuilder();
        for (int y = 0; y < m.height(); y++) all.append(row(m, y, m.width()));
        return all.toString();
    }

    public static void redstone(GameTestHelper helper) {
        var rel = new BlockPos(2, 1, 2);
        var be = place(helper, rel, false, null);
        var pos = be.getBlockPos();
        var level = helper.getLevel();
        // facing north: left = west, right = east, back = south
        be.redstone().setOutput(ComputerSide.LEFT, 15);
        be.redstone().setOutput(ComputerSide.BACK, 7);
        helper.setBlock(rel.east(), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertTrue(level.getSignal(pos, Direction.EAST) == 15, "left output reaches the block to the west");
                helper.assertTrue(level.getSignal(pos, Direction.NORTH) == 7, "back output reaches the block to the south");
                helper.assertTrue(level.getSignal(pos, Direction.WEST) == 0, "nothing towards the east");
                helper.assertTrue(be.redstone().input(ComputerSide.RIGHT) == 15, "the redstone block on the right is an input");
                helper.assertTrue(be.redstone().input(ComputerSide.LEFT) == 0, "nothing on the left");
            })
            .thenExecute(() -> helper.setBlock(rel.east(), Blocks.AIR))
            .thenWaitUntil(() -> helper.assertTrue(be.redstone().input(ComputerSide.RIGHT) == 0, "input follows the world"))
            .thenSucceed();
    }

    public static void breakKeepsId(GameTestHelper helper) {
        var owner = helper.makeMockServerPlayerInLevel();
        var stranger = helper.makeMockServerPlayerInLevel();
        var rel = new BlockPos(2, 1, 2);
        var be = place(helper, rel, true, owner);
        int id = be.computerId();
        be.computer().setLabel("Base");
        var pos = be.getBlockPos();
        var drops = Block.getDrops(be.getBlockState(), helper.getLevel(), pos, be);
        helper.assertTrue(drops.size() == 1, "one item drops");
        ItemStack stack = drops.getFirst();
        var data = stack.get(ComputerBlocks.COMPUTER_DATA.get());
        helper.assertTrue(data != null && data.id() == id && data.label().equals("Base"), "the item keeps id and label");

        // while the original stands, a copy of the item gets a new id
        var copy = place(helper, new BlockPos(4, 1, 2), true, null);
        copy.placed(owner, data);
        helper.assertTrue(copy.computerId() != id, "a copy never shares a running computer");

        helper.destroyBlock(rel);
        helper.startSequence()
            .thenExecute(() -> {
                // a stranger with the item gets a fresh computer
                helper.setBlock(rel, ComputerBlocks.ADVANCED_CLOUD_COMPUTER.get().defaultBlockState());
                var foreign = at(helper, rel);
                foreign.placed(stranger, data);
                helper.assertTrue(foreign.computerId() != id, "another player's item cannot restore the computer");
                helper.destroyBlock(rel);
            })
            .thenExecute(() -> {
                helper.setBlock(rel, ComputerBlocks.ADVANCED_CLOUD_COMPUTER.get().defaultBlockState());
                var restored = at(helper, rel);
                restored.placed(owner, data);
                helper.assertTrue(restored.computerId() == id, "the owner gets the same computer back");
                helper.assertTrue("Base".equals(restored.computer().label()), "with its label");
            })
            .thenSucceed();
    }

    public static void rights(GameTestHelper helper) {
        var owner = helper.makeMockServerPlayerInLevel();
        var stranger = helper.makeMockServerPlayerInLevel();
        var be = place(helper, new BlockPos(2, 1, 2), false, owner);
        helper.assertTrue(be.mayUse(owner), "the owner may use it");
        helper.assertTrue(!be.mayUse(stranger), "a stranger may not");
        be.computer().turnOn();
        be.redstone().setOutput(ComputerSide.TOP, 15);
        be.applyClaimBlock(new ClaimGuard.Block(UUID.randomUUID(), "Other team"));
        helper.assertTrue(!be.computer().isOn(), "a claim of another team shuts it down");
        helper.assertTrue(!be.mayUse(owner), "and nobody may use it");
        helper.assertTrue(be.outputToward(Direction.UP) == 0, "its outputs are off");
        var handle = Cct.computer(helper.getLevel(), be.getBlockPos());
        handle.turnOn();
        helper.assertTrue(!handle.isOn(), "the dashboard cannot turn it on either");
        be.applyClaimBlock(null);
        helper.assertTrue(be.mayUse(owner), "usable again once the claim allows it");
        // a computer without owner (placed by a machine) is open
        var open = place(helper, new BlockPos(4, 1, 2), false, null);
        helper.assertTrue(open.mayUse(stranger), "no owner: everyone");
        helper.succeed();
    }

    public static void dashboardHandle(GameTestHelper helper) {
        var be = place(helper, new BlockPos(2, 1, 2), true, null);
        var handle = Cct.computer(helper.getLevel(), be.getBlockPos());
        helper.assertTrue(handle instanceof CloudComputerHandle, "Cct.computer finds the Cloud Computer (with or without CC: Tweaked)");
        helper.assertTrue(handle.id() == be.computerId() && handle.title().equals("Computer #" + be.computerId()), "id and title");
        helper.assertTrue(handle.advanced(), "advanced");
        helper.assertTrue(handle.frame() == null, "no picture while off");
        handle.turnOn();
        helper.assertTrue(handle.isOn() && handle.frame() != null && handle.frame().width() == 51, "on: the browser terminal gets a frame");
        handle.setLabel("Reactor");
        helper.assertTrue("Reactor".equals(handle.label()) && "Reactor".equals(handle.title()), "label from the dashboard");
        helper.assertTrue(handle.setOutput("top", 9) && handle.output("top") == 9, "redstone output from the Computer card");
        helper.assertTrue(handle.saveDir().equals("cccloud/computers/" + be.computerId()), "save folder");
        handle.queueEvent("char", "a");
        handle.queueEvent("cloud_action", "w1", 1);
        handle.shutdown();
        helper.assertTrue(!handle.isOn() && handle.output("top") == 0, "off: outputs read 0");
        helper.succeed();
    }
}
