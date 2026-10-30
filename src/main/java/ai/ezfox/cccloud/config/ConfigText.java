package ai.ezfox.cccloud.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** How config values are compared and shown (chat, console, log). Pure; secrets are never printed. */
public final class ConfigText {
    /** Key names (last path segment) whose values are secret or point at one: shown only as set / not set. */
    private static final Pattern SECRET = Pattern.compile("(?i).*(secret|token|password|passwd|privatekey|keypath|credential).*");
    /** {@code scheme://user:pass@host}: the user info is masked wherever a value is shown. */
    private static final Pattern USER_INFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)[^/@\\s]+@");
    /** Longest value text in chat (lists of many hosts). */
    static final int MAX_VALUE = 200;

    /** One value for {@code /cccloud config show}. */
    public record Entry(String key, String file, @Nullable Object value, @Nullable Object defaultValue, Reload reload) {
    }

    private ConfigText() {
    }

    public static boolean secret(String key) {
        int dot = key.lastIndexOf('.');
        return SECRET.matcher(dot < 0 ? key : key.substring(dot + 1)).matches();
    }

    /** The value as shown to admins: secrets as "(set, hidden)" / "(not set)", user info in URLs masked, long values cut. */
    public static String display(String key, @Nullable Object value) {
        if (secret(key)) return isEmpty(value) ? "(not set)" : "(set, hidden)";
        String text;
        if (value instanceof Collection<?> list) {
            var parts = new ArrayList<String>();
            for (var item : list) parts.add(quote(item));
            text = "[" + String.join(", ", parts) + "]";
        } else {
            text = quote(value);
        }
        text = USER_INFO.matcher(text).replaceAll("$1***@");
        return text.length() > MAX_VALUE ? text.substring(0, MAX_VALUE - 1) + "…" : text;
    }

    private static boolean isEmpty(@Nullable Object value) {
        return value == null || (value instanceof String s && s.isBlank()) || (value instanceof Collection<?> c && c.isEmpty());
    }

    private static String quote(@Nullable Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s.replace("\n", " ") + "\"";
        return String.valueOf(value);
    }

    /** Keys whose value differs between two snapshots, in the order of {@code after}. */
    public static List<String> changes(Map<String, ?> before, Map<String, ?> after) {
        var changed = new ArrayList<String>();
        for (var e : after.entrySet()) {
            if (!same(before.get(e.getKey()), e.getValue())) changed.add(e.getKey());
        }
        for (var key : before.keySet()) {
            if (!after.containsKey(key)) changed.add(key);
        }
        return changed;
    }

    private static boolean same(@Nullable Object a, @Nullable Object b) {
        if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
        return Objects.equals(a, b);
    }

    /**
     * The lines of {@code /cccloud config show [key]}: every value (or those whose key contains the filter, or the one
     * key that matches exactly, also by its name without the section), with its default and whether it applies live.
     */
    public static List<String> show(List<Entry> entries, @Nullable String filter) {
        var lines = new ArrayList<String>();
        var matches = new ArrayList<Entry>();
        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        for (var e : entries) {
            String key = e.key().toLowerCase(Locale.ROOT);
            String name = key.substring(key.lastIndexOf('.') + 1);
            if (!f.isEmpty() && (key.equals(f) || name.equals(f))) {
                matches.clear();
                matches.add(e);
                break;
            }
            if (f.isEmpty() || key.contains(f)) matches.add(e);
        }
        if (matches.isEmpty()) {
            lines.add("No config value matches '" + filter + "'.");
            return lines;
        }
        String file = null;
        for (var e : matches) {
            if (!e.file().equals(file)) {
                file = e.file();
                lines.add("== " + file + " ==");
            }
            var line = new StringBuilder(e.key()).append(" = ").append(display(e.key(), e.value()));
            if (!secret(e.key()) && !same(e.value(), e.defaultValue())) line.append("  (default ").append(display(e.key(), e.defaultValue())).append(")");
            line.append(e.reload() == Reload.LIVE ? "  [live]" : "  [restart]");
            lines.add(line.toString());
        }
        return lines;
    }
}
