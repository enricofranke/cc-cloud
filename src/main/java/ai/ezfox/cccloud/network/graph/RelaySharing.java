package ai.ezfox.cccloud.network.graph;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Which networks a relay carries (docs/SPEC.md "Relays belong to a team"). Pure Java, unit tested; the game side
 * ({@code NetworkService}) leaves a relay out of a mainframe's {@link NetworkGraph} nodes when it does not carry that
 * network, so it neither routes its traffic nor shows up on its topology, map, 3D view or screen.
 * <p>
 * <b>Rule</b> ({@link #carries}): a relay carries a network when
 * <ul>
 * <li>it has no owner (placed before relays had owners, or by a machine): shared with everyone, as before;</li>
 * <li>it is shared with {@link Share#EVERYONE};</li>
 * <li>the mainframe's owner is the relay's owner;</li>
 * <li>or the mainframe's owner is a member or an ally of a team of the relay's owner ({@link Trust}).</li>
 * </ul>
 * A mainframe without a known owner is only carried by relays shared with everyone.
 */
public final class RelaySharing {
    /** How long a cached team answer is used (ms): team mods without change events (scoreboard teams) apply within this. */
    public static final long TTL_MS = 10_000;

    /** Who a relay carries: its owner's team and allies (the default), or every network in range. */
    public enum Share {
        TEAM, EVERYONE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Share byId(@Nullable String id) {
            if (id == null) return null;
            for (var s : values()) if (s.id().equals(id)) return s;
            return null;
        }
    }

    /** Whether {@code player} is a member or an ally of a team of {@code relayOwner} (the same player is handled before). */
    @FunctionalInterface
    public interface Trust {
        boolean trusts(UUID relayOwner, UUID player);
    }

    private RelaySharing() {
    }

    /**
     * The rule.
     *
     * @param relayOwner     the player who placed the relay; null = no owner (legacy): carries every network
     * @param share          the relay's effective setting (its own, else the server default)
     * @param mainframeOwner the owner of the network's mainframe (null = unknown)
     */
    public static boolean carries(@Nullable UUID relayOwner, Share share, @Nullable UUID mainframeOwner, Trust trust) {
        if (relayOwner == null || share == Share.EVERYONE) return true;
        if (mainframeOwner == null) return false;
        return relayOwner.equals(mainframeOwner) || trust.trusts(relayOwner, mainframeOwner);
    }

    /**
     * Team answers cached per (relay owner, mainframe owner): asked only while a network is recomputed (never per tick),
     * cleared on team change events ({@link #clear}) and re-asked after {@link #TTL_MS} for team mods without events.
     * Thread-safe (team events may come from any thread).
     */
    public static final class Cache implements Trust {
        private record Entry(boolean trusted, long until) {
        }

        private final Trust source;
        private final LongSupplier clock;
        private final long ttl;
        private final Map<UUID, Map<UUID, Entry>> entries = new ConcurrentHashMap<>();
        private final AtomicLong lookups = new AtomicLong();

        /**
         * @param source asks the team mods (server thread)
         * @param clock  milliseconds
         */
        public Cache(Trust source, LongSupplier clock, long ttl) {
            this.source = source;
            this.clock = clock;
            this.ttl = ttl;
        }

        @Override
        public boolean trusts(UUID relayOwner, UUID player) {
            long now = clock.getAsLong();
            var byPlayer = entries.computeIfAbsent(relayOwner, k -> new ConcurrentHashMap<>());
            var entry = byPlayer.get(player);
            if (entry != null && now < entry.until()) return entry.trusted();
            lookups.incrementAndGet();
            boolean trusted;
            try {
                trusted = source.trusts(relayOwner, player);
            } catch (RuntimeException | LinkageError e) {
                trusted = false; // a broken team mod counts as "not in the team"
            }
            byPlayer.put(player, new Entry(trusted, now + ttl));
            return trusted;
        }

        /** A team changed (joined, left, allies, team deleted) or the team settings were reloaded: ask again. */
        public void clear() {
            entries.clear();
        }

        /** How often the team mods were asked (tests). */
        public long lookups() {
            return lookups.get();
        }
    }
}
