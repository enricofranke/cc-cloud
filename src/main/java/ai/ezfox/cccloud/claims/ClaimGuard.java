package ai.ezfox.cccloud.claims;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/**
 * One claim guard for every device that reads or controls a block (docs/SECURITY.md "Claims"): uplinks (the block they
 * stick to, for a computer the computer), Cloud Probes (their target), Cloud Bridges (their own block), Cloud Redstone
 * devices (their own block) and the cards of wired machines (the machine).
 * <p>
 * <b>Rule</b> ({@link #rule}): a device is <i>blocked</i> while that block is in a chunk claimed by a team that none of
 * its owners belongs to or is allied with. Its owners are the player who linked it (a probe: who set its target) and
 * the owner of its mainframe. Unclaimed land and claims of the owners' own or allied teams are fine. Operators get no
 * bypass, like the probe's pick and the map. A blocked device reads nothing and refuses every action
 * ({@value #ERROR}); its card shows the claim instead of values.
 * <p>
 * <b>When</b>: on linking, attaching and setting a probe target ({@link #check}), before every action (a fresh check),
 * at once for the devices in a chunk whose claim changed ({@link #claimsChanged}) and for the devices of a player whose
 * team changed (joined, left, allies; {@link #teamChanged}), and in a safety pass that checks every device once per
 * {@value #PASS_TICKS} ticks, spread over the ticks. Claim lookups are cached per chunk for {@value #CACHE_TICKS} ticks
 * (a claim event clears its chunk), and the work per tick is capped at {@value #MAX_CHECKS_PER_TICK} checks and
 * {@value #MAX_NANOS_PER_TICK} ns. Each transition of a linked device writes one audit line (server log and the
 * mainframe's audit log).
 * <p>
 * Server thread, except {@link #claimsChanged}, {@link #teamChanged} and {@link #claimedByOther} (any thread).
 */
public final class ClaimGuard {
    /** The error of every refused action and query. */
    public static final String ERROR = "claim_blocked";
    /** Every device is checked again at least this often (ticks): the safety pass. */
    public static final int PASS_TICKS = 200;
    /** A cached claim lookup is used for this many ticks; a claim event clears its chunk at once. */
    public static final int CACHE_TICKS = 200;
    /** At most this many checks per tick (event re-checks first, then the pass). */
    public static final int MAX_CHECKS_PER_TICK = 512;
    /** ... and at most this much time per tick (a check is a few map lookups, far below this). */
    public static final long MAX_NANOS_PER_TICK = 2_000_000L;

    /** Why a device is blocked: the team whose claim the block it reads is in. */
    public record Block(UUID teamId, String team) {
    }

    /** What the guard needs from a device. Implemented by uplinks, bridges, redstone devices and wired machine cards. */
    public interface Guarded {
        /** The level of {@link #guardPos()}, or null. */
        @Nullable ServerLevel guardLevel();

        /** The block the device reads or controls, or null when it reads nothing (unlinked, no target): never blocked. */
        @Nullable BlockPos guardPos();

        /** Who may use claimed land for it (see {@link #owners}); empty = nobody. */
        List<UUID> guardOwners();

        /** The current state (null = not blocked). */
        @Nullable Block claimBlock();

        /** A transition (server thread): stop reading (and show the claim), or read again. */
        void applyClaimBlock(@Nullable Block block);

        /** For the audit line, e.g. "uplink 1a2b3c4d". */
        String guardLabel();

        /** The mainframe it belongs to (audit log), or null. */
        @Nullable UUID guardMainframe();

        /** Removed or unloaded since it was queued: skipped. */
        default boolean guardRemoved() {
            return false;
        }
    }

    private static volatile @Nullable ClaimProvider provider;
    private static boolean providerReady;

    private record Entry(ClaimProvider.@Nullable Team team, long until) {
    }

    private static final Object CACHE_LOCK = new Object();
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Entry>> CACHE = new HashMap<>();
    private static volatile long now;

    private record ChunkEvent(ResourceKey<Level> dim, int x, int z) {
    }

    /** Claim and team events (any thread), handled at the end of the tick. */
    private static final ConcurrentLinkedQueue<Object> EVENTS = new ConcurrentLinkedQueue<>();
    /** Devices to check again soon (events), before the pass. */
    private static final LinkedHashSet<Guarded> URGENT = new LinkedHashSet<>();
    /** Blocked devices (re-checked on every team change; their count is the cheap "anything to undo" test). */
    private static final Set<Guarded> BLOCKED = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Blocked devices whose blocking was audited (their unblocking is audited too). */
    private static final Set<Guarded> AUDITED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static List<Guarded> pass = List.of();
    private static int cursor;
    private static boolean listening;
    private static boolean warned;

    private ClaimGuard() {
    }

    public static void init() {
        if (listening) return;
        listening = true;
        ai.ezfox.cccloud.compat.team.Teams.onChange(ClaimGuard::teamChanged);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> clear());
    }

    /** The claim mod (FTB Chunks), or null without one. */
    public static @Nullable ClaimProvider provider() {
        return provider;
    }

    /** Sets the claim mod (integration setup; tests). Cached lookups are dropped and every device is checked again. */
    public static void setProvider(@Nullable ClaimProvider newProvider) {
        provider = newProvider;
        invalidateAll();
    }

    // ---- the rule ----------------------------------------------------------------------------------------------------

    /**
     * The rule (pure): null when the device may read and control the block, else what blocks it. {@code claim} is the
     * team that claimed the block's chunk (null: unclaimed); {@code owners} are the device's owners; {@code trusted}
     * says whether a player is a member or an ally of a team. No owner (unknown) and a claim: blocked.
     */
    public static @Nullable Block rule(ClaimProvider.@Nullable Team claim, Collection<UUID> owners, BiPredicate<ClaimProvider.Team, UUID> trusted) {
        if (claim == null) return null;
        for (var owner : owners) if (owner != null && trusted.test(claim, owner)) return null;
        return new Block(claim.id(), claim.name());
    }

    /**
     * A device's owners: who linked it ({@code by}; a probe: who set its target; an unlinked uplink: who placed it) and
     * the owner of its mainframe (when linked).
     */
    public static List<UUID> owners(@Nullable UUID by, @Nullable MainframeLink link) {
        if (link == null) return by == null ? List.of() : List.of(by);
        UUID owner = mainframeOwner(link.id());
        if (by == null) return owner == null ? List.of() : List.of(owner);
        return owner == null || owner.equals(by) ? List.of(by) : List.of(by, owner);
    }

    private static @Nullable UUID mainframeOwner(UUID mainframeId) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        if (mainframe != null && mainframe.getOwner() != null) return mainframe.getOwner();
        var server = CloudNetwork.server();
        if (server == null) return null;
        var access = ai.ezfox.cccloud.web.WebAuthData.access(server).access(mainframeId);
        return access == null ? null : access.owner();
    }

    // ---- checks ------------------------------------------------------------------------------------------------------

    /** Checks a device now (linking, attaching, before an action) and applies a change. Server thread. */
    public static @Nullable Block check(Guarded device) {
        var verdict = verdict(device);
        apply(device, verdict);
        return verdict;
    }

    /** Whether reading {@code pos} is blocked for these owners right now (cached lookup; e.g. a bridge a Lua widget names). */
    public static boolean blockedAt(ServerLevel level, BlockPos pos, List<UUID> owners) {
        var p = provider;
        if (p == null) return false;
        try {
            return rule(cachedClaim(p, level, pos.getX() >> 4, pos.getZ() >> 4), owners, p::trusted) != null;
        } catch (RuntimeException | LinkageError e) {
            warnOnce(e);
            return true;
        }
    }

    /**
     * The map, the voxel view and a probe's pick (the player picking): true if the chunk is claimed by a team the player
     * is not a member or ally of. Not cached; fails closed. Any thread.
     */
    public static boolean claimedByOther(ServerLevel level, int chunkX, int chunkZ, @Nullable UUID player) {
        var p = provider;
        if (p == null || player == null) return false;
        try {
            var team = p.claimAt(level, chunkX, chunkZ);
            return team != null && !p.trusted(team, player);
        } catch (RuntimeException | LinkageError e) {
            return true; // fail closed
        }
    }

    static @Nullable Block verdict(Guarded device) {
        var p = provider;
        if (p == null) return null;
        var level = device.guardLevel();
        var pos = device.guardPos();
        if (level == null || pos == null) return null;
        try {
            if (!p.ready()) return null;
            return rule(cachedClaim(p, level, pos.getX() >> 4, pos.getZ() >> 4), device.guardOwners(), p::trusted);
        } catch (RuntimeException | LinkageError e) {
            warnOnce(e);
            return new Block(new UUID(0, 0), ""); // fail closed
        }
    }

    private static void apply(Guarded device, @Nullable Block verdict) {
        var old = device.claimBlock();
        if (Objects.equals(old, verdict)) return;
        device.applyClaimBlock(verdict);
        if (verdict != null) BLOCKED.add(device);
        else BLOCKED.remove(device);
        // the same team under a new name is no transition
        if (old != null && verdict != null && old.teamId().equals(verdict.teamId())) return;
        // One audit line per transition of a linked device (an unlinked one shows nothing anywhere); its unblocking is
        // logged when its blocking was, so linking a device that was blocked while unlinked writes no line.
        if (verdict != null) {
            if (device.guardMainframe() == null) {
                AUDITED.remove(device);
                return;
            }
            AUDITED.add(device);
        } else if (!AUDITED.remove(device)) {
            return;
        }
        audit(device, old, verdict);
    }

    private static void audit(Guarded device, @Nullable Block old, @Nullable Block verdict) {
        var pos = device.guardPos();
        var level = device.guardLevel();
        String where = pos == null ? "" : " (" + (level == null ? "" : level.dimension().identifier() + " ") + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ")";
        String text = verdict != null
            ? device.guardLabel() + " blocked: its machine is in " + teamText(verdict) + " claim"
            : device.guardLabel() + " unblocked: the claim of " + (old == null ? "another team" : old.team().isEmpty() ? "another team" : old.team())
                + " no longer applies";
        CloudLog.LOGGER.info("[cccloud] Claim guard: {}{}", text, where);
        var mainframeId = device.guardMainframe();
        var server = CloudNetwork.server();
        if (mainframeId != null && server != null) ai.ezfox.cccloud.web.WebAuthData.access(server).audit(mainframeId, "Claim guard: " + text);
    }

    private static String teamText(Block block) {
        return block.team().isEmpty() ? "another team's" : block.team() + "'s";
    }

    private static void warnOnce(Throwable e) {
        if (warned) return;
        warned = true;
        CloudLog.LOGGER.warn("[cccloud] The claim mod failed to answer; devices it concerns stay blocked", e);
    }

    // ---- cache -------------------------------------------------------------------------------------------------------

    private static ClaimProvider.@Nullable Team cachedClaim(ClaimProvider p, ServerLevel level, int cx, int cz) {
        long key = ChunkPos.pack(cx, cz);
        long t = now;
        synchronized (CACHE_LOCK) {
            var map = CACHE.get(level.dimension());
            var e = map == null ? null : map.get(key);
            if (e != null && e.until() > t) return e.team();
        }
        var team = p.claimAt(level, cx, cz);
        synchronized (CACHE_LOCK) {
            var map = CACHE.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
            if (map.size() >= 65_536) map.clear();
            map.put(key, new Entry(team, t + CACHE_TICKS));
        }
        return team;
    }

    /** Drops every cached lookup (tests that change a fake provider's claims, provider changes). */
    public static void invalidateAll() {
        synchronized (CACHE_LOCK) {
            CACHE.clear();
        }
    }

    // ---- events ------------------------------------------------------------------------------------------------------

    /**
     * A chunk was claimed or unclaimed (claim providers; any thread): its cached lookup goes and the devices that read a
     * block in it are checked again at the end of this tick.
     */
    public static void claimsChanged(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        synchronized (CACHE_LOCK) {
            var map = CACHE.get(dimension);
            if (map != null) map.remove(ChunkPos.pack(chunkX, chunkZ));
        }
        EVENTS.add(new ChunkEvent(dimension, chunkX, chunkZ));
    }

    /** A player's team changed (joined, left, allies; any thread): their devices and every blocked one are checked again. */
    public static void teamChanged(UUID player) {
        EVENTS.add(player);
    }

    /** A device went away (removed, unloaded): forget it. */
    public static void forget(Guarded device) {
        BLOCKED.remove(device);
        AUDITED.remove(device);
        URGENT.remove(device);
    }

    // ---- tick --------------------------------------------------------------------------------------------------------

    /** End of every server tick (counted as the "claim guard" tick-cost part). */
    public static void tick(MinecraftServer server) {
        now = server.getTickCount();
        var p = provider;
        boolean ready = p != null && safeReady(p);
        if (ready && !providerReady) {
            // the claim data just loaded (server start): everything is checked again soon, spread over the ticks
            invalidateAll();
            URGENT.addAll(all());
        }
        providerReady = ready;
        if (p == null && BLOCKED.isEmpty()) {
            EVENTS.clear();
            return;
        }
        drainEvents();
        long start = System.nanoTime();
        int budget = MAX_CHECKS_PER_TICK;
        int done = 0;
        // 1. event re-checks
        for (var it = URGENT.iterator(); it.hasNext() && done < budget; ) {
            var device = it.next();
            it.remove();
            if (!device.guardRemoved()) check(device);
            if ((++done & 7) == 0 && System.nanoTime() - start > MAX_NANOS_PER_TICK) return;
        }
        // 2. the safety pass: every device once per PASS_TICKS
        if (cursor >= pass.size()) {
            pass = all();
            cursor = 0;
            pruneBlocked();
        }
        int slice = passSlice(pass.size());
        for (int i = 0; i < slice && done < budget && cursor < pass.size(); i++) {
            var device = pass.get(cursor++);
            if (!device.guardRemoved()) check(device);
            if ((++done & 7) == 0 && System.nanoTime() - start > MAX_NANOS_PER_TICK) return;
        }
    }

    /** Devices the pass checks per tick so every one is checked once per {@link #PASS_TICKS}. Pure. */
    public static int passSlice(int devices) {
        if (devices <= 0) return 0;
        return Math.min(MAX_CHECKS_PER_TICK, (devices + PASS_TICKS - 1) / PASS_TICKS);
    }

    private static boolean safeReady(ClaimProvider p) {
        try {
            return p.ready();
        } catch (RuntimeException | LinkageError e) {
            return true; // the checks fail closed
        }
    }

    /** Turns the queued events into re-checks: one scan over the devices for all events of this tick. */
    private static void drainEvents() {
        if (EVENTS.isEmpty()) return;
        var chunks = new java.util.HashSet<ChunkEvent>();
        var players = new java.util.HashSet<UUID>();
        Object event;
        while ((event = EVENTS.poll()) != null) {
            if (event instanceof ChunkEvent chunk) chunks.add(chunk);
            else if (event instanceof UUID player) players.add(player);
        }
        for (var device : all()) {
            if (!chunks.isEmpty()) {
                var pos = device.guardPos();
                var level = device.guardLevel();
                if (pos != null && level != null && chunks.contains(new ChunkEvent(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4))) {
                    URGENT.add(device);
                    continue;
                }
            }
            if (!players.isEmpty()) {
                for (var owner : device.guardOwners()) {
                    if (players.contains(owner)) {
                        URGENT.add(device);
                        break;
                    }
                }
            }
        }
        // a blocked device may be freed by any team change (its owner joined the claiming team, became an ally)
        if (!players.isEmpty()) URGENT.addAll(BLOCKED);
    }

    private static void pruneBlocked() {
        if (BLOCKED.isEmpty()) return;
        var live = Collections.newSetFromMap(new IdentityHashMap<Guarded, Boolean>());
        live.addAll(pass);
        BLOCKED.removeIf(d -> !live.contains(d));
        AUDITED.removeIf(d -> !live.contains(d));
    }

    /** Every loaded device the guard watches: uplinks, probes, bridges, wired machine cards and redstone devices. */
    private static List<Guarded> all() {
        var list = new ArrayList<Guarded>();
        for (var section : CloudNetwork.sections()) if (section instanceof Guarded g) list.add(g);
        for (var device : ai.ezfox.cccloud.rsdevice.RsDevices.loadedDevices()) if (device instanceof Guarded g) list.add(g);
        return list;
    }

    // ---- JSON --------------------------------------------------------------------------------------------------------

    /** {@code {"reason":"claim","team":"<name>"}} for a section or widget ("team" is removed for viewers without positions). */
    public static JsonObject json(Block block) {
        var json = new JsonObject();
        json.addProperty("reason", "claim");
        if (!block.team().isEmpty()) json.addProperty("team", block.team());
        return json;
    }

    /**
     * The section head without the claim teams ({@code blocked.team}, {@code blockedWidgets.*.team}) for viewers without
     * {@code view.positions}, share links, displays and the API; null if the head names no team.
     */
    public static @Nullable JsonObject withoutTeams(JsonObject head) {
        boolean section = head.get("blocked") instanceof JsonObject b && b.has("team");
        boolean widgets = false;
        if (head.get("blockedWidgets") instanceof JsonObject map) {
            for (var e : map.entrySet()) if (e.getValue() instanceof JsonObject w && w.has("team")) widgets = true;
        }
        if (!section && !widgets) return null;
        var copy = new JsonObject();
        for (var e : head.entrySet()) copy.add(e.getKey(), e.getValue());
        if (section) {
            var b = head.getAsJsonObject("blocked").deepCopy();
            b.remove("team");
            copy.add("blocked", b);
        }
        if (widgets) {
            var map = new JsonObject();
            for (var e : head.getAsJsonObject("blockedWidgets").entrySet()) {
                if (e.getValue() instanceof JsonObject w) {
                    var c = w.deepCopy();
                    c.remove("team");
                    map.add(e.getKey(), c);
                } else {
                    map.add(e.getKey(), e.getValue());
                }
            }
            copy.add("blockedWidgets", map);
        }
        return copy;
    }

    /** Chat line for the player who just linked a device that is blocked (they stand at it, so the team is named). */
    public static net.minecraft.network.chat.Component linkMessage(Block block) {
        return (block.team().isEmpty() ? net.minecraft.network.chat.Component.translatable("cccloud.claim.linked_blocked_other")
            : net.minecraft.network.chat.Component.translatable("cccloud.claim.linked_blocked", block.team())).withStyle(net.minecraft.ChatFormatting.RED);
    }

    /** The in-game screens: {@code {"team":"<name>"}} with the name only for players with {@code view.positions}. */
    public static JsonObject screenJson(Block block, boolean positions) {
        var json = new JsonObject();
        json.addProperty("team", positions ? block.team() : "");
        return json;
    }

    // ---- tests / lifecycle -------------------------------------------------------------------------------------------

    /** Blocked devices right now (tests). */
    public static int blockedCount() {
        return BLOCKED.size();
    }

    static void clear() {
        invalidateAll();
        EVENTS.clear();
        URGENT.clear();
        BLOCKED.clear();
        AUDITED.clear();
        pass = List.of();
        cursor = 0;
        providerReady = false;
    }
}
