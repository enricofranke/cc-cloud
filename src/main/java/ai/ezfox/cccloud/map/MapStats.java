package ai.ezfox.cccloud.map;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters for the map tiles and the voxel view, shown by {@code /cccloud map stats} (server admins): requests, items
 * (tiles / voxel sections), cache hits, time on the server thread and on background threads, bytes sent and 429s. Used
 * to measure loading and the tick impact (server-thread time per tick since the last reset). Cheap: atomic adds only.
 */
public final class MapStats {
    /** One endpoint's counters. */
    public static final class Counters {
        public final LongAdder requests = new LongAdder();
        /** tiles / sections asked for */
        public final LongAdder items = new LongAdder();
        /** answered from a cache (memory, disk or unchanged on the client) */
        public final LongAdder hits = new LongAdder();
        /** rendered / encoded */
        public final LongAdder misses = new LongAdder();
        /** nothing to send (204, chunk not loaded) */
        public final LongAdder empty = new LongAdder();
        public final LongAdder rateLimited = new LongAdder();
        /** wall-clock time on the server thread */
        public final LongAdder mainNanos = new LongAdder();
        /** CPU time of the server thread (not inflated when the machine is busy) */
        public final LongAdder mainCpuNanos = new LongAdder();
        public final LongAdder workerNanos = new LongAdder();
        public final LongAdder workerCpuNanos = new LongAdder();
        public final LongAdder bytes = new LongAdder();

        void reset() {
            for (var a : new LongAdder[]{requests, items, hits, misses, empty, rateLimited, mainNanos, mainCpuNanos, workerNanos, workerCpuNanos, bytes}) a.reset();
        }

        /** Adds the time since {@link #start()} as server-thread time. */
        public void main(long[] start) {
            mainNanos.add(System.nanoTime() - start[0]);
            mainCpuNanos.add(cpu() - start[1]);
        }

        /** Adds the time since {@link #start()} as background time. */
        public void worker(long[] start) {
            workerNanos.add(System.nanoTime() - start[0]);
            workerCpuNanos.add(cpu() - start[1]);
        }

        String line(String name, long ticks) {
            long n = Math.max(1, items.sum());
            double mainMs = mainNanos.sum() / 1e6, cpuMs = mainCpuNanos.sum() / 1e6;
            return String.format(Locale.ROOT,
                "%s: %d requests, %d items (%d cached, %d built, %d empty), %d rate-limited; server thread %.1f ms wall / %.1f ms CPU "
                    + "(%.3f ms CPU/item, %.4f ms CPU/tick over %d ticks), background %.1f ms wall / %.1f ms CPU, %.1f KB (%.0f B/item)",
                name, requests.sum(), items.sum(), hits.sum(), misses.sum(), empty.sum(), rateLimited.sum(), mainMs, cpuMs, cpuMs / n,
                ticks > 0 ? cpuMs / ticks : 0.0, ticks, workerNanos.sum() / 1e6, workerCpuNanos.sum() / 1e6, bytes.sum() / 1024.0, bytes.sum() / (double) n);
        }
    }

    private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();

    /** CPU time of the current thread in ns (wall clock where the JVM cannot measure it). */
    static long cpu() {
        return THREADS.isCurrentThreadCpuTimeSupported() ? THREADS.getCurrentThreadCpuTime() : System.nanoTime();
    }

    /** A start mark for {@link Counters#main} / {@link Counters#worker}. */
    public static long[] start() {
        return new long[]{System.nanoTime(), cpu()};
    }

    public static final Counters TILES = new Counters();
    public static final Counters VOXELS = new Counters();
    private static volatile int resetTick;

    private MapStats() {
    }

    public static void reset(int tick) {
        TILES.reset();
        VOXELS.reset();
        resetTick = tick;
    }

    /** Two lines (tiles, voxels) for the admin command. */
    public static String[] report(int tick) {
        long ticks = Math.max(0, tick - resetTick);
        return new String[]{TILES.line("Map tiles", ticks), VOXELS.line("Voxels", ticks)};
    }
}
