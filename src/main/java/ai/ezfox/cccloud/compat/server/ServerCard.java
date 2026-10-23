package ai.ezfox.cccloud.compat.server;

import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The Server card (docs/SPEC.md v0.5 B) on every mainframe, for canControl devices: TPS and MSPT from the server's
 * own tick times (per dimension through NeoForge), players online, uptime, memory and a 60-minute MSPT chart. With
 * spark installed it also shows spark's TPS averages and process CPU ({@link Extra}).
 */
public final class ServerCard extends MainframeCard {
    public static final String KIND = "server";
    public static final String TPS = "tps";
    public static final String MSPT = "mspt";
    public static final String HISTORY = "mspt_history";
    public static final String PLAYERS = "players";
    public static final String UPTIME = "uptime";
    public static final String MEMORY = "memory";
    public static final String DIMENSIONS = "dimensions";

    /** Extra statistics from a profiler mod (spark). */
    public interface Extra {
        void widgets(CardWidgets w);

        void refresh(CardOutput out);
    }

    private static volatile @Nullable Supplier<Extra> extraFactory;

    private final MinecraftServer server;
    private final @Nullable Extra extra;

    public ServerCard(MinecraftServer server) {
        this.server = server;
        var factory = extraFactory;
        Extra e = null;
        if (factory != null) {
            try {
                e = factory.get();
            } catch (LinkageError | RuntimeException ignored) {
                e = null;
            }
        }
        this.extra = e;
    }

    /** Installs extra statistics (spark), see compat/spark. */
    public static void setExtra(@Nullable Supplier<Extra> factory) {
        extraFactory = factory;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Server";
    }

    @Override
    public List<Widget> widgets() {
        long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        var w = new CardWidgets()
            .gauge(TPS, "TPS", 0, 20, null, "color", "green", "icon", "cpu")
            .gauge(MSPT, "MSPT", 0, 100, "ms", "warn", 40, "crit", 50)
            .history(HISTORY, "MSPT (60 min)", null, null, "ms")
            .label(PLAYERS, "Players online")
            .label(UPTIME, "Uptime")
            .progress(MEMORY, "Memory", 0, Math.max(1, maxMb), "MB")
            .label(DIMENSIONS, "Tick time per dimension", "span", 2);
        if (extra != null) extra.widgets(w);
        return w.build();
    }

    /** Average of a tick time sample array in milliseconds (0 if empty). */
    static double averageMs(long @Nullable [] nanos) {
        if (nanos == null || nanos.length == 0) return 0;
        long sum = 0;
        int n = 0;
        for (long t : nanos) {
            if (t <= 0) continue;
            sum += t;
            n++;
        }
        return n == 0 ? 0 : sum / (double) n / 1_000_000.0;
    }

    /** TPS from the average tick time, capped at the target tick rate. */
    static double tps(double msptMs, double tickRate) {
        if (msptMs <= 0) return tickRate;
        return Math.min(tickRate, 1000.0 / msptMs);
    }

    @Override
    public void refresh(CardOutput out) {
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        double rate = server.tickRateManager().tickrate();
        out.number(TPS, Math.round(tps(mspt, rate) * 10) / 10.0);
        out.number(MSPT, Math.round(mspt * 10) / 10.0);
        out.number(HISTORY, Math.round(mspt * 10) / 10.0);
        out.text(PLAYERS, server.getPlayerCount() + " / " + server.getMaxPlayers());
        out.text(UPTIME, Fmt.duration(ManagementFactory.getRuntimeMXBean().getUptime() / 1000));
        var runtime = Runtime.getRuntime();
        out.number(MEMORY, (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        var parts = new ArrayList<String>();
        for (var level : server.getAllLevels()) {
            var key = level.dimension();
            double ms = averageMs(server.getTickTime(key));
            parts.add(key.identifier().getPath() + " " + String.format(Locale.ROOT, "%.1f ms", ms));
        }
        out.text(DIMENSIONS, String.join(" · ", parts));
        if (extra != null) extra.refresh(out);
    }
}
