package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.SectionCache;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.SectionChecks;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/**
 * Sections of unloaded uplinks stay on the dashboard: the chunk-unload path (setRemoved) caches the last snapshot
 * (loaded=false, lastSeen, last values) in SavedData, actions get "unloaded"; loading again makes it live.
 */
public final class SectionCacheTests {
    private static final BlockPos UPLINK = new BlockPos(5, 1, 4);

    private SectionCacheTests() {
    }

    public static void unloadAndReload(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN));
        CloudUplinkBlockEntity[] uplink = {null};
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                uplink[0] = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
                uplink[0].forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
                uplink[0].beginLua();
                try {
                    uplink[0].putWidget(WidgetFactory.create(WidgetType.GAUGE, "temp", "Temp", null));
                } catch (WidgetFactory.WidgetException e) {
                    throw helper.assertionException(e.getMessage());
                }
                uplink[0].setValue("temp", new JsonPrimitive(42));
            })
            .thenWaitUntil(() -> {
                var live = WebHub.INSTANCE.uplinkForTest(mainframe.getMainframeId(), uplink[0].getUplinkId().toString());
                helper.assertTrue(live != null && live.get("loaded").getAsBoolean() && live.toString().contains("temp"), "live section published: " + live);
            })
            .thenExecute(() -> {
                // What a chunk unload does to the block entity.
                uplink[0].setRemoved();
                var id = uplink[0].getUplinkId().toString();
                var cached = WebHub.INSTANCE.uplinkForTest(mainframe.getMainframeId(), id);
                helper.assertTrue(cached != null && !cached.get("loaded").getAsBoolean() && cached.has("lastSeen"), "cached section: " + cached);
                helper.assertTrue(cached.toString().contains("42"), "last values kept");
                helper.assertTrue(SectionCache.get(helper.getLevel().getServer()).size(mainframe.getMainframeId()) == 1, "persisted in SavedData");
                helper.assertTrue("unloaded".equals(SectionChecks.check(mainframe.getMainframeId(), uplink[0].getUplinkId())), "actions are refused");
                // Loading again: the live snapshot replaces the cached one.
                uplink[0].clearRemoved();
                uplink[0].onLoad();
                uplink[0].setValue("temp", new JsonPrimitive(43));
            })
            .thenWaitUntil(() -> {
                var live = WebHub.INSTANCE.uplinkForTest(mainframe.getMainframeId(), uplink[0].getUplinkId().toString());
                helper.assertTrue(live != null && live.get("loaded").getAsBoolean(), "live again: " + live);
                helper.assertTrue(SectionChecks.check(mainframe.getMainframeId(), uplink[0].getUplinkId()) == null, "actions reach it again");
            })
            .thenSucceed();
    }
}
