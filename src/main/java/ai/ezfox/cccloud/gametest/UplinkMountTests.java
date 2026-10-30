package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.UplinkMount;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Compact uplinks (docs/MODELS.md "Compact uplink"): an uplink on the side of a slab-height block gets the 8x6 puck on
 * the visible part instead of the 14x14 plate, with a matching hitbox; the top of a tray-height block refuses new
 * uplinks; the mount follows the attached block and fixes itself after loading, also from unknown saved values.
 */
public final class UplinkMountTests {
    /** Sculk sensor: slab height (8 px) and a block entity, so an uplink may stick to its side. */
    private static final BlockPos SENSOR = new BlockPos(1, 1, 2);
    /** Daylight detector: 6 px, block entity. */
    private static final BlockPos DETECTOR = new BlockPos(3, 1, 1);
    /** Plain smooth stone slab: no block entity, no full side. */
    private static final BlockPos SLAB = new BlockPos(3, 1, 4);
    /** A second sensor for the "loaded as a plate" case. */
    private static final BlockPos OLD = new BlockPos(0, 1, 4);

    private UplinkMountTests() {
    }

    public static void slabSide(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(SENSOR, Blocks.SCULK_SENSOR);
        helper.setBlock(DETECTOR, Blocks.DAYLIGHT_DETECTOR);
        helper.setBlock(SLAB, Blocks.SMOOTH_STONE_SLAB);

        // placed like a player does: on the slab-height side -> puck at 1..7 px, inside the sensor's 8 px
        place(helper, player, SENSOR, Direction.NORTH);
        var side = SENSOR.north();
        expect(helper, side, Direction.SOUTH, UplinkMount.LOW);
        var box = shape(helper, side).bounds();
        helper.assertTrue(box.minX == 4 / 16.0 && box.maxX == 12 / 16.0 && box.minY == 1 / 16.0 && box.maxY == 7 / 16.0
            && box.minZ == 13.75 / 16.0 && box.maxZ == 1.0, "Compact hitbox on the sensor's side: " + box);

        // on top of the 6 px detector the plate would float 10 px above it: refused (side faces only)
        place(helper, player, DETECTOR, Direction.UP);
        helper.assertBlockNotPresent(ModRegistry.CLOUD_UPLINK.get(), DETECTOR.above());

        // a saved state with a mount this version does not know (the removed post mounts of a dev build) loads as the
        // plate: uplinks that older versions let sit on such a top stay and keep the plate
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Name", "cccloud:cloud_uplink");
        var props = new net.minecraft.nbt.CompoundTag();
        props.putString("facing", "down");
        props.putString("mount", "post12");
        props.putString("status", "offline");
        props.putString("direct", "true");
        tag.put("Properties", props);
        var legacy = net.minecraft.world.level.block.state.BlockState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
        helper.assertTrue(legacy.getValue(CloudUplinkBlock.MOUNT) == UplinkMount.FULL && legacy.getValue(CloudUplinkBlock.FACING) == Direction.DOWN,
            "Unknown mount loads as the plate: " + legacy);
        helper.setBlock(DETECTOR.above(), legacy);

        // a plain slab still cannot hold an uplink (no block entity, side not sturdy): the placement rule is unchanged
        place(helper, player, SLAB, Direction.NORTH);
        helper.assertBlockNotPresent(ModRegistry.CLOUD_UPLINK.get(), SLAB.north());

        // the attached block changes: full block -> plate, slab-height block -> puck again
        helper.setBlock(SENSOR, Blocks.STONE);
        expect(helper, side, Direction.SOUTH, UplinkMount.FULL);
        helper.setBlock(SENSOR, Blocks.SCULK_SENSOR);
        expect(helper, side, Direction.SOUTH, UplinkMount.LOW);

        // saved before the compact variant existed (or put there by /setblock): loads as a plate, fits itself on its first tick
        helper.setBlock(OLD, Blocks.SCULK_SENSOR);
        helper.setBlock(OLD.north(), ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.SOUTH));
        helper.assertBlockProperty(OLD.north(), CloudUplinkBlock.MOUNT, UplinkMount.FULL);
        helper.succeedWhen(() -> {
            expect(helper, OLD.north(), Direction.SOUTH, UplinkMount.LOW);
            expect(helper, DETECTOR.above(), Direction.DOWN, UplinkMount.FULL);
        });
    }

    private static void place(GameTestHelper helper, Player player, BlockPos against, Direction face) {
        var stack = new ItemStack(ModRegistry.CLOUD_UPLINK_ITEM.get(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.placeAt(player, stack, against, face);
    }

    private static net.minecraft.world.phys.shapes.VoxelShape shape(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos));
    }

    private static void expect(GameTestHelper helper, BlockPos pos, Direction facing, UplinkMount mount) {
        helper.assertBlockPresent(ModRegistry.CLOUD_UPLINK.get(), pos);
        helper.assertBlockProperty(pos, CloudUplinkBlock.FACING, facing);
        helper.assertBlockProperty(pos, CloudUplinkBlock.MOUNT, mount);
        helper.assertTrue(Shapes.equal(shape(helper, pos), mount.shape(facing)), "Hitbox of " + mount + " at " + pos);
    }
}
