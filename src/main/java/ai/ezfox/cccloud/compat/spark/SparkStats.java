package ai.ezfox.cccloud.compat.spark;

import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.server.ServerCard;
import java.util.Locale;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow;
import org.jspecify.annotations.Nullable;

/**
 * spark's public API (me.lucko.spark.api) on the Server card: TPS averages (10 s / 1 m / 5 m / 15 m), MSPT
 * mean/max over the last minute and the server process's CPU usage. spark has no per-dimension data in its API; the
 * per-dimension tick times come from NeoForge either way. Only loaded when spark is present.
 */
public final class SparkStats implements ServerCard.Extra {
    public static final String TPS_AVG = "spark_tps";
    public static final String MSPT_MINUTE = "spark_mspt";
    public static final String CPU = "spark_cpu";

    private SparkStats() {
    }

    public static void init() {
        ServerCard.setExtra(SparkStats::new);
    }

    /** spark sets its provider when the server is about to start; until then (or after stop) it throws. */
    private static @Nullable Spark spark() {
        try {
            return SparkProvider.get();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    @Override
    public void widgets(CardWidgets w) {
        w.label(TPS_AVG, "TPS 10s / 1m / 5m / 15m (spark)", "span", 2)
            .label(MSPT_MINUTE, "MSPT last minute (spark)")
            .percent(CPU, "Process CPU (spark)", "warn", 80, "crit", 95);
    }

    @Override
    public void refresh(DeviceCard.CardOutput out) {
        var spark = spark();
        if (spark == null) {
            out.text(TPS_AVG, "spark is not ready");
            return;
        }
        var tps = spark.tps();
        if (tps != null) {
            out.text(TPS_AVG, String.format(Locale.ROOT, "%.1f / %.1f / %.1f / %.1f", tps.poll(StatisticWindow.TicksPerSecond.SECONDS_10),
                tps.poll(StatisticWindow.TicksPerSecond.MINUTES_1), tps.poll(StatisticWindow.TicksPerSecond.MINUTES_5),
                tps.poll(StatisticWindow.TicksPerSecond.MINUTES_15)));
        }
        var mspt = spark.mspt();
        if (mspt != null) {
            var minute = mspt.poll(StatisticWindow.MillisPerTick.MINUTES_1);
            out.text(MSPT_MINUTE, String.format(Locale.ROOT, "%.1f ms avg, %.1f ms max", minute.mean(), minute.max()));
        }
        out.number(CPU, Math.round(spark.cpuProcess().poll(StatisticWindow.CpuUsage.SECONDS_10) * 1000) / 10.0);
    }
}
