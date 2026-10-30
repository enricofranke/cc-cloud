package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.gui.CloudMenu;
import ai.ezfox.cccloud.gui.GuiNetwork;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.NetworkGraph;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import java.util.TreeSet;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the Cloud Redstone device screen: the view (JSON, same transport as the uplink screen:
 * {@link GuiNetwork.ViewPayload} on open and every 10 ticks while it changes) and the actions
 * ({@code cccloud:rs_device_action}). Every action re-checks on the server that the screen is open and in range, and the
 * player's live rights (settings: {@code network.link} on the device's mainframe).
 */
public final class RsScreens {
    private static final int UPDATE_INTERVAL = 10;

    public record ActionPayload(int containerId, String action, String arg) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "rs_device_action"));
        public static final StreamCodec<ByteBuf, ActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ActionPayload::containerId,
            ByteBufCodecs.stringUtf8(16), ActionPayload::action,
            ByteBufCodecs.stringUtf8(RsScreenActions.MAX_SENSOR_ARG), ActionPayload::arg,
            ActionPayload::new);

        @Override
        public Type<ActionPayload> type() {
            return TYPE;
        }
    }

    private RsScreens() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(RsScreens::register);
        NeoForge.EVENT_BUS.addListener(RsScreens::onServerTick);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ActionPayload.TYPE, ActionPayload.CODEC, RsScreens::onAction);
    }

    static void open(ServerPlayer player, RsDeviceBlockEntity device) {
        String view = view(device, player, null);
        var pos = device.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RsDeviceMenu(id, pos, view), device.getBlockState().getBlock().getName()), buf -> {
            buf.writeBlockPos(pos);
            buf.writeUtf(view, CloudMenu.MAX_VIEW);
        });
    }

    /** May this player change the device's settings? Unlinked devices: anyone who may use the block. */
    static boolean canManage(ServerPlayer player, RsDeviceBlockEntity device) {
        var link = device.getLink();
        return link == null || Access.has(player, link.id(), Perm.NETWORK_LINK);
    }

    /** May this player arm or disarm an Advanced Cloud Sensor? Linked: {@code act.redstone} on its mainframe. */
    static boolean canOperate(ServerPlayer player, RsDeviceBlockEntity device) {
        var link = device.getLink();
        return link == null || Access.has(player, link.id(), Perm.ACT_REDSTONE);
    }

    /**
     * May this player see player names in an Advanced Cloud Sensor's screen? Like the dashboard: {@code view.positions}
     * on its mainframe (unlinked: its owner), and only while names are on for the server.
     */
    static boolean seesNames(ServerPlayer player, RsDeviceBlockEntity device) {
        if (!ai.ezfox.cccloud.sensor.AdvancedSensor.namesEnabled()) return false;
        var link = device.getLink();
        var sensor = device.sensor();
        if (link == null) return sensor != null && player.getUUID().equals(sensor.owner());
        return Access.has(player, link.id(), Perm.VIEW_POSITIONS);
    }

    /** The screen's view: what it is, its state and settings, and how it is connected. */
    public static String view(RsDeviceBlockEntity device, ServerPlayer player, @Nullable String notice) {
        var json = new JsonObject();
        var kind = device.kind();
        var record = device.record();
        json.addProperty("kind", kind.id);
        json.addProperty("status", device.status().getSerializedName());
        if (device.routeState() != null) json.addProperty("routeState", device.routeState());
        json.addProperty("name", device.name());
        json.addProperty("displayName", record.displayName());
        json.addProperty("group", device.group());
        json.addProperty("level", device.signalLevel());
        json.addProperty("analog", device.analog());
        json.addProperty("preset", device.preset());
        json.addProperty("pulse", device.pulse());
        json.addProperty("follow", device.follow());
        json.addProperty("on", record.on());
        json.addProperty("input", device.input());
        json.addProperty("pin", device.pin());
        json.addProperty("confirm", device.confirm());
        boolean manage = canManage(player, device);
        json.addProperty("canManage", manage);
        var link = device.getLink();
        json.addProperty("linked", link != null);
        if (link != null) {
            var p = link.pos().pos();
            boolean positions = Access.has(player, link.id(), Perm.VIEW_POSITIONS);
            json.addProperty("mainframePos", positions ? p.getX() + " " + p.getY() + " " + p.getZ() : "");
            var mainframe = CloudNetwork.mainframe(link.id());
            json.addProperty("mainframe", mainframe == null ? "" : positions ? mainframe.getDisplayName() : "Mainframe");
            boolean sameDim = device.getLevel() != null && link.pos().dimension().equals(device.getLevel().dimension());
            json.addProperty("distance", sameDim && positions ? (int) Math.sqrt(device.getBlockPos().distSqr(p)) : -1);
            json.addProperty("range", NetworkService.deviceRange(NetworkGraph.DeviceType.REDSTONE));
            json.addProperty("inRange", device.hasRoute());
            var route = NetworkService.route(device.deviceId());
            if (route != null && route.connected()) json.addProperty("hops", route.hops());
            var card = RsDevices.sectionOfDevice(link.id(), device.deviceId());
            json.addProperty("card", card == null ? "" : card.getTitle());
            // groups already used on this mainframe, to pick one with a click
            var groups = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            for (var d : RsDevices.devices(link.id())) if (!d.group().isBlank()) groups.add(d.group().strip());
            var array = new JsonArray();
            groups.stream().limit(8).forEach(array::add);
            json.add("groups", array);
            json.addProperty("canUnlink", manage);
        }
        var sensor = device.sensor();
        if (sensor != null) json.add("sensor", sensorView(device, sensor, player, manage));
        if (notice != null) json.addProperty("notice", notice);
        return Json.GSON.toJson(json);
    }

    /** The Advanced Cloud Sensor part of the screen: state, last events, settings (lists only for players who may change them). */
    static JsonObject sensorView(RsDeviceBlockEntity device, ai.ezfox.cccloud.sensor.AdvancedSensor sensor, ServerPlayer player, boolean manage) {
        var s = sensor.settings();
        var o = new JsonObject();
        boolean names = seesNames(player, device);
        o.addProperty("armed", s.armed());
        o.addProperty("canArm", canOperate(player, device));
        o.addProperty("output", s.redstone());
        o.addProperty("team", s.filter().ignoreTeam());
        o.addProperty("detect", s.filter().mode().id);
        o.addProperty("notify", s.alert().id);
        o.addProperty("place", s.placement().id);
        var clamped = s.clamped(ai.ezfox.cccloud.sensor.AdvancedSensor.maxX(), ai.ezfox.cccloud.sensor.AdvancedSensor.maxY(),
            ai.ezfox.cccloud.sensor.AdvancedSensor.maxZ());
        var zone = new JsonArray();
        zone.add(clamped.sizeX());
        zone.add(clamped.sizeY());
        zone.add(clamped.sizeZ());
        o.add("zone", zone);
        var max = new JsonArray();
        max.add(ai.ezfox.cccloud.sensor.AdvancedSensor.maxX());
        max.add(ai.ezfox.cccloud.sensor.AdvancedSensor.maxY());
        max.add(ai.ezfox.cccloud.sensor.AdvancedSensor.maxZ());
        o.add("max", max);
        if (manage) {
            o.addProperty("allow", ai.ezfox.cccloud.sensor.SensorFilter.join(s.filter().allow()));
            o.addProperty("deny", ai.ezfox.cccloud.sensor.SensorFilter.join(s.filter().deny()));
        }
        if (sensor.zoneProblem() != null) o.addProperty("problem", sensor.zoneProblem());
        var inside = sensor.inside();
        o.addProperty("inside", inside.isEmpty() ? "" : ai.ezfox.cccloud.sensor.SensorText.sentence(inside, names, ai.ezfox.cccloud.sensor.SensorText.who(inside, names)));
        o.addProperty("today", sensor.log().today(System.currentTimeMillis(), ai.ezfox.cccloud.rules.RuleService.zone()));
        var events = new JsonArray();
        var list = sensor.log().events();
        for (int i = list.size() - 1; i >= 0 && events.size() < 5; i--) {
            var e = list.get(i);
            var row = new JsonObject();
            row.addProperty("t", e.time());
            row.addProperty("in", e.entered());
            row.addProperty("n", names && e.entry().named() ? e.entry().name() : ai.ezfox.cccloud.sensor.SensorText.capitalize(e.entry().publicLabel()));
            events.add(row);
        }
        o.add("events", events);
        return o;
    }

    private static void onAction(ActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ai.ezfox.cccloud.ingame.PacketLimiter.SCREEN_ACTIONS.allow(player.getUUID())) return;
        if (!(player.containerMenu instanceof RsDeviceMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)
            || !(player.level().getBlockEntity(menu.pos()) instanceof RsDeviceBlockEntity device)) {
            return;
        }
        String result = run(player, device, payload.action(), payload.arg());
        if (result != null && !result.equals("saved")) {
            CCCloud.LOGGER.info("[cccloud] Rejected screen action {} of {} on the {} at {}: {}", payload.action(), player.getGameProfile().name(),
                device.kind().block, device.getBlockPos(), result);
        }
        push(player, menu, device, result);
    }

    /** Runs one screen action; returns the notice ("saved") or the error. */
    static @Nullable String run(ServerPlayer player, RsDeviceBlockEntity device, String name, String arg) {
        var action = RsScreenActions.parse(name, arg);
        if (action == null || !RsScreenActions.fits(action, device.kind())) return "invalid";
        if (RsScreenActions.needsManage(action) && !canManage(player, device)) return "no_rights";
        if (RsScreenActions.needsOperate(action) && !canOperate(player, device)) return "no_rights";
        String who = player.getGameProfile().name();
        switch (action) {
            case RsScreenActions.Rename r -> device.setNameAndGroup(r.name(), device.group());
            case RsScreenActions.Regroup r -> device.setNameAndGroup(device.name(), r.group());
            case RsScreenActions.Mode m -> device.setAnalog(m.analog());
            case RsScreenActions.Level l -> {
                if (device.kind() == RsKind.LAMP) device.setDimmer(l.level());
                else device.setOutput(l.level());
                return null;
            }
            case RsScreenActions.Power p -> {
                if (device.kind() == RsKind.LAMP) device.setLamp(p.on());
                else device.setSwitch(p.on());
                return null;
            }
            case RsScreenActions.Press p -> {
                device.press(player);
                return null;
            }
            case RsScreenActions.Pulse p -> device.setPulse(p.ticks());
            case RsScreenActions.Follow f -> device.setFollow(f.on());
            case RsScreenActions.Pin p -> device.setProtection(p.on(), device.confirm());
            case RsScreenActions.Confirm c -> device.setProtection(device.pin(), c.on());
            case RsScreenActions.Unlink u -> {
                if (device.getLink() == null) return "not_linked";
                device.unlink(who);
                return "unlinked";
            }
            case RsScreenActions.Sensor sa -> {
                var sensor = device.sensor();
                if (sensor == null) return "invalid";
                var r = ai.ezfox.cccloud.sensor.SensorActions.screen(sensor.settings(), sa.key(), sa.arg(), ai.ezfox.cccloud.sensor.AdvancedSensor.maxX(),
                    ai.ezfox.cccloud.sensor.AdvancedSensor.maxY(), ai.ezfox.cccloud.sensor.AdvancedSensor.maxZ());
                if (r.settings() == null) return "invalid";
                String error = sensor.update(r.settings(), player);
                if (error != null) return error;
                device.setChanged();
            }
        }
        CCCloud.LOGGER.info("[cccloud] {} changed the {} at {}: {} = {}", who, device.kind().block, device.getBlockPos(), name, arg);
        return "saved";
    }

    private static void push(ServerPlayer player, RsDeviceMenu menu, RsDeviceBlockEntity device, @Nullable String notice) {
        String view = view(device, player, notice);
        menu.setLastSent(view);
        PacketDistributor.sendToPlayer(player, new GuiNetwork.ViewPayload(menu.containerId, view));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % UPDATE_INTERVAL != 0) return;
        for (var player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof RsDeviceMenu menu)) continue;
            if (!(player.level().getBlockEntity(menu.pos()) instanceof RsDeviceBlockEntity device)) continue;
            try {
                String view = view(device, player, null);
                if (view.equals(menu.lastSent())) continue;
                menu.setLastSent(view);
                PacketDistributor.sendToPlayer(player, new GuiNetwork.ViewPayload(menu.containerId, view));
            } catch (RuntimeException e) {
                CCCloud.LOGGER.error("[cccloud] Could not update the redstone device screen of {}", player.getGameProfile().name(), e);
            }
        }
    }
}
