package ai.ezfox.cccloud.compat.card;

import java.util.Locale;

/** Number formatting for card labels (plain English, like the other templates). Pure Java, unit tested. */
public final class Fmt {
    private static final String[] SUFFIX = {"", " k", " M", " G", " T", " P", " E"};

    private Fmt() {
    }

    /** 1234567 → "1.23 M". */
    public static String si(double value) {
        if (!Double.isFinite(value)) return "–";
        double abs = Math.abs(value);
        int i = 0;
        while (abs >= 1000 && i < SUFFIX.length - 1) {
            abs /= 1000;
            i++;
        }
        String number = i == 0 ? (abs == Math.rint(abs) ? String.valueOf((long) abs) : String.format(Locale.ROOT, "%.1f", abs))
            : abs >= 100 ? String.format(Locale.ROOT, "%.0f", abs) : abs >= 10 ? String.format(Locale.ROOT, "%.1f", abs) : String.format(Locale.ROOT, "%.2f", abs);
        return (value < 0 ? "-" : "") + number + SUFFIX[i];
    }

    /** "1.23 M / 5.00 M FE". */
    public static String energy(long stored, long capacity) {
        return si(stored) + " / " + si(capacity) + " FE";
    }

    /** "+120 FE/t", "-3.5 k FE/t", "0 FE/t". */
    public static String rate(double perTick) {
        if (!Double.isFinite(perTick)) return "–";
        if (Math.abs(perTick) < 0.05) return "0 FE/t";
        return (perTick > 0 ? "+" : "") + si(perTick) + " FE/t";
    }

    /** Unsigned rate: "120 FE/t". */
    public static String flow(double perTick) {
        return Double.isFinite(perTick) ? si(Math.abs(perTick)) + " FE/t" : "–";
    }

    /** stored / capacity in percent (0..100), 0 when the capacity is 0. */
    public static double percent(double stored, double capacity) {
        if (capacity <= 0 || !Double.isFinite(stored) || !Double.isFinite(capacity)) return 0;
        return Math.round(Math.max(0, Math.min(100, stored * 100 / capacity)) * 10) / 10.0;
    }

    /** Seconds as "3 d 4 h", "2 h 5 min", "4 min 12 s", "12 s". */
    public static String duration(long seconds) {
        if (seconds < 0) return "–";
        long d = seconds / 86_400, h = seconds / 3600 % 24, m = seconds / 60 % 60, s = seconds % 60;
        if (d > 0) return d + " d " + h + " h";
        if (h > 0) return h + " h " + m + " min";
        if (m > 0) return m + " min " + s + " s";
        return s + " s";
    }

    /** "energy_cell_basic" → "Energy Cell Basic" (dedicated servers have no translations of other mods). */
    public static String title(String registryPath) {
        var sb = new StringBuilder();
        for (String part : registryPath.split("[_/]")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String s = sb.toString();
        return s.length() > ai.ezfox.cccloud.uplink.Limits.TITLE ? s.substring(0, ai.ezfox.cccloud.uplink.Limits.TITLE) : s;
    }
}
