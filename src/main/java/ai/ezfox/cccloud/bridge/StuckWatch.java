package ai.ezfox.cccloud.bridge;

import org.jspecify.annotations.Nullable;

/**
 * Notices a crafting job that stopped moving (docs/SPEC.md "Stuck crafts"): stuck after {@code limit} ticks without
 * forward progress, back to normal as soon as it moves again, and reported once per job however often it stalls. Pure
 * Java, unit tested; works in ticks, so an unloaded bridge or a paused server does not count as a stall.
 */
final class StuckWatch {
    private double progress;
    private long since;
    private boolean stuck;
    private boolean reported;

    StuckWatch(long tick) {
        since = tick;
    }

    /**
     * @param limit ticks without progress until the job counts as stuck, 0 = never
     * @return true the one time the job should be reported
     */
    boolean update(long tick, double progress, long limit) {
        if (progress > this.progress + 1e-9) {
            this.progress = progress;
            since = tick;
            stuck = false;
            return false;
        }
        stuck = limit > 0 && tick - since >= limit;
        if (!stuck || reported) return false;
        reported = true;
        return true;
    }

    /** The job left planning and started: its clock begins now. */
    void started(long tick) {
        since = tick;
        stuck = false;
    }

    boolean stuck() {
        return stuck;
    }

    /** Plain words for an alert; the dashboard has its own, see {@code stuckText} in the job widgets. */
    static String text(@Nullable String reason) {
        return switch (reason == null ? "" : reason) {
            case "not_enough_items" -> "not enough items";
            case "no_cpu" -> "no free crafting CPU";
            case "no_machine" -> "no machine found";
            case "machine_rejected" -> "the machine does not accept the items";
            case "machine_locked" -> "the machine is locked";
            case "suspended" -> "suspended in the crafting status";
            case "offline" -> "the network is offline";
            case "planning" -> "still planning";
            default -> "no progress";
        };
    }
}
