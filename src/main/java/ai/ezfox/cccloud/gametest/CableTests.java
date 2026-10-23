package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.cable.EnderFiberLinkBlockEntity;
import ai.ezfox.cccloud.cable.FiberCableBlock;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.NetworkGraph;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Cables (docs/SPEC.md "Cables"): CC Networking Cable + Wired Modems (part A) and our Fiber Cable / Ender Fiber Link
 * (part B). Long cable runs go straight up above the test (own chunk column, like {@link NetworkTests}).
 */
public final class CableTests {
    /** Beyond every radio range around the mainframe (mainframe 96, uplink 48). */
    private static final int FAR = 110;

    private CableTests() {
    }

    private record Setup(Player owner, CloudMainframeBlockEntity mainframe, MainframeLink link, BlockPos base) {
    }

    private static Setup mainframe(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var link = new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
        return new Setup(owner, mainframe, link, mainframe.getBlockPos());
    }

    // ---- placing -----------------------------------------------------------------------------------------------------

    private static Block cc(String id) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("computercraft", id));
    }

    private static BlockState ccCable() {
        var block = cc("cable");
        var state = block.defaultBlockState();
        var cable = block.getStateDefinition().getProperty("cable");
        if (cable instanceof BooleanProperty p) state = state.setValue(p, true);
        return state;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void fiber(ServerLevel level, BlockPos pos) {
        place(level, pos, FiberCableBlock.withConnections(ModRegistry.FIBER_CABLE.get().defaultBlockState(), level, pos));
    }

    /** An uplink at {@code pos} attached to a stone block on its {@code facing} side. */
    private static void uplink(ServerLevel level, BlockPos pos, Direction facing) {
        place(level, pos.relative(facing), Blocks.STONE.defaultBlockState());
        place(level, pos, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, facing));
    }

    private static CloudUplinkBlockEntity uplinkAt(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CloudUplinkBlockEntity uplink)) throw helper.assertionException("uplink missing at " + pos);
        return uplink;
    }

    private static NetworkGraph.Route route(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        var route = NetworkService.route(uplinkAt(helper, level, pos).getUplinkId());
        if (route == null) throw helper.assertionException("network not computed yet");
        return route;
    }

    private static void clear(ServerLevel level, List<BlockPos> positions) {
        for (var pos : positions) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    // ---- A: CC cables ------------------------------------------------------------------------------------------------

    /**
     * Mainframe → 110 blocks of CC cable (with a full Wired Modem in between) → uplink, far outside every radio range:
     * linking works over the cable, the route is wired (100 %, no hop FE, "wired" link on the map); breaking the cable
     * leaves the uplink with no_route.
     */
    public static void ccCable(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        for (int y = 2; y <= FAR; y++) {
            var pos = s.base().above(y);
            place(level, pos, y == 50 ? cc("wired_modem_full").defaultBlockState() : ccCable());
            placed.add(pos);
        }
        var up = s.base().above(FAR).east();
        uplink(level, up, Direction.EAST);
        placed.add(up);
        placed.add(up.east());
        var cut = s.base().above(80);

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(uplinkAt(helper, level, up).wired().visibleNodes().contains(s.mainframe().getMainframeId().toString()),
                "the uplink sees the mainframe on the CC wired network"))
            .thenExecute(() -> {
                var uplink = uplinkAt(helper, level, up);
                uplink.bind(s.link(), s.owner());
                helper.assertTrue(uplink.getLink() != null, "linking over the cable works far outside radio range");
            })
            .thenWaitUntil(() -> {
                var route = route(helper, level, up);
                helper.assertTrue(route.connected() && route.wired() && !route.fiber(), "routed by cable: " + route);
                helper.assertTrue(route.signal() == 1.0 && route.airHops() == 0 && route.hops() == 1, "100 %, no air hop: " + route);
                helper.assertTrue(s.mainframe().powerBreakdown().get("hops") == 0, "cables cost no hop FE");
                var json = ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(s.mainframe().getMainframeId(), true);
                helper.assertTrue(json != null && json.toString().contains("\"kind\":\"wired\"") && json.toString().contains("\"medium\":\"cc\""),
                    "the map shows a wired link: " + json);
            })
            .thenExecute(() -> level.setBlock(cut, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
            .thenWaitUntil(() -> {
                var route = route(helper, level, up);
                helper.assertTrue(!route.connected(), "cable cut → no_route: " + route);
                helper.assertTrue("no_route".equals(uplinkAt(helper, level, up).routeState()), "uplink reports no_route");
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    /**
     * An uplink in radio range that is also cabled uses the cable (and the map sends no radio link for that pair);
     * cutting the cable re-routes it over radio.
     */
    public static void prefersCable(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        var start = s.base().above(2);
        for (int i = 0; i <= 10; i++) {
            var pos = start.above(i);
            place(level, pos, ccCable());
            placed.add(pos);
        }
        var up = start.above(10).east();
        uplink(level, up, Direction.EAST);
        placed.add(up);
        placed.add(up.east());
        String mf = s.mainframe().getMainframeId().toString();

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(uplinkAt(helper, level, up).wired().visibleNodes().contains(mf), "cabled"))
            .thenExecute(() -> uplinkAt(helper, level, up).bind(s.link(), s.owner()))
            .thenWaitUntil(() -> {
                var route = route(helper, level, up);
                helper.assertTrue(route.connected() && route.wired(), "cable preferred over radio: " + route);
                var result = NetworkService.result(s.mainframe().getMainframeId());
                String id = uplinkAt(helper, level, up).getUplinkId().toString();
                helper.assertTrue(result != null && result.links().stream().noneMatch(l -> l.kind() == NetworkGraph.LinkKind.RADIO
                    && l.a().equals(mf) && l.b().equals(id)), "no radio link for the cabled pair");
            })
            .thenExecute(() -> level.setBlock(start.above(5), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
            .thenWaitUntil(() -> {
                var route = route(helper, level, up);
                helper.assertTrue(route.connected() && route.via() == NetworkGraph.LinkKind.RADIO, "re-routed over radio: " + route);
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    // ---- B: fiber ----------------------------------------------------------------------------------------------------

    /**
     * Fiber connects to cloud blocks and fiber only: not to stone and not to a CC cable (and the CC cable does not
     * connect to it). A relay 110 blocks up joins the mainframe over fiber; an uplink that only touches a CC cable next
     * to the fiber sees no cloud node.
     */
    public static void fiberConnections(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        var relayPos = s.base().above(FAR + 1);
        level.setBlock(relayPos, ModRegistry.CLOUD_RELAY.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(relayPos) instanceof CloudRelayBlockEntity relay) {
            relay.fillForTest();
            relay.restrictForTest(s.mainframe().getMainframeId()); // tests run side by side: serve only this test's network
        }
        placed.add(relayPos);
        for (int y = 2; y <= FAR; y++) {
            var pos = s.base().above(y);
            fiber(level, pos);
            placed.add(pos);
        }
        var probe = s.base().above(40);
        var stone = probe.east();
        var ccPos = probe.north();
        var ccUplink = ccPos.north();
        place(level, stone, Blocks.STONE.defaultBlockState());
        place(level, ccPos, ccCable());
        uplink(level, ccUplink, Direction.UP);
        placed.addAll(List.of(stone, ccPos, ccUplink, ccUplink.above()));

        helper.startSequence()
            .thenWaitUntil(() -> {
                var state = level.getBlockState(probe);
                helper.assertTrue(state.getValue(FiberCableBlock.SIDES.get(Direction.UP)) && state.getValue(FiberCableBlock.SIDES.get(Direction.DOWN)),
                    "fiber connects to fiber");
                helper.assertTrue(!state.getValue(FiberCableBlock.SIDES.get(Direction.EAST)), "fiber does not connect to stone");
                helper.assertTrue(!state.getValue(FiberCableBlock.SIDES.get(Direction.NORTH)), "fiber does not connect to a CC cable");
                var ccState = level.getBlockState(ccPos);
                var south = ccState.getBlock().getStateDefinition().getProperty("south");
                helper.assertTrue(south instanceof BooleanProperty p && !ccState.getValue(p), "the CC cable does not connect to fiber");
                var bottom = level.getBlockState(s.base().above(2));
                helper.assertTrue(bottom.getValue(FiberCableBlock.SIDES.get(Direction.DOWN)), "fiber connects to the mainframe");
                var top = level.getBlockState(s.base().above(FAR));
                helper.assertTrue(top.getValue(FiberCableBlock.SIDES.get(Direction.UP)), "fiber connects to the relay");
            })
            .thenWaitUntil(() -> {
                var result = NetworkService.result(s.mainframe().getMainframeId());
                String relayId = ((CloudRelayBlockEntity) level.getBlockEntity(relayPos)).relayId().toString();
                helper.assertTrue(result != null && result.reachable(relayId), "relay reachable over fiber");
                helper.assertTrue(result.links().stream().anyMatch(l -> l.kind() == NetworkGraph.LinkKind.FIBER
                    && (l.a().equals(relayId) || l.b().equals(relayId))), "fiber link mainframe ↔ relay");
                helper.assertTrue(uplinkAt(helper, level, ccUplink).wired().visibleNodes().isEmpty(), "CC cable next to fiber sees no cloud node");
                helper.assertTrue(NetworkService.fibers(uplinkAt(helper, level, ccUplink).getUplinkId().toString()).isEmpty(),
                    "the uplink on the CC cable is not on the fiber network");
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    /**
     * A fiber member counts {@code fiberCapacityWeight} (0.5) against the device capacity and pays half the per-device
     * FE; a radio uplink next to it counts fully.
     */
    public static void fiberCapacity(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        var start = s.base().above(2);
        for (int i = 0; i < 4; i++) {
            fiber(level, start.above(i));
            placed.add(start.above(i));
        }
        var fiberUplink = start.above(4);
        uplink(level, fiberUplink, Direction.UP);
        var radioUplink = s.base().above(12).west(3);
        uplink(level, radioUplink, Direction.UP);
        placed.addAll(List.of(fiberUplink, fiberUplink.above(), radioUplink, radioUplink.above()));

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(!NetworkService.fibers(uplinkAt(helper, level, fiberUplink).getUplinkId().toString()).isEmpty(),
                "uplink is on the fiber network"))
            .thenExecute(() -> {
                uplinkAt(helper, level, fiberUplink).bind(s.link(), s.owner());
                uplinkAt(helper, level, radioUplink).bind(s.link(), s.owner());
            })
            .thenWaitUntil(() -> {
                var fiberRoute = route(helper, level, fiberUplink);
                var radioRoute = route(helper, level, radioUplink);
                helper.assertTrue(fiberRoute.connected() && fiberRoute.fiber(), "fiber route: " + fiberRoute);
                helper.assertTrue(radioRoute.connected() && radioRoute.via() == NetworkGraph.LinkKind.RADIO, "radio route: " + radioRoute);
                var counts = NetworkService.counts(s.mainframe().getMainframeId());
                helper.assertTrue(counts.devices() == 2 && Math.abs(counts.capacityUsed() - 1.5) < 1e-9, "capacity 1 + 0.5: " + counts);
                long uplinks = s.mainframe().powerBreakdown().get("uplinks");
                // Rates come from the server config (a dev run directory may keep older defaults).
                long rate = ai.ezfox.cccloud.CloudConfig.get(ai.ezfox.cccloud.CloudConfig.MAINFRAME_PER_UPLINK_FE);
                double factor = ai.ezfox.cccloud.CloudConfig.get(ai.ezfox.cccloud.CloudConfig.FIBER_POWER_FACTOR);
                long expected = rate + (long) Math.ceil(rate * factor - 1e-9);
                helper.assertTrue(uplinks == expected, "1 radio uplink (" + rate + " FE) + 1 fiber uplink (" + factor + "×): expected " + expected + ", got " + uplinks);
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    /** Two Ender Fiber Links bound to the mainframe join an Overworld and a Nether fiber network. */
    public static void enderFiberAcrossDimensions(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "nether");
        var placed = new ArrayList<BlockPos>();
        var start = s.base().above(2);
        for (int i = 0; i < 3; i++) {
            fiber(level, start.above(i));
            placed.add(start.above(i));
        }
        var l1 = start.above(3);
        level.setBlock(l1, ModRegistry.ENDER_FIBER_LINK.get().defaultBlockState(), Block.UPDATE_ALL);
        placed.add(l1);

        var netherBase = new BlockPos((s.base().getX() >> 3) - 40, 100, (s.base().getZ() >> 3) - 40);
        var chunk = new net.minecraft.world.level.ChunkPos(netherBase.getX() >> 4, netherBase.getZ() >> 4);
        nether.setChunkForced(chunk.x(), chunk.z(), true);
        var netherPlaced = new ArrayList<BlockPos>();
        var l2 = netherBase;
        nether.setBlock(l2, ModRegistry.ENDER_FIBER_LINK.get().defaultBlockState(), Block.UPDATE_ALL);
        netherPlaced.add(l2);
        for (int i = 1; i <= 3; i++) {
            fiber(nether, l2.above(i));
            netherPlaced.add(l2.above(i));
        }
        var up = l2.above(4);
        uplink(nether, up, Direction.UP);
        netherPlaced.addAll(List.of(up, up.above()));

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                var uplink = uplinkAt(helper, nether, up);
                uplink.bind(s.link(), s.owner());
                helper.assertTrue(uplink.getLink() == null, "before the links are bound the Nether uplink is unreachable");
                ((EnderFiberLinkBlockEntity) level.getBlockEntity(l1)).bind(s.link(), s.owner());
                ((EnderFiberLinkBlockEntity) nether.getBlockEntity(l2)).bind(s.link(), s.owner());
            })
            .thenIdle(2)
            .thenExecute(() -> {
                var uplink = uplinkAt(helper, nether, up);
                uplink.bind(s.link(), s.owner());
                helper.assertTrue(uplink.getLink() != null, "linking in the Nether works over the ender fiber link");
            })
            .thenWaitUntil(() -> {
                var route = route(helper, nether, up);
                helper.assertTrue(route.connected() && route.fiber() && route.airHops() == 0
                    && route.path().equals(List.of(s.mainframe().getMainframeId().toString())), "mainframe ⇄ fiber ⇄ uplink across dimensions: " + route);
            })
            .thenExecute(() -> {
                clear(nether, netherPlaced);
                clear(level, placed);
                nether.setChunkForced(chunk.x(), chunk.z(), false);
            })
            .thenSucceed();
    }
}
