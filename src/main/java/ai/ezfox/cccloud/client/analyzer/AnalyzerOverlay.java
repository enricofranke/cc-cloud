package ai.ezfox.cccloud.client.analyzer;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.analyzer.AnalyzerGeometry;
import ai.ezfox.cccloud.analyzer.AnalyzerService;
import ai.ezfox.cccloud.client.CloudClientConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the Cloud Analyzer: while the player holds the analyzer, a Link Card or a cloud device item, draws the
 * networks' range bubbles (rings / spheres / off), links through walls (green / yellow weak / red broken, dashed ender
 * links), device markers, the placement preview of a held relay/uplink/bridge, and a HUD line with the signal at the
 * player's position (or an arrow to the nearest node). Uses the vanilla gizmo system from the client tick (20 Hz, no
 * per-frame work of our own), only within 128 blocks and at most {@link AnalyzerGeometry#MAX_ELEMENTS} shapes.
 */
public final class AnalyzerOverlay {
    public enum Mode { RINGS, SPHERES, OFF }

    private static final int CYAN = 0xFF4FC3D7;
    private static final int GREEN = 0xFF7FD67F;
    private static final int YELLOW = 0xFFE8D25A;
    private static final int RED = 0xFFE06666;

    private static Mode mode = Mode.RINGS;
    private static @Nullable JsonObject payload;
    private static List<AnalyzerGeometry.PNode> nodes = List.of();
    private static final Map<String, Vec3> positions = new HashMap<>();
    private static final Map<String, String> statuses = new HashMap<>();
    private static List<Vec3> highlight = List.of();
    private static long highlightUntil;
    private static String hud = "";
    private static int hudColor = 0xFFFFFFFF;

    private AnalyzerOverlay() {
    }

    public static void cycleMode() {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("cccloud.analyzer.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** Server data: overlay geometry, {@code {}} (nothing), or a route highlight. */
    public static void accept(String json) {
        JsonObject obj;
        try {
            obj = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        if (obj.has("route")) {
            var list = new ArrayList<Vec3>();
            for (var p : obj.getAsJsonArray("route")) {
                var a = p.getAsJsonArray();
                list.add(new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
            }
            highlight = list;
            highlightUntil = System.currentTimeMillis() + (obj.has("ms") ? obj.get("ms").getAsLong() : 10_000);
            return;
        }
        if (!obj.has("nodes")) {
            payload = null;
            nodes = List.of();
            positions.clear();
            statuses.clear();
            return;
        }
        payload = obj;
        nodes = AnalyzerGeometry.nodes(obj);
        positions.clear();
        statuses.clear();
        for (var n : nodes) positions.put(n.id(), new Vec3(n.x(), n.y(), n.z()));
        for (var d : obj.getAsJsonArray("devices")) {
            var dev = d.getAsJsonObject();
            positions.put(dev.get("id").getAsString(), new Vec3(dev.get("x").getAsDouble(), dev.get("y").getAsDouble(), dev.get("z").getAsDouble()));
            statuses.put(dev.get("id").getAsString(), dev.get("status").getAsString());
        }
    }

    public static void clear() {
        accept("{}");
        highlight = List.of();
    }

    // ---- per tick ----------------------------------------------------------------------------------------------------

    /** Called from the client tick (inside the vanilla per-tick gizmo collection). */
    public static void tick() {
        var mc = Minecraft.getInstance();
        hud = "";
        previewLabel = null;
        if (mc.player == null || mc.level == null) return;
        if (!highlight.isEmpty()) {
            if (System.currentTimeMillis() > highlightUntil) highlight = List.of();
            else for (int i = 1; i < highlight.size(); i++) Gizmos.line(highlight.get(i - 1), highlight.get(i), 0xFFFFD040, 6f).setAlwaysOnTop();
        }
        var data = payload;
        if (data == null || !CloudClientConfig.analyzerOverlay() || !holding(mc)) return;
        if (!mc.level.dimension().identifier().toString().equals(data.get("dim").getAsString())) return;
        var eye = mc.player.position();
        int budget = AnalyzerGeometry.MAX_ELEMENTS;
        if (mode != Mode.OFF) {
            for (var node : nodes) {
                if (budget <= 0) break;
                budget -= bubble(node, eye);
            }
        }
        for (var l : data.getAsJsonArray("links")) {
            if (budget-- <= 0) break;
            var link = l.getAsJsonObject();
            var a = positions.get(link.get("a").getAsString());
            var b = positions.get(link.get("b").getAsString());
            if (a == null || b == null) continue;
            double signal = link.get("signal").getAsDouble();
            boolean used = link.has("used");
            String kind = link.get("kind").getAsString();
            boolean cable = "wired".equals(kind) || "fiber".equals(kind);
            int color = cable ? CYAN : signal <= 0 ? RED : signal < 0.15 ? YELLOW : GREEN;
            if (!used) color = (color & 0x00FFFFFF) | 0x60000000;
            if ("ender".equals(kind)) dashed(a, b, color);
            else Gizmos.line(a, b, color, used ? 2.5f : 1.5f).setAlwaysOnTop();
        }
        // Cloud Probes: a thin line to the machine each one reads.
        if (data.get("probes") instanceof com.google.gson.JsonArray probes) {
            for (var element : probes) {
                if (budget-- <= 0) break;
                var a = element.getAsJsonArray();
                var from = new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
                var to = new Vec3(a.get(3).getAsDouble(), a.get(4).getAsDouble(), a.get(5).getAsDouble());
                if (from.distanceTo(eye) > 128) continue;
                Gizmos.line(from, to, 0xC0B48AE8, 1f).setAlwaysOnTop();
                Gizmos.cuboid(new AABB(BlockPos.containing(to)).inflate(0.01), GizmoStyle.stroke(0x90B48AE8, 1f)).setAlwaysOnTop();
            }
        }
        for (var e : statuses.entrySet()) {
            if (budget-- <= 0) break;
            var p = positions.get(e.getKey());
            if (p == null) continue;
            int color = "online".equals(e.getValue()) ? GREEN : "legacy_out_of_range".equals(e.getValue()) ? YELLOW : RED;
            Gizmos.cuboid(new AABB(BlockPos.containing(p)).inflate(0.02), GizmoStyle.stroke(color, 2f)).setAlwaysOnTop();
        }
        preview(mc, data);
        hudLine(mc, data);
    }

    /** Range bubble of one node; returns the shapes used. */
    private static int bubble(AnalyzerGeometry.PNode node, Vec3 eye) {
        var center = new Vec3(node.x(), node.y(), node.z());
        double distance = center.distanceTo(eye);
        int color = node.reachable() ? 0x904FC3D7 : 0x60E06666;
        int dim = node.reachable() ? 0x404FC3D7 : 0x30E06666;
        if (mode == Mode.RINGS) {
            Gizmos.circle(center, (float) node.deviceRange(), GizmoStyle.stroke(color, 2f));
            Gizmos.circle(center, (float) node.nodeRange(), GizmoStyle.stroke(dim, 1f));
            return 2;
        }
        // Spheres: latitude rings, fewer when far away (LOD).
        int rings = distance < 48 ? 7 : distance < 128 ? 5 : 3;
        for (int i = 0; i < rings; i++) {
            double phi = -Math.PI / 2 + Math.PI * (i + 1) / (rings + 1);
            double r = node.deviceRange() * Math.cos(phi);
            Gizmos.circle(center.add(0, node.deviceRange() * Math.sin(phi), 0), (float) r, GizmoStyle.stroke(i == rings / 2 ? color : dim, 1.5f));
        }
        return rings;
    }

    private static void dashed(Vec3 a, Vec3 b, int color) {
        double length = a.distanceTo(b);
        int parts = (int) Math.min(40, Math.max(2, length / 2));
        for (int i = 0; i < parts; i += 2) {
            Gizmos.line(a.lerp(b, (double) i / parts), a.lerp(b, (double) (i + 1) / parts), color, 2f).setAlwaysOnTop();
        }
    }

    // ---- placement preview & HUD -------------------------------------------------------------------------------------

    /** Range of the held device item: [range, asNode ? 1 : 0], or null if it is not a placeable network device. */
    static double @Nullable [] heldRange(Item item, JsonObject data) {
        var ranges = data.getAsJsonObject("ranges");
        String kind = item == ModRegistry.CLOUD_UPLINK_ITEM.get() ? "uplink" : item == ModRegistry.ADVANCED_CLOUD_UPLINK_ITEM.get() ? "advanced_uplink"
            : item == ModRegistry.CLOUD_PROBE_ITEM.get() ? "probe"
            : item == ModRegistry.CLOUD_RELAY_ITEM.get() ? "relay" : item == ModRegistry.ADVANCED_CLOUD_RELAY_ITEM.get() ? "advanced_relay"
            : item == ModRegistry.ENDER_RELAY_ITEM.get() ? "ender_relay" : isBridge(item) ? "bridge"
            : AnalyzerService.isRedstoneDevice(item) ? "redstone" : null;
        if (kind == null || !ranges.has(kind)) return null;
        var r = ranges.getAsJsonArray(kind);
        boolean node = kind.endsWith("relay");
        return new double[]{r.get(0).getAsDouble(), node ? 1 : 0, node ? r.get(1).getAsDouble() : r.get(0).getAsDouble()};
    }

    private static boolean isBridge(Item item) {
        for (var bridge : ai.ezfox.cccloud.bridge.BridgeRegistry.ITEMS) if (item == bridge.get()) return true;
        return false;
    }

    private static void preview(Minecraft mc, JsonObject data) {
        var range = heldRange(mc.player.getMainHandItem().getItem(), data);
        if (range == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var pos = hit.getBlockPos().relative(hit.getDirection());
        var center = pos.getCenter();
        var p = AnalyzerGeometry.preview(nodes, data.get("dim").getAsString(), center.x, center.y, center.z, range[0], range[1] > 0);
        int color = p.connects() ? GREEN : RED;
        Gizmos.cuboid(new AABB(pos), GizmoStyle.strokeAndFill(color, 2f, (color & 0x00FFFFFF) | 0x30000000)).setAlwaysOnTop();
        Gizmos.circle(center, (float) range[2], GizmoStyle.stroke((color & 0x00FFFFFF) | 0x90000000, 2f));
        String label;
        if (p.connects() && p.via() != null) {
            Gizmos.line(center, new Vec3(p.via().x(), p.via().y(), p.via().z()), GREEN, 2f).setAlwaysOnTop();
            label = I18n.get("cccloud.analyzer.would_connect", name(p.via()), Math.round(p.signal() * 100),
                ai.ezfox.cccloud.analyzer.CloudAnalyzerItem.via(p.hops()).getString());
        } else if (p.nearest() != null) {
            label = I18n.get("cccloud.analyzer.out_of_range", name(p.nearest()), Math.round(p.distance()), Math.round(p.range()));
        } else {
            label = I18n.get("cccloud.analyzer.no_network");
        }
        previewLabel = new Label(label, center.add(0, 0.6, 0), color);
    }

    // ---- placement label billboard -----------------------------------------------------------------------------------

    private record Label(String text, Vec3 pos, int color) {
    }

    private static volatile @Nullable Label previewLabel;
    /** Where the label's anchor is on screen this frame: x, y in 0..1 (top-left origin) and the camera distance; null if behind. */
    private static volatile float @Nullable [] labelScreen;
    /** GUI scale of the label: {@code LABEL_SCALE_AT / distance}, clamped so it stays readable near and far. */
    static final float LABEL_SCALE_AT = 2.5f, MIN_LABEL_SCALE = 0.55f, MAX_LABEL_SCALE = 1f;

    static float labelScale(double distance) {
        return Mth.clamp((float) (LABEL_SCALE_AT / Math.max(0.1, distance)), MIN_LABEL_SCALE, MAX_LABEL_SCALE);
    }

    /**
     * Projects the placement label's world anchor to the screen (after the level is drawn, with this frame's matrices),
     * so the HUD can draw it as a billboard panel above everything.
     */
    public static void projectLabel(RenderLevelStageEvent.AfterWeather event) {
        var label = previewLabel;
        var camera = event.getLevelRenderState().cameraRenderState;
        if (label == null || !camera.initialized) {
            labelScreen = null;
            return;
        }
        Vec3 rel = label.pos().subtract(camera.pos);
        var clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f);
        clip.mul(event.getModelViewMatrix());
        clip.mul(camera.projectionMatrix);
        if (clip.w <= 0.05f) {
            labelScreen = null;
            return;
        }
        float nx = clip.x / clip.w, ny = clip.y / clip.w;
        labelScreen = new float[]{(nx + 1) / 2, (1 - ny) / 2, (float) rel.length()};
    }

    /** The placement label as a billboard: a panel with a border above the ghost block, scaled by distance (clamped). */
    private static void renderLabel(GuiGraphicsExtractor g, Minecraft mc) {
        var label = previewLabel;
        var at = labelScreen;
        if (label == null || at == null) return;
        float scale = labelScale(at[2]);
        int w = mc.font.width(label.text());
        // Keep the panel on screen and below the HUD signal line.
        float half = (w / 2f + 6) * scale;
        float cx = Mth.clamp(at[0] * g.guiWidth(), half, Math.max(half, g.guiWidth() - half));
        float cy = Math.max(20 + 18 * scale, at[1] * g.guiHeight());
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        int x0 = -w / 2 - 5, y0 = -16, x1 = w / 2 + 5, y1 = -2;
        int border = (label.color() & 0x00FFFFFF) | 0xFF000000;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, border);
        g.fill(x0, y0, x1, y1, 0xF0101418);
        // A small pointer down to the block.
        g.fill(-2, y1 + 1, 2, y1 + 3, border);
        g.text(mc.font, label.text(), -w / 2, y0 + 3, 0xFFFFFFFF, false);
        g.pose().popMatrix();
    }

    private static void hudLine(Minecraft mc, JsonObject data) {
        var ranges = data.getAsJsonObject("ranges");
        double range = ranges.has("uplink") ? ranges.getAsJsonArray("uplink").get(0).getAsDouble() : 48;
        var pos = mc.player.position();
        var p = AnalyzerGeometry.preview(nodes, data.get("dim").getAsString(), pos.x, pos.y + 1, pos.z, range, false);
        if (p.connects() && p.via() != null) {
            hud = I18n.get("cccloud.analyzer.hud_signal", Math.round(p.signal() * 100), name(p.via()));
            hudColor = p.signal() < 0.15 ? YELLOW : GREEN;
        } else if (p.nearest() != null) {
            var to = new Vec3(p.nearest().x(), p.nearest().y(), p.nearest().z()).subtract(pos);
            hud = I18n.get("cccloud.analyzer.hud_none", arrow(to, mc.player.getYRot()), name(p.nearest()), Math.round(p.distance()));
            hudColor = RED;
        } else if (!nodes.isEmpty()) {
            hud = I18n.get("cccloud.analyzer.no_network");
            hudColor = RED;
        }
    }

    /** One of eight arrows pointing towards {@code to}, relative to where the player looks. */
    static String arrow(Vec3 to, float yaw) {
        double angle = Math.toDegrees(Math.atan2(-to.x, to.z)) - yaw;
        int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[sector];
    }

    private static String name(AnalyzerGeometry.PNode node) {
        if ("mainframe".equals(node.type())) return node.name();
        String block = switch (node.type()) {
            case "advanced_relay" -> "advanced_cloud_relay";
            case "ender_relay" -> "ender_relay";
            default -> "cloud_relay";
        };
        return I18n.get("block.cccloud." + block) + " @ " + (int) Math.floor(node.x()) + " " + (int) Math.floor(node.y()) + " " + (int) Math.floor(node.z());
    }

    private static boolean holding(Minecraft mc) {
        return AnalyzerService.showsOverlay(mc.player.getMainHandItem()) || AnalyzerService.showsOverlay(mc.player.getOffhandItem());
    }

    /** HUD layer: the signal line, top centre. */
    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (hud.isEmpty() || mode == null) return;
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        renderLabel(g, mc);
        int w = mc.font.width(hud);
        int x = (g.guiWidth() - w) / 2;
        g.fill(x - 4, 3, x + w + 4, 16, 0x90000000);
        g.text(mc.font, hud, x, 6, hudColor, false);
    }
}
