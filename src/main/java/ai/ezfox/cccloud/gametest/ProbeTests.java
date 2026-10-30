package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.item.ProbeTarget;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Cloud Probe and side handling (docs/SPEC.md "Cloud Probe"). Only in GameTest runs ({@code -Dcccloud.gametest=true})
 * two vanilla block entities get test machines' energy: the beacon only on its internal (side-less) view, the enchanting
 * table only on its top; and positions in {@link #DENIED} refuse block interactions like a claim mod does.
 */
public final class ProbeTests {
    /** Absolute positions where right-clicks are cancelled (as FTB Chunks / Open Parties and Claims do in claims). */
    static final Set<BlockPos> DENIED = ConcurrentHashMap.newKeySet();
    private static final SimpleEnergyHandler INTERNAL = new SimpleEnergyHandler(10_000);
    private static final SimpleEnergyHandler TOP_ONLY = new SimpleEnergyHandler(10_000);

    static {
        INTERNAL.set(4_000);
        TOP_ONLY.set(2_000);
    }

    private ProbeTests() {
    }

    /** Test machines (GameTest runs only). */
    public static void registerTestCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BlockEntityType.BEACON, (be, side) -> side == null ? INTERNAL : null);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BlockEntityType.ENCHANTING_TABLE, (be, side) -> side == Direction.UP ? TOP_ONLY : null);
    }

    /** Claim stand-in (GameTest runs only). */
    public static void init() {
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, e -> {
            if (DENIED.contains(e.getPos())) {
                e.setCancellationResult(InteractionResult.FAIL);
                e.setCanceled(true);
            }
        });
    }

    private static InteractionResult use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos relative, Direction face) {
        var pos = helper.absolutePos(relative);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }

    /**
     * A probe on a wall reads a machine three blocks away: picking it is refused where a claim cancels the click, works
     * otherwise, only the picking player can hand it to the probe, a machine out of range is refused, the card shows up
     * like on an attached uplink, and removing the machine clears the target (the card goes, like on detaching).
     */
    public static void remoteTarget(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = InGameTestSupport.player(helper, "probe_owner");
        var other = InGameTestSupport.player(helper, "probe_other");
        BlockPos machine = new BlockPos(1, 1, 1);
        BlockPos wall = new BlockPos(4, 1, 1);
        BlockPos probePos = new BlockPos(4, 1, 2);
        helper.setBlock(machine, Blocks.BEACON);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(probePos, ModRegistry.CLOUD_PROBE.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.NORTH));
        var probe = (CloudUplinkBlockEntity) level.getBlockEntity(helper.absolutePos(probePos));
        helper.assertTrue(probe != null && probe.isProbe() && probe.readPos() == null, "a probe reads nothing until it has a target");

        var card = new ItemStack(ModRegistry.CLOUD_LINK_CARD.get());
        player.setShiftKeyDown(true);
        DENIED.add(helper.absolutePos(machine));
        try {
            use(helper, player, card, machine, Direction.UP);
        } finally {
            DENIED.remove(helper.absolutePos(machine));
        }
        helper.assertTrue(!card.has(ModRegistry.PROBE_TARGET.get()), "a claim that refuses the click refuses picking the machine");
        use(helper, player, card, machine, Direction.UP);
        var picked = card.get(ModRegistry.PROBE_TARGET.get());
        helper.assertTrue(picked != null && picked.pos().pos().equals(helper.absolutePos(machine)) && picked.side() == Direction.UP,
            "sneak + right-click picks the machine and the clicked face: " + picked);
        player.setShiftKeyDown(false);

        helper.assertTrue(!probe.setRemoteTarget(picked, other), "another player cannot use a machine someone else picked");
        use(helper, player, card, probePos, Direction.SOUTH);
        helper.assertTrue(helper.absolutePos(machine).equals(probe.remoteTarget()) && probe.readSide() == Direction.UP, "right-clicking the probe sets it");
        helper.assertTrue(!card.has(ModRegistry.PROBE_TARGET.get()), "the card is emptied");
        helper.assertBlockProperty(probePos, CloudUplinkBlock.DIRECT, true);

        BlockPos far = machine.above(10);
        helper.setBlock(far, Blocks.BEACON);
        var tooFar = new ProbeTarget(GlobalPos.of(level.dimension(), helper.absolutePos(far)), Direction.UP, player.getUUID());
        helper.assertTrue(!probe.setRemoteTarget(tooFar, player), "beyond probeRange (8) is refused");
        helper.assertTrue(helper.absolutePos(machine).equals(probe.remoteTarget()), "the old target stays");
        var own = new ProbeTarget(GlobalPos.of(level.dimension(), helper.absolutePos(probePos)), Direction.UP, player.getUUID());
        helper.assertTrue(!probe.setRemoteTarget(own, player), "not itself (CC: Cloud blocks are not machines)");

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue("energy".equals(probe.deviceKind()), "the machine's card, read through its internal view: " + probe.deviceKind()))
            .thenExecute(() -> helper.setBlock(machine, Blocks.STONE))
            .thenWaitUntil(() -> {
                helper.assertTrue(probe.remoteTarget() == null, "a removed machine clears the target");
                helper.assertTrue(probe.deviceKind().isEmpty(), "and its card");
                helper.assertBlockProperty(probePos, CloudUplinkBlock.DIRECT, false);
            })
            .thenExecute(() -> helper.setBlock(far, Blocks.AIR))
            .thenSucceed();
    }

    /**
     * Side handling: a machine that exposes energy only on its internal view still gets its card through any face
     * (side first, then the side-less view); one that exposes nothing on the touched face shows no card and the screen
     * hint names a face that does.
     */
    public static void sideFallbackAndHint(GameTestHelper helper) {
        BlockPos beacon = new BlockPos(1, 1, 2);
        BlockPos table = new BlockPos(3, 1, 2);
        helper.setBlock(beacon, Blocks.BEACON);
        helper.setBlock(table, Blocks.ENCHANTING_TABLE);
        helper.setBlock(beacon.north(), ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.SOUTH));
        helper.setBlock(table.north(), ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.SOUTH));
        var onBeacon = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(beacon.north()));
        var onTable = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(table.north()));
        helper.succeedWhen(() -> {
            helper.assertTrue("energy".equals(onBeacon.deviceKind()), "side lookup falls back to the internal view: " + onBeacon.deviceKind());
            helper.assertTrue(onBeacon.exposedElsewhere() == null, "no hint when there is a card");
            helper.assertTrue(onTable.deviceKind().isEmpty(), "nothing on the north face, nothing inside: no card");
            helper.assertTrue(onTable.exposedElsewhere() == Direction.UP, "the hint names the top: " + onTable.exposedElsewhere());
        });
    }
}
