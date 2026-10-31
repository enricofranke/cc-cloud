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


    // ---- relays belong to a team (docs/SPEC.md "Relays belong to a team") ---------------------------------------------

    /** Places a relay the way a player does (setPlacedBy records the owner), full of FE, serving only these mainframes. */
    static CloudRelayBlockEntity ownedRelay(GameTestHelper helper, BlockPos pos, Block block, Player owner, java.util.UUID... mainframes) {
        var level = helper.getLevel();
        var state = block.defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        block.setPlacedBy(level, pos, state, owner, new net.minecraft.world.item.ItemStack(block));
        if (!(level.getBlockEntity(pos) instanceof CloudRelayBlockEntity relay)) throw helper.assertionException("relay missing at " + pos);
        relay.fillForTest();
        relay.restrictForTest(mainframes); // tests run side by side: other tests' networks never see it
        return relay;
    }

    /** A mock player with its own name (makeMockPlayer names every player alike; scoreboard teams go by name). */
    static Player namedPlayer(GameTestHelper helper, String name) {
        var id = java.util.UUID.nameUUIDFromBytes(("cccloud-test:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Player(helper.getLevel(), new com.mojang.authlib.GameProfile(id, name)) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }

            @Override
            public boolean isClientAuthoritative() {
                return false;
            }
        };
    }

    private static boolean inNetwork(java.util.UUID mainframeId, CloudRelayBlockEntity relay) {
        var result = NetworkService.result(mainframeId);
        return result != null && result.nodes().stream().anyMatch(n -> n.id().equals(relay.relayId().toString()));
    }

    private static boolean onMap(java.util.UUID mainframeId, CloudRelayBlockEntity relay) {
        var json = ai.ezfox.cccloud.web.WebHub.INSTANCE.networkForTest(mainframeId, true);
        return json != null && json.toString().contains(relay.relayId().toString());
    }

    /**
     * Two owners side by side (Enrico's team relay between both bases): only Enrico's network routes over it and only his
     * map shows it; Steini cannot link through it, change it or join it to his Ender group. Same scoreboard team: it
     * carries Steini too, and stops when he leaves. "Share with: Everyone" (owner only) carries every network. A relay
     * without an owner (placed before relays had owners) carries everyone.
     */
    public static void relayTeams(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        String tag = Integer.toHexString(helper.absolutePos(BlockPos.ZERO).hashCode() & 0xffff);
        var enrico = namedPlayer(helper, "cc_enrico_" + tag);
        var steini = namedPlayer(helper, "cc_steini_" + tag);
        for (var p : List.of(enrico, steini)) server.services().nameToIdCache().add(new net.minecraft.server.players.NameAndId(p.getUUID(), p.getGameProfile().name()));
        var mfA = CloudGameTests.placeMainframe(helper, enrico, new BlockPos(1, 0, 1));
        var mfB = CloudGameTests.placeMainframe(helper, steini, new BlockPos(3, 0, 3));
        for (var m : List.of(mfA, mfB)) {
            m.setEnabled(true);
            CloudGameTests.keepPowered(helper, m);
        }
        var linkA = new MainframeLink(GlobalPos.of(level.dimension(), mfA.getBlockPos()), mfA.getMainframeId());
        var linkB = new MainframeLink(GlobalPos.of(level.dimension(), mfB.getBlockPos()), mfB.getMainframeId());
        var base = mfA.getBlockPos();
        var relayPos = base.above(80);
        var upA = base.above(120);
        var upB = base.above(120).east(2);
        var enderPos = helper.absolutePos(new BlockPos(0, 1, 4));
        var legacyPos = helper.absolutePos(new BlockPos(4, 1, 0));
        var relay = ownedRelay(helper, relayPos, ModRegistry.CLOUD_RELAY.get(), enrico, mfA.getMainframeId(), mfB.getMainframeId());
        uplink(level, upA);
        uplink(level, upB);
        var scoreboard = server.getScoreboard();
        String teamName = "cccloud_r" + tag;

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                helper.assertTrue(enrico.getUUID().equals(relay.owner()) && !relay.legacy(), "the placer owns the relay");
                helper.assertTrue(relay.effectiveShare() == ai.ezfox.cccloud.network.graph.RelaySharing.Share.TEAM, "team by default");
                uplinkAt(helper, level, upA).bind(linkA, enrico);
                helper.assertTrue(uplinkAt(helper, level, upA).getLink() != null, "Enrico links through his own relay");
                uplinkAt(helper, level, upB).bind(linkB, steini);
                helper.assertTrue(uplinkAt(helper, level, upB).getLink() == null, "Steini cannot link through Enrico's relay");
                helper.assertTrue(!relay.mayChangeShare(steini) && relay.mayChangeShare(enrico), "only the owner changes the setting");
                helper.assertTrue("relay_not_owner".equals(ai.ezfox.cccloud.gui.GuiActions.relay("share", "everyone", true, relay.mayChangeShare(steini),
                    relay.legacy(), share -> "changed")), "Steini's screen action is refused");
            })
            .thenWaitUntil(() -> {
                var route = NetworkService.route(uplinkAt(helper, level, upA).getUplinkId());
                helper.assertTrue(route != null && route.connected() && route.path().contains(relay.relayId().toString()), "Enrico routes over his relay: " + route);
                helper.assertTrue(inNetwork(mfA.getMainframeId(), relay) && onMap(mfA.getMainframeId(), relay), "Enrico's map shows it");
                helper.assertTrue(!inNetwork(mfB.getMainframeId(), relay) && !onMap(mfB.getMainframeId(), relay), "Steini's topology/map/3D never show it");
                var screen = NetworkService.screenNetwork(mfB);
                helper.assertTrue(screen != null && !screen.json().toString().contains("\"relay\""), "nor his mainframe screen");
                helper.assertTrue(NetworkService.isServing(relay.relayId()), "the relay serves Enrico");
            })
            .thenExecute(() -> {
                // Same scoreboard team: the relay carries Steini's network too (scoreboard teams have no events: the
                // game re-asks within 10 s; the test does what a team event does).
                var team = scoreboard.getPlayerTeam(teamName) != null ? scoreboard.getPlayerTeam(teamName) : scoreboard.addPlayerTeam(teamName);
                scoreboard.addPlayerToTeam(enrico.getGameProfile().name(), team);
                scoreboard.addPlayerToTeam(steini.getGameProfile().name(), team);
                ai.ezfox.cccloud.relay.RelayShares.teamsChanged();
                helper.assertTrue(relay.carries(steini.getUUID()), "a team mate's network is carried");
                uplinkAt(helper, level, upB).bind(linkB, steini);
                helper.assertTrue(uplinkAt(helper, level, upB).getLink() != null, "Steini links through his team mate's relay");
            })
            .thenWaitUntil(() -> {
                var route = NetworkService.route(uplinkAt(helper, level, upB).getUplinkId());
                helper.assertTrue(route != null && route.connected() && route.path().contains(relay.relayId().toString()), "team route: " + route);
                helper.assertTrue(onMap(mfB.getMainframeId(), relay), "now on Steini's map");
            })
            .thenExecute(() -> {
                scoreboard.removePlayerFromTeam(steini.getGameProfile().name(), scoreboard.getPlayerTeam(teamName));
                ai.ezfox.cccloud.relay.RelayShares.teamsChanged();
            })
            .thenWaitUntil(() -> {
                var route = NetworkService.route(uplinkAt(helper, level, upB).getUplinkId());
                helper.assertTrue(route != null && !route.connected(), "left the team: no route over the relay any more: " + route);
                helper.assertTrue(!onMap(mfB.getMainframeId(), relay), "and gone from Steini's map");
            })
            .thenExecute(() -> {
                // The owner shares it with everyone (the relay screen's "Share with").
                String error = ai.ezfox.cccloud.gui.GuiActions.relay("share", "everyone", true, relay.mayChangeShare(enrico), relay.legacy(), share -> {
                    relay.setShare(share, enrico.getGameProfile().name());
                    return null;
                });
                helper.assertTrue(error == null && relay.share() == ai.ezfox.cccloud.network.graph.RelaySharing.Share.EVERYONE, "owner shares it: " + error);
            })
            .thenWaitUntil(() -> {
                var route = NetworkService.route(uplinkAt(helper, level, upB).getUplinkId());
                helper.assertTrue(route != null && route.connected() && route.path().contains(relay.relayId().toString()), "an everyone relay carries Steini: " + route);
            })
            .thenExecute(() -> {
                // Ender groups: Steini cannot pull Enrico's Ender Relay into his group; Enrico can.
                var ender = ownedRelay(helper, enderPos, ModRegistry.ENDER_RELAY.get(), enrico, mfA.getMainframeId(), mfB.getMainframeId());
                ender.bindEnder(linkB, steini);
                helper.assertTrue(ender.enderGroup() == null, "another team's mainframe does not join Enrico's Ender Relay");
                ender.bindEnder(linkA, enrico);
                helper.assertTrue(mfA.getMainframeId().equals(ender.enderGroup()), "the owner links it");
                // A relay without an owner (saved before relays had owners) carries every network.
                level.setBlock(legacyPos, ModRegistry.CLOUD_RELAY.get().defaultBlockState(), Block.UPDATE_ALL);
                var legacy = (CloudRelayBlockEntity) level.getBlockEntity(legacyPos);
                if (legacy != null) legacy.restrictForTest(mfA.getMainframeId(), mfB.getMainframeId());
                helper.assertTrue(legacy != null && legacy.legacy() && legacy.carries(steini.getUUID()) && legacy.carries(enrico.getUUID()),
                    "legacy relay carries everyone");
                helper.assertTrue("relay_legacy".equals(ai.ezfox.cccloud.gui.GuiActions.relay("share", "team", true, true, legacy.legacy(), share -> "changed")),
                    "it has no setting");
            })
            .thenExecute(() -> {
                var team = scoreboard.getPlayerTeam(teamName);
                if (team != null) scoreboard.removePlayerTeam(team);
                clear(level, upA, upB);
                for (var pos : List.of(relayPos, enderPos, legacyPos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenSucceed();
    }
}
