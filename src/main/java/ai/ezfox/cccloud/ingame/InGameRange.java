package ai.ezfox.cccloud.ingame;

import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.NetworkGraph;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Coverage of a mainframe's network at a position (v0.7 Cloud Tablet): inside the device range of any available node
 * (the mainframe or a relay) that carries the network's traffic, in the same dimension. Uses the topology the network
 * service computes every few ticks; an unloaded mainframe has no coverage. The advanced tablet skips this check.
 */
public final class InGameRange {
    private InGameRange() {
    }

    public static boolean covered(UUID mainframeId, Level level, Vec3 pos) {
        var result = NetworkService.result(mainframeId);
        if (result == null) return false;
        return covered(result, level.dimension().identifier().toString(), pos.x, pos.y, pos.z);
    }

    /** Pure check on a computed topology (unit tested). */
    public static boolean covered(NetworkGraph.Result result, String dim, double x, double y, double z) {
        for (var node : result.nodes()) {
            if (!node.available() || !result.reachable(node.id()) || !node.dim().equals(dim)) continue;
            double dx = node.x() - x, dy = node.y() - y, dz = node.z() - z;
            if (dx * dx + dy * dy + dz * dz <= node.deviceRange() * node.deviceRange()) return true;
        }
        return false;
    }
}
