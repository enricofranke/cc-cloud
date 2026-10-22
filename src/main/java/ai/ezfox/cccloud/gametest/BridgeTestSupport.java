package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.bridge.BridgePeripheral;
import ai.ezfox.cccloud.bridge.CloudBridgeBlock;
import ai.ezfox.cccloud.bridge.StorageTemplate;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.WidgetType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Shared checks of the bridge game tests (compat/ae2, compat/rs). Mod-independent: the storage-mod specific setup
 * (placing a powered network and inserting items) is done by the caller.
 */
public final class BridgeTestSupport {
    public static final String ITEM = "minecraft:iron_ingot";
    public static final long AMOUNT = 100;

    private BridgeTestSupport() {
    }

    /** Mainframe floor for the linking stage (lower half at 0,1,4; the network sits at x 1..3, z 2). */
    static final BlockPos MAINFRAME_FLOOR = new BlockPos(0, 0, 4);

    /** State shared by the linking stage of the bridge tests. */
    public static final class Linking {
        net.minecraft.world.entity.player.Player owner;
        ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity mainframe;
        ai.ezfox.cccloud.item.MainframeLink link;
    }

    /** Places an owned mainframe; NeoForge registers it on the next tick (onLoad). */
    public static Linking placeMainframe(GameTestHelper helper) {
        var linking = new Linking();
        linking.owner = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        linking.mainframe = CloudGameTests.placeMainframe(helper, linking.owner, MAINFRAME_FLOOR);
        linking.mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, linking.mainframe);
        linking.link = new ai.ezfox.cccloud.item.MainframeLink(
            net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), linking.mainframe.getBlockPos()), linking.mainframe.getMainframeId());
        return linking;
    }

    /** Binds bridge and uplink: a stranger is refused, the owner succeeds. */
    public static void bind(GameTestHelper helper, BlockPos bridgePos, BridgeHost host, Linking linking) {
        var stranger = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        host.bridgeSection().bind(linking.link, stranger);
        helper.assertTrue(!host.bridgeSection().isLinked(), "a stranger must not link the bridge");
        host.bridgeSection().bind(linking.link, linking.owner);
        helper.assertTrue(host.bridgeSection().isLinked(), "the owner links the bridge");
        var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridgePos.above()));
        uplink.bind(linking.link, linking.owner);
        helper.assertTrue(uplink.getLink() != null, "the uplink on the bridge is linked too");
    }

    /** A linked bridge is its own Storage section; the uplink on it is hidden (only one section). */
    public static void assertLinked(GameTestHelper helper, BlockPos bridgePos, BridgeHost host, Linking linking) {
        var hub = ai.ezfox.cccloud.web.WebHub.INSTANCE;
        var section = host.bridgeSection();
        var mainframeId = linking.mainframe.getMainframeId();
        String sectionId = section.sectionId().toString();
        helper.assertTrue("item_search".equals(hub.widgetType(mainframeId, sectionId, StorageTemplate.SEARCH)), "bridge section on the dashboard");
        helper.assertTrue(section.status() == CloudStatus.ONLINE, "section online with its mainframe, got " + section.status());
        var items = section.values().get("items");
        helper.assertTrue(items != null && items.isJsonPrimitive() && items.getAsString().equals(String.valueOf(AMOUNT)), "section values: " + items);
        var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridgePos.above()));
        helper.assertTrue(uplink.isSuppressed(), "the uplink on a linked bridge is hidden");
        helper.assertTrue(hub.widgetType(mainframeId, uplink.getUplinkId().toString(), StorageTemplate.SEARCH) == null, "only one section (the bridge's own)");
        helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.section(section.sectionId()) == section, "actions are routed to the bridge section");
        var query = section.handleQuery(StorageTemplate.SEARCH, "iron");
        helper.assertTrue(query.error() == null && query.rows().size() == 1, "search through the bridge section: " + query.rows() + " " + query.error());
        var bad = section.handleAction(StorageTemplate.SEARCH, com.google.gson.JsonParser.parseString("{\"op\":\"craft\",\"name\":\"minecraft:iron_ingot\",\"count\":1}"),
            new Op.Craft(ITEM, 1), "gametest", false).getNow(null);
        helper.assertTrue(bad != null && "not_craftable".equals(bad.error()), "craft via the section validates: " + (bad == null ? null : bad.error()));
    }

    /** After unlinking the bridge, the uplink's own section comes back. */
    public static void unlinkAndAssert(GameTestHelper helper, BlockPos bridgePos, BridgeHost host, Linking linking) {
        host.bridgeSection().unlink("gametest");
        helper.assertTrue(!host.bridgeSection().isLinked(), "unlinked");
    }

    public static void assertUnlinked(GameTestHelper helper, BlockPos bridgePos, BridgeHost host, Linking linking) {
        var hub = ai.ezfox.cccloud.web.WebHub.INSTANCE;
        var mainframeId = linking.mainframe.getMainframeId();
        helper.assertTrue(hub.widgetType(mainframeId, host.bridgeSection().sectionId().toString(), StorageTemplate.SEARCH) == null, "bridge section removed");
        var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridgePos.above()));
        helper.assertTrue(!uplink.isSuppressed(), "uplink shown again");
        helper.assertTrue("item_search".equals(hub.widgetType(mainframeId, uplink.getUplinkId().toString(), StorageTemplate.SEARCH)), "uplink section is back");
    }

    /** A block of another mod by id; fails the test if it does not exist. */
    public static Block block(GameTestHelper helper, String id) {
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == null || block == Blocks.AIR) throw helper.assertionException("Missing block " + id);
        return block;
    }

    /** Places a direct-mode uplink on top of the bridge. */
    public static void placeUplinkOnTop(GameTestHelper helper, BlockPos bridge) {
        helper.setBlock(bridge.above(), ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.DOWN));
    }

    /**
     * Asserts everything the dashboard and Lua see once {@link #AMOUNT} iron ingots are in the network. Runs inside
     * {@code succeedWhen}, i.e. repeatedly until it passes; {@code once} guards the one-time watchlist edit.
     */
    public static void assertBridge(GameTestHelper helper, BlockPos bridgePos, BridgeHost host, BridgePeripheral peripheral, boolean[] once) {
        var core = host.bridgeCore();
        helper.assertTrue(core.isConnected(), "bridge must be connected to a powered network");
        helper.assertBlockProperty(bridgePos, CloudBridgeBlock.STATUS, CloudStatus.ONLINE);

        var row = core.getItem(ITEM);
        helper.assertTrue(row != null && row.count() == AMOUNT, "getItem: expected " + AMOUNT + " iron ingots, got " + row);
        var lua = peripheral.getItem(ITEM);
        helper.assertTrue(lua != null && ((Number) lua.get("count")).longValue() == AMOUNT, "peripheral getItem: " + lua);
        helper.assertTrue(lua != null && "Iron Ingot".equals(lua.get("displayName")), "display name: " + lua);

        var page = core.index().search("iron ingot", 50);
        helper.assertTrue(page.rows().stream().anyMatch(r -> r.name().equals(ITEM) && r.count() == AMOUNT), "findItems('iron ingot'): " + page.rows());
        helper.assertTrue(core.index().search("@minecraft", 50).rows().stream().anyMatch(r -> r.name().equals(ITEM)), "findItems('@minecraft')");
        helper.assertTrue(core.index().search("diamond", 50).rows().isEmpty(), "no diamonds expected");

        var storage = core.storage();
        helper.assertTrue(storage.has("itemsUsed") && storage.get("itemsUsed").getAsLong() >= AMOUNT, "getStorage itemsUsed: " + storage);
        helper.assertTrue(storage.has("typesUsed") && storage.get("typesUsed").getAsLong() >= 1, "getStorage typesUsed: " + storage);

        var craft = core.craft(ITEM, 1, "gametest");
        helper.assertTrue("not_craftable".equals(craft.error()), "craft without patterns must fail with not_craftable, got " + craft.error());
        helper.assertTrue("too_many".equals(core.craft(ITEM, 10_000_000, "gametest").error()) || !core.isCraftable(ITEM), "craft limit");

        if (!once[0]) {
            once[0] = true;
            helper.assertTrue(core.watchOp(new Op.WatchAdd(ITEM, 500)) == null, "watchlist add");
            helper.assertTrue("already_watched".equals(core.watchOp(new Op.WatchAdd(ITEM, 1))), "watchlist duplicate");
            helper.assertTrue("unknown_item".equals(core.watchOp(new Op.WatchAdd("nosuchmod:thing", 1))), "watchlist unknown item");
        }
        var watch = core.watchRows();
        helper.assertTrue(watch.size() == 1 && watch.get(0).getAsJsonObject().get("count").getAsLong() == AMOUNT, "watch rows: " + watch);

        // Direct mode: the uplink on top publishes the Storage template.
        var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bridgePos.above()));
        helper.assertTrue(uplink != null && uplink.isTemplate(), "uplink on a bridge must switch to the Storage template");
        var search = uplink.getWidget(StorageTemplate.SEARCH);
        helper.assertTrue(search != null && search.type() == WidgetType.ITEM_SEARCH, "template has an item_search widget");
        var items = uplink.getWidget("items");
        helper.assertTrue(items != null && items.value().isJsonPrimitive() && items.value().getAsString().equals(String.valueOf(AMOUNT)),
            "template label 'items' must show " + AMOUNT + ", got " + (items == null ? null : items.value()));
        var watchWidget = uplink.getWidget(StorageTemplate.WATCH);
        helper.assertTrue(watchWidget != null && watchWidget.value().isJsonArray() && watchWidget.value().getAsJsonArray().size() == 1,
            "template watchlist widget shows the watched item");
    }
}
