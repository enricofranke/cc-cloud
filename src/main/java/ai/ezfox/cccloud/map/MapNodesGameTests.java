package ai.ezfox.cccloud.map;

import static ai.ezfox.cccloud.ingame.InGameTestSupport.player;

import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.network.NetworkService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Map nodes (JourneyMap waypoints): a subscribed owner gets his mainframe, a subscribed stranger gets nothing, an
 * unsubscribed player gets no payload at all.
 */
public final class MapNodesGameTests {
    static final Map<UUID, String> CAPTURED = new ConcurrentHashMap<>();

    private MapNodesGameTests() {
    }

    public static void filtering(GameTestHelper helper) {
        MapNodesService.sender = (player, data) -> {
            if (player instanceof FakePlayer) CAPTURED.put(player.getUUID(), data.json());
            else PacketDistributor.sendToPlayer(player, data);
        };
        var owner = player(helper, "mapnodes_owner");
        var stranger = player(helper, "mapnodes_stranger");
        var lurker = player(helper, "mapnodes_lurker");
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(0, 0, 4));
        mainframe.setOwner(owner);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        String id = mainframe.getMainframeId().toString();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(NetworkService.result(mainframe.getMainframeId()) != null, "network computed"))
            .thenExecute(() -> {
                CAPTURED.clear();
                MapNodesService.subscribe(owner, true);
                MapNodesService.subscribe(stranger, true);
                helper.assertTrue(CAPTURED.getOrDefault(owner.getUUID(), "").contains("\"mf\":\"" + id + "\""), "owner gets the mainframe: " + CAPTURED.get(owner.getUUID()));
                helper.assertTrue(CAPTURED.getOrDefault(owner.getUUID(), "").contains("\"type\":\"mainframe\""), "mainframe node");
                helper.assertTrue(!CAPTURED.getOrDefault(stranger.getUUID(), "").contains(id), "stranger gets nothing: " + CAPTURED.get(stranger.getUUID()));
                helper.assertTrue(!CAPTURED.containsKey(lurker.getUUID()) && !MapNodesService.subscribed(lurker), "no payload without subscription");
                MapNodesService.subscribe(owner, false);
                MapNodesService.subscribe(stranger, false);
                helper.assertTrue(!MapNodesService.subscribed(owner), "unsubscribed");
            })
            .thenSucceed();
    }
}
