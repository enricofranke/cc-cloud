package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.cc.CcComputers;
import ai.ezfox.cccloud.uplink.ComputerCard;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Auto mode with a real CC computer: the Computer card appears, a Lua program's widgets replace it, and the card comes
 * back after clear() and after the computer shuts down. Redstone outputs set through the card reach the world.
 */
public final class AutoModeTests {
    private static final BlockPos COMPUTER = new BlockPos(2, 1, 2);
    private static final BlockPos UPLINK = new BlockPos(3, 1, 2);

    private AutoModeTests() {
    }

    public static void computerCard(GameTestHelper helper) {
        CloudGameTests.throttleForComputers(helper);
        helper.setBlock(COMPUTER, BridgeTestSupport.block(helper, "computercraft:computer_normal"));
        // facing = direction from the uplink towards the block it sticks to (the computer is at -x).
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
        var level = helper.getLevel();
        var computerPos = helper.absolutePos(COMPUTER);

        helper.startSequence()
            .thenWaitUntil(() -> {
                var uplink = uplink(helper);
                helper.assertTrue(uplink.isAuto(), "uplink on a computer must show the Computer card");
                helper.assertTrue(uplink.getWidget(ComputerCard.POWER) != null && uplink.getWidget(ComputerCard.REDSTONE) != null, "card widgets");
                helper.assertTrue(uplink.getTitle().startsWith("Computer #"), "title from the computer: " + uplink.getTitle());
                helper.assertTrue("auto".equals(uplink.snapshot().get("mode").getAsString()), "snapshot mode");
            })
            .thenExecute(() -> {
                // A Lua program defines a widget: the card makes room.
                var uplink = uplink(helper);
                helper.assertTrue(uplink.beginLua(), "Lua may edit");
                try {
                    uplink.putWidget(WidgetFactory.create(WidgetType.LABEL, "hello", "Hi", null));
                } catch (WidgetFactory.WidgetException e) {
                    throw helper.assertionException(e.getMessage());
                }
                helper.assertTrue(!uplink.isAuto() && uplink.widgetCount() == 1, "Lua widgets replace the card");
                helper.assertTrue("lua".equals(uplink.mode()), "mode lua");
            })
            .thenIdle(25)
            .thenExecute(() -> helper.assertTrue(!uplink(helper).isAuto(), "the card must not come back while Lua widgets exist"))
            .thenExecute(() -> uplink(helper).clearWidgets())
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).isAuto(), "card is back after clear()"))
            .thenExecute(() -> {
                var handle = CcComputers.find(level, computerPos);
                helper.assertTrue(handle != null, "computer handle");
                handle.turnOn();
            })
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).computerCount() > 0, "the running computer attaches the uplink peripheral"))
            .thenExecute(() -> {
                var handle = CcComputers.find(level, computerPos);
                helper.assertTrue(handle.canSetOutputs(), "redstone outputs must be settable (CC internals reachable)");
                helper.assertTrue(handle.setOutput("top", 15), "set output");
            })
            .thenWaitUntil(() -> {
                var handle = CcComputers.find(level, computerPos);
                helper.assertTrue(handle.output("top") == 15, "output stored");
                int signal = level.getBestNeighborSignal(computerPos.above());
                helper.assertTrue(signal == 15, "the world sees the output above the computer, got " + signal);
                var widget = uplink(helper).getWidget(ComputerCard.REDSTONE);
                var top = widget == null ? null : widget.value().getAsJsonArray().get(0).getAsJsonObject();
                helper.assertTrue(top != null && "top".equals(top.get("side").getAsString()) && top.get("out").getAsInt() == 15,
                    "redstone_sides value mirrors the output: " + (widget == null ? null : widget.value()));
            })
            .thenExecute(() -> {
                var uplink = uplink(helper);
                uplink.beginLua();
                try {
                    uplink.putWidget(WidgetFactory.create(WidgetType.LABEL, "hello", "Hi", null));
                } catch (WidgetFactory.WidgetException e) {
                    throw helper.assertionException(e.getMessage());
                }
                CcComputers.find(level, computerPos).shutdown();
            })
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).isAuto() && uplink(helper).getWidget("hello") == null,
                "after the computer shut down the Lua widgets are dropped and the card is back"))
            .thenSucceed();
    }

    private static CloudUplinkBlockEntity uplink(GameTestHelper helper) {
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
        if (!(be instanceof CloudUplinkBlockEntity uplink)) throw helper.assertionException("uplink missing");
        return uplink;
    }
}
