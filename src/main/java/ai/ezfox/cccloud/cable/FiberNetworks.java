package ai.ezfox.cccloud.cable;

import ai.ezfox.cccloud.CCCloud;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Finds the fiber networks (docs/SPEC.md "Cables", part B) by flood-filling fiber blocks from every cloud element.
 * Elements are members of the networks that touch one of their blocks; they do not pass fiber through. Ender Fiber
 * Links bound to the same mainframe merge their networks across any distance and dimension. Only loaded chunks are
 * read; runs on fiber changes (and the network's safety pass), never per tick.
 */
public final class FiberNetworks {
    /** Upper bound of fiber blocks visited per pass (protects the server tick from huge cable mazes). */
    static final int MAX_BLOCKS = 65_536;
    private static boolean warned;

    /** A cloud element: its id and its blocks in one level. */
    public record Element(String id, ServerLevel level, List<BlockPos> positions) {
    }

    private FiberNetworks() {
    }

    /** @return element id → ids of the fiber networks it is on ({@code fiber:<n>}); elements without fiber are left out */
    public static Map<String, Set<String>> compute(List<Element> elements, Iterable<EnderFiberLinkBlockEntity> enderLinks) {
        var visited = new HashMap<ServerLevel, Map<BlockPos, Integer>>();
        var parent = new ArrayList<Integer>();
        var members = new HashMap<String, Set<Integer>>();
        int[] budget = {MAX_BLOCKS};
        for (var element : elements) {
            var seen = visited.computeIfAbsent(element.level(), k -> new HashMap<>());
            for (var pos : element.positions()) {
                for (var dir : Direction.values()) {
                    var next = pos.relative(dir);
                    if (element.positions().contains(next) || !element.level().isLoaded(next)) continue;
                    Integer net = seen.get(next);
                    if (net == null) {
                        if (!FiberCableBlock.isFiber(element.level().getBlockState(next))) continue;
                        net = parent.size();
                        parent.add(net);
                        flood(element.level(), next, net, seen, budget);
                    }
                    members.computeIfAbsent(element.id(), k -> new HashSet<>()).add(net);
                }
            }
        }
        // Ender Fiber Links of one group join their networks.
        var firstOfGroup = new HashMap<java.util.UUID, Integer>();
        for (var link : enderLinks) {
            if (link.group() == null || link.isRemoved() || !(link.getLevel() instanceof ServerLevel level)) continue;
            var net = visited.getOrDefault(level, Map.of()).get(link.getBlockPos());
            if (net == null) continue; // not touching any element's network: nothing to bridge
            var first = firstOfGroup.putIfAbsent(link.group(), net);
            if (first != null) union(parent, first, net);
        }
        var result = new HashMap<String, Set<String>>();
        for (var e : members.entrySet()) {
            var ids = new HashSet<String>();
            for (int net : e.getValue()) ids.add("fiber:" + find(parent, net));
            result.put(e.getKey(), Set.copyOf(ids));
        }
        return result;
    }

    private static void flood(ServerLevel level, BlockPos start, int net, Map<BlockPos, Integer> seen, int[] budget) {
        var queue = new ArrayDeque<BlockPos>();
        seen.put(start, net);
        queue.add(start);
        while (!queue.isEmpty()) {
            var pos = queue.poll();
            if (--budget[0] < 0) {
                if (!warned) {
                    warned = true;
                    CCCloud.LOGGER.warn("[cccloud] Fiber networks are larger than {} blocks; the rest is ignored", MAX_BLOCKS);
                }
                return;
            }
            for (var dir : Direction.values()) {
                var next = pos.relative(dir);
                if (seen.containsKey(next) || !level.isLoaded(next) || !FiberCableBlock.isFiber(level.getBlockState(next))) continue;
                seen.put(next, net);
                queue.add(next);
            }
        }
    }

    static int find(List<Integer> parent, int i) {
        while (parent.get(i) != i) {
            parent.set(i, parent.get(parent.get(i)));
            i = parent.get(i);
        }
        return i;
    }

    static void union(List<Integer> parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent.set(Math.max(ra, rb), Math.min(ra, rb));
    }
}
