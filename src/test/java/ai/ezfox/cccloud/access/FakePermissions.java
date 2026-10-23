package ai.ezfox.cccloud.access;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Test permissions: everyone has cccloud.use unless revoked; nodes granted per player; bans. */
public final class FakePermissions implements ServerPermissions {
    public final Map<UUID, Set<Node>> granted = new HashMap<>();
    public final Set<UUID> banned = new HashSet<>();
    public final Set<UUID> noUse = new HashSet<>();

    public FakePermissions grant(UUID player, Node... nodes) {
        granted.computeIfAbsent(player, k -> EnumSet.noneOf(Node.class)).addAll(Set.of(nodes));
        return this;
    }

    @Override
    public boolean banned(UUID player) {
        return banned.contains(player);
    }

    @Override
    public boolean has(UUID player, Node node) {
        if (node == Node.USE) return !noUse.contains(player);
        return granted.getOrDefault(player, Set.of()).contains(node);
    }

    @Override
    public int mainframeLimit(UUID player) {
        return 3;
    }
}
