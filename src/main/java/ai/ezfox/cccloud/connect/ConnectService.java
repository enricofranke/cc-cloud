package ai.ezfox.cccloud.connect;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.PublicConfig;
import ai.ezfox.cccloud.web.TunnelBridge;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * CC: Cloud Connect in the game: starts the tunnel when {@code internetAccess = connect} and a name is set, follows
 * config changes (checked every 5 s, so {@code /cccloud internet connect} and a live config reload apply without a
 * restart), stops it with the server, and keeps the status for the command and the mainframe screen.
 */
public final class ConnectService {
    private static volatile @Nullable MinecraftServer server;
    private static volatile @Nullable ConnectClient client;
    private static volatile @Nullable TunnelBridge bridge;
    private static volatile ConnectStatus idle = ConnectStatus.OFF;
    private static final List<CompletableFuture<ConnectStatus>> waiting = new ArrayList<>();
    private static int ticks;

    private ConnectService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ServerStartedEvent.class, e -> {
            server = e.getServer();
            apply();
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> {
            stopAll();
            server = null;
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            if (++ticks % 100 == 0) apply();
        });
    }

    /** The key folder: next to the server, not in the world ({@code <server folder>/cccloud/connect}). */
    public static Path keyDir(MinecraftServer server) {
        return server.getServerDirectory().resolve("cccloud").resolve("connect");
    }

    /** The current status (live browser count included). */
    public static ConnectStatus status() {
        var c = client;
        return c != null ? c.status() : idle;
    }

    /** {@code https://<name>.cccloud.dev} while Connect is on and a name is set, else "". */
    public static String url() {
        var s = status();
        return s.state() == ConnectStatus.State.OFF || s.state() == ConnectStatus.State.NO_NAME ? "" : s.url();
    }

    /** The address for links in chat: the Connect address while it is online (it works from anywhere), else {@code other}. */
    public static @Nullable String preferredUrl(@Nullable String other) {
        var s = status();
        return s.online() ? s.url() : other;
    }

    /** Re-reads the config and starts, restarts or stops the tunnel (server thread). */
    public static synchronized void apply() {
        var srv = server;
        if (srv == null) return;
        var settings = ConnectSettings.fromConfig();
        var current = client;
        if (!settings.enabled()) {
            stopClient();
            idle = ConnectStatus.OFF;
            return;
        }
        if (settings.name().isEmpty() || !ConnectNames.valid(settings.name())) {
            stopClient();
            idle = new ConnectStatus(ConnectStatus.State.NO_NAME, "", "", null, true, 0, System.currentTimeMillis(), 0);
            return;
        }
        if (CloudWebServer.internetMode() != ai.ezfox.cccloud.web.internet.InternetMode.CONNECT) {
            // saved but not applied yet (the live config applies it within seconds; or it failed, see the log)
            stopClient();
            idle = new ConnectStatus(ConnectStatus.State.CONNECTING, settings.name(), ConnectClient.expectedUrl(settings, settings.name()), "pending", true, 0,
                System.currentTimeMillis(), 0);
            return;
        }
        if (!CloudWebServer.isRunning()) {
            // nothing to forward to (webEnabled = false or the port failed)
            stopClient();
            idle = new ConnectStatus(ConnectStatus.State.FAILED, settings.name(), ConnectClient.expectedUrl(settings, settings.name()), "web_off", true, 0,
                System.currentTimeMillis(), 0);
            return;
        }
        if (current != null && current.name().equals(settings.name()) && current.settings().relay().equals(settings.relay())) return;
        stopClient();
        start(srv, settings);
    }

    private static void start(MinecraftServer srv, ConnectSettings settings) {
        ConnectKeys keys;
        try {
            keys = ConnectKeys.loadOrCreate(keyDir(srv));
        } catch (IOException e) {
            CloudLog.LOGGER.error("[cccloud] Connect: {}", e.getMessage());
            idle = new ConnectStatus(ConnectStatus.State.FAILED, settings.name(), ConnectClient.expectedUrl(settings, settings.name()), "key_error", true, 0,
                System.currentTimeMillis(), 0);
            settle(idle);
            return;
        }
        var b = bridge;
        if (b == null) {
            b = TunnelBridge.start();
            bridge = b;
        }
        var c = new ConnectClient(settings, settings.name(), keys, version(), BundleId.current(), PublicConfig.storeOrigin(), b::open,
            ConnectService::changed, false);
        client = c;
        CloudLog.LOGGER.info("[cccloud] Connect: connecting {} as {} (server key {})", settings.tunnelUri(settings.name()).getHost(),
            ConnectClient.expectedUrl(settings, settings.name()), keys.fingerprint());
        c.start();
    }

    private static void changed(ConnectStatus status) {
        switch (status.state()) {
            case CONNECTED -> CloudLog.LOGGER.info("[cccloud] Connect: online at {}", status.url());
            case RETRYING -> CloudLog.LOGGER.info("[cccloud] Connect: {} ({}), trying again in {} s", status.url(), status.error(),
                Math.max(1, (status.retryAt() - System.currentTimeMillis() + 999) / 1000));
            case FAILED -> CloudLog.LOGGER.warn("[cccloud] Connect: {} refused ({}); fix it with /cccloud internet connect", status.url(), status.error());
            default -> {
            }
        }
        if (status.state() == ConnectStatus.State.CONNECTED || status.state() == ConnectStatus.State.FAILED
            || status.state() == ConnectStatus.State.RETRYING && status.error() != null && ConnectClient.PERMANENT.contains(status.error())) {
            settle(status);
        }
    }

    private static void settle(ConnectStatus status) {
        List<CompletableFuture<ConnectStatus>> done;
        synchronized (waiting) {
            done = new ArrayList<>(waiting);
            waiting.clear();
        }
        for (var f : done) f.complete(status);
    }

    /** Completes when the tunnel is online or refused, or after {@code seconds} with whatever the status is then. */
    public static CompletableFuture<ConnectStatus> settled(int seconds) {
        var f = new CompletableFuture<ConnectStatus>();
        var now = status();
        if (now.state() == ConnectStatus.State.CONNECTED || now.state() == ConnectStatus.State.FAILED) {
            f.complete(now);
            return f;
        }
        synchronized (waiting) {
            waiting.add(f);
        }
        return f.completeOnTimeout(null, seconds, TimeUnit.SECONDS).thenApply(s -> s != null ? s : status());
    }

    /**
     * Gives the configured name back to the relay and switches Connect off ({@code internetAccess = off}). Works while
     * offline too: a short connection proves the key and releases the name.
     */
    public static CompletableFuture<String> release() {
        var srv = server;
        var settings = ConnectSettings.fromConfig();
        String name = settings.name();
        if (srv == null || name.isEmpty()) return CompletableFuture.completedFuture("no_name");
        var current = client;
        CompletableFuture<String> result;
        if (current != null && current.name().equals(name) && current.status().state() == ConnectStatus.State.CONNECTED) {
            result = current.release();
        } else {
            ConnectKeys keys;
            try {
                keys = ConnectKeys.loadOrCreate(keyDir(srv));
            } catch (IOException e) {
                return CompletableFuture.completedFuture("key_error");
            }
            var once = new ConnectClient(settings, name, keys, version(), BundleId.current(), null, (ip, host, h) -> {
                throw new IllegalStateException("release only");
            }, s -> { }, true);
            once.start();
            result = once.released().completeOnTimeout("timeout", 15, TimeUnit.SECONDS).whenComplete((r, e) -> once.stop());
        }
        return result.thenApply(r -> {
            if (r.equals("released")) {
                srv.execute(() -> {
                    write(CloudConfig.CONNECT_NAME, "");
                    setInternetAccess(ai.ezfox.cccloud.web.internet.InternetMode.OFF.id());
                    save();
                    applyLive();
                    apply();
                });
            }
            return r;
        });
    }

    /** Sets {@code internetAccess = connect} and the name, saves the config and connects. Server thread. */
    public static boolean claim(String name) {
        try {
            write(CloudConfig.CONNECT_NAME, name);
            setInternetAccess(ConnectSettings.MODE);
            save();
            applyLive();
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Could not save the Connect settings to the server config", e);
            return false;
        }
        synchronized (ConnectService.class) {
            stopClient(); // a fresh attempt, also after a refusal
            apply();
        }
        return true;
    }

    private static void write(ModConfigSpec.ConfigValue<String> value, String text) {
        value.set(text);
    }

    static void setInternetAccess(String mode) {
        CloudConfig.INTERNET_ACCESS.set(mode);
    }

    /** The live config switches the dashboard server's mode now (as /cccloud internet does). */
    private static void applyLive() {
        var srv = server;
        if (srv == null) return;
        var report = ai.ezfox.cccloud.config.ConfigReload.apply(srv, "internet");
        for (String error : report.errors()) CloudLog.LOGGER.error("[cccloud] Connect: {}", error);
    }

    private static void save() {
        CloudConfig.SPEC.save();
    }

    private static void stopClient() {
        var c = client;
        client = null;
        if (c != null) c.stop();
    }

    private static void stopAll() {
        stopClient();
        var b = bridge;
        bridge = null;
        if (b != null) b.stop();
        idle = ConnectStatus.OFF;
    }

    static String version() {
        return net.neoforged.fml.ModList.get().getModContainerById(CCCloud.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("dev");
    }
}
