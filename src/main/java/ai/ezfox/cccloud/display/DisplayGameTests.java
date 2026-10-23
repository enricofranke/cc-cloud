package ai.ezfox.cccloud.display;

import static ai.ezfox.cccloud.ingame.InGameTestSupport.*;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.compat.server.MainframeSection;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.ingame.InGameRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Cloud Display: forming (a 3-wide row, a 3×2 wall, splitting when a block is broken – always consistent rectangles
 * with the right edge states) and content (an admin binds and picks a section; restricted sections and viewers are
 * refused; the origin carries the section snapshot; in-world clicks need operator and only work when enabled).
 */
public final class DisplayGameTests {
    private DisplayGameTests() {
    }

    private static void place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, InGameRegistry.CLOUD_DISPLAY.get().defaultBlockState()
            .setValue(CloudDisplayBlock.FACING, Direction.NORTH).setValue(CloudDisplayBlock.ORIENTATION, Direction.NORTH));
        display(helper, pos).expand(); // what setPlacedBy does for a player
    }

    static CloudDisplayBlockEntity display(GameTestHelper helper, BlockPos pos) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof CloudDisplayBlockEntity d)) {
            throw helper.assertionException("no display at " + pos);
        }
        return d;
    }

    /** Every display in the list belongs to a complete, consistent rectangle; returns the distinct origins. */
    static List<CloudDisplayBlockEntity> assertConsistent(GameTestHelper helper, List<BlockPos> positions) {
        var origins = new ArrayList<CloudDisplayBlockEntity>();
        for (var pos : positions) {
            var d = display(helper, pos);
            var origin = d.origin();
            helper.assertTrue(origin != null && origin.isOrigin(), "origin of " + pos);
            for (int x = 0; x < d.width(); x++) {
                for (int y = 0; y < d.height(); y++) {
                    var other = helper.getLevel().getBlockEntity(d.toWorldPos(x, y));
                    helper.assertTrue(other instanceof CloudDisplayBlockEntity o && o.xIndex() == x && o.yIndex() == y && o.width() == d.width()
                        && o.height() == d.height(), "member " + x + "," + y + " of the display at " + pos);
                }
            }
            boolean up = d.yIndex() < d.height() - 1, down = d.yIndex() > 0, left = d.xIndex() > 0, right = d.xIndex() < d.width() - 1;
            helper.assertBlockProperty(pos, CloudDisplayBlock.STATE, DisplayEdgeState.of(up, down, left, right));
            if (!origins.contains(origin)) origins.add(origin);
        }
        return origins;
    }

    public static void forming(GameTestHelper helper) {
        var row = List.of(new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(3, 1, 2));
        var top = List.of(new BlockPos(1, 2, 2), new BlockPos(2, 2, 2), new BlockPos(3, 2, 2));
        helper.startSequence()
            .thenExecute(() -> row.forEach(p -> place(helper, p)))
            .thenExecute(() -> {
                var origins = assertConsistent(helper, row);
                helper.assertTrue(origins.size() == 1 && origins.get(0).width() == 3 && origins.get(0).height() == 1, "one 3x1 display");
            })
            .thenExecute(() -> top.forEach(p -> place(helper, p)))
            .thenExecute(() -> {
                var all = new ArrayList<>(row);
                all.addAll(top);
                var origins = assertConsistent(helper, all);
                helper.assertTrue(origins.size() == 1 && origins.get(0).width() == 3 && origins.get(0).height() == 2, "one 3x2 wall, got " + origins.size());
            })
            .thenExecute(() -> helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR))
            .thenExecute(() -> {
                var rest = List.of(new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(3, 1, 2), new BlockPos(1, 2, 2), new BlockPos(3, 2, 2));
                var origins = assertConsistent(helper, rest);
                int blocks = origins.stream().mapToInt(o -> o.width() * o.height()).sum();
                helper.assertTrue(blocks == 5, "the 5 remaining blocks form consistent rectangles, got " + blocks + " in " + origins.size());
                for (var o : origins) helper.assertTrue(o.width() <= 3 && o.height() <= 2, "no rectangle spans the gap");
            })
            .thenSucceed();
    }

    public static void content(GameTestHelper helper) {
        var setup = mainframe(helper, "display_owner");
        var viewer = player(helper, "display_viewer");
        var stranger = player(helper, "display_stranger");
        placeUplink(helper);
        var screen = new BlockPos(1, 2, 2);
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                bindAndFill(helper, setup);
                grant(helper, setup, viewer, Role.VIEWER);
                place(helper, screen);
                var card = new ItemStack(ModRegistry.CLOUD_LINK_CARD.get());
                card.set(ModRegistry.LINKED_MAINFRAME.get(), setup.link());
                DisplayService.bind(stranger, display(helper, screen), card);
                helper.assertTrue(display(helper, screen).mainframeId() == null, "strangers cannot bind a display");
                DisplayService.bind(setup.owner(), display(helper, screen), card);
                helper.assertTrue(setup.id().equals(display(helper, screen).mainframeId()), "the owner binds it");
            })
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).isOnline(), "uplink online"))
            .thenExecute(() -> {
                var d = display(helper, screen);
                String uplinkId = uplink(helper).getUplinkId().toString();
                String serverCard = MainframeSection.sectionId(setup.id(), "server").toString();
                // The screen's actions, as the menu would send them.
                setup.owner().containerMenu = new DisplayMenu(91, d.getBlockPos(), "{}");
                viewer.containerMenu = new DisplayMenu(92, d.getBlockPos(), "{}");
                DisplayService.onAction(setup.owner(), new InGamePayloads.DisplayAction(91, "select", serverCard));
                helper.assertTrue(d.sectionId().isEmpty(), "restricted sections (Server card) cannot be shown on a display");
                DisplayService.onAction(viewer, new InGamePayloads.DisplayAction(92, "select", uplinkId));
                helper.assertTrue(d.sectionId().isEmpty(), "viewers cannot configure a display");
                DisplayService.onAction(setup.owner(), new InGamePayloads.DisplayAction(91, "select", uplinkId));
                helper.assertTrue(d.sectionId().equals(uplinkId), "the owner picks the uplink's section");
                helper.assertTrue(setup.owner().getUUID().equals(d.configuredBy()), "remembers who configured it");
            })
            .thenWaitUntil(() -> {
                var content = display(helper, screen).content();
                helper.assertTrue(content.contains("Hello from the cloud") && content.contains("\"lamp\""), "the display shows the section: " + content);
            })
            .thenExecute(() -> {
                var d = display(helper, screen);
                var section = DisplayService.parse(d.content());
                var layout = DisplayLayout.layout(section, d.width(), d.height());
                var lamp = layout.boxes().stream().filter(b -> b.id().equals("lamp")).findFirst().orElse(null);
                var secret = layout.boxes().stream().filter(b -> b.id().equals("secret")).findFirst().orElse(null);
                helper.assertTrue(lamp != null && lamp.clickable(), "the lamp toggle is clickable in-world");
                helper.assertTrue(secret == null || !secret.clickable(), "PIN widgets are never clickable in-world");
                DisplayService.press(setup.owner(), d, section, lamp);
                helper.assertTrue(!uplink(helper).getWidget("lamp").value().getAsBoolean(), "clicks do nothing until operators enable them");
                setup.owner().containerMenu = new DisplayMenu(93, d.getBlockPos(), "{}");
                DisplayService.onAction(setup.owner(), new InGamePayloads.DisplayAction(93, "interactive", "true"));
                helper.assertTrue(d.interactive(), "in-world clicks enabled");
                DisplayService.press(viewer, d, section, lamp);
                helper.assertTrue(!uplink(helper).getWidget("lamp").value().getAsBoolean(), "a viewer's click is refused");
                DisplayService.press(setup.owner(), d, section, lamp);
                helper.assertTrue(uplink(helper).getWidget("lamp").value().getAsBoolean(), "an operator's click flips the toggle");
            })
            .thenSucceed();
    }
}
