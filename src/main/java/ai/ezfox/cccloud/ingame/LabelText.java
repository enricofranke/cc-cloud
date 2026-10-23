package ai.ezfox.cccloud.ingame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The web dashboard's rule for label values (cloud/web/src/lib/format.ts {@code splitEntries}, Widgets.tsx
 * {@code LabelRow}): multi-entry values like "overworld 12.4 ms · the_nether 3.1 ms" are split into name/value pairs
 * (separators " · ", ";", newlines; entries in ms also get the TPS they allow), and long or multi-entry values get
 * their own row with the caption above. Pure, shared by the tablet and unit tests.
 */
public final class LabelText {
    /** Values longer than this are stacked under the caption (same as the web). */
    public static final int LONG = 32;

    private static final Pattern SEPARATORS = Pattern.compile("\\s+·\\s+|\\s*;\\s*|\\n+");
    private static final Pattern COLON = Pattern.compile("^([^:]*\\S):\\s*(.+)$");
    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*\\S)\\s+(-?[\\d.,]+\\s*[a-zA-Z%/]*)$");
    private static final Pattern MS = Pattern.compile("^([\\d.]+)\\s*ms$");

    public record Entry(String name, String value, @Nullable String tps) {
    }

    private LabelText() {
    }

    /** Name/value pairs, or an empty list if the text has fewer than two entries. */
    public static List<Entry> split(String text) {
        var parts = new ArrayList<String>();
        for (var p : SEPARATORS.split(text)) if (!p.isBlank()) parts.add(p.trim());
        var out = new ArrayList<Entry>();
        if (parts.size() < 2) return out;
        for (var p : parts) {
            var m = COLON.matcher(p);
            if (!m.matches()) m = TRAILING_NUMBER.matcher(p);
            if (!m.matches()) {
                out.add(new Entry(p, "", null));
                continue;
            }
            String tps = null;
            var ms = MS.matcher(m.group(2));
            if (ms.matches()) {
                try {
                    tps = String.format(Locale.ROOT, "%.1f", Math.min(20, 1000 / Math.max(0.001, Double.parseDouble(ms.group(1)))));
                } catch (NumberFormatException ignored) {
                    // not a number after all
                }
            }
            out.add(new Entry(m.group(1), m.group(2), tps));
        }
        return out;
    }

    /** Whether a labelled value gets its own row below the caption. */
    public static boolean stacked(String label, String text) {
        return !label.isEmpty() && !text.isEmpty() && (text.length() > LONG || split(text).size() > 1);
    }
}
