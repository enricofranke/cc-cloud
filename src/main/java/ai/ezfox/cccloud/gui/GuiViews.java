package ai.ezfox.cccloud.gui;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Builds what the in-game screens show (server thread). Players without rights get a read-only view without the
 * pairing code, approvals, devices, trust list or PIN state.
 */
public final class GuiViews {
    private GuiViews() {
    }

    public static String mainframe(CloudMainframeBlockEntity mainframe, ServerPlayer player, @Nullable String notice) {
        var server = player.level().getServer();
        boolean manage = mainframe.canManage(player);
        var json = new JsonObject();
        json.addProperty("name", mainframe.getDisplayName());
        var pos = mainframe.getBlockPos();
        json.addProperty("pos", pos.getX() + " " + pos.getY() + " " + pos.getZ());
        json.addProperty("status", mainframe.isPowered() ? "killed" : mainframe.status().getSerializedName());
        json.addProperty("enabled", mainframe.isEnabled());
        json.addProperty("cloudAllowed", CloudConfig.get(CloudConfig.ALLOW_CLOUD));
        json.addProperty("canManage", manage);
        json.addProperty("clients", WebHub.INSTANCE.clientCount(mainframe.getMainframeId()));
        json.addProperty("webRunning", CloudWebServer.isRunning());
        json.addProperty("internet", CloudWebServer.allowsInternet());
        json.addProperty("tls", CloudWebServer.usesTls());
        var urls = new JsonArray();
        for (var url : CloudWebServer.dashboardUrls()) urls.add(url);
        json.add("urls", urls);
        json.addProperty("uplinks", mainframe.getLinkedUplinks().size());
        if (notice != null) json.addProperty("notice", notice);
        if (!manage || server == null) return Json.GSON.toJson(json);

        var auth = WebAuthData.get(server);
        var id = mainframe.getMainframeId();
        long now = System.currentTimeMillis();
        var code = auth.currentCode(id, player.getUUID());
        json.addProperty("code", AuthStore.formatCode(code.code()));
        json.addProperty("codeSeconds", Math.max(0, (code.expiresAt() - now) / 1000));
        var pending = new JsonArray();
        for (var p : auth.pendingFor(id)) {
            if (p.state() != AuthStore.PendingState.PENDING) continue;
            var row = new JsonObject();
            row.addProperty("id", p.id());
            row.addProperty("name", p.deviceName());
            row.addProperty("ip", p.ip());
            row.addProperty("seconds", Math.max(0, (p.expiresAt() - now) / 1000));
            pending.add(row);
        }
        json.add("pending", pending);
        var devices = new JsonArray();
        for (var d : auth.devices(id)) {
            var row = new JsonObject();
            row.addProperty("id", AuthStore.deviceId(d));
            row.addProperty("name", d.name());
            row.addProperty("player", d.playerName());
            row.addProperty("lastUsedAgo", Math.max(0, (now - d.lastUsed()) / 1000));
            row.addProperty("control", d.canControl());
            devices.add(row);
        }
        json.add("devices", devices);
        json.addProperty("owner", mainframe.getOwnerName());
        var trusted = new JsonArray();
        for (var t : mainframe.getTrusted().entrySet()) {
            var row = new JsonObject();
            row.addProperty("uuid", t.getKey().toString());
            row.addProperty("name", t.getValue());
            trusted.add(row);
        }
        json.add("trusted", trusted);
        json.addProperty("pinSet", auth.hasPin(id));
        return Json.GSON.toJson(json);
    }

    public static String uplink(CloudUplinkBlockEntity uplink, ServerPlayer player, @Nullable String notice) {
        var json = new JsonObject();
        json.addProperty("advanced", uplink.isAdvanced());
        json.addProperty("status", uplink.status().getSerializedName());
        json.addProperty("title", uplink.getTitle());
        json.addProperty("mode", uplink.mode());
        json.addProperty("widgets", uplink.widgetCount());
        json.addProperty("computers", uplink.computerCount());
        var link = uplink.getLink();
        json.addProperty("linked", link != null);
        boolean canUnlink = false;
        if (link != null) {
            var p = link.pos().pos();
            json.addProperty("mainframePos", p.getX() + " " + p.getY() + " " + p.getZ());
            json.addProperty("mainframeDim", link.pos().dimension().identifier().toString());
            var mainframe = CloudNetwork.mainframe(link.id());
            json.addProperty("mainframe", mainframe != null ? mainframe.getDisplayName() : "");
            json.addProperty("mainframeLoaded", mainframe != null);
            boolean sameDim = uplink.getLevel() != null && link.pos().dimension().equals(uplink.getLevel().dimension());
            json.addProperty("distance", sameDim ? (int) Math.sqrt(uplink.getBlockPos().distSqr(p)) : -1);
            json.addProperty("range", uplink.isAdvanced() ? -1 : CloudConfig.get(CloudConfig.UPLINK_RANGE));
            json.addProperty("inRange", uplink.getLevel() instanceof ServerLevel level && mainframe != null && uplink.isInRange(level, mainframe)
                || uplink.isAdvanced() && mainframe != null);
            canUnlink = GuiNetwork.canManageMainframe(player, link.id());
        }
        json.addProperty("canUnlink", canUnlink);
        if (notice != null) json.addProperty("notice", notice);
        return Json.GSON.toJson(json);
    }
}
