package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.client.gui.RsDeviceScreen;
import ai.ezfox.cccloud.sensor.SensorZones;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Outlines the zones of Advanced Cloud Sensors (docs/SPEC.md "Advanced Cloud Sensor") while the player holds an
 * Advanced Cloud Sensor or a Link Card (every sensor within {@link #RANGE} blocks), or has a sensor's screen open (only
 * that one). Cyan = watching, grey = not watching (disarmed without redstone output, or the zone reaches into another
 * team's claim). Uses the vanilla gizmo system from the client tick, like the Cloud Analyzer, at most
 * {@link #MAX_ZONES} boxes.
 */
public final class SensorZoneOverlay {
    static final int RANGE = 48;
    static final int MAX_ZONES = 32;
    private static final int ON = 0xFF4FC3D7;
    private static final int OFF = 0xFF8A8F96;

    private SensorZoneOverlay() {
    }

    /** Called from the client tick. */
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || SensorZones.CLIENT.isEmpty()) return;
        @Nullable BlockPos only = mc.screen instanceof RsDeviceScreen screen ? screen.devicePos() : null;
        boolean holding = shows(mc.player.getMainHandItem()) || shows(mc.player.getOffhandItem());
        if (only == null && !holding) return;
        var eye = mc.player.position();
        int budget = MAX_ZONES;
        for (var sensor : SensorZones.CLIENT) {
            if (sensor.isRemoved() || sensor.getLevel() != mc.level) continue;
            if (only != null && !sensor.getBlockPos().equals(only)) continue;
            if (sensor.getBlockPos().distToCenterSqr(eye) > (double) RANGE * RANGE) continue;
            var min = sensor.clientZoneMin();
            var max = sensor.clientZoneMax();
            if (min == null || max == null) continue;
            if (budget-- <= 0) break;
            int color = sensor.clientZoneOn() ? ON : OFF;
            var box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1).inflate(0.01);
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, 2f, (color & 0x00FFFFFF) | 0x1C000000));
        }
    }

    /** Items that show the zones: the Advanced Cloud Sensor itself and the Link Card. */
    static boolean shows(ItemStack stack) {
        return stack.is(ModRegistry.ADVANCED_CLOUD_SENSOR_ITEM.get()) || stack.is(ModRegistry.CLOUD_LINK_CARD.get());
    }
}
