package ai.ezfox.cccloud.compat.railcraft;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import mods.railcraft.world.level.block.entity.CokeOvenBlockEntity;
import mods.railcraft.world.level.block.entity.tank.TankBlockEntity;
import mods.railcraft.world.module.CokeOvenModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Railcraft Reborn: a lone solid-fueled firebox gets the Steam Boiler card ("Not formed"); a formed coke oven (3×3×3) and a
 * formed iron tank (3×4×3) are read through their master block entity from an uplink on a corner block (not the master), and a
 * lone blast furnace brick gets the Blast Furnace card ("Not formed").
 */
public final class RailcraftGameTests {
    private RailcraftGameTests() {
    }

    public static void boiler(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "railcraft:solid_fueled_firebox"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "railcraft_boiler", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "railcraft_boiler", linked);
                helper.assertTrue(text(helper, RailcraftCards.STATUS).equals("Not formed"), "status: " + text(helper, RailcraftCards.STATUS));
            })
            .thenSucceed();
    }

    /**
     * A 3×{@code height}×3 box of one block, hollow inside (the smallest coke oven is 3 high, the smallest iron tank 4),
     * with its lowest corner at {@code from}.
     */
    private static void box(GameTestHelper helper, BlockPos from, Block block, int height) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < 3; z++) {
                    boolean inside = x == 1 && z == 1 && y > 0 && y < height - 1;
                    if (!inside) helper.setBlock(from.offset(x, y, z), block);
                }
            }
        }
    }

    /** An uplink east of {@code target}, facing it. */
    private static CloudUplinkBlockEntity uplinkAt(GameTestHelper helper, BlockPos target) {
        var pos = target.east();
        helper.setBlock(pos, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof CloudUplinkBlockEntity uplink)) throw helper.assertionException("uplink missing");
        return uplink;
    }

    private static String value(CloudUplinkBlockEntity uplink, String id) {
        var widget = uplink.getWidget(id);
        return widget == null ? "<missing>" : widget.value().isJsonPrimitive() ? widget.value().getAsString() : widget.value().toString();
    }

    public static void multiblocks(GameTestHelper helper) {
        var oven = new BlockPos(1, 1, 0);
        var tank = new BlockPos(6, 1, 0);
        var furnace = new BlockPos(1, 1, 6);
        box(helper, oven, block(helper, "railcraft:coke_oven_bricks"), 3);
        box(helper, tank, block(helper, "railcraft:white_iron_tank_wall"), 4);
        helper.setBlock(furnace, block(helper, "railcraft:blast_furnace_bricks"));
        // uplinks on a corner block of each (east side), not on the master
        var ovenUplink = uplinkAt(helper, oven.offset(2, 0, 0));
        var tankUplink = uplinkAt(helper, tank.offset(2, 0, 0));
        var furnaceUplink = uplinkAt(helper, furnace);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> {
                helper.assertTrue("railcraft_coke_oven".equals(ovenUplink.deviceKind()), "coke oven card: " + ovenUplink.deviceKind());
                helper.assertTrue("railcraft_tank".equals(tankUplink.deviceKind()), "tank card: " + tankUplink.deviceKind());
                helper.assertTrue("railcraft_blast_furnace".equals(furnaceUplink.deviceKind()), "blast furnace card: " + furnaceUplink.deviceKind());
                helper.assertTrue("Not formed".equals(value(furnaceUplink, RailcraftCards.STATUS)), "lone brick: " + value(furnaceUplink, RailcraftCards.STATUS));
            })
            .thenWaitUntil(() -> {
                var corner = helper.getLevel().getBlockEntity(helper.absolutePos(oven.offset(2, 0, 0)));
                helper.assertTrue(corner instanceof CokeOvenBlockEntity c && c.isFormed(), "coke oven formed");
                var wall = helper.getLevel().getBlockEntity(helper.absolutePos(tank.offset(2, 0, 0)));
                helper.assertTrue(wall instanceof TankBlockEntity t && t.isFormed(), "tank formed");
            })
            .thenExecute(() -> {
                var corner = (CokeOvenBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(oven.offset(2, 0, 0)));
                var master = corner.getMasterBlockEntity().orElseThrow();
                helper.assertTrue(master != corner, "the uplink's block is not the master");
                master.getCokeOvenModule().container().setItem(CokeOvenModule.SLOT_OUTPUT, new ItemStack(Items.CHARCOAL, 12));
                var wall = (TankBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(tank.offset(2, 0, 0)));
                wall.getMasterBlockEntity().orElseThrow().getModule().getTank().setFluid(new FluidStack(Fluids.WATER, 4000));
                for (var uplink : new CloudUplinkBlockEntity[]{ovenUplink, tankUplink, furnaceUplink}) {
                    uplink.bind(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), linked.mainframe().getBlockPos()),
                        linked.mainframe().getMainframeId()), linked.owner());
                    helper.assertTrue(uplink.getLink() != null, "uplink linked");
                }
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(!"Not formed".equals(value(ovenUplink, RailcraftCards.STATUS)), "oven status: " + value(ovenUplink, RailcraftCards.STATUS));
                helper.assertTrue(value(ovenUplink, RailcraftCards.OUTPUT).startsWith("12×"), "the master's output, read from a corner: " + value(ovenUplink, RailcraftCards.OUTPUT));
                helper.assertTrue("Formed".equals(value(tankUplink, RailcraftCards.STATUS)), "tank status: " + value(tankUplink, RailcraftCards.STATUS));
                helper.assertTrue("Water".equals(value(tankUplink, RailcraftCards.FLUID)), "tank fluid: " + value(tankUplink, RailcraftCards.FLUID));
                helper.assertTrue(value(tankUplink, RailcraftCards.AMOUNT).startsWith("4"), "tank amount: " + value(tankUplink, RailcraftCards.AMOUNT));
                helper.assertTrue(tankUplink.isOnline() && ovenUplink.isOnline(), "cards online");
            })
            .thenSucceed();
    }
}
