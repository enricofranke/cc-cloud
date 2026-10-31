package ai.ezfox.cccloud.bridge;

/**
 * Finds how many of a craft the current items allow, by bisecting simulated amounts: {@code low} was simulated without
 * anything missing (or is 0), {@code high} was simulated with something missing. Bounded by {@link #MAX_STEPS}, so the
 * answer is a tested lower bound when it did not converge. Pure Java, unit tested.
 */
final class MaxSearch {
    /** Simulated plans per search; 8 settle every amount up to 256. */
    static final int MAX_STEPS = 8;

    private long low;
    private long high;
    private int steps;

    /** @param high an amount that was simulated with something missing */
    MaxSearch(long high) {
        this.high = high;
    }

    /** The next amount to simulate, or -1 when the search is over. */
    long next() {
        if (high - low <= 1 || steps >= MAX_STEPS) return -1;
        steps++;
        return low + (high - low) / 2;
    }

    void result(long amount, boolean possible) {
        if (possible) low = amount;
        else high = amount;
    }

    long best() {
        return low;
    }

    /** {@link #best()} is the real maximum, not only an amount known to work. */
    boolean exact() {
        return high - low <= 1;
    }
}
