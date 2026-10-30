package ai.ezfox.cccloud.sensor;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.access.TeamProviders;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.network.SectionCache;
import ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity;
import ai.ezfox.cccloud.rsdevice.RsPlateBlock;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.jspecify.annotations.Nullable;

/**
 * The game side of one Advanced Cloud Sensor (docs/SPEC.md "Advanced Cloud Sensor"), owned by its
 * {@link RsDeviceBlockEntity}: settings, owner, event log, the zone scan, the redstone output, alerts and its own
 * dashboard card ({@link SensorSection}). Server thread only.
 * <p>
 * <b>Scan</b>: every {@link #SCAN_INTERVAL} ticks, at a phase taken from the block position (so sensors spread over the
 * ticks), at most {@code advancedSensorScansPerTick} scans on the whole server per tick (the rest wait a tick), one
 * bounded AABB entity query of the zone (entities in unloaded chunks are never found), at most
 * {@link #MAX_ENTITIES} entities looked at. Nothing is scanned while the sensor is disarmed and its redstone output off,
 * or while the zone touches a chunk another team claimed.
 */
public final class AdvancedSensor {
    public static final int SCAN_INTERVAL = 10;
    /** Entities looked at per scan (a mob farm in the zone does not make a scan expensive). */
    public static final int MAX_ENTITIES = 256;
    /** Claims of other teams are checked again this often (ms). */
    static final long CLAIM_CHECK_MS = 5_000;
    /** Team membership of a player is cached this long (ms). */
    static final long TEAM_CACHE_MS = 5_000;
    /** The card is rebuilt at least this often (the "today" counter rolls over at midnight). */
    static final int REFRESH_TICKS = 1_200;

    private static int budgetTick = Integer.MIN_VALUE;
    private static int budgetUsed;
    /** GameTests: a clock per sensor (cooldowns), so tests do not wait in real time. */
    private static final Map<UUID, LongSupplier> TEST_CLOCKS = new ConcurrentHashMap<>();
    /** GameTests: fake players are not in the world's entity list; tests hand them to the scan here. */
    public static final List<Entity> TEST_ENTITIES = new CopyOnWriteArrayList<>();
    /**
     * GameTests: chunks (packed) claimed by another team for a player, on top of the real claims – a hook of its own, so
     * tests that swap {@code XrayGuard.claims} at the same time do not interfere.
     */
    public static final Map<UUID, java.util.Set<Long>> TEST_CLAIMS = new ConcurrentHashMap<>();

    private final RsDeviceBlockEntity device;
    private SensorSettings settings = SensorSettings.DEFAULT;
    private SensorLog log = new SensorLog();
    private @Nullable UUID owner;
    private String ownerName = "";

    private final SensorTracker tracker = new SensorTracker();
    private final SensorAlerts alerts = new SensorAlerts();
    private @Nullable String zoneProblem;
    private long zoneCheckedAt = Long.MIN_VALUE;
    private boolean scanDue = true;
    private int lastRefresh;
    private boolean dirty = true;
    private final Map<UUID, Membership> teams = new HashMap<>();
    private @Nullable SensorSection section;

    private record Membership(boolean member, long until) {
    }

    public AdvancedSensor(RsDeviceBlockEntity device) {
        this.device = device;
    }

    // ---- state ---------------------------------------------------------------------------------------------------------

    public SensorSettings settings() {
        return settings;
    }

    public SensorLog log() {
        return log;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** Something that passes the filter is inside (the redstone output, the plate's {@code powered}). */
    public boolean occupied() {
        return tracker.occupied();
    }

    /** The redstone output: 15 while something matching is inside and the output is on. */
    public int redstoneOutput() {
        return settings.redstone() && tracker.occupied() ? 15 : 0;
    }

    public List<SensorEntry> inside() {
        return tracker.inside();
    }

    /** Why the sensor does not look at its zone right now ({@code claimed}), or null. */
    public @Nullable String zoneProblem() {
        return zoneProblem;
    }

    /** The zone, its size clamped to the server's current maximum. */
    public SensorZone zone() {
        return SensorZone.of(device.getBlockPos(), facing(), settings.clamped(maxX(), maxY(), maxZ()));
    }

    private Direction facing() {
        var state = device.getBlockState();
        return state.hasProperty(RsPlateBlock.FACING) ? state.getValue(RsPlateBlock.FACING) : Direction.NORTH;
    }

    public static int maxX() {
        return CloudConfig.get(CloudConfig.ADVANCED_SENSOR_MAX_ZONE_X);
    }

    public static int maxY() {
        return CloudConfig.get(CloudConfig.ADVANCED_SENSOR_MAX_ZONE_Y);
    }

    public static int maxZ() {
        return CloudConfig.get(CloudConfig.ADVANCED_SENSOR_MAX_ZONE_Z);
    }

    /** Player names are reported (server config); off = always "a player" and no names in the log. */
    public static boolean namesEnabled() {
        return CloudConfig.get(CloudConfig.ADVANCED_SENSOR_PLAYER_NAMES);
    }

    long now() {
        var clock = TEST_CLOCKS.get(device.deviceId());
        return clock != null ? clock.getAsLong() : System.currentTimeMillis();
    }

    /** GameTests: the clock this sensor's cooldowns use (null = the real clock again). */
    public static void setClockForTest(UUID deviceId, @Nullable LongSupplier clock) {
        if (clock == null) TEST_CLOCKS.remove(deviceId);
        else TEST_CLOCKS.put(deviceId, clock);
    }

    static ZoneId zoneId() {
        return ai.ezfox.cccloud.rules.RuleService.zone();
    }

    /** The sensor with this device id, if it is loaded. */
    public static @Nullable AdvancedSensor of(UUID deviceId) {
        var device = ai.ezfox.cccloud.rsdevice.RsDevices.loaded(deviceId);
        return device == null ? null : device.sensor();
    }

    // ---- persistence ---------------------------------------------------------------------------------------------------

    public String save() {
        var json = new JsonObject();
        json.add("settings", settings.toJson());
        json.add("log", log.toJson());
        if (owner != null) {
            json.addProperty("owner", owner.toString());
            json.addProperty("ownerName", ownerName);
        }
        return Json.GSON.toJson(json);
    }

    public void load(String text) {
        if (text.isEmpty()) return;
        try {
            var json = JsonParser.parseString(text).getAsJsonObject();
            settings = SensorSettings.fromJson(json.get("settings") instanceof JsonObject o ? o : null);
            log = SensorLog.fromJson(json.get("log") instanceof JsonObject o ? o : null);
            owner = json.has("owner") ? UUID.fromString(json.get("owner").getAsString()) : null;
            ownerName = json.has("ownerName") ? json.get("ownerName").getAsString() : "";
            if (ownerName.length() > 16) ownerName = ownerName.substring(0, 16);
        } catch (RuntimeException e) {
            settings = SensorSettings.DEFAULT;
            log = new SensorLog();
        }
        tracker.rebase();
        dirty = true;
    }

    /** The player who placed it: "ignore my team" and the claim check are about this player. */
    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        teams.clear();
        zoneCheckedAt = Long.MIN_VALUE;
        dirty = true;
    }

    /**
     * Whose team and claims count: the player who placed it, else the owner of its mainframe (a sensor placed by
     * {@code /setblock} or a structure).
     */
    @Nullable UUID effectiveOwner() {
        if (owner != null) return owner;
        var link = device.getLink();
        var mainframe = link == null ? null : CloudNetwork.mainframe(link.id());
        return mainframe == null ? null : mainframe.getOwner();
    }

    String effectiveOwnerName() {
        if (owner != null) return ownerName;
        var link = device.getLink();
        var mainframe = link == null ? null : CloudNetwork.mainframe(link.id());
        return mainframe == null ? "" : mainframe.getOwnerName();
    }

    // ---- settings ------------------------------------------------------------------------------------------------------

    /**
     * Applies new settings (screen, dashboard, rules, scenes). A new zone must lie in the owner's claim or unclaimed
     * land, not in another team's claim, not under spawn protection and inside the world border: checked with
     * {@code actor} when a player changes it in game, else with the owner. A new zone or filter starts a new baseline
     * (who is inside then is not reported again); arming does too, so arming never reports who is already there.
     *
     * @return null when applied, else the error ({@code zone_claimed}, {@code zone_protected})
     */
    public @Nullable String update(SensorSettings next, @Nullable ServerPlayer actor) {
        next = next.clamped(maxX(), maxY(), maxZ());
        var before = settings;
        if (next.equals(before)) return null;
        boolean zoneChanged = next.sizeX() != before.sizeX() || next.sizeY() != before.sizeY() || next.sizeZ() != before.sizeZ()
            || next.placement() != before.placement();
        if (zoneChanged && device.getLevel() instanceof ServerLevel level) {
            String problem = checkZone(level, SensorZone.of(device.getBlockPos(), facing(), next), actor);
            if (problem != null) return problem;
        }
        settings = next;
        if (zoneChanged || !next.filter().equals(before.filter()) || next.armed() && !before.armed() || !next.scans()) tracker.rebase();
        if (!next.armed()) alerts.clear();
        if (zoneChanged) zoneCheckedAt = Long.MIN_VALUE;
        if (next.armed() != before.armed() && device.getLink() != null) NetworkService.markDirty(device.getLink().id()); // its FE rate
        dirty = true;
        scanDue = true;
        device.sensorChanged(zoneChanged || next.scans() != before.scans());
        return null;
    }

    /** Checks a zone for the player who sets it (in game) or for the owner (dashboard, rules). Null = fine. */
    @Nullable String checkZone(ServerLevel level, SensorZone zone, @Nullable ServerPlayer actor) {
        UUID who = actor != null ? actor.getUUID() : effectiveOwner();
        for (var chunk : zone.chunks()) if (claimedByOther(level, chunk.x(), chunk.z(), who)) return "zone_claimed";
        for (var corner : zone.corners()) if (!level.getWorldBorder().isWithinBounds(corner)) return "zone_protected";
        var spawn = level.getRespawnData().pos();
        var closest = zone.closestTo(spawn);
        ServerPlayer player = actor;
        if (player == null && who != null) player = level.getServer().getPlayerList().getPlayer(who);
        if (player != null) return level.mayInteract(player, closest) ? null : "zone_protected";
        // the owner is offline: the dedicated server's spawn protection rule, the owner counted as no operator unless listed
        if (level.getServer() instanceof DedicatedServer server && level.getRespawnData().dimension() == level.dimension()
            && server.spawnProtectionRadius() > 0 && !server.getPlayerList().getOps().isEmpty()
            && (who == null || !server.getPlayerList().isOp(new NameAndId(who, effectiveOwnerName())))) {
            int dist = Math.max(Math.abs(closest.getX() - spawn.getX()), Math.abs(closest.getZ() - spawn.getZ()));
            if (dist <= server.spawnProtectionRadius()) return "zone_protected";
        }
        return null;
    }

    // ---- tick ----------------------------------------------------------------------------------------------------------

    /** Called from the block entity's tick (server thread). */
    public void tick(ServerLevel level) {
        int tick = level.getServer().getTickCount();
        if (Math.floorMod(tick + phase(), SCAN_INTERVAL) == 0) scanDue = true;
        if (!settings.scans()) {
            scanDue = false;
            if (tracker.occupied() || tracker.size() > 0) {
                tracker.rebase();
                device.sensorChanged(false);
                dirty = true;
            }
        } else if (scanDue && acquire(tick)) {
            scanDue = false;
            scan(level);
        }
        if (settings.armed() && settings.alert() != SensorSettings.Alert.OFF) {
            var due = alerts.due(now(), alertCooldownMs());
            if (due != null) sendAlert(due);
        }
        if (tick - lastRefresh >= REFRESH_TICKS || tick < lastRefresh) dirty = true;
        if (dirty) {
            dirty = false;
            lastRefresh = tick;
            publish();
        }
    }

    private int phase() {
        return Math.floorMod(Long.hashCode(device.getBlockPos().asLong()) * 31, SCAN_INTERVAL);
    }

    /** The server-wide scan budget of this tick. */
    private static boolean acquire(int tick) {
        if (tick != budgetTick) {
            budgetTick = tick;
            budgetUsed = 0;
        }
        if (budgetUsed >= CloudConfig.get(CloudConfig.ADVANCED_SENSOR_SCANS_PER_TICK)) return false;
        budgetUsed++;
        return true;
    }

    static long entityCooldownMs() {
        return CloudConfig.get(CloudConfig.ADVANCED_SENSOR_ENTITY_COOLDOWN) * 1000L;
    }

    static long alertCooldownMs() {
        return CloudConfig.get(CloudConfig.ADVANCED_SENSOR_ALERT_COOLDOWN) * 1000L;
    }

    /** One scan of the zone (also called directly by GameTests). */
    public void scan(ServerLevel level) {
        long now = now();
        var zone = zone();
        if (now - zoneCheckedAt >= CLAIM_CHECK_MS || zoneCheckedAt == Long.MIN_VALUE) {
            zoneCheckedAt = now;
            String problem = claimProblem(level, zone);
            if (!java.util.Objects.equals(problem, zoneProblem)) {
                dirty = true;
                zoneProblem = problem;
                device.syncZone(); // the outline shows whether it watches
            }
        }
        boolean wasOccupied = tracker.occupied();
        int before = tracker.size();
        List<SensorTracker.Change> changes;
        if (zoneProblem != null) {
            tracker.rebase();
            changes = List.of();
        } else {
            var box = zone.box();
            var seen = new ArrayList<SensorTracker.Seen>();
            var entities = new ArrayList<Entity>(level.getEntities((Entity) null, box, AdvancedSensor::relevant));
            if (!TEST_ENTITIES.isEmpty()) {
                for (var e : TEST_ENTITIES) if (e.level() == level && e.getBoundingBox().intersects(box) && relevant(e)) entities.add(e);
            }
            int looked = 0;
            for (var e : entities) {
                if (++looked > MAX_ENTITIES || seen.size() >= SensorTracker.MAX_INSIDE) break;
                var candidate = classify(e);
                if (candidate == null || !settings.filter().test(candidate.filter())) continue;
                seen.add(new SensorTracker.Seen(e.getUUID(), candidate.entry()));
            }
            changes = tracker.scan(seen, now, entityCooldownMs());
        }
        if (wasOccupied != tracker.occupied()) device.sensorChanged(false);
        if (before != tracker.size() || !changes.isEmpty()) dirty = true;
        if (!settings.armed() || changes.isEmpty()) return;
        var entered = new ArrayList<SensorEntry>();
        var zoneId = zoneId();
        for (var c : changes) {
            log.add(c.entered(), c.entry(), now, zoneId);
            if (c.entered()) entered.add(c.entry());
        }
        device.sensorLogged();
        if (!entered.isEmpty() && settings.alert() != SensorSettings.Alert.OFF) {
            var alert = alerts.entered(entered, now, alertCooldownMs());
            if (alert != null) sendAlert(alert);
        }
    }

    /** Entities a sensor may report at all (cheap pre-filter of the entity query). */
    static boolean relevant(Entity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        return e instanceof Player || e instanceof Mob || e instanceof ItemEntity || e instanceof VehicleEntity || e instanceof LivingEntity;
    }

    private record Candidate(SensorEntry entry, SensorFilter.Candidate filter) {
    }

    /** What an entity is for the sensor, or null if it is never reported (spectators, vanished players). */
    private @Nullable Candidate classify(Entity e) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        if (e instanceof Player p) {
            if (p.isSpectator() || vanished(p)) return null;
            String name = p.getGameProfile().name();
            var entry = new SensorEntry(SensorKind.PLAYER, type, namesEnabled() ? name : null);
            return new Candidate(entry, new SensorFilter.Candidate(SensorKind.PLAYER, name, team(p)));
        }
        SensorKind kind;
        String name;
        if (e instanceof Mob) {
            kind = e instanceof Enemy ? SensorKind.HOSTILE : SensorKind.PASSIVE;
            name = e.getType().getDescription().getString();
        } else if (e instanceof ItemEntity item) {
            kind = SensorKind.ITEM;
            name = item.getItem().getItemName().getString(); // the item's name, never an anvil name
        } else {
            kind = SensorKind.OTHER;
            name = e.getType().getDescription().getString();
        }
        var entry = new SensorEntry(kind, type, plain(name));
        return new Candidate(entry, new SensorFilter.Candidate(kind, null, false));
    }

    private static String plain(String s) {
        var sb = new StringBuilder();
        s.codePoints().filter(c -> !Character.isISOControl(c)).limit(32).forEach(sb::appendCodePoint);
        return sb.toString();
    }

    /**
     * Players hidden by a vanish mod, as far as that is cheap to tell: the entity tag {@code vanished}, the NeoForge
     * persistent flag {@code vanished}, or invisible without the invisibility effect (what vanish mods set).
     */
    static boolean vanished(Player p) {
        if (p.entityTags().contains("vanished")) return true;
        if (p.getPersistentData().getBooleanOr("vanished", false)) return true;
        return p.isInvisible() && !p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
    }

    /** The owner, or a member of one of the owner's teams (scoreboard team, FTB Teams, …); cached per player. */
    private boolean team(Player p) {
        var ownerId = effectiveOwner();
        if (ownerId == null) return false;
        if (ownerId.equals(p.getUUID())) return true;
        long now = now();
        var cached = teams.get(p.getUUID());
        if (cached != null && cached.until() > now) return cached.member();
        boolean member = false;
        String name = effectiveOwnerName();
        if (!name.isEmpty()) {
            var ownerTeam = p.level().getScoreboard().getPlayersTeam(name);
            member = ownerTeam != null && p.getTeam() == ownerTeam;
        }
        if (!member) {
            try {
                for (var provider : TeamProviders.enabled()) {
                    if (provider.id().equals(ai.ezfox.cccloud.access.ScoreboardTeams.ID)) continue;
                    for (var t : provider.teamsOf(ownerId)) {
                        if (provider.rankOf(t.id(), p.getUUID()) != null) {
                            member = true;
                            break;
                        }
                    }
                    if (member) break;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // a team mod failing never breaks the scan
            }
        }
        if (teams.size() >= 64) teams.clear();
        teams.put(p.getUUID(), new Membership(member, now + TEAM_CACHE_MS));
        return member;
    }

    /** Claims of other teams on the zone (checked every few seconds; spawn protection is checked when the zone is set). */
    private @Nullable String claimProblem(ServerLevel level, SensorZone zone) {
        UUID who = effectiveOwner();
        for (var chunk : zone.chunks()) if (claimedByOther(level, chunk.x(), chunk.z(), who)) return "zone_claimed";
        return null;
    }

    /** A chunk claimed by a team {@code who} is not a member or ally of (FTB Chunks, like the Cloud Probe). */
    static boolean claimedByOther(ServerLevel level, int chunkX, int chunkZ, @Nullable UUID who) {
        if (ai.ezfox.cccloud.map.XrayGuard.claimedByOther(level, chunkX, chunkZ, who)) return true;
        if (who == null || TEST_CLAIMS.isEmpty()) return false;
        var test = TEST_CLAIMS.get(who);
        return test != null && test.contains(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ));
    }

    // ---- alerts --------------------------------------------------------------------------------------------------------

    private void sendAlert(SensorAlerts.Alert alert) {
        var link = device.getLink();
        if (link == null || device.status() != CloudStatus.ONLINE) return;
        String where = device.record().displayName();
        boolean names = namesEnabled();
        String full = SensorText.entered(alert.entries(), names, alert.more(), where);
        String pub = SensorText.entered(alert.entries(), false, alert.more(), where);
        WebHub.INSTANCE.publishAlert(link.id(), device.deviceId().toString(), full, settings.alert().id, pub.equals(full) ? null : pub);
    }

    // ---- rules ---------------------------------------------------------------------------------------------------------

    /** The newest event's sequence number (rules start after it). */
    public long lastSeq() {
        return log.lastSeq();
    }

    /** "Entered" events after {@code seq} (rules), oldest first. */
    public List<SensorLog.Event> enteredAfter(long seq) {
        var list = new ArrayList<SensorLog.Event>();
        for (var e : log.after(seq)) if (e.entered()) list.add(e);
        return list;
    }

    // ---- dashboard card ------------------------------------------------------------------------------------------------

    /** The card (null while the sensor is not linked). */
    public @Nullable SensorSection section() {
        return section;
    }

    /** The sensor was linked, loaded or its mainframe came back: (re)registers its card. */
    public void attach() {
        var link = device.getLink();
        if (link == null) {
            detach(false);
            return;
        }
        if (section != null && section.getLink().equals(link)) return;
        detach(false);
        var s = new SensorSection(this, device, link);
        if (CloudNetwork.registerSection(s)) section = s;
        dirty = true;
    }

    /**
     * The card goes: {@code keep} = chunk unload (it stays on the dashboard greyed out with its last values, also
     * across restarts), else unlinked or broken (removed from the dashboard).
     */
    public void detach(boolean keep) {
        var s = section;
        if (s == null) return;
        section = null;
        CloudNetwork.unregisterSection(s);
        var server = CloudNetwork.server();
        if (keep) {
            var cached = SectionCache.unloaded(s.snapshot());
            WebHub.INSTANCE.publishUplink(s.mainframeId(), cached);
            if (server != null) SectionCache.get(server).store(s.mainframeId(), cached);
        } else {
            WebHub.INSTANCE.publishUplinkRemoved(s.mainframeId(), device.deviceId().toString());
        }
    }

    /** Publishes the card if it changed (values only when nothing else did). */
    void publish() {
        var s = section;
        if (s != null) s.publish();
    }

    /** Something changed that the card shows (status, name): rebuild it on the next tick. */
    public void markDirty() {
        dirty = true;
    }

    /** The values of the card ({@link SensorCards}). */
    SensorCards.State cardState() {
        long now = now();
        return new SensorCards.State(settings, tracker.inside(), log.events(), log.today(now, zoneId()), zoneProblem, namesEnabled(),
            maxX(), maxY(), maxZ(), device.pin(), device.confirm());
    }
}
