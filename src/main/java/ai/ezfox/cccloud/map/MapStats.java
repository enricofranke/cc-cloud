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
        public final LongAdder mainNanos = new LongAdder();
        public final LongAdder workerNanos = new LongAdder();
        public final LongAdder bytes = new LongAdder();

        void reset() {
            for (var a : new LongAdder[]{requests, items, hits, misses, empty, rateLimited, mainNanos, workerNanos, bytes}) a.reset();
        }

        String line(String name, long ticks) {
            long n = Math.max(1, items.sum());
            double mainMs = mainNanos.sum() / 1e6;
            return String.format(Locale.ROOT,
                "%s: %d requests, %d items (%d cached, %d built, %d empty), %d rate-limited; server thread %.1f ms (%.3f ms/item, %.3f ms/tick over %d ticks), background %.1f ms, %.1f KB (%.0f B/item)",
                name, requests.sum(), items.sum(), hits.sum(), misses.sum(), empty.sum(), rateLimited.sum(), mainMs, mainMs / n,
                ticks > 0 ? mainMs / ticks : 0.0, ticks, workerNanos.sum() / 1e6, bytes.sum() / 1024.0, bytes.sum() / (double) n);
        }
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
