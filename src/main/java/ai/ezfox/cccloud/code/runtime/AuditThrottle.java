package ai.ezfox.cccloud.code.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a busy script from flooding the mainframe's audit history (it keeps 50 entries): the first {@code perMinute}
 * actions of a minute are recorded one by one, the rest as one line ({@code 37 more actions in the last minute}) when
 * the next minute starts or the script ends.
 */
final class AuditThrottle {
    private final int perMinute;
    private final LongSupplier clock;
    private long minuteStart;
    private int count;
    private int suppressed;
    private @Nullable String lastSuppressed;

    AuditThrottle(int perMinute, LongSupplier clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    /** The audit lines to write for this action (none, the action, and/or the summary of the minute before). */
    synchronized List<String> record(String what) {
        var out = new ArrayList<String>(2);
        long now = clock.getAsLong();
        if (now - minuteStart >= 60_000) {
            String summary = summary();
            if (summary != null) out.add(summary);
            minuteStart = now;
            count = 0;
        }
        if (count < perMinute) {
            count++;
            out.add(what);
        } else {
            suppressed++;
            lastSuppressed = what;
        }
        return out;
    }

    /** The pending summary, if any (when the script ends). */
    synchronized @Nullable String flush() {
        return summary();
    }

    private @Nullable String summary() {
        if (suppressed == 0) return null;
        String s = suppressed == 1 ? lastSuppressed : suppressed + " more actions in the last minute (last: " + lastSuppressed + ")";
        suppressed = 0;
        lastSuppressed = null;
        return s;
    }
}
