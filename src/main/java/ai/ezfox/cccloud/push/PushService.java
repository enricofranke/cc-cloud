package ai.ezfox.cccloud.push;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.web.TokenBucket;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Push notifications (docs/SPEC.md v0.5 D): turns alerts, rule runs, approval requests and power changes into HTTP
 * POSTs to the mainframe's targets. Asynchronous (2 worker threads), at most {@link #PER_MINUTE} deliveries per minute
 * and mainframe, 3 attempts with backoff (2 s, 6 s) for network errors, 429 and 5xx, a bounded queue. Config
 * {@code push.pushEnabled} switches everything off.
 */
public final class PushService {
    public static final int PER_MINUTE = 30;
    public static final int MAX_QUEUED = 200;
    public static final int ATTEMPTS = 3;
    /** Power lost/back pushes of one mainframe at most once a minute (flapping). */
    public static final long POWER_DEBOUNCE_MS = 60_000;

    private static final Map<UUID, TokenBucket> LIMITS = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> STATUS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_POWER = new ConcurrentHashMap<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static volatile @Nullable MinecraftServer server;
    private static volatile @Nullable ScheduledThreadPoolExecutor executor;
    private static volatile @Nullable PushHttp http;

    private PushService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> start(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> stop());
        ai.ezfox.cccloud.rules.RuleService.setNotifier(n -> notify(n.mainframeId(), n.title(), PushTarget.Event.RULES, n.level(), "Rule", n.text()));
        ai.ezfox.cccloud.web.WebHub.onAlert = (mainframeId, source, text, level) -> {
            var srv = server;
            if (srv == null) return;
            Runnable r = () -> notify(mainframeId, mainframeName(mainframeId), PushTarget.Event.ALERTS, level, source == null ? "Alert" : source, text);
            if (srv.isSameThread()) r.run();
            else srv.execute(r);
        };
    }

    private static void start(MinecraftServer srv) {
        server = srv;
        var pool = new ScheduledThreadPoolExecutor(2, r -> {
            var t = new Thread(r, "cccloud-push");
            t.setDaemon(true);
            return t;
        });
        pool.setRemoveOnCancelPolicy(true);
        executor = pool;
        http = new PushHttp();
    }

    private static void stop() {
        var pool = executor;
        if (pool != null) pool.shutdownNow();
        executor = null;
        var h = http;
        if (h != null) h.shutdown();
        http = null;
        server = null;
        LIMITS.clear();
        STATUS.clear();
        LAST_POWER.clear();
        QUEUED.set(0);
    }

    public static boolean enabled() {
        return CloudConfig.get(CloudConfig.PUSH_ENABLED);
    }

    public static Set<String> allowedHosts() {
        return Set.copyOf(CloudConfig.get(CloudConfig.PUSH_ALLOWED_HOSTS).stream().map(String::valueOf).toList());
    }

    private static String mainframeName(UUID mainframeId) {
        var mainframe = ai.ezfox.cccloud.network.CloudNetwork.mainframe(mainframeId);
        return mainframe != null ? mainframe.getDisplayName() : "Mainframe";
    }

    /** Server thread: sends a message to every target of the mainframe that wants it. */
    public static void notify(UUID mainframeId, String mainframe, PushTarget.Event event, String level, String title, String text) {
        var srv = server;
        if (srv == null || !enabled()) return;
        var message = new PushMessage(mainframeId, mainframe, event, level, title, text, System.currentTimeMillis());
        for (var target : PushData.get(srv).store().targets(mainframeId)) {
            if (target.wants(event, level)) enqueue(target, message);
        }
    }

    /** A browser waits for in-game approval on a mainframe. */
    public static void approvalPending(UUID mainframeId, String deviceName) {
        notify(mainframeId, mainframeName(mainframeId), PushTarget.Event.APPROVALS, "warn", "Approval needed",
            "The browser \"" + deviceName + "\" waits for approval in-game.");
    }

    /** The mainframe's power state flipped (debounced). */
    public static void power(UUID mainframeId, String mainframe, boolean hasPower) {
        long now = System.currentTimeMillis();
        Long last = LAST_POWER.get(mainframeId);
        if (last != null && now - last < POWER_DEBOUNCE_MS) return;
        LAST_POWER.put(mainframeId, now);
        notify(mainframeId, mainframe, PushTarget.Event.POWER, hasPower ? "info" : "error", hasPower ? "Power restored" : "No power",
            hasPower ? "The mainframe has power again." : "The mainframe has no power: the dashboard and rules are offline.");
    }

    /** "Send test notification". */
    public static @Nullable String test(UUID mainframeId, PushTarget target) {
        if (server == null || !enabled()) return "disabled";
        var message = new PushMessage(mainframeId, mainframeName(mainframeId), PushTarget.Event.ALERTS, "info", "Test notification",
            "CC: Cloud can reach this target.", System.currentTimeMillis());
        return enqueue(target, message) ? null : "rate_limited";
    }

    public static @Nullable JsonObject status(String targetId) {
        var s = STATUS.get(targetId);
        return s == null ? null : s.deepCopy();
    }

    private static boolean enqueue(PushTarget target, PushMessage message) {
        var pool = executor;
        if (pool == null) return false;
        if (!LIMITS.computeIfAbsent(message.mainframeId(), k -> new TokenBucket(PER_MINUTE, PER_MINUTE / 60.0)).tryAcquire()) {
            record(target, "rate_limited");
            return false;
        }
        if (QUEUED.incrementAndGet() > MAX_QUEUED) {
            QUEUED.decrementAndGet();
            record(target, "queue_full");
            return false;
        }
        pool.execute(() -> attempt(target, message, 1));
        return true;
    }

    private static void attempt(PushTarget target, PushMessage message, int n) {
        // read for every attempt: a host removed from pushAllowedHosts by a config reload gets no further retries
        Set<String> hosts = allowedHosts();
        var h = http;
        var pool = executor;
        if (h == null || pool == null) {
            QUEUED.decrementAndGet();
            return;
        }
        PushHttp.Result result;
        try {
            result = h.post(target.url(), PushFormat.request(target, message), hosts);
        } catch (RuntimeException e) {
            result = new PushHttp.Result(0, "io");
        }
        if (!result.ok() && result.retryable() && n < ATTEMPTS) {
            long delay = n == 1 ? 2 : 6;
            try {
                pool.schedule(() -> attempt(target, message, n + 1), delay, TimeUnit.SECONDS);
                return;
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // shutting down
            }
        }
        QUEUED.decrementAndGet();
        record(target, result.code());
        if (!result.ok()) CCCloud.LOGGER.warn("[cccloud] Push to '{}' ({}) failed: {}", target.name(), target.type().id, result.code());
    }

    private static void record(PushTarget target, String result) {
        var s = new JsonObject();
        s.addProperty("lastSent", System.currentTimeMillis());
        s.addProperty("lastResult", result);
        STATUS.put(target.id(), s);
    }

    /** The mainframe was broken. */
    public static void forget(MinecraftServer srv, UUID mainframeId) {
        for (var t : PushData.get(srv).store().targets(mainframeId)) STATUS.remove(t.id());
        PushData.get(srv).store().remove(mainframeId);
        LIMITS.remove(mainframeId);
        LAST_POWER.remove(mainframeId);
    }
}
