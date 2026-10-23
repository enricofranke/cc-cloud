package ai.ezfox.cccloud.analyzer;

import static ai.ezfox.cccloud.ingame.InGameTestSupport.player;

import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.ingame.InGameRegistry;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.NetworkGraph;
import ai.ezfox.cccloud.web.WebAuthData;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cloud Analyzer: the overlay payload only ever contains networks where the receiving player has view.positions (two
 * owners see only their own mainframe, a stranger and a viewer see nothing, an operator sees that network, a player who stops holding the item gets
 * one empty payload), and the placement preview agrees with the server's own link check.
 */
public final class AnalyzerGameTests {
    static final Map<UUID, List<String>> CAPTURED = new ConcurrentHashMap<>();

    private AnalyzerGameTests() {
    }

    static void capture() {
        AnalyzerService.sender = (player, data) -> {
            if (player instanceof FakePlayer) CAPTURED.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(data.json());
            else PacketDistributor.sendToPlayer(player, data);
        };
    }

    static String last(ServerPlayer player) {
        var list = CAPTURED.get(player.getUUID());
        return list == null || list.isEmpty() ? null : list.getLast();
    }

    static void hold(ServerPlayer player, boolean analyzer) {
        player.setItemInHand(InteractionHand.MAIN_HAND, analyzer ? new ItemStack(InGameRegistry.CLOUD_ANALYZER.get()) : ItemStack.EMPTY);
    }

    public static void filtering(GameTestHelper helper) {
        capture();
        var alice = player(helper, "analyzer_alice");
        var bob = player(helper, "analyzer_bob");
        var carol = player(helper, "analyzer_carol");
        var mfA = CloudGameTests.placeMainframe(helper, alice, new BlockPos(0, 0, 4));
        var mfB = CloudGameTests.placeMainframe(helper, bob, new BlockPos(4, 0, 0));
        mfA.setOwner(alice);
        mfB.setOwner(bob);
        mfA.setEnabled(true);
        mfB.setEnabled(true);
        CloudGameTests.keepPowered(helper, mfA);
        CloudGameTests.keepPowered(helper, mfB);
        String a = mfA.getMainframeId().toString(), b = mfB.getMainframeId().toString();
        long[] tick = {1000};
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(NetworkService.result(mfA.getMainframeId()) != null && NetworkService.result(mfB.getMainframeId()) != null,
                "both networks computed"))
            .thenExecute(() -> {
                for (var p : List.of(alice, bob, carol)) {
                    AnalyzerService.reset(p);
                    CAPTURED.remove(p.getUUID());
                    hold(p, true);
                    AnalyzerService.update(p, tick[0]);
                }
                helper.assertTrue(last(alice).contains(a) && !last(alice).contains(b), "Alice gets only her network: " + last(alice));
                helper.assertTrue(last(bob).contains(b) && !last(bob).contains(a), "Bob gets only his network");
                helper.assertTrue(!last(carol).contains(a) && !last(carol).contains(b), "a stranger gets no network at all: " + last(carol));
                var payload = JsonParser.parseString(last(alice)).getAsJsonObject();
                helper.assertTrue(payload.getAsJsonArray("nodes").size() >= 1 && payload.has("ranges"), "nodes and ranges: " + payload);

                var access = WebAuthData.access(helper.getLevel().getServer());
                access.setRole(mfA.getMainframeId(), carol.getUUID(), "analyzer_carol", Role.VIEWER);
                tick[0] += AnalyzerService.RESEND_TICKS;
                AnalyzerService.update(carol, tick[0]);
                helper.assertTrue(!last(carol).contains(a), "viewers have no view.positions: no positions for them");
                access.setRole(mfA.getMainframeId(), carol.getUUID(), "analyzer_carol", Role.OPERATOR);
                tick[0] += AnalyzerService.RESEND_TICKS;
                AnalyzerService.update(carol, tick[0]);
                helper.assertTrue(last(carol).contains(a) && !last(carol).contains(b), "view.positions on Alice's mainframe shows only that network");

                int before = CAPTURED.get(alice.getUUID()).size();
                AnalyzerService.update(alice, 1000 + 1); // Alice's last send was at tick 1000
                AnalyzerService.update(alice, 1000 + AnalyzerService.RESEND_TICKS - 1);
                helper.assertTrue(CAPTURED.get(alice.getUUID()).size() == before, "unchanged data is not resent within 2 s");
                AnalyzerService.update(alice, 1000 + AnalyzerService.RESEND_TICKS);
                helper.assertTrue(CAPTURED.get(alice.getUUID()).size() == before + 1, "resent every 2 s while held");
                before++;
                hold(alice, false);
                AnalyzerService.update(alice, tick[0] + 2);
                helper.assertTrue("{}".equals(last(alice)), "putting the analyzer away clears the overlay once");
                AnalyzerService.update(alice, tick[0] + 3);
                helper.assertTrue(CAPTURED.get(alice.getUUID()).size() == before + 1, "and only once");
            })
            .thenSucceed();
    }

    public static void preview(GameTestHelper helper) {
        capture();
        var owner = player(helper, "analyzer_preview_owner");
        var mf = CloudGameTests.placeMainframe(helper, owner, new BlockPos(0, 0, 4));
        mf.setOwner(owner);
        mf.setEnabled(true);
        CloudGameTests.keepPowered(helper, mf);
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(NetworkService.result(mf.getMainframeId()) != null, "network computed"))
            .thenExecute(() -> {
                var payload = AnalyzerService.build(owner);
                var nodes = AnalyzerGeometry.nodes(payload);
                helper.assertTrue(nodes.size() == 1 && nodes.getFirst().hops() == 0, "the mainframe node: " + nodes);
                var ranges = payload.getAsJsonObject("ranges");
                double uplink = ranges.getAsJsonArray("uplink").get(0).getAsDouble();
                double relayNodes = ranges.getAsJsonArray("relay").get(0).getAsDouble();
                var level = helper.getLevel();
                String dim = level.dimension().identifier().toString();
                var center = mf.getBlockPos();
                for (int dx : new int[]{5, 40, 80, 95, 97, 150, 300}) {
                    var pos = center.offset(dx, 0, 0);
                    var p = AnalyzerGeometry.preview(nodes, dim, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, uplink, false);
                    boolean server = NetworkService.checkLink(mf, level, pos, NetworkGraph.DeviceType.UPLINK) == null;
                    helper.assertTrue(p.connects() == server, "preview and server agree at +" + dx + ": preview " + p.connects() + ", server " + server);
                    if (p.connects()) {
                        helper.assertTrue(p.hops() == 1 && p.signal() > 0 && p.signal() <= 1, "one hop to the mainframe: " + p);
                    } else {
                        helper.assertTrue(p.nearest() != null && Math.abs(p.distance() - dx) < 2, "names the nearest node and its distance: " + p);
                    }
                }
                var near = AnalyzerGeometry.preview(nodes, dim, center.getX() + 50.5, center.getY() + 1, center.getZ() + .5, relayNodes, true);
                helper.assertTrue(near.connects() && near.hops() == 1, "a relay 50 blocks away joins the mainframe: " + near);
                var far = AnalyzerGeometry.preview(nodes, dim, center.getX() + 500.5, center.getY() + 1, center.getZ() + .5, relayNodes, true);
                helper.assertTrue(!far.connects(), "a relay 500 blocks away does not");
            })
            .thenSucceed();
    }
}
