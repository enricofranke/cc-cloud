package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Game tests (see data/cccloud/test_instance). Run with {@code ./gradlew runGameTestServer}. */
public final class CloudGameTests {
    static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    static final BlockPos LOWER = FLOOR.above();
    static final BlockPos UPPER = LOWER.above();

    private CloudGameTests() {
    }

    static CloudMainframeBlockEntity placeMainframe(GameTestHelper helper, net.minecraft.world.entity.player.Player player) {
        // UseOnContext takes the item from the player's hand, so it has to be held.
        var stack = new ItemStack(ModRegistry.CLOUD_MAINFRAME_ITEM.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        helper.placeAt(player, stack, FLOOR, Direction.UP);
        helper.assertBlockProperty(LOWER, CloudMainframeBlock.HALF, DoubleBlockHalf.LOWER);
        helper.assertBlockProperty(UPPER, CloudMainframeBlock.HALF, DoubleBlockHalf.UPPER);
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(LOWER));
        if (!(be instanceof CloudMainframeBlockEntity mainframe)) throw helper.assertionException("Mainframe block entity missing");
        helper.assertTrue(!(helper.getLevel().getBlockEntity(helper.absolutePos(UPPER)) instanceof CloudMainframeBlockEntity), "Upper half must not have a block entity");
        return mainframe;
    }

    /** Placing creates both halves; breaking the upper half removes the lower half too. */
    public static void placeAndBreakUpper(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        placeMainframe(helper, player);
        helper.destroyBlock(UPPER);
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(ModRegistry.CLOUD_MAINFRAME.get(), LOWER);
            helper.assertBlockNotPresent(ModRegistry.CLOUD_MAINFRAME.get(), UPPER);
        });
    }

    /** Breaking the lower half removes the upper half and drops exactly one item. */
    public static void breakLowerDropsOne(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        placeMainframe(helper, player);
        helper.getLevel().destroyBlock(helper.absolutePos(LOWER), true);
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(ModRegistry.CLOUD_MAINFRAME.get(), LOWER);
            helper.assertBlockNotPresent(ModRegistry.CLOUD_MAINFRAME.get(), UPPER);
            helper.assertItemEntityCountIs(ModRegistry.CLOUD_MAINFRAME_ITEM.get(), LOWER, 3, 1);
        });
    }

    /** The placer owns the mainframe; strangers may not manage it until trusted. */
    public static void ownership(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = placeMainframe(helper, owner);
        helper.assertTrue(owner.getUUID().equals(mainframe.getOwner()), "Placer must be the owner");
        helper.assertTrue(mainframe.canManage(owner), "Owner can manage");
        helper.assertTrue(!mainframe.canManage(stranger), "Stranger must not manage");
        mainframe.trust(new NameAndId(stranger.getUUID(), "stranger"));
        helper.assertTrue(mainframe.canManage(stranger), "Trusted player can manage");
        mainframe.untrust(stranger.getUUID());
        helper.assertTrue(!mainframe.canManage(stranger), "Untrusted player must not manage");

        // Binding an uplink needs rights on the mainframe. NeoForge calls BlockEntity#onLoad (which registers the
        // mainframe) on the tick after placement, so this part runs a little later.
        helper.runAfterDelay(2, () -> {
            var uplinkPos = new BlockPos(0, 1, 0);
            helper.setBlock(uplinkPos, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN));
            var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(uplinkPos));
            var link = new ai.ezfox.cccloud.item.MainframeLink(
                net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
            helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(mainframe.getMainframeId()) == mainframe, "Mainframe must be registered");
            uplink.bind(link, stranger);
            helper.assertTrue(uplink.getLink() == null, "Stranger must not bind an uplink");
            uplink.bind(link, owner);
            helper.assertTrue(uplink.getLink() != null, "Owner can bind an uplink");
            helper.succeed();
        });
    }

    static Component text(String s) {
        return Component.literal(s);
    }
}
