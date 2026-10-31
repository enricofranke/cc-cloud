package ai.ezfox.cccloud.voxel;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.block.MainframeRetention;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.graph.NetworkGraph;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The last known view area of every mainframe (docs/SPEC.md "Voxel view – last known terrain"): where its reachable
 * nodes and its devices were the last time it was online, per dimension, including linked devices whose chunk is not
 * loaded. A network that is not loaded has no live view, so this is what still says which terrain belongs to it.
 * <ul>
 *   <li>It only opens the caches: a section inside it but outside the live view is served from memory or disk, never
 *       read from a chunk ({@link VoxelService#view}).</li>
 *   <li>A mainframe that is loaded but offline (no power, kill switch) reaches no nodes, so it keeps its last area.</li>
 *   <li>It decides which unloading chunks are worth a snapshot ({@link #at}: one map lookup for almost every chunk).</li>
 *   <li>It is saved under {@code <world>/cccloud/voxels/<mainframe>/area.gz} with the last published network map, so
 *       dashboards have the map (and with it the view) after a restart, before the mainframe's chunk loads.</li>
 * </ul>
 * Server thread only, except {@link #at}: the lookup is rebuilt in the background when an area changes, so a big network
 * that changes costs the server thread nothing.
 */
public final class VoxelAreas {
    /** A mainframe whose area may reach into a region of the world, with its points near that region. */
    public record Hit(UUID mainframe, double[][] points) {
    }

    private static final class Area {
        Map<String, double[][]> points;
        long savedAt;

        Area(Map<String, double[][]> points, long savedAt) {
            this.points = points;
            this.savedAt = savedAt;
        }
    }

    /**
     * Mainframes per region of 8×8 chunks. Only regions within the radius of a point have an entry, so a chunk anywhere
     * else costs one lookup. Pure, unit tested.
     */
    static final class Index<K> {
        private static final int SHIFT = 3;
        final int radius;
        private final Map<K, Long2ObjectOpenHashMap<Hit[]>> dims = new HashMap<>();

        Index(Map<K, Map<UUID, double[][]>> areas, int radius) {
            this.radius = radius;
            for (var dim : areas.entrySet()) {
                var regions = new Long2ObjectOpenHashMap<Map<UUID, List<double[]>>>();
                for (var area : dim.getValue().entrySet()) {
                    for (var p : area.getValue()) {
                        // one chunk more on each side than the radius needs: the exact check comes after the lookup
                        int x0 = chunk(p[0] - radius) - 1, x1 = chunk(p[0] + radius) + 1, z0 = chunk(p[2] - radius) - 1, z1 = chunk(p[2] + radius) + 1;
                        for (int rx = x0 >> SHIFT; rx <= x1 >> SHIFT; rx++) {
                            for (int rz = z0 >> SHIFT; rz <= z1 >> SHIFT; rz++) {
                                regions.computeIfAbsent(key(rx, rz), k -> new LinkedHashMap<>()).computeIfAbsent(area.getKey(), k -> new ArrayList<>()).add(p);
                            }
                        }
                    }
                }
                var hits = new Long2ObjectOpenHashMap<Hit[]>(regions.size());
                for (var region : regions.long2ObjectEntrySet()) {
                    var list = new ArrayList<Hit>(region.getValue().size());
                    for (var area : region.getValue().entrySet()) list.add(new Hit(area.getKey(), area.getValue().toArray(new double[0][])));
                    hits.put(region.getLongKey(), list.toArray(new Hit[0]));
                }
                dims.put(dim.getKey(), hits);
            }
        }

        private static int chunk(double block) {
            return Math.floorDiv((int) Math.floor(block), 16);
        }

        private static long key(int rx, int rz) {
            return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
        }

        /** Mainframes that may reach this chunk (all sections of it are checked with the exact rule later), or null. */
        Hit @Nullable [] at(K dim, int cx, int cz) {
            var regions = dims.get(dim);
            return regions == null ? null : regions.get(key(cx >> SHIFT, cz >> SHIFT));
        }
    }

    private static final Map<UUID, Area> AREAS = new HashMap<>();
    /** The file is written when the area changes, else at most this often (the map in it goes stale slowly). */
    private static final long SAVE_MS = 600_000;
    /** A map larger than this is left out of the file; the area itself is small. */
    private static final int MAX_NETWORK_CHARS = 2 * 1024 * 1024;
    private static volatile Index<ResourceKey<Level>> index = new Index<>(Map.of(), 0);
    private static boolean indexDirty = true;
    private static final AtomicBoolean INDEXING = new AtomicBoolean();

    private VoxelAreas() {
    }

    /**
     * Mainframes whose area may reach this chunk, or null for nearly every chunk of a world. One map lookup, no allocation.
     */
    public static Hit @Nullable [] at(ResourceKey<Level> dim, int cx, int cz) {
        return index.at(dim, cx, cz);
    }

    /** Every tick: after a change of an area or of {@code voxelRadius} the lookup is built again in the background. */
    static void tick() {
        int radius = CloudConfig.get(CloudConfig.VOXEL_RADIUS);
        if ((!indexDirty && index.radius == radius) || !INDEXING.compareAndSet(false, true)) return;
        indexDirty = false;
        var byDim = new HashMap<ResourceKey<Level>, Map<UUID, double[][]>>();
        for (var area : AREAS.entrySet()) {
            for (var dim : area.getValue().points.entrySet()) {
                var id = Identifier.tryParse(dim.getKey());
                if (id != null) byDim.computeIfAbsent(ResourceKey.create(Registries.DIMENSION, id), k -> new HashMap<>()).put(area.getKey(), dim.getValue());
            }
        }
        VoxelService.executor().execute(() -> {
            try {
                index = new Index<>(byDim, radius);
            } finally {
                INDEXING.set(false);
            }
        });
    }

    /** True if an area is known for this mainframe (it was online with this version at least once). */
    public static boolean known(UUID mainframeId) {
        return AREAS.containsKey(mainframeId);
    }

    /** The points of the last known area in a dimension (empty if there are none). */
    public static double[][] points(UUID mainframeId, String dim) {
        var area = AREAS.get(mainframeId);
        var points = area == null ? null : area.points.get(dim);
        return points == null ? new double[0][] : points;
    }

    /** Every dimension of a mainframe's area with its points. */
    public static Map<UUID, Map<String, double[][]>> all() {
        var out = new HashMap<UUID, Map<String, double[][]>>();
        for (var e : AREAS.entrySet()) out.put(e.getKey(), e.getValue().points);
        return out;
    }

    /**
     * A network map was published: an online mainframe's area follows its network at once (a device that was unlinked is
     * out of it), an offline one keeps its last area. Saved in the background.
     */
    public static void update(CloudMainframeBlockEntity mainframe, NetworkGraph.Result result, JsonObject full, JsonObject stripped) {
        var level = mainframe.getLevel();
        var server = level == null ? null : level.getServer();
        if (server == null) return;
        var id = mainframe.getMainframeId();
        var area = AREAS.get(id);
        boolean changed = false;
        if (mainframe.isOnline()) {
            var points = points(mainframe, result);
            if (area == null || !same(area.points, points)) {
                if (area == null) AREAS.put(id, area = new Area(points, 0));
                else area.points = points;
                indexDirty = true;
                changed = true;
            }
        } else if (area == null) {
            AREAS.put(id, area = new Area(Map.of(), 0));
            changed = true;
        }
        long now = System.currentTimeMillis();
        if (!changed && now - area.savedAt < SAVE_MS) return;
        area.savedAt = now;
        var points = area.points;
        VoxelService.disk(server).saveArea(id, () -> encode(points, full, stripped, now));
    }

    /** The same positions as {@link VoxelService#viewPoints}, plus linked devices whose chunk is not loaded. */
    private static Map<String, double[][]> points(CloudMainframeBlockEntity mainframe, NetworkGraph.Result result) {
        var byDim = new HashMap<String, List<double[]>>();
        for (var node : result.nodes()) {
            if (result.reachable(node.id())) byDim.computeIfAbsent(node.dim(), k -> new ArrayList<>()).add(new double[]{node.x(), node.y(), node.z()});
        }
        for (var device : result.devices()) {
            byDim.computeIfAbsent(device.dim(), k -> new ArrayList<>()).add(new double[]{device.x(), device.y(), device.z()});
        }
        for (var link : mainframe.getLinkedUplinks().entrySet()) {
            if (NetworkService.overCapacity(link.getKey())) continue;
            var pos = link.getValue().pos();
            byDim.computeIfAbsent(link.getValue().dimension().identifier().toString(), k -> new ArrayList<>())
                .add(new double[]{pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5});
        }
        var out = new HashMap<String, double[][]>();
        for (var e : byDim.entrySet()) out.put(e.getKey(), sorted(e.getValue()));
        return out;
    }

    /** Sorted without duplicates, so two areas compare by content. */
    static double[][] sorted(List<double[]> points) {
        var list = new ArrayList<>(points);
        list.sort(Comparator.<double[]>comparingDouble(p -> p[0]).thenComparingDouble(p -> p[1]).thenComparingDouble(p -> p[2]));
        var out = new ArrayList<double[]>(list.size());
        for (var p : list) if (out.isEmpty() || !Arrays.equals(out.getLast(), p)) out.add(p);
        return out.toArray(new double[0][]);
    }

    static boolean same(Map<String, double[][]> a, Map<String, double[][]> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (var e : a.entrySet()) if (!Arrays.deepEquals(e.getValue(), b.get(e.getKey()))) return false;
        return true;
    }

    static byte[] encode(Map<String, double[][]> points, @Nullable JsonObject full, @Nullable JsonObject stripped, long savedAt) {
        var json = new JsonObject();
        json.addProperty("savedAt", savedAt);
        var dims = new JsonObject();
        for (var e : points.entrySet()) {
            var list = new JsonArray();
            for (var p : e.getValue()) {
                var a = new JsonArray();
                for (double v : p) a.add(v);
                list.add(a);
            }
            dims.add(e.getKey(), list);
        }
        json.add("points", dims);
        // the hub's objects are never mutated, so reading them here is safe on any thread
        String text = json.toString();
        if (full != null && stripped != null) {
            String withMap = text.substring(0, text.length() - 1) + ",\"full\":" + full + ",\"stripped\":" + stripped + "}";
            if (withMap.length() <= MAX_NETWORK_CHARS) text = withMap;
        }
        return VoxelBatch.gzip(text.getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, double[][]> decodePoints(JsonObject json) {
        var out = new HashMap<String, double[][]>();
        for (var dim : json.getAsJsonObject("points").entrySet()) {
            var list = new ArrayList<double[]>();
            for (var e : dim.getValue().getAsJsonArray()) {
                var p = e.getAsJsonArray();
                if (p.size() != 3) throw new IllegalArgumentException("bad point");
                list.add(new double[]{p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()});
            }
            out.put(dim.getKey(), sorted(list));
        }
        return out;
    }

    /**
     * The saved map as a dashboard should see it after a restart: nothing of the network is known to be running, so the
     * mainframe is offline, the rest unloaded, and {@code lastSeen} says when it was saved.
     */
    static JsonObject offline(JsonObject network, long savedAt) {
        var copy = network.deepCopy();
        for (var e : copy.getAsJsonArray("nodes")) {
            var node = e.getAsJsonObject();
            boolean root = node.has("type") && NetworkGraph.NodeType.MAINFRAME.id.equals(node.get("type").getAsString());
            node.addProperty("status", root ? "offline" : "unloaded");
            if (!node.has("lastSeen")) node.addProperty("lastSeen", savedAt);
        }
        return copy;
    }

    /** Server start: the areas come back and the dashboards get the saved map of every mainframe that is not removed. */
    public static void load(MinecraftServer server, VoxelDisk disk) {
        AREAS.clear();
        indexDirty = true;
        var retention = MainframeRetention.get(server);
        for (var e : disk.areas().entrySet()) {
            try {
                var json = JsonParser.parseString(new String(e.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                long savedAt = json.get("savedAt").getAsLong();
                AREAS.put(e.getKey(), new Area(decodePoints(json), savedAt));
                if (retention.entry(e.getKey()) == null && json.get("full") instanceof JsonObject full && json.get("stripped") instanceof JsonObject stripped) {
                    WebHub.INSTANCE.publishNetwork(e.getKey(), offline(full, savedAt), offline(stripped, savedAt));
                }
            } catch (RuntimeException ex) {
                CloudLog.LOGGER.debug("[cccloud] Damaged voxel area of {}", e.getKey(), ex);
            }
        }
    }

    /** Mainframe purged: its area is gone (the disk cache deletes the file). */
    public static void forget(UUID mainframeId) {
        if (AREAS.remove(mainframeId) != null) indexDirty = true;
    }

    public static void clear() {
        AREAS.clear();
        index = new Index<>(Map.of(), 0);
        indexDirty = true;
        INDEXING.set(false);
    }
}
