package ai.ezfox.cccloud.access;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Test permissions: everyone has cccloud.use (and the feature nodes) unless revoked; nodes granted per player; bans. */
public final class FakePermissions implements ServerPermissions {
    public final Map<UUID, Set<Node>> granted = new HashMap<>();
    public final Set<UUID> banned = new HashSet<>();
    public final Set<UUID> noUse = new HashSet<>();
    public final Set<UUID> noScripts = new HashSet<>();
    public final Set<UUID> noApi = new HashSet<>();

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
        if (node == Node.FEATURE_SCRIPTS) return !noScripts.contains(player);
        if (node == Node.API_KEYS) return !noApi.contains(player);
        return granted.getOrDefault(player, Set.of()).contains(node);
    }

    @Override
    public int mainframeLimit(UUID player) {
        return 3;
    }
}
