package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.ingame.InGameTestSupport;
import ai.ezfox.cccloud.item.ProbeTarget;
import ai.ezfox.cccloud.map.XrayGuard;
import com.google.gson.JsonObject;
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
     * The network map knows Cloud Probes: node type {@code probe} (not a generic uplink) and, with positions, the machine
     * it reads as {@code target} (also in the in-game screen's compact network); without positions no target. Setting
     * and clearing the target redraw the map.
     */
    public static void networkNode(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(1, 0, 1));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var id = mainframe.getMainframeId();
        BlockPos machine = new BlockPos(4, 1, 5);
        BlockPos probePos = new BlockPos(4, 1, 2);
        helper.setBlock(machine, Blocks.BEACON);
        helper.setBlock(probePos.north(), Blocks.STONE);
        helper.setBlock(probePos, ModRegistry.CLOUD_PROBE.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.NORTH));
        var probe = (CloudUplinkBlockEntity) level.getBlockEntity(helper.absolutePos(probePos));
        var at = helper.absolutePos(machine);
        String probeId = probe.getUplinkId().toString();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(id) == mainframe && mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                probe.bind(new ai.ezfox.cccloud.item.MainframeLink(GlobalPos.of(level.dimension(), mainframe.getBlockPos()), id), owner);
                helper.assertTrue(probe.getLink() != null, "probe linked");
                helper.assertTrue(probe.setRemoteTarget(new ProbeTarget(GlobalPos.of(level.dimension(), at), Direction.UP, owner.getUUID()), owner), "target set");
            })
            .thenWaitUntil(() -> {
                var node = networkNode(ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(id, true), "uplinkId", probeId);
                helper.assertTrue(node != null && "probe".equals(node.get("type").getAsString()), "its own node type: " + node);
                var target = node.getAsJsonObject("target");
                helper.assertTrue(target != null && target.get("x").getAsInt() == at.getX() && target.get("y").getAsInt() == at.getY()
                    && target.get("z").getAsInt() == at.getZ(), "the machine it reads: " + node);
                var stripped = networkNode(ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(id, false), "uplinkId", probeId);
                helper.assertTrue(stripped != null && "probe".equals(stripped.get("type").getAsString()) && !stripped.has("target") && !stripped.has("pos"),
                    "without positions: the type, no coordinates: " + stripped);
                var screen = ai.ezfox.cccloud.network.NetworkService.screenNetwork(mainframe);
                var compact = screen == null ? null : networkNode(screen.json(), "type", "probe");
                helper.assertTrue(compact != null && compact.getAsJsonObject("target") != null
                    && compact.getAsJsonObject("target").get("x").getAsInt() == at.getX() && compact.getAsJsonObject("target").get("z").getAsInt() == at.getZ(),
                    "the in-game screen gets the line too: " + compact);
            })
            .thenExecute(() -> probe.clearRemoteTarget("test"))
            .thenWaitUntil(() -> {
                var node = networkNode(ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(id, true), "uplinkId", probeId);
                helper.assertTrue(node != null && "probe".equals(node.get("type").getAsString()) && !node.has("target"), "target cleared: " + node);
            })
            .thenExecute(() -> {
                helper.setBlock(probePos, Blocks.AIR);
                helper.setBlock(machine, Blocks.AIR);
            })
            .thenSucceed();
    }

    private static @org.jspecify.annotations.Nullable JsonObject networkNode(@org.jspecify.annotations.Nullable JsonObject network,
                                                                                           String key, String value) {
        if (network == null) return null;
        for (var e : network.getAsJsonArray("nodes")) {
            var n = e.getAsJsonObject();
            if (n.has(key) && value.equals(n.get(key).getAsString())) return n;
        }
        return null;
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

    /**
     * Security review 2026-10-30: a probe reads its machine only while the rules hold, not only when it was linked. A
     * picked machine expires after 10 minutes; a chunk another team claimed for the picking player is refused when
     * linking and stops the reading (and the card) later; a probe moved with its data (block movers, contraptions)
     * forgets its target instead of reading the same coordinates at its new place.
     */
    public static void guards(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = InGameTestSupport.player(helper, "probe_guard");
        BlockPos machine = new BlockPos(1, 1, 1);
        BlockPos probePos = new BlockPos(3, 1, 1);
        BlockPos movedPos = new BlockPos(3, 1, 3);
        helper.setBlock(machine, Blocks.BEACON);
        helper.setBlock(probePos.north(), Blocks.STONE);
        helper.setBlock(movedPos.north(), Blocks.STONE);
        helper.setBlock(probePos, ModRegistry.CLOUD_PROBE.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.NORTH));
        var probe = (CloudUplinkBlockEntity) level.getBlockEntity(helper.absolutePos(probePos));
        var at = GlobalPos.of(level.dimension(), helper.absolutePos(machine));
        // claims of other teams for this test's player only (everything else goes to the real provider)
        var claimed = ConcurrentHashMap.<Long>newKeySet();
        long chunk = net.minecraft.world.level.ChunkPos.pack(at.pos().getX() >> 4, at.pos().getZ() >> 4);
        var previous = XrayGuard.claims;
        XrayGuard.claims = (l, cx, cz, p) -> p.equals(player.getUUID()) && claimed.contains(net.minecraft.world.level.ChunkPos.pack(cx, cz))
            || previous != null && previous.claimedByOther(l, cx, cz, p);

        var stale = new ProbeTarget(at, Direction.UP, player.getUUID(), System.currentTimeMillis() - ProbeTarget.VALID_MS - 1_000);
        helper.assertTrue(!probe.setRemoteTarget(stale, player) && probe.remoteTarget() == null, "a machine picked more than 10 minutes ago is refused");
        helper.assertTrue(!probe.setRemoteTarget(new ProbeTarget(at, Direction.UP, player.getUUID(), 0), player), "a card without a pick time is refused");
        claimed.add(chunk);
        helper.assertTrue(!probe.setRemoteTarget(new ProbeTarget(at, Direction.UP, player.getUUID()), player) && probe.remoteTarget() == null,
            "a machine in a chunk another team claimed is refused when linking (also a card made without picking)");
        claimed.clear();
        helper.assertTrue(probe.setRemoteTarget(new ProbeTarget(at, Direction.UP, player.getUUID()), player), "fresh and unclaimed: linked");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue("energy".equals(probe.deviceKind()) && at.pos().equals(probe.readPos()), "reads the machine"))
            .thenExecute(() -> claimed.add(chunk))
            .thenWaitUntil(() -> {
                helper.assertTrue(probe.readPos() == null, "claimed later: nothing is read");
                helper.assertTrue(probe.deviceKind().isEmpty(), "and the card is gone: " + probe.deviceKind());
            })
            .thenExecute(() -> claimed.clear())
            .thenWaitUntil(() -> helper.assertTrue("energy".equals(probe.deviceKind()), "unclaimed again: the card is back"))
            .thenExecute(() -> {
                // Moved with its data (a contraption, a block mover): the saved probe is placed somewhere else.
                var tag = probe.saveWithFullMetadata(level.registryAccess());
                helper.setBlock(probePos, Blocks.AIR);
                helper.setBlock(movedPos, ModRegistry.CLOUD_PROBE.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.NORTH));
                var abs = helper.absolutePos(movedPos);
                var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, level.getBlockState(abs), tag, level.registryAccess());
                helper.assertTrue(loaded instanceof CloudUplinkBlockEntity u && at.pos().equals(u.remoteTarget()) && u.readPos() == null,
                    "a moved probe does not read the old coordinates");
                level.setBlockEntity(loaded);
            })
            .thenWaitUntil(() -> {
                var moved = (CloudUplinkBlockEntity) level.getBlockEntity(helper.absolutePos(movedPos));
                helper.assertTrue(moved != null && moved.remoteTarget() == null, "and forgets its target");
                helper.assertTrue(moved.deviceKind().isEmpty(), "no card");
            })
            .thenExecute(() -> {
                XrayGuard.claims = previous;
                helper.setBlock(movedPos, Blocks.AIR);
            })
            .thenSucceed();
    }
}
