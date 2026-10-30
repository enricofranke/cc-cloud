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
import net.minecraft.core.registries.BuiltInRegistries;
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
        return mainframe(mainframe, player, notice, -1, null);
    }

    /**
     * The mainframe screen's view. The network (compact, see {@link ai.ezfox.cccloud.network.NetworkService#screenNetwork})
     * is only included when its version differs from {@code knownNetV} (what the screen already has); {@code netV}
     * receives the version of the network the screen has after this view (-1: none). Docs/SPEC.md "Scaling".
     */
    public static String mainframe(CloudMainframeBlockEntity mainframe, ServerPlayer player, @Nullable String notice, long knownNetV, long @Nullable [] netV) {
        if (netV != null) netV[0] = -1;
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
        // CC: Cloud Connect: its HTTPS address first while it is online, and its state for the screen
        var connect = ai.ezfox.cccloud.connect.ConnectService.status();
        if (connect.online()) urls.add(connect.url());
        for (var url : CloudWebServer.dashboardUrls()) urls.add(url);
        json.add("urls", urls);
        if (connect.state() != ai.ezfox.cccloud.connect.ConnectStatus.State.OFF) {
            var c = new JsonObject();
            c.addProperty("state", connect.state().id());
            c.addProperty("url", connect.url());
            c.addProperty("browsers", connect.browsers());
            if (connect.error() != null) c.addProperty("error", connect.error());
            json.add("connect", c);
        }
        json.addProperty("uplinks", mainframe.getLinkedUplinks().size());
        // Plain counts for the overview: devices that reach the mainframe, relays that carry the network.
        var counted = ai.ezfox.cccloud.network.NetworkService.result(mainframe.getMainframeId());
        if (counted != null) {
            int connected = 0, relays = 0;
            for (var route : counted.routes().values()) if (route != null && route.connected()) connected++;
            for (var node : counted.nodes()) {
                if (node.type() != ai.ezfox.cccloud.network.graph.NetworkGraph.NodeType.MAINFRAME && counted.reachable(node.id())) relays++;
            }
            json.addProperty("devicesConnected", connected);
            json.addProperty("relays", relays);
        }
        // Node coordinates only with view.positions (security review M5), like the dashboard's network map. Compact and
        // only when it changed: a 500-device network is ~60 KB here (the full map form was ~540 KB).
        var screen = resolution.has(ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS) ? ai.ezfox.cccloud.network.NetworkService.screenNetwork(mainframe) : null;
        if (screen != null) {
            json.addProperty("netV", screen.version());
            if (screen.version() != knownNetV) json.add("network", screen.json());
            if (netV != null) netV[0] = screen.version();
        }
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
        if (!manage || server == null) return fit(json, netV);

        var auth = WebAuthData.get(server);
        var id = mainframe.getMainframeId();
        long now = System.currentTimeMillis();
        // Each management section only with its own permission (security review L11).
        boolean devicesOk = resolution.has(ai.ezfox.cccloud.access.Perm.DEVICES_MANAGE);
        boolean accessOk = resolution.has(ai.ezfox.cccloud.access.Perm.ACCESS_MANAGE);
        if (resolution.has(ai.ezfox.cccloud.access.Perm.PIN_MANAGE)) json.addProperty("pinSet", auth.hasPin(id));
        if (devicesOk) devicesJson(json, auth, id, player, now);
        if (accessOk) accessJson(json, mainframe, server, id, now);
        return fit(json, netV);
    }

    /** Last resort: a view over the update limit goes without the network (the screen shows none instead of nothing). */
    private static String fit(JsonObject json, long @Nullable [] netV) {
        String text = Json.GSON.toJson(json);
        if (text.length() <= ai.ezfox.cccloud.gui.CloudMenu.MAX_VIEW || !json.has("network")) return text;
        json.remove("network");
        json.remove("netV");
        if (netV != null) netV[0] = -1;
        return Json.GSON.toJson(json);
    }

    private static void devicesJson(JsonObject json, AuthStore auth, java.util.UUID id, ServerPlayer player, long now) {
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
    }

    private static void accessJson(JsonObject json, CloudMainframeBlockEntity mainframe, net.minecraft.server.MinecraftServer server, java.util.UUID id, long now) {
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
    }

    /** A Cloud Bridge in the uplink-style screen: what it reads, its Storage card, the connection and the actions. */
    public static String bridge(ai.ezfox.cccloud.bridge.BridgeHost bridge, ServerPlayer player, @Nullable String notice) {
        var section = bridge.bridgeSection();
        var core = bridge.bridgeCore();
        var be = bridge.asBlockEntity();
        var json = new JsonObject();
        json.addProperty("kind", "bridge");
        json.addProperty("advanced", true);
        json.addProperty("status", section.isLinked() ? section.status().getSerializedName() : core.status().getSerializedName());
        json.addProperty("network", core.status().getSerializedName());
        json.addProperty("storage", core.kind());
        json.addProperty("block", BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString());
        json.addProperty("pos", pos(be.getBlockPos()));
        json.addProperty("mode", "direct");
        json.addProperty("cardKind", "storage");
        json.addProperty("widgets", section.widgetCount());
        json.addProperty("sectionId", section.sectionId().toString());
        var link = section.getLink();
        card(json, section.snapshot(), section.sectionId(), link);
        link(json, player, link, be.getLevel(), be.getBlockPos(), section.sectionId(),
            ai.ezfox.cccloud.network.NetworkService.deviceRange(ai.ezfox.cccloud.network.graph.NetworkGraph.DeviceType.BRIDGE), null);
        if (notice != null) json.addProperty("notice", notice);
        return Json.GSON.toJson(json);
    }

    public static String uplink(CloudUplinkBlockEntity uplink, ServerPlayer player, @Nullable String notice) {
        var json = new JsonObject();
        json.addProperty("kind", "uplink");
        json.addProperty("advanced", uplink.isAdvanced());
        json.addProperty("probe", uplink.isProbe());
        json.addProperty("status", uplink.status().getSerializedName());
        json.addProperty("mode", uplink.mode());
        json.addProperty("cardKind", uplink.isTemplate() ? "storage" : !uplink.deviceKind().isEmpty() ? "device"
            : uplink.isAuto() ? "auto" : uplink.widgetCount() > 0 ? "lua" : "none");
        if (!uplink.deviceKind().isEmpty()) json.addProperty("template", uplink.deviceKind());
        json.addProperty("widgets", uplink.widgetCount());
        json.addProperty("computers", uplink.computerCount());
        json.addProperty("sectionId", uplink.getUplinkId().toString());
        json.addProperty("pos", pos(uplink.getBlockPos()));
        attached(json, uplink);
        var link = uplink.getLink();
        card(json, uplink.snapshot(), uplink.getUplinkId(), link);
        link(json, player, link, uplink.getLevel(), uplink.getBlockPos(), uplink.getUplinkId(),
            ai.ezfox.cccloud.network.NetworkService.deviceRange(ai.ezfox.cccloud.network.NetworkService.deviceType(uplink)), uplink.routeState());
        boolean canEdit = bool(json, "canUnlink");
        if (uplink.isProbe()) probe(json, uplink, !bool(json, "linked") || canEdit);
        var elsewhere = uplink.exposedElsewhere();
        if (elsewhere != null) json.addProperty("exposedElsewhere", elsewhere.getSerializedName());
        // v0.7 wireless redstone
        var redstone = new JsonObject();
        redstone.addProperty("mode", uplink.redstoneMode().id());
        redstone.addProperty("channel", uplink.redstoneChannel());
        redstone.addProperty("level", uplink.redstoneMode() == ai.ezfox.cccloud.redstone.RedstoneMode.TRANSMIT ? uplink.redstoneInput() : uplink.redstoneOutput());
        if (link != null) {
            var channel = ai.ezfox.cccloud.redstone.RedstoneService.channels(link.id()).get(uplink.redstoneChannel());
            if (channel != null) redstone.addProperty("channelLevel", channel.level());
        }
        redstone.addProperty("canEdit", canEdit);
        json.add("redstone", redstone);
        if (notice != null) json.addProperty("notice", notice);
        return Json.GSON.toJson(json);
    }

    private static String pos(net.minecraft.core.BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static boolean bool(JsonObject json, String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }

    /**
     * Cloud Probe: {@code remote: {block, distance, pos, side, range}} while it has a target ({@code pos} only with the
     * rights to change it), the range, and whether the player may clear it.
     */
    private static void probe(JsonObject json, CloudUplinkBlockEntity probe, boolean canChange) {
        var remote = new JsonObject();
        remote.addProperty("range", CloudConfig.get(CloudConfig.PROBE_RANGE));
        var target = probe.remoteTarget();
        if (target != null && probe.getLevel() != null) {
            var level = probe.getLevel();
            remote.addProperty("loaded", level.isLoaded(target));
            if (level.isLoaded(target)) remote.addProperty("block", BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock()).toString());
            remote.addProperty("distance", (int) Math.round(Math.sqrt(probe.getBlockPos().distSqr(target))));
            remote.addProperty("side", probe.readSide().getSerializedName());
            if (canChange) remote.addProperty("pos", pos(target));
        }
        json.add("remote", remote);
        json.addProperty("canClearTarget", canChange && target != null);
    }

    /** What the uplink reads: the block it sticks to (a probe: its target) – the block id, and for a CC computer its id, label and power. */
    private static void attached(JsonObject json, CloudUplinkBlockEntity uplink) {
        if (!(uplink.getLevel() instanceof ServerLevel level)) return;
        var at = uplink.readPos();
        if (at == null || !level.isLoaded(at)) return;
        var state = level.getBlockState(at);
        if (state.isAir()) return;
        var attached = new JsonObject();
        attached.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        var computer = ai.ezfox.cccloud.compat.cc.CcComputers.find(level, at);
        if (computer != null) {
            attached.addProperty("computer", computer.id());
            String label = computer.label();
            if (label != null && !label.isBlank()) attached.addProperty("label", label);
            attached.addProperty("on", computer.isOn());
        } else if (level.getBlockEntity(at) instanceof ai.ezfox.cccloud.bridge.BridgeHost host) {
            attached.addProperty("bridge", host.bridgeCore().kind());
        }
        json.add("attached", attached);
    }

    /**
     * The card as it appears on the dashboard: the title (the layout's custom title first), the default title, and a
     * compact live preview of the widgets ({@link CardPreview}).
     */
    private static void card(JsonObject json, JsonObject snapshot, java.util.UUID sectionId, ai.ezfox.cccloud.item.@Nullable MainframeLink link) {
        var layout = link == null ? null : WebHub.INSTANCE.layout(link.id());
        String defaultTitle = snapshot.has("title") ? snapshot.get("title").getAsString() : "";
        String custom = customTitle(layout, sectionId.toString());
        json.addProperty("title", custom != null ? custom : defaultTitle);
        json.addProperty("defaultTitle", defaultTitle);
        json.addProperty("customTitle", custom != null);
        json.add("preview", CardPreview.of(snapshot, layout, sectionId.toString()));
    }

    /** The card's title in the dashboard layout, or null if it has none. */
    static @Nullable String customTitle(@Nullable JsonObject layout, String cardKey) {
        if (layout == null || !(layout.get("cards") instanceof JsonObject cards) || !(cards.get(cardKey) instanceof JsonObject card)) return null;
        var title = card.get("title");
        return title != null && title.isJsonPrimitive() && !title.getAsString().isEmpty() ? title.getAsString() : null;
    }

    /**
     * The link to the mainframe in plain terms: its name (positions only with {@code view.positions}), whether it is up
     * (power, redstone, dashboard switch), the route (signal, relays, cable) and what the player may do here.
     */
    private static void link(JsonObject json, ServerPlayer player, ai.ezfox.cccloud.item.@Nullable MainframeLink link, net.minecraft.world.level.@Nullable Level level,
                             net.minecraft.core.BlockPos here, java.util.UUID deviceId, int deviceRange, @Nullable String routeState) {
        json.addProperty("linked", link != null);
        json.addProperty("range", deviceRange);
        if (link == null) {
            json.addProperty("canUnlink", false);
            return;
        }
        var resolution = ai.ezfox.cccloud.access.Access.resolve(player, link.id());
        var p = link.pos().pos();
        // where the mainframe is: only for players with view.positions on it (security review L12)
        boolean positions = resolution.has(ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS);
        json.addProperty("mainframePos", positions ? pos(p) : "");
        json.addProperty("mainframeDim", link.pos().dimension().identifier().toString());
        var mainframe = CloudNetwork.mainframe(link.id());
        // The default name already ends in "@ x y z": never append the position again.
        json.addProperty("mainframe", mainframe == null ? "" : positions ? mainframe.getDisplayName() : "Mainframe");
        json.addProperty("mainframeLoaded", mainframe != null);
        json.addProperty("mainframeState", mainframe == null ? "unloaded" : mainframe.isPowered() ? "killed" : !mainframe.hasPower() ? "no_power"
            : !CloudConfig.get(CloudConfig.ALLOW_CLOUD) ? "cloud_disabled" : !mainframe.isEnabled() ? "dashboard_off" : "ok");
        if (mainframe != null) {
            json.addProperty("mainframeRange", (int) Math.round(CloudConfig.get(CloudConfig.RANGE_MAINFRAME) * mainframe.effects().rangeFactor()));
        }
        boolean sameDim = level != null && link.pos().dimension().equals(level.dimension());
        json.addProperty("distance", sameDim && positions ? (int) Math.sqrt(here.distSqr(p)) : -1);
        var route = ai.ezfox.cccloud.network.NetworkService.route(deviceId);
        boolean connected = route != null && route.connected();
        json.addProperty("inRange", connected);
        if (routeState == null && route != null && !connected) {
            routeState = ai.ezfox.cccloud.network.NetworkService.overCapacity(deviceId) ? "over_capacity" : "no_route";
        }
        if (routeState != null) json.addProperty("routeState", routeState);
        if (connected) {
            json.addProperty("hops", route.hops());
            json.addProperty("relays", Math.max(0, route.hops() - 1));
            json.addProperty("signal", Math.round(route.signal() * 100) / 100.0);
            if (route.wired()) json.addProperty("cable", route.fiber() ? "fiber" : "cable");
            if (route.legacyOutOfRange()) json.addProperty("signal", 0);
        }
        json.addProperty("canUnlink", resolution.has(ai.ezfox.cccloud.access.Perm.NETWORK_LINK));
        json.addProperty("canRename", resolution.has(ai.ezfox.cccloud.access.Perm.LAYOUT_EDIT));
        json.addProperty("canDashboard", resolution.has(ai.ezfox.cccloud.access.Perm.VIEW) && CloudWebServer.isRunning());
    }

    /**
     * A Cloud Relay: what it does (ranges), the networks it carries (only those where the player has {@code view},
     * names with positions only with {@code view.positions}; the rest are counted), devices routed through it, its own
     * link to the mainframe, and its power.
     */
    public static String relay(ai.ezfox.cccloud.relay.CloudRelayBlockEntity relay, ServerPlayer player, @Nullable String notice) {
        var json = new JsonObject();
        var type = relay.type();
        json.addProperty("kind", "relay");
        json.addProperty("advanced", type != ai.ezfox.cccloud.relay.RelayType.RELAY);
        json.addProperty("relayType", switch (type) {
            case RELAY -> "relay";
            case ADVANCED -> "advanced";
            case ENDER -> "ender";
        });
        json.addProperty("block", BuiltInRegistries.BLOCK.getKey(relay.getBlockState().getBlock()).toString());
        json.addProperty("pos", pos(relay.getBlockPos()));
        json.addProperty("relayId", relay.relayId().toString());
        json.addProperty("powered", relay.hasPower());
        json.addProperty("energy", relay.energy().energy());
        json.addProperty("capacity", relay.energy().capacity());
        json.addProperty("usage", type.usage());
        json.addProperty("nodeRange", type.nodeRange());
        json.addProperty("deviceRange", type.deviceRange());
        if (type == ai.ezfox.cccloud.relay.RelayType.ENDER) json.addProperty("enderLinked", relay.enderGroup() != null);
        String id = relay.relayId().toString();
        var networks = new JsonArray();
        int others = 0;
        for (var mainframe : CloudNetwork.mainframes()) {
            var result = ai.ezfox.cccloud.network.NetworkService.result(mainframe.getMainframeId());
            if (result == null || !result.reachable(id)) continue;
            var resolution = ai.ezfox.cccloud.access.Access.resolve(player, mainframe.getMainframeId());
            if (!resolution.has(ai.ezfox.cccloud.access.Perm.VIEW) || networks.size() >= 8) {
                others++;
                continue;
            }
            var row = new JsonObject();
            row.addProperty("name", resolution.has(ai.ezfox.cccloud.access.Perm.VIEW_POSITIONS) ? mainframe.getDisplayName() : "Mainframe");
            var path = result.nodePaths().get(id);
            row.addProperty("relays", Math.max(0, path.size() - 2));
            if (path.size() >= 2) {
                String parent = path.get(path.size() - 2);
                for (var link : result.links()) {
                    if ((link.a().equals(parent) && link.b().equals(id)) || (link.b().equals(parent) && link.a().equals(id))) {
                        if (link.kind().cable()) row.addProperty("cable", link.kind() == ai.ezfox.cccloud.network.graph.NetworkGraph.LinkKind.FIBER ? "fiber" : "cable");
                        else row.addProperty("signal", Math.round(link.signal() * 100) / 100.0);
                        break;
                    }
                }
            }
            int devices = 0;
            for (var route : result.routes().values()) if (route != null && route.path().contains(id)) devices++;
            row.addProperty("devices", devices);
            networks.add(row);
        }
        json.add("networks", networks);
        json.addProperty("otherNetworks", others);
        if (notice != null) json.addProperty("notice", notice);
        return Json.GSON.toJson(json);
    }
}
