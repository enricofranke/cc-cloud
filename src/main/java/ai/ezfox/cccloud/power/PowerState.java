package ai.ezfox.cccloud.power;

/**
 * Mainframe power logic (docs/SPEC.md "Power (v0.3)"), pure Java and unit tested: each tick the usage is drawn from the
 * buffer; when the buffer cannot cover it, the mainframe keeps running for {@link #GRACE_TICKS} (5 s, no flicker on
 * short brownouts) and then reports {@code no_power}. As soon as the buffer covers the usage again it is powered.
 */
public final class PowerState {
    public static final int GRACE_TICKS = 100;

    /** Removes energy from the buffer. */
    @FunctionalInterface
    public interface Drain {
        void drain(long amount);
    }

    private int outageTicks;
    private boolean powered = true;

    /**
     * @param stored  energy in the buffer
     * @param usage   FE this tick
     * @param require config {@code requirePower}
     * @return whether the mainframe has power this tick
     */
    public boolean tick(long stored, long usage, boolean require, Drain drain) {
        if (!require || usage <= 0) {
            outageTicks = 0;
            return powered = true;
        }
        if (stored >= usage) {
            drain.drain(usage);
            outageTicks = 0;
            return powered = true;
        }
        if (stored > 0) drain.drain(stored);
        outageTicks++;
        return powered = outageTicks <= GRACE_TICKS;
    }

    public boolean powered() {
        return powered;
    }

    /** Ticks since the buffer last covered the usage (0 = fine). */
    public int outageTicks() {
        return outageTicks;
    }

    /** FE per tick: base + per loaded linked uplink + per connected dashboard client. */
    public static long usage(long base, long perUplink, long perClient, int uplinks, int clients) {
        return Math.max(0, base) + Math.max(0, perUplink) * Math.max(0, uplinks) + Math.max(0, perClient) * Math.max(0, clients);
    }
}
