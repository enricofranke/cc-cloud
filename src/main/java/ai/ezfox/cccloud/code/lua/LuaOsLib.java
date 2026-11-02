package ai.ezfox.cccloud.code.lua;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaInteger;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.LibFunction;

/**
 * The harmless part of Lua's {@code os} library: {@code time}, {@code date}, {@code clock} and {@code difftime}
 * (standard Lua semantics, in the server's time zone; {@code clock} is the seconds since the script started).
 * Nothing that touches files, processes or the environment exists here.
 */
final class LuaOsLib {
    private LuaOsLib() {
    }

    static LuaTable create(LuaState state, ZoneId zone, DoubleSupplier clock) {
        var os = new LuaTable();
        os.rawset("time", LibFunction.createV((s, args) -> time(args, zone)));
        os.rawset("date", LibFunction.createV((s, args) -> date(args, zone)));
        os.rawset("clock", LibFunction.createV((s, args) -> ValueFactory.valueOf(clock.getAsDouble())));
        os.rawset("difftime", LibFunction.createV((s, args) -> ValueFactory.valueOf(args.arg(1).checkDouble() - args.arg(2).optDouble(0))));
        return os;
    }

    private static Varargs time(Varargs args, ZoneId zone) throws LuaError {
        if (args.first().isNil()) return LuaInteger.valueOf(System.currentTimeMillis() / 1000);
        var t = args.first().checkTable();
        int year = field(t, "year", -1), month = field(t, "month", -1), day = field(t, "day", -1);
        if (year < 0 || month < 0 || day < 0) throw new LuaError("field 'day', 'month' and 'year' are needed");
        int hour = field(t, "hour", 12), min = field(t, "min", 0), sec = field(t, "sec", 0);
        // Lua normalises out-of-range fields (month 13, day 0): so does LocalDateTime arithmetic
        var dt = LocalDateTime.of(year, 1, 1, 0, 0).plusMonths(month - 1L).plusDays(day - 1L).plusHours(hour).plusMinutes(min).plusSeconds(sec);
        return LuaInteger.valueOf(dt.atZone(zone).toEpochSecond());
    }

    private static int field(LuaTable t, String key, int fallback) throws LuaError {
        LuaValue v = t.rawget(key);
        if (v.isNil()) return fallback;
        double d = v.checkDouble();
        if (d != Math.rint(d) || Math.abs(d) > 1e9) throw new LuaError("field '" + key + "' is not an integer");
        return (int) d;
    }

    private static Varargs date(Varargs args, ZoneId zone) throws LuaError {
        String format = args.first().isNil() ? "%c" : args.first().checkLuaString().toString();
        long seconds = args.arg(2).isNil() ? System.currentTimeMillis() / 1000 : args.arg(2).checkLong();
        ZoneId z = zone;
        if (format.startsWith("!")) {
            z = ZoneOffset.UTC;
            format = format.substring(1);
        }
        ZonedDateTime t;
        try {
            t = Instant.ofEpochSecond(seconds).atZone(z);
        } catch (java.time.DateTimeException e) {
            throw new LuaError("time out of range");
        }
        if (format.startsWith("*t")) {
            var table = new LuaTable(0, 9);
            table.rawset("year", ValueFactory.valueOf(t.getYear()));
            table.rawset("month", ValueFactory.valueOf(t.getMonthValue()));
            table.rawset("day", ValueFactory.valueOf(t.getDayOfMonth()));
            table.rawset("hour", ValueFactory.valueOf(t.getHour()));
            table.rawset("min", ValueFactory.valueOf(t.getMinute()));
            table.rawset("sec", ValueFactory.valueOf(t.getSecond()));
            table.rawset("wday", ValueFactory.valueOf(t.getDayOfWeek().getValue() % 7 + 1));
            table.rawset("yday", ValueFactory.valueOf(t.getDayOfYear()));
            table.rawset("isdst", ValueFactory.valueOf(z.getRules().isDaylightSavings(t.toInstant())));
            return table;
        }
        if (format.length() > 256) throw new LuaError("bad argument #1 to 'date' (format too long)");
        var out = new StringBuilder();
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c != '%') {
                out.append(c);
                continue;
            }
            if (++i >= format.length()) throw new LuaError("bad argument #1 to 'date' (invalid conversion specifier '%')");
            char f = format.charAt(i);
            switch (f) {
                case 'a' -> out.append(t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ROOT));
                case 'A' -> out.append(t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ROOT));
                case 'b', 'h' -> out.append(t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ROOT));
                case 'B' -> out.append(t.getMonth().getDisplayName(TextStyle.FULL, Locale.ROOT));
                case 'c' -> out.append(String.format(Locale.ROOT, "%s %s %2d %02d:%02d:%02d %d", t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ROOT),
                    t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ROOT), t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getYear()));
                case 'd' -> out.append(two(t.getDayOfMonth()));
                case 'D' -> out.append(two(t.getMonthValue())).append('/').append(two(t.getDayOfMonth())).append('/').append(two(t.getYear() % 100));
                case 'e' -> out.append(String.format(Locale.ROOT, "%2d", t.getDayOfMonth()));
                case 'F' -> out.append(t.getYear()).append('-').append(two(t.getMonthValue())).append('-').append(two(t.getDayOfMonth()));
                case 'H' -> out.append(two(t.getHour()));
                case 'I' -> out.append(two((t.getHour() + 11) % 12 + 1));
                case 'j' -> out.append(String.format(Locale.ROOT, "%03d", t.getDayOfYear()));
                case 'm' -> out.append(two(t.getMonthValue()));
                case 'M' -> out.append(two(t.getMinute()));
                case 'p' -> out.append(t.getHour() < 12 ? "AM" : "PM");
                case 'S' -> out.append(two(t.getSecond()));
                case 'T', 'X' -> out.append(two(t.getHour())).append(':').append(two(t.getMinute())).append(':').append(two(t.getSecond()));
                case 'R' -> out.append(two(t.getHour())).append(':').append(two(t.getMinute()));
                case 'u' -> out.append(t.getDayOfWeek().getValue());
                case 'w' -> out.append(t.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : t.getDayOfWeek().getValue());
                case 'x' -> out.append(two(t.getMonthValue())).append('/').append(two(t.getDayOfMonth())).append('/').append(two(t.getYear() % 100));
                case 'y' -> out.append(two(t.getYear() % 100));
                case 'Y' -> out.append(t.getYear());
                case 'z' -> out.append(t.getOffset().getId().replace(":", "").replace("Z", "+0000"));
                case 'Z' -> out.append(t.getZone().getDisplayName(TextStyle.SHORT, Locale.ROOT));
                case '%' -> out.append('%');
                default -> throw new LuaError("bad argument #1 to 'date' (invalid conversion specifier '%" + f + "')");
            }
        }
        return LuaConvert.str(out.toString());
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }
}
