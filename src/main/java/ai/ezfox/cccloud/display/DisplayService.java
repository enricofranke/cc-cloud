package ai.ezfox.cccloud.display;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Perm;
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
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the Cloud Display: binding (Link Card), its screen, the dashboard's list, and in-world clicks. Rights
 * come from {@link InGameAccess} (AccessResolver permissions): binding and unlinking need {@code network.link}, choosing
 * what the display shows {@code layout.edit}; restricted sections also need the per-display {@code allowRestricted},
 * which only someone who may see them turns on (a display is visible to everyone in front of it). In-world clicks need
 * {@code act.buttons} for the clicking player and only work on buttons/toggles without PIN or confirmation.
 * Server thread only.
 */
public final class DisplayService {
    /** Who changes a display, from the dashboard or the screen, with their live permissions. */
    public record Actor(@Nullable UUID player, String name, Set<Perm> perms) {
    }

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
        if (!InGameAccess.has(player, link.id(), Perm.NETWORK_LINK)) {
            player.sendSystemMessage(Component.translatable("cccloud.display.not_allowed").withStyle(ChatFormatting.RED));
            return;
        }
        var origin = display.origin();
        if (origin == null) return;
        if (!ai.ezfox.cccloud.access.Access.mayRelink(player, origin.mainframeId(), link.id())) {
            player.sendSystemMessage(Component.translatable("cccloud.link_card.linked_elsewhere").withStyle(ChatFormatting.RED));
            return;
        }
        boolean same = link.id().equals(origin.mainframeId());
        boolean restricted = same && origin.allowRestricted() && InGameAccess.has(player, link.id(), Perm.VIEW_POSITIONS);
        origin.configure(link.id(), mainframeName(link.id(), link.describe()), same ? origin.sectionId() : "", same && origin.interactive(), restricted, player.getUUID());
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
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new DisplayMenu(id, pos, view), Component.translatable("block.cccloud.cloud_display")),
            buf -> GuiNetwork.writeOpen(buf, pos, view));
        GuiNetwork.sendLargeView(player, view);
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
        json.addProperty("allowRestricted", origin.allowRestricted());
        var content = parse(origin.readContent());
        json.addProperty("status", content != null && content.has("error") ? content.get("error").getAsString() : "ok");
        json.addProperty("title", content != null && content.has("title") ? content.get("title").getAsString() : "");
        boolean configure = mainframe != null && InGameAccess.has(player, mainframe, Perm.LAYOUT_EDIT);
        json.addProperty("canConfigure", configure);
        if (configure) {
            boolean seesRestricted = InGameAccess.has(player, mainframe, Perm.VIEW_POSITIONS);
            json.addProperty("canRestricted", seesRestricted);
            var sections = new com.google.gson.JsonArray();
            for (var row : InGameClients.sections(mainframe)) {
                var r = row.getAsJsonObject();
                if (!r.get("restricted").getAsBoolean() || seesRestricted && origin.allowRestricted()) sections.add(r.deepCopy());
            }
            var source = WebHub.INSTANCE.displaySource(mainframe);
            sections.addAll(DisplayCompose.targets(source.layout(), source.sections(), source.restricted()));
            DisplayCompose.markDuplicates(sections, id -> seesRestricted ? place(id, origin) : null);
            json.add("sections", sections);
            json.addProperty("canUnlink", InGameAccess.has(player, mainframe, Perm.NETWORK_LINK));
        }
        return Json.GSON.toJson(json);
    }

    /** Where a section's device stands, or null when unknown or in a claim its owners do not belong to. */
    private static @Nullable String place(String id, CloudDisplayBlockEntity origin) {
        var uuid = ClientMessages.parseUuid(id);
        if (uuid == null || !(ai.ezfox.cccloud.network.CloudNetwork.section(uuid) instanceof ai.ezfox.cccloud.claims.ClaimGuard.Guarded device)) return null;
        var level = device.guardLevel();
        var pos = device.guardPos();
        if (level == null || pos == null || ai.ezfox.cccloud.claims.ClaimGuard.blockedAt(level, pos, device.guardOwners())) return null;
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        return level == origin.getLevel() ? at : at + " " + level.dimension().identifier().getPath();
    }

    static @Nullable JsonObject parse(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Screen actions: {@code select <target>}, {@code interactive true|false}, {@code restricted true|false}, {@code unlink}. */
    public static void onAction(ServerPlayer player, InGamePayloads.DisplayAction payload) {
        if (!(player.containerMenu instanceof DisplayMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)) return;
        if (!(player.level().getBlockEntity(menu.pos()) instanceof CloudDisplayBlockEntity display)) return;
        var origin = display.origin();
        if (origin == null || origin.mainframeId() == null) return;
        var mainframe = origin.mainframeId();
        var actor = new Actor(player.getUUID(), player.getGameProfile().name(), InGameAccess.resolve(player, mainframe).perms());
        var server = player.level().getServer();
        boolean on = "true".equals(payload.arg());
        String error = switch (payload.action()) {
            case "select" -> DisplayCompose.validTarget(payload.arg()) ? change(server, mainframe, origin, actor, payload.arg(), null, null) : "not_allowed";
            case "interactive" -> change(server, mainframe, origin, actor, null, on, null);
            case "restricted" -> change(server, mainframe, origin, actor, null, null, on);
            case "unlink" -> unlink(server, mainframe, origin, actor);
            default -> "";
        };
        if ("".equals(error)) return;
        var view = parse(view(origin, player));
        if (view != null) view.addProperty("notice", error == null ? "saved" : "not_allowed");
        PacketDistributor.sendToPlayer(player, new GuiNetwork.ViewPayload(menu.containerId, Json.GSON.toJson(view)));
    }

    /** The dashboard's list. Positions only with {@code view.positions}: {@code id} is opaque. */
    public static JsonObject list(UUID mainframe, Set<Perm> perms) {
        boolean positions = perms.contains(Perm.VIEW_POSITIONS);
        var displays = new com.google.gson.JsonArray();
        for (var row : DisplayRegistry.list(mainframe)) {
            var info = row.info();
            var json = new JsonObject();
            json.addProperty("id", info.id().toString());
            json.addProperty("w", info.width());
            json.addProperty("h", info.height());
            json.addProperty("status", row.live() == null ? "unloaded" : status(row.live()));
            json.addProperty("target", info.target());
            json.addProperty("interactive", info.interactive());
            json.addProperty("allowRestricted", info.allowRestricted());
            if (positions) {
                json.addProperty("dim", info.dim());
                json.addProperty("x", info.pos().getX());
                json.addProperty("y", info.pos().getY());
                json.addProperty("z", info.pos().getZ());
            }
            displays.add(json);
        }
        var result = new JsonObject();
        result.add("displays", displays);
        return result;
    }

    private static String status(CloudDisplayBlockEntity origin) {
        var content = parse(origin.readContent());
        return content != null && content.has("error") ? content.get("error").getAsString() : "ok";
    }

    /** A dashboard change ({@code set} or {@code unlink}). Null or an error code. */
    public static @Nullable String command(MinecraftServer server, UUID mainframe, Actor actor, ClientMessages.DisplayCommand message) {
        var row = message.id() == null ? null : DisplayRegistry.find(mainframe, message.id());
        if (row == null) return "unknown_display";
        var origin = row.live();
        if (origin == null) return "unloaded";
        return message.op().equals("unlink") ? unlink(server, mainframe, origin, actor)
            : change(server, mainframe, origin, actor, message.target(), message.interactive(), message.allowRestricted());
    }

    private static @Nullable String unlink(MinecraftServer server, UUID mainframe, CloudDisplayBlockEntity origin, Actor actor) {
        if (!actor.perms().contains(Perm.NETWORK_LINK)) return "not_allowed";
        origin.configure(null, "", "", false, false, null);
        logChange(server, mainframe, origin, actor, "unlinked");
        return null;
    }

    /**
     * Changes what a display shows and how (null = keep). Whoever picks the target becomes the configuring player, and
     * so does whoever allows restricted cards: {@link CloudDisplayBlockEntity#readContent} checks that player's rights.
     * Someone who may not see restricted cards can't leave a choice behind that shows them: picking a target clears it.
     */
    static @Nullable String change(MinecraftServer server, UUID mainframe, CloudDisplayBlockEntity origin, Actor actor, @Nullable String target,
                                   @Nullable Boolean interactive, @Nullable Boolean allowRestricted) {
        if (actor.player() == null || !actor.perms().contains(Perm.LAYOUT_EDIT)) return "not_allowed";
        boolean sees = WebHub.seesRestricted(actor.perms());
        if (Boolean.TRUE.equals(allowRestricted) && !sees) return "not_allowed";
        boolean allow = allowRestricted != null ? allowRestricted : origin.allowRestricted();
        boolean retarget = target != null && !target.equals(origin.sectionId());
        if (retarget) {
            String error = checkTarget(mainframe, target, allow && sees);
            if (error != null) return error;
            allow = allow && sees;
        }
        boolean allowedNow = allow && !origin.allowRestricted();
        var by = retarget || allowedNow || origin.configuredBy() == null ? actor.player() : origin.configuredBy();
        origin.configure(mainframe, origin.mainframeName(), target != null ? target : origin.sectionId(), interactive != null ? interactive : origin.interactive(), allow, by);
        var what = new java.util.ArrayList<String>();
        if (retarget) what.add(target.isEmpty() ? "cleared what it shows" : "chose " + target);
        if (interactive != null) what.add("in-world clicks " + (interactive ? "on" : "off"));
        if (allowRestricted != null) what.add("restricted cards " + (allowRestricted ? "allowed" : "not allowed"));
        logChange(server, mainframe, origin, actor, String.join(", ", what));
        return null;
    }

    private static @Nullable String checkTarget(UUID mainframe, String target, boolean mayRestricted) {
        if (target.isEmpty()) return null;
        if (DisplayCompose.isComposite(target)) {
            var source = WebHub.INSTANCE.displaySource(mainframe);
            return DisplayCompose.exists(target, source.layout(), source.sections()) ? null : "unknown_target";
        }
        if (WebHub.INSTANCE.sectionText(mainframe, target) == null) return "unknown_target";
        return WebHub.INSTANCE.isRestricted(mainframe, target) && !mayRestricted ? "not_allowed" : null;
    }

    private static void logChange(MinecraftServer server, UUID mainframe, CloudDisplayBlockEntity origin, Actor actor, String what) {
        if (what.isEmpty()) return;
        CCCloud.LOGGER.info("[cccloud] {} changed the Cloud Display at {}: {}", actor.name(), origin.getBlockPos().toShortString(), what);
        ai.ezfox.cccloud.web.WebAuthData.access(server).audit(mainframe, actor.name() + " changed a Cloud Display: " + what);
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
        String denied = AccessGate.check(access.role(), access.perms(), Perm.ACT_BUTTONS);
        if (denied != null) {
            player.sendOverlayMessage(Component.translatable("cccloud.display.click_denied").withStyle(ChatFormatting.RED));
            return;
        }
        JsonObject widget = null;
        for (var w : section.getAsJsonArray("widgets")) if (DisplayLayout.str(w.getAsJsonObject(), "id").equals(box.id())) widget = w.getAsJsonObject();
        if (widget == null) return;
        // a composed card says where each widget comes from
        String src = DisplayLayout.str(widget, "src");
        int slash = src.indexOf('/');
        String target = slash > 0 ? src.substring(0, slash) : origin.sectionId();
        String widgetId = slash > 0 ? src.substring(slash + 1) : box.id();
        // a restricted card can be read here, but only someone who may see it operates it
        if (WebHub.INSTANCE.isRestricted(mainframe, target) && !WebHub.seesRestricted(access.perms())) {
            player.sendOverlayMessage(Component.translatable("cccloud.display.click_denied").withStyle(ChatFormatting.RED));
            return;
        }
        var value = box.type().equals("toggle") ? new JsonPrimitive(!(widget.has("value") && widget.get("value").getAsBoolean())) : new JsonPrimitive(true);
        UUID sectionId;
        try {
            sectionId = UUID.fromString(target);
        } catch (IllegalArgumentException e) {
            return;
        }
        var server = player.level().getServer();
        var action = new ClientMessages.Action("display", sectionId, widgetId, value, null);
        InGameClients.action(server, InGameClients.device(player, mainframe, "display"), action, false).whenComplete((result, ex) -> server.execute(() -> {
            if (ex != null || result == null || !result.ok()) {
                player.sendOverlayMessage(Component.translatable("cccloud.display.click_failed", ex != null || result == null ? "internal_error" : result.error())
                    .withStyle(ChatFormatting.RED));
            }
        }));
    }
}
