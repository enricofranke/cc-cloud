package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Shared GameTest steps of the device-card tests: a machine at {@link #DEVICE}, a direct-mode uplink next to it
 * (facing it), a powered mainframe the uplink is linked to, and helpers to read card values and run dashboard actions
 * exactly as the web layer does after its rights check ({@code handleAction}).
 */
public final class CardTestSupport {
    public static final BlockPos DEVICE = new BlockPos(2, 1, 2);
    public static final BlockPos UPLINK = new BlockPos(3, 1, 2);
    static final BlockPos MAINFRAME_FLOOR = new BlockPos(0, 0, 4);

    /** A linked mainframe and its owner. */
    public record Linked(Player owner, CloudMainframeBlockEntity mainframe) {
    }

    private CardTestSupport() {
    }

    public static Block block(GameTestHelper helper, String id) {
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == null || block == Blocks.AIR) throw helper.assertionException("Missing block " + id);
        return block;
    }

    /** Places a Cloud Uplink at {@link #UPLINK} sticking to the block at {@link #DEVICE} (towards -x). */
    public static void placeUplink(GameTestHelper helper) {
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
    }

    /** Face of the device the uplink touches. */
    public static Direction deviceSide() {
        return Direction.EAST;
    }

    public static CloudUplinkBlockEntity uplink(GameTestHelper helper) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK)) instanceof CloudUplinkBlockEntity uplink)) {
            throw helper.assertionException("uplink missing");
        }
        return uplink;
    }

    /** Places a powered, enabled mainframe (registered on the next tick); bind with {@link #bind}. */
    public static Linked placeMainframe(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, MAINFRAME_FLOOR);
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        return new Linked(owner, mainframe);
    }

    public static void bind(GameTestHelper helper, Linked linked) {
        var link = new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), linked.mainframe().getBlockPos()), linked.mainframe().getMainframeId());
        uplink(helper).bind(link, linked.owner());
        helper.assertTrue(uplink(helper).getLink() != null, "uplink linked");
    }

    /** The card is shown: kind, direct mode, snapshot template, published to the dashboard. */
    public static void assertCard(GameTestHelper helper, String kind, @Nullable Linked linked) {
        var uplink = uplink(helper);
        helper.assertTrue(kind.equals(uplink.deviceKind()), "device card " + kind + " expected, got '" + uplink.deviceKind() + "'");
        helper.assertTrue("direct".equals(uplink.mode()), "direct mode");
        var snapshot = uplink.snapshot();
        helper.assertTrue(kind.equals(snapshot.get("template").getAsString()), "snapshot template: " + snapshot.get("template"));
        if (linked != null) {
            helper.assertTrue(uplink.isOnline(), "uplink online");
            var first = snapshot.getAsJsonArray("widgets").get(0).getAsJsonObject().get("id").getAsString();
            helper.assertTrue(WebHub.INSTANCE.widgetType(linked.mainframe().getMainframeId(), uplink.getUplinkId().toString(), first) != null,
                "card published to the dashboard");
        }
    }

    public static JsonElement value(GameTestHelper helper, String widgetId) {
        var widget = uplink(helper).getWidget(widgetId);
        if (widget == null) throw helper.assertionException("widget " + widgetId + " missing");
        return widget.value();
    }

    public static double number(GameTestHelper helper, String widgetId) {
        var v = value(helper, widgetId);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw helper.assertionException(widgetId + " is not a number: " + v);
        return v.getAsDouble();
    }

    public static boolean bool(GameTestHelper helper, String widgetId) {
        var v = value(helper, widgetId);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw helper.assertionException(widgetId + " is not a boolean: " + v);
        return v.getAsBoolean();
    }

    public static String text(GameTestHelper helper, String widgetId) {
        var v = value(helper, widgetId);
        return v.isJsonPrimitive() ? v.getAsString() : v.toString();
    }

    /** Runs a dashboard action like the web layer does after its canControl check; returns the error or null. */
    public static @Nullable String act(GameTestHelper helper, String widgetId, JsonElement value, boolean pinVerified) {
        ActionResult result = uplink(helper).handleAction(widgetId, value, null, "gametest", pinVerified).getNow(null);
        if (result == null) throw helper.assertionException("action did not complete");
        return result.error();
    }

    public static @Nullable String act(GameTestHelper helper, String widgetId, Object value) {
        JsonElement json = value instanceof Boolean b ? new JsonPrimitive(b) : value instanceof Number n ? new JsonPrimitive(n) : new JsonPrimitive(value.toString());
        return act(helper, widgetId, json, false);
    }

    /** Inserts FE into the block through its energy capability; returns the inserted amount. */
    public static int insertEnergy(GameTestHelper helper, BlockPos pos, @Nullable Direction side, int amount) {
        var handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side);
        if (handler == null) throw helper.assertionException("no energy capability at " + pos);
        try (var tx = Transaction.openRoot()) {
            int inserted = handler.insert(amount, tx);
            tx.commit();
            return inserted;
        }
    }
}
