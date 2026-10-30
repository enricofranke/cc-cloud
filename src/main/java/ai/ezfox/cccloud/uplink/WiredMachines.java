package ai.ezfox.cccloud.uplink;

import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.cable.WiredReach;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.SectionCache;
import ai.ezfox.cccloud.web.WebHub;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Machines an uplink's computer reaches over CC wired networks get their ready-made cards (docs/SPEC.md "CC wired
 * networks"). The computer's wired modems are followed with a {@link WiredReach}; every peripheral on those networks
 * whose target is a block entity (CC's generic inventory / tank / energy peripherals and most mod peripherals) is
 * looked up with {@link DeviceCards#detect} like a block an uplink sticks to, and gets a {@link WiredMachineSection}.
 * <p>
 * One card per block (a machine with two modems is one card), at most {@code wiredMachineCards} per uplink (names in
 * order), never for a block that another uplink or probe of the same mainframe already reads or that another uplink's
 * computer already shows, never for CC: Cloud or CC: Tweaked blocks. Cards of an unloaded uplink stay on the dashboard
 * greyed out like the uplink's own card; a machine that is gone when the uplink loads again is removed. Server thread.
 */
public final class WiredMachines {
    /** Blocks without a card are looked at again after this many refreshes (seconds). */
    static final int NO_CARD_RETRY = 10;
    /** Refreshes after loading before saved cards that did not come back are removed (the networks connect first). */
    static final int SETTLE = 3;
    /** Machines shown by some uplink: mainframe|dimension|pos → uplink id (one card per machine and mainframe). */
    private static final Map<String, UUID> HOSTS = new ConcurrentHashMap<>();

    private final CloudUplinkBlockEntity uplink;
    private final WiredReach reach;
    /** pos → card, in the order of the peripheral names. */
    private final LinkedHashMap<BlockPos, WiredMachineSection> sections = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> noCard = new HashMap<>();
    /** Ids the uplink showed when it was saved (removed if their machine does not come back). */
    private final Set<UUID> saved = new HashSet<>();
    private @Nullable UUID publishedTo;
    private int refreshes;
    /** Config reload: detect every machine's card again on the next refresh (server thread). */
    private boolean redetect;

    public WiredMachines(CloudUplinkBlockEntity uplink) {
        this.uplink = uplink;
        this.reach = new WiredReach(new WiredReach.Supplier() {
            @Override
            public net.minecraft.world.level.@Nullable Level level() {
                return uplink.getLevel();
            }

            @Override
            public BlockPos pos() {
                return uplink.getBlockPos();
            }

            @Override
            public String id() {
                return uplink.getUplinkId().toString();
            }
        });
    }

    /** How many cards an uplink may show for wired machines (config {@code wiredMachineCards}, 0 = none). */
    public static int limit() {
        return Integrations.deviceCardsEnabled() ? IntegrationConfig.get(IntegrationConfig.WIRED_MACHINE_CARDS) : 0;
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    /** Ids saved with the uplink (the cards it showed). */
    public void load(List<UUID> ids) {
        saved.clear();
        saved.addAll(ids);
    }

    /** Ids to save with the uplink. */
    public List<UUID> ids() {
        var ids = new ArrayList<UUID>();
        for (var s : sections.values()) ids.add(s.sectionId());
        return ids;
    }

    /** A peripheral the uplink's computer reaches over its wired networks (Lua bridge widgets), or null. */
    public @Nullable IPeripheral peripheral(String name) {
        return reach.peripheral(name);
    }

    /** The cards shown right now (tests, screen). */
    public List<WiredMachineSection> sections() {
        return List.copyOf(sections.values());
    }

    /** The networks the computer is on (tests). */
    public WiredReach reach() {
        return reach;
    }

    /** After a config reload ({@code [cards]}): the next refresh detects every card again with the new settings. */
    public void redetect() {
        redetect = true;
    }

    /** Once a second: follows the computer's modems, adds and removes cards, reads the machines. */
    public void refresh(ServerLevel level) {
        refreshes++;
        boolean again = redetect;
        redetect = false;
        if (again) noCard.clear();
        var link = uplink.getLink();
        UUID mainframe = link == null ? null : link.id();
        if (publishedTo != null && !publishedTo.equals(mainframe)) removeAll(true); // unlinked or linked elsewhere
        int limit = limit();
        // probes read one machine at a distance; only an uplink on a computer follows the computer's modems
        var readPos = uplink.isProbe() ? null : uplink.readPos();
        boolean computer = readPos != null && ai.ezfox.cccloud.compat.cc.CcComputers.find(level, readPos) != null;
        reach.update(level, limit > 0 && computer ? readPos : null, uplink.getBlockPos());
        if (mainframe == null || limit <= 0 || !computer) {
            if (!sections.isEmpty()) removeAll(true);
            // the computer may sit in a chunk that loads a moment later: saved cards wait for the networks to connect
            if (refreshes >= SETTLE) forgetSaved();
            return;
        }
        publishedTo = mainframe;
        String dim = level.dimension().identifier().toString();
        var all = new ArrayList<WiredCards.Candidate>();
        for (var e : reach.peripherals().entrySet()) {
            var pos = machine(level, e.getValue());
            if (pos != null) all.add(new WiredCards.Candidate(e.getKey(), pos));
        }
        var wanted = new LinkedHashMap<BlockPos, Map.Entry<String, ai.ezfox.cccloud.compat.card.DeviceCard>>();
        for (var c : all.isEmpty() ? List.<WiredCards.Candidate>of() : WiredCards.candidates(all, taken(mainframe, dim))) {
            if (wanted.size() >= limit) break;
            var existing = sections.get(c.pos());
            if (existing != null && !again && !existing.failed() && DeviceCards.isValid(existing.card())) {
                wanted.put(c.pos(), Map.entry(c.name(), existing.card()));
                continue;
            }
            Integer retry = noCard.get(c.pos());
            if (retry != null && refreshes < retry) continue;
            var card = DeviceCards.detect(level, c.pos(), side(level, c.pos()));
            if (card == null) {
                noCard.put(c.pos(), refreshes + NO_CARD_RETRY);
                continue;
            }
            noCard.remove(c.pos());
            wanted.put(c.pos(), Map.entry(c.name(), card));
        }
        if (noCard.size() > 256) noCard.clear();
        // drop what is no longer wanted (or whose card changed)
        for (var e : List.copyOf(sections.entrySet())) {
            var want = wanted.get(e.getKey());
            if (want == null || want.getValue() != e.getValue().card() || !want.getKey().equals(e.getValue().peripheral())) remove(e.getValue(), true);
        }
        var titles = WiredCards.titles(wanted.values().stream().map(w -> Map.entry(w.getKey(), DeviceCards.title(w.getValue()))).toList());
        int i = 0;
        var next = new LinkedHashMap<BlockPos, WiredMachineSection>();
        for (var e : wanted.entrySet()) {
            String title = titles.get(i++);
            var section = sections.get(e.getKey());
            if (section == null) {
                section = new WiredMachineSection(uplink.getUplinkId(), dim, e.getKey(), link, e.getValue().getKey(), e.getValue().getValue(), title);
                if (!CloudNetwork.registerSection(section)) continue;
                HOSTS.put(hostKey(mainframe, dim, e.getKey()), uplink.getUplinkId());
                saved.remove(section.sectionId());
            }
            section.setTitle(title);
            section.refresh();
            next.put(e.getKey(), section);
        }
        sections.clear();
        sections.putAll(next);
        if (refreshes >= SETTLE) forgetSaved();
    }

    /** Every tick: charts and publishing. */
    public void tick(long gameTime) {
        if (sections.isEmpty()) return;
        boolean online = uplink.isOnline();
        for (var section : sections.values()) section.tick(gameTime, online);
    }

    /** The uplink's chunk unloads: the cards stay on the dashboard greyed out (also across a restart). */
    public void unloaded(ServerLevel level) {
        reach.close();
        for (var section : sections.values()) {
            CloudNetwork.unregisterSection(section);
            HOSTS.remove(hostKey(section.getLink().id(), level.dimension().identifier().toString(), section.pos()), uplink.getUplinkId());
            var cached = SectionCache.unloaded(section.snapshot());
            WebHub.INSTANCE.publishUplink(section.getLink().id(), cached);
            SectionCache.get(level.getServer()).store(section.getLink().id(), cached);
        }
        sections.clear();
    }

    /** The uplink is broken (or unlinked for good): its cards go. */
    public void removed() {
        reach.close();
        removeAll(true);
        forgetSaved();
    }

    private void removeAll(boolean fromDashboard) {
        for (var section : List.copyOf(sections.values())) remove(section, fromDashboard);
        sections.clear();
        publishedTo = null;
    }

    private void remove(WiredMachineSection section, boolean fromDashboard) {
        sections.remove(section.pos());
        CloudNetwork.unregisterSection(section);
        var level = uplink.getLevel();
        if (level != null) HOSTS.remove(hostKey(section.getLink().id(), level.dimension().identifier().toString(), section.pos()), uplink.getUplinkId());
        if (fromDashboard) WebHub.INSTANCE.publishUplinkRemoved(section.getLink().id(), section.sectionId().toString());
    }

    /** Saved cards that did not come back: removed from the dashboard and the section cache. */
    private void forgetSaved() {
        if (saved.isEmpty()) return;
        var link = uplink.getLink();
        UUID mainframe = link != null ? link.id() : publishedTo;
        if (mainframe != null) for (var id : saved) WebHub.INSTANCE.publishUplinkRemoved(mainframe, id.toString());
        saved.clear();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    /** The block behind a peripheral, if it may get a card: a loaded block entity in this level, not ours or CC's. */
    static @Nullable BlockPos machine(ServerLevel level, IPeripheral peripheral) {
        Object target;
        try {
            target = peripheral.getTarget();
        } catch (RuntimeException e) {
            return null;
        }
        if (!(target instanceof BlockEntity be) || be.isRemoved() || be.getLevel() != level || !level.isLoaded(be.getBlockPos())) return null;
        String ns = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getNamespace();
        if (ai.ezfox.cccloud.CCCloud.MODID.equals(ns) || "computercraft".equals(ns)) return null;
        return be.getBlockPos().immutable();
    }

    /** The face a wired modem touches (the face CC reads through), else the top. */
    static Direction side(ServerLevel level, BlockPos pos) {
        for (var d : Direction.values()) {
            var at = pos.relative(d);
            if (level.isLoaded(at) && "computercraft".equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()).getNamespace())) return d;
        }
        return Direction.UP;
    }

    /** Positions this uplink must not show: read by another uplink or probe of the mainframe, or shown by another one. */
    private Set<BlockPos> taken(UUID mainframe, String dim) {
        var taken = new HashSet<BlockPos>();
        for (var other : CloudNetwork.uplinks()) {
            if (other == uplink || other.getLink() == null || !mainframe.equals(other.getLink().id()) || other.getLevel() != uplink.getLevel()) continue;
            // a probe's own target (its full read check runs in the probe once a second already)
            var read = other.isProbe() ? other.remoteTarget() : other.readPos();
            if (read != null) taken.add(read.immutable());
        }
        String prefix = mainframe + "|" + dim + "|";
        for (var e : HOSTS.entrySet()) {
            if (e.getValue().equals(uplink.getUplinkId()) || !e.getKey().startsWith(prefix) || CloudNetwork.uplink(e.getValue()) == null) continue;
            taken.add(BlockPos.of(Long.parseLong(e.getKey().substring(prefix.length()))));
        }
        return taken;
    }

    private static String hostKey(UUID mainframe, String dim, BlockPos pos) {
        return mainframe + "|" + dim + "|" + pos.asLong();
    }

    /** Server stop. */
    public static void clear() {
        HOSTS.clear();
    }
}
