package ai.ezfox.cccloud.network;

/**
 * Optional measurement of the server-thread time spent in cccloud block entity ticks (used by the load test).
 * Disabled by default; then it costs a single boolean check per tick.
 */
public final class TickCost {
    public static volatile boolean enabled;
    private static long nanos;

    private TickCost() {
    }

    public static long start() {
        return enabled ? System.nanoTime() : 0;
    }

    public static void end(long start) {
        if (start != 0) nanos += System.nanoTime() - start;
    }

    /** Returns and resets the accumulated nanoseconds (server thread only). */
    public static long take() {
        long value = nanos;
        nanos = 0;
        return value;
    }
}
