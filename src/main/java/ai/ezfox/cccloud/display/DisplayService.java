package ai.ezfox.cccloud.display;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.gui.GuiNetwork;
import ai.ezfox.cccloud.ingame.InGameAccess;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.web.AccessGate;
import ai.ezfox.cccloud.web.ClientMessages;
import ai.ezfox.cccloud.web.InGameClients;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the Cloud Display: binding (Link Card), its screen, and in-world clicks. Rights come from
 * {@link InGameAccess} (AccessResolver permissions): binding needs {@code network.link}, choosing what the display shows
 * {@code layout.edit} (a display is visible to everyone in front of it, so only non-restricted sections can be chosen);
 * in-world clicks need {@code act.buttons} for the clicking player and only work on buttons/toggles without PIN or
 * confirmation. Server thread only.
 */
public final class DisplayService {
    static final double CLICK_RANGE = 8.0;

    private DisplayService() {
    }

    /** Right-click with a Cloud Link Card. */
    static void bind(ServerPlayer player, CloudDisplayBlockEntity display, ItemStack card) {
        var link = card.get(ModRegistry.LINKED_MAINFRAME.get());
        if (link == null) {
            player.sendSystemMessage(Component.translatable("cccloud.link_card.empty").withStyle(ChatFormatting.RED));
            return;
        }
        if (!InGameAccess.has(player, link.id(), ai.ezfox.cccloud.access.Perm.NETWORK_LINK)) {
            player.sendSystemMessage(Component.translatable("cccloud.display.not_allowed").withStyle(ChatFormatting.RED));
            return;
        }
        var origin = display.origin();
        if (origin == null) return;
        boolean same = link.id().equals(origin.mainframeId());
        origin.configure(link.id(), mainframeName(link.id(), link.describe()), same ? origin.sectionId() : "", same && origin.interactive(), player.getUUID());
        CCCloud.LOGGER.info("[cccloud] {} bound the Cloud Display at {} to {}", player.getGameProfile().name(), origin.getBlockPos().toShortString(), link.describe());
        player.sendSystemMessage(Component.translatable("cccloud.display.bound", link.describe()).withStyle(ChatFormatting.GREEN));
    }

    private static String mainframeName(UUID id, String fallback) {
        var info = WebHub.INSTANCE.info(id);
        return info != null && !info.name().isEmpty() ? info.name() : fallback;
    }

    /** Sneak + right-click (or a click on a side): the display's screen. */
    static void openScreen(ServerPlayer player, CloudDisplayBlockEntity display) {
        var origin = display.origin();
        if (origin == null) return;
        String view = view(origin, player);
        var pos = origin.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new DisplayMenu(id, pos, view), Component.translatable("block.cccloud.cloud_display")), buf -> {
            buf.writeBlockPos(pos);
            buf.writeUtf(view, DisplayMenu.MAX_VIEW);
        });
    }

    /** The screen's view: status, rights and (for admins) the sections that may be shown. */
    static String view(CloudDisplayBlockEntity origin, ServerPlayer player) {
        var json = new JsonObject();
        json.addProperty("size", origin.width() + " x " + origin.height());
        var mainframe = origin.mainframeId();
        json.addProperty("linked", mainframe != null);
        json.addProperty("mainframe", origin.mainframeName());
        json.addProperty("section", origin.sectionId());
        json.addProperty("interactive", origin.interactive());
        var content = parse(origin.readContent());
        json.addProperty("status", content != null && content.has("error") ? content.get("error").getAsString() : "ok");
        json.addProperty("title", content != null && content.has("title") ? content.get("title").getAsString() : "");
        boolean configure = mainframe != null && InGameAccess.has(player, mainframe, ai.ezfox.cccloud.access.Perm.LAYOUT_EDIT);
        json.addProperty("canConfigure", configure);
        if (configure) {
            var sections = new com.google.gson.JsonArray();
            for (var row : InGameClients.sections(mainframe)) {
                var r = row.getAsJsonObject();
                if (!r.get("restricted").getAsBoolean()) sections.add(r);
            }
            json.add("sections", sections);
        }
        return Json.GSON.toJson(json);
    }

    static @Nullable JsonObject parse(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Screen actions: {@code select <sectionId>}, {@code interactive true|false}, {@code unlink}. */
    public static void onAction(ServerPlayer player, InGamePayloads.DisplayAction payload) {
        if (!(player.containerMenu instanceof DisplayMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)) return;
        if (!(player.level().getBlockEntity(menu.pos()) instanceof CloudDisplayBlockEntity display)) return;
        var origin = display.origin();
        if (origin == null || origin.mainframeId() == null) return;
        var mainframe = origin.mainframeId();
        String notice = null;
        boolean unlink = "unlink".equals(payload.action());
        if (!InGameAccess.has(player, mainframe, unlink ? ai.ezfox.cccloud.access.Perm.NETWORK_LINK : ai.ezfox.cccloud.access.Perm.LAYOUT_EDIT)) {
            notice = "not_allowed";
        } else {
            switch (payload.action()) {
                case "select" -> {
                    boolean allowed = false;
                    for (var row : InGameClients.sections(mainframe)) {
                        var r = row.getAsJsonObject();
                        if (r.get("uplinkId").getAsString().equals(payload.arg()) && !r.get("restricted").getAsBoolean()) allowed = true;
                    }
                    if (!allowed) {
                        notice = "not_allowed";
                    } else {
                        origin.configure(mainframe, origin.mainframeName(), payload.arg(), origin.interactive(), player.getUUID());
                        notice = "saved";
                    }
                }
                case "interactive" -> {
                    origin.configure(mainframe, origin.mainframeName(), origin.sectionId(), "true".equals(payload.arg()), player.getUUID());
                    notice = "saved";
                }
                case "unlink" -> {
                    origin.configure(null, "", "", false, null);
                    notice = "saved";
                }
                default -> {
                    return;
                }
            }
            CCCloud.LOGGER.info("[cccloud] {} changed the Cloud Display at {}: {} {}", player.getGameProfile().name(), origin.getBlockPos().toShortString(),
                payload.action(), payload.arg());
        }
        var view = parse(view(origin, player));
        if (view != null && notice != null) view.addProperty("notice", notice);
        PacketDistributor.sendToPlayer(player, new GuiNetwork.ViewPayload(menu.containerId, Json.GSON.toJson(view)));
    }

    /** A right-click on the screen: presses the button / flips the toggle under the cursor (operators, if enabled). */
    static void click(ServerPlayer player, CloudDisplayBlockEntity display, BlockHitResult hit) {
        var origin = display.origin();
        if (origin == null || origin.mainframeId() == null) return;
        if (!origin.interactive()) {
            openScreen(player, display);
            return;
        }
        if (player.distanceToSqr(hit.getLocation()) > CLICK_RANGE * CLICK_RANGE) return;
        var section = parse(origin.content());
        if (section == null || section.has("error")) return;
        var pos = hit.getBlockPos();
        double[] px = DisplayLayout.toPixels((float) (hit.getLocation().x - pos.getX()), (float) (hit.getLocation().y - pos.getY()),
            (float) (hit.getLocation().z - pos.getZ()), display.direction(), display.orientation(), display.xIndex(), display.yIndex(), display.height());
        var box = DisplayLayout.layout(section, origin.width(), origin.height()).hit(px[0], px[1]);
        if (box == null || !box.clickable()) return;
        press(player, origin, section, box);
    }

    /** Runs the click on one widget box (after the hit test; also used by the GameTests). */
    static void press(ServerPlayer player, CloudDisplayBlockEntity origin, JsonObject section, DisplayLayout.Box box) {
        var mainframe = origin.mainframeId();
        if (mainframe == null || !box.clickable() || !origin.interactive()) return;
        var access = InGameAccess.resolve(player, mainframe);
        String denied = AccessGate.check(access.role(), access.perms(), ai.ezfox.cccloud.access.Perm.ACT_BUTTONS);
        if (denied != null) {
            player.sendOverlayMessage(Component.translatable("cccloud.display.click_denied").withStyle(ChatFormatting.RED));
            return;
        }
        JsonObject widget = null;
        for (var w : section.getAsJsonArray("widgets")) if (DisplayLayout.str(w.getAsJsonObject(), "id").equals(box.id())) widget = w.getAsJsonObject();
        if (widget == null) return;
        var value = box.type().equals("toggle") ? new JsonPrimitive(!(widget.has("value") && widget.get("value").getAsBoolean())) : new JsonPrimitive(true);
        UUID sectionId;
        try {
            sectionId = UUID.fromString(origin.sectionId());
        } catch (IllegalArgumentException e) {
            return;
        }
        var server = player.level().getServer();
        var action = new ClientMessages.Action("display", sectionId, box.id(), value, null);
        InGameClients.action(server, InGameClients.device(player, mainframe, "display"), action, false).whenComplete((result, ex) -> server.execute(() -> {
            if (ex != null || result == null || !result.ok()) {
                player.sendOverlayMessage(Component.translatable("cccloud.display.click_failed", ex != null || result == null ? "internal_error" : result.error())
                    .withStyle(ChatFormatting.RED));
            }
        }));
    }
}
