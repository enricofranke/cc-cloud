package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.claims.ClaimGuard;
import ai.ezfox.cccloud.claims.ClaimProvider;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A claim mod for GameTests without FTB Chunks: a test claims a chunk for a team of its own whose strangers are only the
 * test's players, so devices of tests running next to it (other players) are never blocked. Wraps the real provider
 * (FTB Chunks, when loaded) for every other chunk and team. Installed once and left in place; a test removes its
 * claims when it ends. Claims and unclaims are reported to the guard like a claim mod's events.
 */
public final class TestClaims implements ClaimProvider {
    private static final Object LOCK = new Object();
    private static volatile @Nullable TestClaims installed;

    private record Key(ResourceKey<Level> dim, long chunk) {
    }

    private final @Nullable ClaimProvider real;
    /** Claimed chunk → the claiming test team. */
    private final Map<Key, Team> claims = new ConcurrentHashMap<>();
    /** Test team → the players it does not trust (everybody else counts as a member). */
    private final Map<UUID, Set<UUID>> strangers = new ConcurrentHashMap<>();

    private TestClaims(@Nullable ClaimProvider real) {
        this.real = real;
    }

    /** The fake claim mod (installed on first use, wrapping the real one). */
    public static TestClaims get() {
        synchronized (LOCK) {
            var current = installed;
            if (current != null && ClaimGuard.provider() == current) return current;
            var fresh = new TestClaims(ClaimGuard.provider());
            installed = fresh;
            ClaimGuard.setProvider(fresh);
            return fresh;
        }
    }

    /** A team for one test, named {@code name}, that does not trust {@code strangerPlayers}. */
    public Team team(String name, Set<UUID> strangerPlayers) {
        var team = new Team(UUID.nameUUIDFromBytes(("cccloud-test-team:" + name).getBytes(StandardCharsets.UTF_8)), name);
        strangers.put(team.id(), Set.copyOf(strangerPlayers));
        return team;
    }

    /** {@code team} claims the chunk of {@code pos} (reported to the guard like a claim event). */
    public void claim(ServerLevel level, BlockPos pos, Team team) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        claims.put(new Key(level.dimension(), ChunkPos.pack(cx, cz)), team);
        ClaimGuard.claimsChanged(level.dimension(), cx, cz);
    }

    /** The chunk of {@code pos} is unclaimed again. */
    public void unclaim(ServerLevel level, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        claims.remove(new Key(level.dimension(), ChunkPos.pack(cx, cz)));
        ClaimGuard.claimsChanged(level.dimension(), cx, cz);
    }

    /** The test ends: its claims and team go. */
    public void forget(ServerLevel level, Team team) {
        claims.entrySet().removeIf(e -> {
            if (!e.getValue().equals(team)) return false;
            ClaimGuard.claimsChanged(e.getKey().dim(), ChunkPos.getX(e.getKey().chunk()), ChunkPos.getZ(e.getKey().chunk()));
            return true;
        });
        strangers.remove(team.id());
    }

    /** {@code player} becomes an ally of {@code team} or stops being one (reported like a team event). */
    public void ally(Team team, UUID player, boolean ally) {
        var set = new java.util.HashSet<>(strangers.getOrDefault(team.id(), Set.of()));
        if (ally) set.remove(player);
        else set.add(player);
        strangers.put(team.id(), Set.copyOf(set));
        ClaimGuard.teamChanged(player);
    }

    @Override
    public boolean ready() {
        var r = real;
        return r == null || r.ready();
    }

    @Override
    public @Nullable Team claimAt(ServerLevel level, int chunkX, int chunkZ) {
        var team = claims.get(new Key(level.dimension(), ChunkPos.pack(chunkX, chunkZ)));
        if (team != null) return team;
        var r = real;
        return r == null ? null : r.claimAt(level, chunkX, chunkZ);
    }

    @Override
    public boolean trusted(Team team, UUID player) {
        var set = strangers.get(team.id());
        if (set != null) return !set.contains(player);
        var r = real;
        return r != null && r.trusted(team, player);
    }
}
