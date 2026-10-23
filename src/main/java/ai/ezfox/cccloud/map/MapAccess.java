package ai.ezfox.cccloud.map;

import ai.ezfox.cccloud.network.NetworkService;
import java.util.UUID;

/** Which tiles a mainframe's dashboard may request: only tiles within {@link #RADIUS} blocks of one of its nodes. */
public final class MapAccess {
    public static final int RADIUS = 512;

    private MapAccess() {
    }

    /** Distance from a point to the tile's square (0 inside). Pure. */
    public static double distance(int tx, int tz, double x, double z) {
        double minX = tx * (double) MapTileRenderer.SIZE, minZ = tz * (double) MapTileRenderer.SIZE;
        double dx = Math.max(Math.max(minX - x, 0), x - (minX + MapTileRenderer.SIZE));
        double dz = Math.max(Math.max(minZ - z, 0), z - (minZ + MapTileRenderer.SIZE));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** True if a node or device of the mainframe's network (in that dimension) is within 512 blocks of the tile. */
    public static boolean allowed(UUID mainframeId, String dim, int tx, int tz) {
        var result = NetworkService.result(mainframeId);
        if (result == null) return false;
        for (var node : result.nodes()) {
            if (node.dim().equals(dim) && result.reachable(node.id()) && distance(tx, tz, node.x(), node.z()) <= RADIUS) return true;
        }
        for (var device : result.devices()) {
            if (device.dim().equals(dim) && distance(tx, tz, device.x(), device.z()) <= RADIUS) return true;
        }
        return false;
    }
}
