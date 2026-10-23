package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.history.HistoryApi;
import ai.ezfox.cccloud.history.HistoryData;
import ai.ezfox.cccloud.history.HistoryService;
import ai.ezfox.cccloud.history.HistorySeries;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import com.google.gson.JsonPrimitive;
import java.util.EnumSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.5 F long-term statistics on a real network: a gauge with {@code history:"24h"} is sampled into the store, limited
 * to the base retention (1 h without a Memory upgrade), and served by the history API.
 */
public final class HistoryTests {
    private static final BlockPos ANCHOR = new BlockPos(5, 1, 4);
    private static final BlockPos UPLINK = new BlockPos(4, 1, 4);

    private HistoryTests() {
    }

    public static void widgetSeries(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(ANCHOR, Blocks.STONE);
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.EAST));
        var server = helper.getLevel().getServer();
        String[] key = {null};
        String[] uplinkId = {null};
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
                uplink.forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
                helper.assertTrue(uplink.beginLua(), "Lua may edit");
                try {
                    uplink.putWidget(WidgetFactory.create(WidgetType.GAUGE, "temp", "Temp", Map.of("value", 42, "history", "24h"), 64));
                } catch (WidgetFactory.WidgetException e) {
                    throw helper.assertionException(e.getMessage());
                }
                uplinkId[0] = uplink.getUplinkId().toString();
                key[0] = HistoryService.widgetKey(uplinkId[0], "temp");
            })
            .thenWaitUntil(() -> {
                var store = HistoryData.get(server).store();
                var points = store.points(mainframe.getMainframeId(), key[0], HistorySeries.Range.H24, HistoryService.now());
                helper.assertTrue(!points.isEmpty() && points.getLast().value() == 42, "sampled: " + points);
                helper.assertTrue(store.range(mainframe.getMainframeId(), key[0]) == HistorySeries.Range.H1, "base retention is 1 h");
            })
            .thenExecute(() -> {
                var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
                uplink.setValue("temp", new JsonPrimitive(58));
            })
            .thenWaitUntil(() -> {
                var response = HistoryApi.handle(HistoryData.get(server).store(), mainframe.getMainframeId(), EnumSet.allOf(ai.ezfox.cccloud.access.Perm.class),
                    false, "24h", "json", "w", uplinkId[0], "temp", "24h", HistoryService.retentionHours(mainframe.getMainframeId()), HistoryService.now());
                helper.assertTrue(response.status() == 200, "api: " + response.body());
                var body = response.body();
                helper.assertTrue("1h".equals(body.get("kept").getAsString()) && body.get("retentionHours").getAsInt() == 1, "kept: " + body);
                var last = body.getAsJsonArray("points").get(body.getAsJsonArray("points").size() - 1).getAsJsonArray().get(1).getAsDouble();
                helper.assertTrue(last > 42 && last <= 58, "the running minute averages the new value in: " + last);
            })
            .thenSucceed();
    }
}
