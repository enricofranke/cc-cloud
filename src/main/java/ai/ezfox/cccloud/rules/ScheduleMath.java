package ai.ezfox.cccloud.rules;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Occurrences of a weekly {@link Rule.ScheduleTrigger} (v0.10 schedules): days of the week + a wall-clock time in a
 * time zone. Daylight saving: a time that does not exist on a day (spring forward) moves forward by the gap, a time
 * that exists twice (fall back) counts once, at its first instant. Pure Java, unit tested.
 */
public final class ScheduleMath {
    private ScheduleMath() {
    }

    /** The latest occurrence at or before {@code now} (ms), or {@link Long#MIN_VALUE} if none in the last 8 days. */
    public static long last(Rule.ScheduleTrigger t, long now, ZoneId zone) {
        var today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int back = 0; back <= 7; back++) {
            var day = today.minusDays(back);
            if (!on(t, day.getDayOfWeek().getValue())) continue;
            long at = at(t, day, zone);
            if (at <= now) return at;
        }
        return Long.MIN_VALUE;
    }

    /** The first occurrence strictly after {@code now} (ms). */
    public static long next(Rule.ScheduleTrigger t, long now, ZoneId zone) {
        var today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int ahead = 0; ahead <= 8; ahead++) {
            var day = today.plusDays(ahead);
            if (!on(t, day.getDayOfWeek().getValue())) continue;
            long at = at(t, day, zone);
            if (at > now) return at;
        }
        return Long.MAX_VALUE; // no day set (the codec refuses that)
    }

    /** @param isoDay 1 = Monday … 7 = Sunday */
    private static boolean on(Rule.ScheduleTrigger t, int isoDay) {
        return (t.days() & 1 << (isoDay - 1)) != 0;
    }

    private static long at(Rule.ScheduleTrigger t, java.time.LocalDate day, ZoneId zone) {
        return ZonedDateTime.of(day, LocalTime.of(t.minute() / 60, t.minute() % 60), zone).toInstant().toEpochMilli();
    }
}
