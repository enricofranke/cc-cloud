package ai.ezfox.cccloud.terminal;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.cc.CcComputers;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Browser terminals (docs/SPEC.md v0.5 E): streams the terminal of the CC computer behind an uplink to the sessions
 * that opened it (at most {@link #FPS} pictures per second, only changed rows) and queues their input as CC events.
 * Rights are checked by the caller on open and input, and again every frame through {@link Viewer#allowed()}.
 * Server thread only.
 */
public final class TerminalService {
    public static final int FPS = 10;
    public static final int MAX_PER_VIEWER = 2;
    public static final int MAX_VIEWERS = 8;

    /** A browser session with an open terminal. */
    public interface Viewer {
        void send(List<String> messages);

        /** Still connected and still has {@code view} + {@code terminal} on the mainframe. */
        boolean allowed();
    }

    record Key(UUID mainframeId, UUID uplinkId) {
    }

    static final class Open {
        final Set<Viewer> viewers = new LinkedHashSet<>();
        final Set<Viewer> fresh = new HashSet<>();
        @Nullable TerminalFrame last;
        boolean on = true;
    }

    private static final Map<Key, Open> OPEN = new LinkedHashMap<>();

    private TerminalService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> OPEN.clear());
    }

    public static boolean enabled() {
        return CloudConfig.get(CloudConfig.TERMINAL_ENABLED);
    }

    /** The computer behind an uplink of this mainframe, or null. */
    static CcComputers.@Nullable Handle computer(UUID mainframeId, UUID uplinkId) {
        var section = CloudNetwork.section(uplinkId);
        if (!(section instanceof CloudUplinkBlockEntity uplink) || section.getLink() == null || !section.getLink().id().equals(mainframeId)) return null;
        if (!(uplink.getLevel() instanceof ServerLevel level)) return null;
        if (claimBlocked(uplink)) return null;
        return CcComputers.find(level, uplink.getBlockPos().relative(uplink.getBlockState().getValue(ai.ezfox.cccloud.block.CloudUplinkBlock.FACING)));
    }

    /** Claim guard: the uplink is blocked, or its computer is in a chunk another team claimed for its owners. */
    static boolean claimBlocked(CloudUplinkBlockEntity uplink) {
        if (ai.ezfox.cccloud.claims.ClaimGuard.check(uplink) != null) return true;
        return uplink.getLevel() instanceof ServerLevel level && ai.ezfox.cccloud.claims.ClaimGuard.blockedAt(level, uplink.attachedPos(), uplink.guardOwners());
    }

    /** Opens (or re-sends) a terminal for a viewer: null, or {@code disabled}, {@code unknown_computer}, {@code too_many}. */
    public static @Nullable String open(UUID mainframeId, UUID uplinkId, Viewer viewer) {
        if (!enabled()) return "disabled";
        if (CloudNetwork.section(uplinkId) instanceof CloudUplinkBlockEntity uplink && claimBlocked(uplink)) return ai.ezfox.cccloud.claims.ClaimGuard.ERROR;
        if (computer(mainframeId, uplinkId) == null) return "unknown_computer";
        var key = new Key(mainframeId, uplinkId);
        var open = OPEN.get(key);
        if (open == null || !open.viewers.contains(viewer)) {
            int mine = 0;
            for (var o : OPEN.values()) if (o.viewers.contains(viewer)) mine++;
            if (mine >= MAX_PER_VIEWER) return "too_many";
            if (open != null && open.viewers.size() >= MAX_VIEWERS) return "too_many";
        }
        open = OPEN.computeIfAbsent(key, k -> new Open());
        open.viewers.add(viewer);
        open.fresh.add(viewer); // gets a full picture with the next frame
        return null;
    }

    public static void close(UUID mainframeId, UUID uplinkId, Viewer viewer) {
        var key = new Key(mainframeId, uplinkId);
        var open = OPEN.get(key);
        if (open == null) return;
        open.viewers.remove(viewer);
        open.fresh.remove(viewer);
        if (open.viewers.isEmpty()) OPEN.remove(key);
    }

    /** The session went away. */
    public static void closeAll(Viewer viewer) {
        var it = OPEN.values().iterator();
        while (it.hasNext()) {
            var open = it.next();
            open.viewers.remove(viewer);
            open.fresh.remove(viewer);
            if (open.viewers.isEmpty()) it.remove();
        }
    }

    public static boolean isOpen(UUID mainframeId, UUID uplinkId, Viewer viewer) {
        var open = OPEN.get(new Key(mainframeId, uplinkId));
        return open != null && open.viewers.contains(viewer);
    }

    /**
     * Queues validated input on the computer: null, or {@code not_open}, {@code unknown_computer},
     * {@code computer_off} (only {@code turn_on} works while it is off).
     */
    public static @Nullable String input(UUID mainframeId, UUID uplinkId, Viewer viewer, List<TerminalInput.Event> events) {
        if (!isOpen(mainframeId, uplinkId, viewer)) return "not_open";
        var computer = computer(mainframeId, uplinkId);
        if (computer == null) return "unknown_computer";
        for (var event : events) {
            switch (event.name()) {
                case "turn_on" -> computer.turnOn();
                case "reboot" -> computer.reboot();
                case "shutdown" -> computer.shutdown();
                default -> {
                    if (!computer.isOn()) return "computer_off";
                    computer.queueEvent(event.name(), event.args());
                }
            }
        }
        return null;
    }

    /** Terminal size and colour support for validating mouse input; null while off or unknown. */
    public static @Nullable TerminalFrame lastFrame(UUID mainframeId, UUID uplinkId) {
        var open = OPEN.get(new Key(mainframeId, uplinkId));
        return open == null ? null : open.last;
    }

    private static void tick(MinecraftServer server) {
        if (OPEN.isEmpty() || server.getTickCount() % (20 / FPS) != 0) return;
        var it = OPEN.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var key = entry.getKey();
            var open = entry.getValue();
            String uplink = key.uplinkId().toString();
            for (var viewer : new ArrayList<>(open.viewers)) {
                if (viewer.allowed()) continue;
                open.viewers.remove(viewer);
                open.fresh.remove(viewer);
                viewer.send(List.of(closed(uplink, "forbidden")));
            }
            if (open.viewers.isEmpty() || !enabled()) {
                for (var viewer : open.viewers) viewer.send(List.of(closed(uplink, "disabled")));
                it.remove();
                continue;
            }
            var computer = computer(key.mainframeId(), key.uplinkId());
            if (computer == null) {
                for (var viewer : open.viewers) viewer.send(List.of(closed(uplink, "unknown_computer")));
                it.remove();
                continue;
            }
            TerminalFrame frame;
            try {
                frame = computer.frame();
            } catch (RuntimeException | LinkageError e) {
                CCCloud.LOGGER.warn("[cccloud] Cannot read the terminal of computer #{}", computer.id(), e);
                for (var viewer : open.viewers) viewer.send(List.of(closed(uplink, "unsupported")));
                it.remove();
                continue;
            }
            if (frame == null) {
                if (open.on || !open.fresh.isEmpty()) {
                    var off = new JsonObject();
                    off.addProperty("t", "term");
                    off.addProperty("uplinkId", uplink);
                    off.addProperty("on", false);
                    String json = Json.GSON.toJson(off);
                    for (var viewer : open.on ? open.viewers : open.fresh) viewer.send(List.of(json));
                }
                open.on = false;
                open.last = null;
                open.fresh.clear();
                continue;
            }
            open.on = true;
            var delta = TerminalFrame.diff(open.last, frame, uplink);
            var full = open.fresh.isEmpty() ? null : TerminalFrame.diff(null, frame, uplink);
            String deltaJson = delta == null ? null : Json.GSON.toJson(withOn(delta));
            String fullJson = full == null ? null : Json.GSON.toJson(withOn(full));
            for (var viewer : open.viewers) {
                if (open.fresh.contains(viewer)) viewer.send(List.of(fullJson));
                else if (deltaJson != null) viewer.send(List.of(deltaJson));
            }
            open.fresh.clear();
            open.last = frame;
        }
    }

    private static JsonObject withOn(JsonObject json) {
        json.addProperty("on", true);
        return json;
    }

    static String closed(String uplinkId, String reason) {
        var json = new JsonObject();
        json.addProperty("t", "term_closed");
        json.addProperty("uplinkId", uplinkId);
        json.addProperty("reason", reason);
        return Json.GSON.toJson(json);
    }

    /** Test/diagnostics: open terminals. */
    public static int openCount() {
        return OPEN.size();
    }

    static Map<Key, Open> openForTest() {
        return new HashMap<>(OPEN);
    }
}
