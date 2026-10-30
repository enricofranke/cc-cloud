package ai.ezfox.cccloud.map;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.network.NetworkService;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the cave slices and the voxel view from working as an X-ray (security review M4, docs/SECURITY.md):
 * <ul>
 *   <li>config {@code map.mapSlices} ({@code auto}: on for singleplayer and LAN-only dashboards, off when
 *       {@code webAllowInternet} is set);</li>
 *   <li>slices only within {@link #SLICE_RANGE} blocks (Y) of one of the requesting mainframe's nodes;</li>
 *   <li>nothing from chunks claimed by another team (FTB Chunks, when installed);</li>
 *   <li>ores ({@code #c:ores}) are shown as stone unless the ore touches air and is within {@link #ORE_RADIUS} blocks of
 *       one of the mainframe's nodes.</li>
 * </ul>
 */
public final class XrayGuard {
    public static final int ORE_RADIUS = 16;
    public static final int SLICE_RANGE = 32;

    /** Claims of other teams (set by the FTB Chunks integration). */
    public interface Claims {
        /** True if the chunk is claimed by a team the player is not a member or ally of. */
        boolean claimedByOther(ServerLevel level, int chunkX, int chunkZ, UUID player);
    }

    public static volatile @Nullable Claims claims;

    private XrayGuard() {
    }

    public static boolean slicesEnabled(MinecraftServer server) {
        return switch (CloudConfig.get(CloudConfig.MAP_SLICES)) {
            case "on" -> true;
            case "off" -> false;
            // the running dashboard's rule counts too: after a reload that could not be applied, it may still allow the
            // internet while the file says LAN only, and slices must stay off then
            default -> server.isSingleplayer() || !(CloudConfig.get(CloudConfig.WEB_ALLOW_INTERNET) || ai.ezfox.cccloud.web.CloudWebServer.allowsInternet());
        };
    }

    /** Node and device positions of the mainframe's network in this dimension ({x, y, z} each). Server thread. */
    public static int[][] nodes(UUID mainframeId, String dim) {
        var result = NetworkService.result(mainframeId);
        var list = new ArrayList<int[]>();
        if (result != null) {
            for (var node : result.nodes()) {
                if (node.dim().equals(dim) && result.reachable(node.id())) list.add(new int[]{(int) Math.floor(node.x()), (int) Math.floor(node.y()), (int) Math.floor(node.z())});
            }
            for (var device : result.devices()) {
                if (device.dim().equals(dim)) list.add(new int[]{(int) Math.floor(device.x()), (int) Math.floor(device.y()), (int) Math.floor(device.z())});
            }
        }
        return list.toArray(new int[0][]);
    }

    /** A slice at height {@code y} is allowed when a node is within {@link #SLICE_RANGE} blocks vertically. Pure. */
    public static boolean sliceInRange(int[][] nodes, int y) {
        for (var n : nodes) if (Math.abs(n[1] - y) <= SLICE_RANGE) return true;
        return false;
    }

    /** Within {@link #ORE_RADIUS} blocks (Euclidean) of a node. Pure. */
    public static boolean nearNode(int[][] nodes, int x, int y, int z) {
        long r2 = (long) ORE_RADIUS * ORE_RADIUS;
        for (var n : nodes) {
            long dx = n[0] - x, dy = n[1] - y, dz = n[2] - z;
            if (dx * dx + dy * dy + dz * dz <= r2) return true;
        }
        return false;
    }

    public static boolean isOre(@Nullable BlockState state) {
        return state != null && state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES);
    }

    public static boolean claimedByOther(ServerLevel level, int chunkX, int chunkZ, @Nullable UUID player) {
        var c = claims;
        if (c == null || player == null) return false;
        try {
            return c.claimedByOther(level, chunkX, chunkZ, player);
        } catch (RuntimeException | LinkageError e) {
            return true; // fail closed
        }
    }
}
