package ai.ezfox.cccloud.network;

/**
 * Optional measurement of the server-thread time cccloud spends per tick, by part (used by the load test). Disabled by
 * default; then it costs a single boolean check per call.
 */
public final class TickCost {
    public static final int MAINFRAME = 0, UPLINK = 1, OTHER_BE = 2, GLOBAL = 3;
    public static final String[] NAMES = {"mainframe", "uplink", "other block entities", "global tick handlers"};
    public static volatile boolean enabled;
    private static final long[] NANOS = new long[NAMES.length];

    private TickCost() {
    }

    public static long start() {
        return enabled ? System.nanoTime() : 0;
    }

    /** Adds the time since {@code start} to the uplink part (kept for older call sites). */
    public static void end(long start) {
        end(UPLINK, start);
    }

    public static void end(int part, long start) {
        if (start != 0) NANOS[part] += System.nanoTime() - start;
    }

    /** Returns and resets the accumulated nanoseconds of all parts (server thread only). */
    public static long take() {
        long sum = 0;
        for (int i = 0; i < NANOS.length; i++) {
            sum += NANOS[i];
            NANOS[i] = 0;
        }
        return sum;
    }

    /** Returns and resets the accumulated nanoseconds per part. */
    public static long[] takeParts() {
        var copy = NANOS.clone();
        java.util.Arrays.fill(NANOS, 0);
        return copy;
    }
}
