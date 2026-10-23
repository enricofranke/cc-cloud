package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.OrphanSections;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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

    /** Places a powered mainframe and a linked uplink with one widget; returns {mainframe, uplink} once it is live. */
    private static void linkedUplink(GameTestHelper helper, java.util.function.BiConsumer<ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity, CloudUplinkBlockEntity> then) {
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
            })
            .thenWaitUntil(() -> helper.assertTrue(WebHub.INSTANCE.uplinkForTest(mainframe.getMainframeId(), uplink[0].getUplinkId().toString()) != null,
                "section published"))
            .thenExecute(() -> then.accept(mainframe, uplink[0]))
            .thenSucceed();
    }

    /** Breaking an uplink the normal way drops its section at once (dashboard, cache, the mainframe's link list). */
    public static void brokenUplinkDropsSection(GameTestHelper helper) {
        linkedUplink(helper, (mainframe, uplink) -> {
            var id = uplink.getUplinkId();
            helper.getLevel().destroyBlock(helper.absolutePos(UPLINK), false);
            helper.assertTrue(WebHub.INSTANCE.uplinkForTest(mainframe.getMainframeId(), id.toString()) == null, "section gone after breaking");
            helper.assertTrue(!mainframe.getLinkedUplinks().containsKey(id), "unlinked from the mainframe");
            helper.assertTrue(SectionCache.get(helper.getLevel().getServer()).size(mainframe.getMainframeId()) == 0, "not cached");
        });
    }

    /**
     * Removed without side effects (what /setblock and /fill do): the section stays as an offline card until the
     * block has been missing for the grace time, then it is dropped like a broken one.
     */
    public static void silentlyRemovedUplinkDropsSection(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        int grace = 40;
        long[] start = {0};
        java.util.UUID[] ids = {null, null};
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
                ids[0] = mainframe.getMainframeId();
                ids[1] = uplink[0].getUplinkId();
            })
            .thenWaitUntil(() -> helper.assertTrue(WebHub.INSTANCE.uplinkForTest(ids[0], ids[1].toString()) != null, "section published"))
            .thenExecute(() -> {
                // /setblock ... air: flags 2 | 256 (no block-entity side effects, so no preRemoveSideEffects)
                helper.getLevel().setBlock(helper.absolutePos(UPLINK), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
                var cached = WebHub.INSTANCE.uplinkForTest(ids[0], ids[1].toString());
                helper.assertTrue(cached != null && !cached.get("online").getAsBoolean(), "first an offline card: " + cached);
                start[0] = server.getTickCount();
                OrphanSections.sweep(server, start[0], grace);
                helper.assertTrue(WebHub.INSTANCE.uplinkForTest(ids[0], ids[1].toString()) != null, "kept within the grace time");
                helper.assertTrue(mainframe.getLinkedUplinks().containsKey(ids[1]), "still linked within the grace time");
            })
            .thenIdle(grace + 1)
            .thenExecute(() -> {
                OrphanSections.sweep(server, server.getTickCount(), grace);
                helper.assertTrue(WebHub.INSTANCE.uplinkForTest(ids[0], ids[1].toString()) == null, "orphan section dropped");
                helper.assertTrue(!mainframe.getLinkedUplinks().containsKey(ids[1]), "unlinked from the mainframe");
                helper.assertTrue(SectionCache.get(server).size(ids[0]) == 0, "cache forgotten");
            })
            .thenSucceed();
    }
}
