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

    /**
     * A 4×2 wall across a chunk border survives reloading: every member is saved exactly like a chunk save does
     * ({@code saveWithFullMetadata}, which writes the block position as "x"/"y"/"z" – until 0.9.0 the display's own index
     * keys, so every reload broke the wall), dropped, and loaded again chunk by chunk in the other order (the east half
     * first). Then the same with saves of 0.9.0 (indices lost): the wall forms again by itself.
     */
    public static void reload(GameTestHelper helper) {
        var level = helper.getLevel();
        var start = helper.absolutePos(new BlockPos(1, 1, 2)).above(12);
        int border = Math.round(start.getX() / 16f) * 16; // the nearest chunk border along x
        var wall = new ArrayList<BlockPos>();
        for (int y = 0; y < 2; y++) for (int x = 0; x < 4; x++) wall.add(new BlockPos(border - 2 + x, start.getY() + y, start.getZ()));
        // Facing south: right() is east, so the x index runs along +x across the border, rows go up.
        var state = InGameRegistry.CLOUD_DISPLAY.get().defaultBlockState().setValue(CloudDisplayBlock.FACING, Direction.SOUTH)
            .setValue(CloudDisplayBlock.ORIENTATION, Direction.NORTH);
        var mainframe = java.util.UUID.randomUUID();
        var by = java.util.UUID.randomUUID();
        java.util.function.Function<BlockPos, CloudDisplayBlockEntity> at = pos -> {
            if (!(level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity d)) throw helper.assertionException("no display at " + pos);
            return d;
        };
        Runnable assertWall = () -> {
            for (var pos : wall) {
                var d = at.apply(pos);
                int x = pos.getX() - (border - 2), y = pos.getY() - start.getY();
                helper.assertTrue(d.width() == 4 && d.height() == 2 && d.xIndex() == x && d.yIndex() == y,
                    "member " + x + "," + y + " is " + d.xIndex() + "," + d.yIndex() + " of " + d.width() + "x" + d.height());
                helper.assertTrue(mainframe.equals(d.mainframeId()) && d.sectionId().equals("sec") && by.equals(d.configuredBy()), "configuration kept at " + pos);
                helper.assertTrue(level.getBlockState(pos).getValue(CloudDisplayBlock.STATE) == DisplayEdgeState.of(y < 1, y > 0, x > 0, x < 3), "edge state at " + pos);
            }
        };
        var tags = new java.util.LinkedHashMap<BlockPos, net.minecraft.nbt.CompoundTag>();
        Runnable unload = () -> {
            tags.clear();
            for (var pos : wall) {
                var d = at.apply(pos);
                tags.put(pos, d.saveWithFullMetadata(level.registryAccess()));
                d.onChunkUnloaded();
                level.removeBlockEntity(pos);
            }
        };
        java.util.function.Consumer<Boolean> loadHalf = east -> {
            for (var e : tags.entrySet()) {
                if ((e.getKey().getX() >= border) != east) continue;
                level.setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity.loadStatic(e.getKey(), level.getBlockState(e.getKey()), e.getValue(),
                    level.registryAccess()));
            }
        };
        helper.startSequence()
            .thenExecute(() -> {
                for (var pos : wall) {
                    level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
                    at.apply(pos).expand(); // what setPlacedBy does for a player
                }
                at.apply(wall.getFirst()).configure(mainframe, "Main", "sec", false, by);
                assertWall.run();
                helper.assertTrue((border - 2) >> 4 != (border + 1) >> 4, "the wall crosses a chunk border");
            })
            .thenExecute(() -> {
                unload.run();
                loadHalf.accept(true);
                loadHalf.accept(false);
                // Before any tick: the saved shape comes back as it was (no reset and re-forming needed).
                for (var pos : wall) {
                    var d = at.apply(pos);
                    int x = pos.getX() - (border - 2), y = pos.getY() - start.getY();
                    helper.assertTrue(d.xIndex() == x && d.yIndex() == y && d.width() == 4 && d.height() == 2,
                        "loaded " + pos + " as " + d.xIndex() + "," + d.yIndex() + " of " + d.width() + "x" + d.height());
                }
            })
            .thenIdle(5)
            .thenExecute(assertWall)
            // Saves of 0.9.0: the indices were stored as "x"/"y", which the chunk save overwrote with the position.
            .thenExecute(() -> {
                unload.run();
                for (var tag : tags.values()) {
                    tag.remove("index_x");
                    tag.remove("index_y");
                }
                loadHalf.accept(false);
                loadHalf.accept(true);
            })
            .thenIdle(5)
            .thenExecute(assertWall)
            .thenExecute(() -> {
                for (var pos : wall) level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
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
