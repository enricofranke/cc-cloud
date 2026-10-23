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

    /** Effective upgrade limits for the Upgrades tab. */
    static JsonObject upgradesJson(CloudMainframeBlockEntity mainframe) {
        var e = mainframe.effects();
        var u = new JsonObject();
        u.addProperty("devices", e.devices() == ai.ezfox.cccloud.upgrade.UpgradeEffects.UNLIMITED ? -1 : e.devices());
        u.addProperty("used", Math.round(ai.ezfox.cccloud.network.NetworkService.capacityUsed(mainframe.getMainframeId()) * 10) / 10.0);
        u.addProperty("browsers", e.browsers() == ai.ezfox.cccloud.upgrade.UpgradeEffects.UNLIMITED ? -1 : e.browsers());
        u.addProperty("rules", ai.ezfox.cccloud.rules.RuleService.limit(mainframe.getMainframeId()));
        u.addProperty("historyHours", e.historyHours());
        u.addProperty("range", (int) Math.round(CloudConfig.get(CloudConfig.RANGE_MAINFRAME) * e.rangeFactor()));
        u.addProperty("feFactor", e.feFactor());
        u.addProperty("quantum", e.quantum());
        u.addProperty("anchor", e.chunkAnchor());
        u.addProperty("anchorAllowed", CloudConfig.get(CloudConfig.CHUNK_ANCHOR));
        u.addProperty("anchorChunks", ai.ezfox.cccloud.upgrade.ChunkAnchor.chunks(mainframe.getMainframeId()));
        return u;
    }

    public static String mainframe(CloudMainframeBlockEntity mainframe, ServerPlayer player, @Nullable String notice) {
        var server = player.level().getServer();
        var resolution = ai.ezfox.cccloud.access.Access.resolve(player, mainframe.getMainframeId());
        // Management tabs for players who manage devices, access or the PIN (fine-grained permissions).
        boolean manage = resolution.has(ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE) || resolution.has(ai.ezfox.cccloud.access.Perm.ACCESS_MANAGE)
            || resolution.has(ai.ezfox.cccloud.access.Perm.PIN_MANAGE);
        var json = new JsonObject();
        json.addProperty("role", resolution.role().id);
        var permsJson = new JsonArray();
        for (var p : resolution.perms()) permsJson.add(p.id);
        json.add("perms", permsJson);
        json.addProperty("name", mainframe.getDisplayName());
        var pos = mainframe.getBlockPos();
        json.addProperty("pos", pos.getX() + " " + pos.getY() + " " + pos.getZ());
        json.addProperty("status", mainframe.isPowered() ? "killed" : !mainframe.hasPower() ? "no_power" : mainframe.status().getSerializedName());
        json.addProperty("energy", mainframe.energy().energy());
        json.addProperty("energyCapacity", mainframe.energy().capacity());
        json.addProperty("energyUsage", mainframe.energyUsage());
        json.addProperty("requirePower", CloudConfig.get(CloudConfig.REQUIRE_POWER));
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
        var network = ai.ezfox.cccloud.network.NetworkService.result(mainframe.getMainframeId());
        if (network != null) json.add("network", ai.ezfox.cccloud.network.NetworkService.toJson(mainframe, network, true));
        var power = new JsonObject();
        for (var e : mainframe.powerBreakdown().entrySet()) power.addProperty(e.getKey(), e.getValue());
        json.add("powerBreakdown", power);
        json.add("upgrades", upgradesJson(mainframe));
        var icons = ai.ezfox.cccloud.icons.IconService.current();
        if (icons != null) {
            var status = icons.status();
            json.addProperty("iconsDone", status.done());
            json.addProperty("iconsTotal", status.total());
        }
        if (notice != null) json.addProperty("notice", notice);
        if (server != null) {
            // "Link this browser": the player's account code, if one is active.
            var account = WebAuthData.get(server).activeAccountCode(player.getUUID());
            if (account != null) {
                json.addProperty("accountCode", AuthStore.formatCode(account.code()));
                json.addProperty("accountCodeSeconds", Math.max(0, (account.expiresAt() - System.currentTimeMillis()) / 1000));
            }
        }
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
        // Players with a direct role (v0.6); the web Access page also shows team members and server admins.
        var trusted = new JsonArray();
        for (var t : mainframe.getGrants().entrySet()) {
            var row = new JsonObject();
            row.addProperty("uuid", t.getKey().toString());
            row.addProperty("name", t.getValue().name().isEmpty() ? t.getKey().toString().substring(0, 8) : t.getValue().name());
            row.addProperty("role", t.getValue().role().id);
            trusted.add(row);
        }
        json.add("trusted", trusted);
        var access = WebAuthData.access(server).access(id);
        if (access != null && access.team() != null) {
            var team = ai.ezfox.cccloud.access.TeamProviders.get(access.team().provider());
            var info = team == null ? null : team.team(access.team().teamId());
            json.addProperty("team", (info != null ? info.name() : access.team().teamId()) + " (" + access.team().provider() + ")");
        }
        var audit = new JsonArray();
        var entries = WebAuthData.access(server).audit(id);
        for (int i = entries.size() - 1; i >= 0 && audit.size() < 20; i--) {
            var row = new JsonObject();
            row.addProperty("ago", Math.max(0, (now - entries.get(i).time()) / 1000));
            row.addProperty("text", entries.get(i).text());
            audit.add(row);
        }
        json.add("audit", audit);
        json.addProperty("pinSet", auth.hasPin(id));
        return Json.GSON.toJson(json);
    }

    /** A Cloud Bridge in the uplink-style screen: network status, link (unlimited range), its Storage section. */
    public static String bridge(ai.ezfox.cccloud.bridge.BridgeHost bridge, ServerPlayer player, @Nullable String notice) {
        var section = bridge.bridgeSection();
        var core = bridge.bridgeCore();
        var json = new JsonObject();
        json.addProperty("kind", "bridge");
        json.addProperty("advanced", true);
        json.addProperty("status", section.isLinked() ? section.status().getSerializedName() : core.status().getSerializedName());
        json.addProperty("network", core.status().getSerializedName());
        json.addProperty("title", section.getTitle());
        json.addProperty("mode", "direct");
        json.addProperty("widgets", section.widgetCount());
        var link = section.getLink();
        json.addProperty("linked", link != null);
        boolean canUnlink = false;
        if (link != null) {
            var p = link.pos().pos();
            json.addProperty("mainframePos", p.getX() + " " + p.getY() + " " + p.getZ());
            var mainframe = CloudNetwork.mainframe(link.id());
            json.addProperty("mainframe", mainframe != null ? mainframe.getDisplayName() : "");
            boolean sameDim = bridge.asBlockEntity().getLevel() != null && link.pos().dimension().equals(bridge.asBlockEntity().getLevel().dimension());
            json.addProperty("distance", sameDim ? (int) Math.sqrt(bridge.asBlockEntity().getBlockPos().distSqr(p)) : -1);
            json.addProperty("range", ai.ezfox.cccloud.network.NetworkService.deviceRange(ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType.BRIDGE));
            json.addProperty("inRange", section.hasRoute());
            canUnlink = GuiNetwork.canManageMainframe(player, link.id());
        }
        json.addProperty("canUnlink", canUnlink);
        if (notice != null) json.addProperty("notice", notice);
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
            json.addProperty("range", ai.ezfox.cccloud.network.NetworkService.deviceRange(ai.ezfox.cccloud.network.NetworkService.deviceType(uplink)));
            json.addProperty("inRange", uplink.hasRoute());
            if (uplink.routeState() != null) json.addProperty("routeState", uplink.routeState());
            var route = ai.ezfox.cccloud.network.NetworkService.route(uplink.getUplinkId());
            if (route != null && route.connected()) json.addProperty("hops", route.hops());
            canUnlink = GuiNetwork.canManageMainframe(player, link.id());
        }
        json.addProperty("canUnlink", canUnlink);
        // v0.7 wireless redstone
        var redstone = new JsonObject();
        redstone.addProperty("mode", uplink.redstoneMode().id());
        redstone.addProperty("channel", uplink.redstoneChannel());
        redstone.addProperty("level", uplink.redstoneMode() == ai.ezfox.cccloud.redstone.RedstoneMode.TRANSMIT ? uplink.redstoneInput() : uplink.redstoneOutput());
        if (link != null) {
            var channel = ai.ezfox.cccloud.redstone.RedstoneService.channels(link.id()).get(uplink.redstoneChannel());
            if (channel != null) redstone.addProperty("channelLevel", channel.level());
        }
        redstone.addProperty("canEdit", canUnlink);
        json.add("redstone", redstone);
        if (notice != null) json.addProperty("notice", notice);
        return Json.GSON.toJson(json);
    }
}
