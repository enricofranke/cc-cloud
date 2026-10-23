package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.4 network with real distances: relays are stacked straight up above the test (no other test areas there, same
 * chunk column), the ender test uses a force-loaded chunk in the Nether.
 */
public final class NetworkTests {
    private NetworkTests() {
    }

    private static final class Setup {
        Player owner;
        CloudMainframeBlockEntity mainframe;
        MainframeLink link;
        BlockPos base;
    }

    private static Setup mainframe(GameTestHelper helper) {
        var s = new Setup();
        s.owner = helper.makeMockPlayer(GameType.SURVIVAL);
        s.mainframe = CloudGameTests.placeMainframe(helper, s.owner);
        s.mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, s.mainframe);
        s.link = new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), s.mainframe.getBlockPos()), s.mainframe.getMainframeId());
        s.base = s.mainframe.getBlockPos();
        return s;
    }

    private static void relay(ServerLevel level, BlockPos pos, Block block, java.util.UUID mainframeId) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CloudRelayBlockEntity relay) {
            relay.fillForTest();
            relay.restrictForTest(mainframeId); // tests run side by side: serve only this test's network
        }
    }

    /** A stone block with an uplink on top (facing down onto the stone). */
    private static void uplink(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN), Block.UPDATE_ALL);
    }

    private static CloudUplinkBlockEntity uplinkAt(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CloudUplinkBlockEntity uplink)) throw helper.assertionException("uplink missing at " + pos);
        return uplink;
    }

    private static void clear(ServerLevel level, BlockPos... positions) {
        for (var pos : positions) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Mainframe → relay (80 up) → relay (160 up, plus an alternative 2 blocks aside) → uplink (200 up). Checks the
     * route over 2 relays, re-routing when the used relay runs out of power, no_route when the other one is gone too,
     * a range error when linking a device that no node reaches, and the legacy grace for an old out-of-range link.
     */
    public static void routing(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var r1 = s.base.above(80);
        var r2 = s.base.above(160);
        var r2b = s.base.above(160).east(2);
        var up = s.base.above(200);
        var far = s.base.above(240).east(1);   // no node within reach
        var legacy = s.base.above(240).west(1);
        relay(level, r1, ModRegistry.CLOUD_RELAY.get(), s.mainframe.getMainframeId());
        relay(level, r2, ModRegistry.CLOUD_RELAY.get(), s.mainframe.getMainframeId());
        relay(level, r2b, ModRegistry.CLOUD_RELAY.get(), s.mainframe.getMainframeId());
        uplink(level, up);
        uplink(level, far);
        uplink(level, legacy);
        String[] used = {null};

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                var uplink = uplinkAt(helper, level, up);
                uplink.bind(s.link, s.owner);
                helper.assertTrue(uplink.getLink() != null, "linking 200 blocks away works over the relays");
                var tooFar = uplinkAt(helper, level, far);
                tooFar.bind(s.link, s.owner);
                helper.assertTrue(tooFar.getLink() == null, "linking out of range of every node is refused");
                var old = uplinkAt(helper, level, legacy);
                old.forceLinkForTest(s.link);
                old.markLegacyForTest();
            })
            .thenWaitUntil(() -> {
                var route = NetworkService.route(uplinkAt(helper, level, up).getUplinkId());
                helper.assertTrue(route != null && route.connected() && route.hops() == 3, "route mainframe → relay → relay → uplink: " + route);
                used[0] = route.path().getLast();
                helper.assertTrue(uplinkAt(helper, level, up).status() != CloudStatus.OFFLINE, "uplink connected");
                var legacyRoute = NetworkService.route(uplinkAt(helper, level, legacy).getUplinkId());
                helper.assertTrue(legacyRoute != null && legacyRoute.connected() && legacyRoute.legacyOutOfRange(),
                    "legacy link stays connected, flagged: " + legacyRoute);
                var json = ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(s.mainframe.getMainframeId(), true);
                helper.assertTrue(json != null && json.toString().contains("legacy_out_of_range"), "map flags the legacy device");
                var stripped = ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(s.mainframe.getMainframeId(), false);
                helper.assertTrue(stripped != null && !stripped.toString().contains("\"pos\""), "no positions for read-only dashboards");
                var power = s.mainframe.powerBreakdown();
                helper.assertTrue(power.get("hops") >= 2, "extra hops cost power: " + power);
            })
            .thenExecute(() -> {
                // The relay in use runs out of power: after the 5 s grace the uplink re-routes over the other one.
                var usedPos = used[0].equals(relayId(level, r2)) ? r2 : r2b;
                ((CloudRelayBlockEntity) level.getBlockEntity(usedPos)).energy().set(0);
            })
            .thenWaitUntil(() -> {
                var route = NetworkService.route(uplinkAt(helper, level, up).getUplinkId());
                helper.assertTrue(route != null && route.connected() && !route.path().getLast().equals(used[0]),
                    "re-routed over the other relay: " + route);
            })
            .thenExecute(() -> {
                // The remaining relay disappears too → no route.
                var otherPos = used[0].equals(relayId(level, r2)) ? r2b : r2;
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenWaitUntil(() -> {
                var uplink = uplinkAt(helper, level, up);
                var route = NetworkService.route(uplink.getUplinkId());
                helper.assertTrue(route != null && !route.connected(), "no_route without a relay");
                helper.assertTrue("no_route".equals(uplink.routeState()) && uplink.status() == CloudStatus.OFFLINE, "uplink offline with no_route");
            })
            .thenExecute(() -> clear(level, r1, r2, r2b, up, far, legacy))
            .thenSucceed();
    }

    private static String relayId(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CloudRelayBlockEntity r ? r.relayId().toString() : "";
    }

    /** Two Ender Relays linked to the same mainframe connect the Overworld and the Nether. */
    public static void enderAcrossDimensions(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "nether");
        var e1 = helper.absolutePos(new BlockPos(4, 1, 4));
        var netherBase = new BlockPos((s.base.getX() >> 3) + 3, 100, (s.base.getZ() >> 3) + 3);
        var chunk = new net.minecraft.world.level.ChunkPos(netherBase.getX() >> 4, netherBase.getZ() >> 4);
        nether.setChunkForced(chunk.x(), chunk.z(), true);
        var e2 = netherBase;
        var up = netherBase.east(20).above(2);
        relay(level, e1, ModRegistry.ENDER_RELAY.get(), s.mainframe.getMainframeId());
        relay(nether, e2, ModRegistry.ENDER_RELAY.get(), s.mainframe.getMainframeId());
        uplink(nether, up);

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                var nearUplink = uplinkAt(helper, nether, up);
                nearUplink.bind(s.link, s.owner);
                helper.assertTrue(nearUplink.getLink() == null, "before the ender relays are linked the Nether is out of range");
                ((CloudRelayBlockEntity) level.getBlockEntity(e1)).bindEnder(s.link, s.owner);
                ((CloudRelayBlockEntity) nether.getBlockEntity(e2)).bindEnder(s.link, s.owner);
            })
            .thenIdle(2)
            .thenExecute(() -> {
                var uplink = uplinkAt(helper, nether, up);
                uplink.bind(s.link, s.owner);
                helper.assertTrue(uplink.getLink() != null, "linking in the Nether works through the ender relays");
            })
            .thenWaitUntil(() -> {
                var route = NetworkService.route(uplinkAt(helper, nether, up).getUplinkId());
                helper.assertTrue(route != null && route.connected() && route.hops() == 3, "mainframe → ender → ender → uplink: " + route);
                var result = NetworkService.result(s.mainframe.getMainframeId());
                helper.assertTrue(result != null && result.links().stream().anyMatch(l -> l.kind() == ai.ezfox.cccloud.network.graph.NetworkGraph.LinkKind.ENDER),
                    "ender link in the graph");
            })
            .thenExecute(() -> {
                clear(nether, up);
                nether.setBlock(e2, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(e1, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                nether.setChunkForced(chunk.x(), chunk.z(), false);
            })
            .thenSucceed();
    }

}
