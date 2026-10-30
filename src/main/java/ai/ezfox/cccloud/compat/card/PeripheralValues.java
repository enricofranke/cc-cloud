package ai.ezfox.cccloud.compat.card;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The pure rules of the generic Peripheral card (docs/COMPAT.md "Peripheral card"), free of CC: Tweaked and Minecraft
 * classes so they are unit tested on their own: which method names count as getters and setters, captions, how a
 * method's result becomes a short, safe text, and the validation of the card settings the owner sends.
 */
public final class PeripheralValues {
    /** At most this many values per card. */
    public static final int MAX_VALUES = 6;
    /** At most this many of them also get a chart. */
    public static final int MAX_CHARTS = 2;
    /** A formatted value is at most this long (characters). */
    public static final int MAX_TEXT = 64;
    /** Method names longer than this are never offered. */
    public static final int MAX_NAME = 48;
    /** At most this many table entries are looked at when a result is summarised. */
    static final int MAX_VISIT = 256;

    private static final Pattern GETTER = Pattern.compile("(get|is|list)[A-Z0-9_][A-Za-z0-9_]*");
    private static final Pattern SETTER = Pattern.compile("set[A-Z0-9_][A-Za-z0-9_]*");
    /** CC: Tweaked's own read-only generic methods whose names are not get/is/list. */
    private static final Set<String> READ_ONLY = Set.of("list", "size", "tanks");

    private PeripheralValues() {
    }

    /** Getter names: {@code get*}, {@code is*}, {@code list*}, and CC's {@code list}, {@code size}, {@code tanks}. */
    public static boolean isGetterName(String name) {
        return name.length() <= MAX_NAME && (READ_ONLY.contains(name) || GETTER.matcher(name).matches());
    }

    /** Setter names: {@code set*}. Only setters that take exactly one boolean become the card's switch. */
    public static boolean isSetterName(String name) {
        return name.length() <= MAX_NAME && SETTER.matcher(name).matches();
    }

    /** The getters that report a setter's state: {@code setEnabled} → {@code isEnabled}, {@code getEnabled}. */
    public static List<String> pairedGetters(String setter) {
        String rest = setter.substring(3);
        return List.of("is" + rest, "get" + rest);
    }

    /** "getEnergyCapacity" → "Energy capacity", "isRunning" → "Running", "listItems" → "Items", "size" → "Size". */
    public static String caption(String method) {
        String rest = method;
        for (String prefix : new String[]{"get", "is", "list", "set"}) {
            if (method.length() > prefix.length() && method.startsWith(prefix) && !Character.isLowerCase(method.charAt(prefix.length()))) {
                rest = method.substring(prefix.length());
                break;
            }
        }
        // camelCase and snake_case → words; acronyms ("FE", "RF") stay upper case, other words lower case
        var words = new ArrayList<String>();
        var word = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            boolean split = c == '_' || i > 0 && Character.isUpperCase(c) && (!Character.isUpperCase(rest.charAt(i - 1))
                || i + 1 < rest.length() && Character.isLowerCase(rest.charAt(i + 1)));
            if (split && !word.isEmpty()) {
                words.add(word.toString());
                word.setLength(0);
            }
            if (c != '_') word.append(c);
        }
        if (!word.isEmpty()) words.add(word.toString());
        var sb = new StringBuilder();
        for (String w : words) {
            boolean acronym = w.length() > 1 && w.chars().allMatch(ch -> Character.isUpperCase(ch) || Character.isDigit(ch));
            String out = acronym ? w : w.toLowerCase(Locale.ROOT);
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(out);
        }
        String s = sb.isEmpty() ? method : Character.toUpperCase(sb.charAt(0)) + sb.substring(1);
        return s.length() > ai.ezfox.cccloud.uplink.Limits.LABEL ? s.substring(0, ai.ezfox.cccloud.uplink.Limits.LABEL) : s;
    }

    // ---- results ---------------------------------------------------------------------------------------------------

    /** True for results that are a plain true/false (shown as an indicator). */
    public static @Nullable Boolean bool(@Nullable Object value) {
        Object v = first(value);
        return v instanceof Boolean b ? b : null;
    }

    /** The number of a numeric result, or null. */
    public static @Nullable Double number(@Nullable Object value) {
        Object v = first(value);
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        return null;
    }

    /** Multiple results (CC's {@code Object[]} from a {@code MethodResult}) → the first one. */
    private static @Nullable Object first(@Nullable Object value) {
        if (value instanceof Object[] array) return array.length == 0 ? null : array[0];
        return value;
    }

    /**
     * A method's result as a short text: numbers with k/M/G, booleans yes/no, strings without control characters, a
     * table as its size or (small tables) its first entries, item details as "64× minecraft:stone". Never longer than
     * {@link #MAX_TEXT}, never calls anything on unknown objects, looks at no more than {@link #MAX_VISIT} entries.
     */
    public static String format(@Nullable Object value) {
        if (value instanceof Object[] array) {
            if (array.length == 0) return "–";
            if (array.length == 1) return format(array[0]);
            var parts = new ArrayList<String>();
            for (int i = 0; i < Math.min(3, array.length); i++) parts.add(format(array[i]));
            return clip(String.join(" · ", parts));
        }
        return clip(text(value, 0, new int[]{0}));
    }

    private static String text(@Nullable Object value, int depth, int[] visited) {
        if (value == null) return "–";
        if (value instanceof Boolean b) return b ? "yes" : "no";
        if (value instanceof Number n) return number(n);
        if (value instanceof CharSequence s) return clean(s.length() > 4 * MAX_TEXT ? s.subSequence(0, 4 * MAX_TEXT).toString() : s.toString());
        if (value instanceof Character c) return clean(String.valueOf(c));
        if (value instanceof Enum<?> e) return e.name().toLowerCase(Locale.ROOT);
        if (value instanceof Optional<?> o) return o.isPresent() ? text(o.get(), depth, visited) : "–";
        if (value instanceof ByteBuffer b) return b.remaining() + " bytes";
        if (value instanceof Map<?, ?> map) return table(map, depth, visited);
        if (value instanceof Collection<?> c) return entries(c.size());
        if (value.getClass().isArray()) return entries(Array.getLength(value));
        return "(object)";
    }

    private static String table(Map<?, ?> map, int depth, int[] visited) {
        int size = map.size();
        if (size == 0) return "empty";
        // an item or fluid detail table: {name, count|amount}
        Object name = get(map, "name");
        if (name instanceof CharSequence n) {
            Object count = get(map, "count");
            boolean fluid = count == null && (count = get(map, "amount")) != null;
            String id = clean(n.toString());
            if (!(count instanceof Number c)) return id;
            String amount = Math.abs(c.doubleValue()) < 1_000_000 ? String.valueOf(c.longValue()) : number(c);
            return amount + (fluid ? " mB " : "× ") + id;
        }
        boolean list = true;
        for (var key : map.keySet()) {
            if (++visited[0] > MAX_VISIT) return entries(size);
            if (!(key instanceof Number)) {
                list = false;
                break;
            }
        }
        if (list || depth > 0 || size > 3) return entries(size);
        var parts = new ArrayList<String>();
        for (var e : map.entrySet()) {
            if (++visited[0] > MAX_VISIT) break;
            String k = e.getKey() instanceof CharSequence s ? clean(s.toString()) : String.valueOf(e.getKey() instanceof Number n ? number(n) : "?");
            parts.add(k + " " + text(e.getValue(), depth + 1, visited));
        }
        parts.sort(String::compareTo);
        return String.join(", ", parts);
    }

    /** {@code map.get(key)} that never throws (sorted maps with number keys throw on a string key). */
    private static @Nullable Object get(Map<?, ?> map, String key) {
        try {
            return map.get(key);
        } catch (ClassCastException | NullPointerException e) {
            return null;
        }
    }

    private static String entries(int n) {
        return n == 1 ? "1 entry" : n + " entries";
    }

    private static String number(Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) return "–";
            if (Math.abs(d) < 1000 && d != Math.rint(d)) return String.format(Locale.ROOT, Math.abs(d) < 10 ? "%.2f" : "%.1f", d);
        }
        return Fmt.si(n.doubleValue());
    }

    /** Printable characters only (no control or format characters), whitespace runs as one space. */
    static String clean(String s) {
        var sb = new StringBuilder(Math.min(s.length(), 4 * MAX_TEXT));
        boolean space = false;
        for (int i = 0; i < s.length() && sb.length() < 4 * MAX_TEXT; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                space = !sb.isEmpty();
                continue;
            }
            int type = Character.getType(c);
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.PRIVATE_USE || type == Character.UNASSIGNED
                || type == Character.SURROGATE) continue;
            if (c == '§') { // Minecraft formatting codes
                i++;
                continue;
            }
            if (space) sb.append(' ');
            space = false;
            sb.append(c);
        }
        return sb.isEmpty() ? "–" : sb.toString();
    }

    private static String clip(String s) {
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT - 1) + "…";
    }

    // ---- settings ----------------------------------------------------------------------------------------------------

    /**
     * What the owner picked: the getters shown as values (in order), which of them also get a chart, the setter used as
     * on/off switch (or null) and whether the switch asks for the PIN.
     */
    public record Settings(List<String> show, Set<String> charts, @Nullable String toggle, boolean pin) {
        public static final Settings EMPTY = new Settings(List.of(), Set.of(), null, true);

        public JsonObject toJson() {
            var json = new JsonObject();
            var s = new JsonArray();
            show.forEach(s::add);
            json.add("show", s);
            var c = new JsonArray();
            show.stream().filter(charts::contains).forEach(c::add);
            json.add("charts", c);
            if (toggle != null) json.addProperty("toggle", toggle);
            json.addProperty("pin", pin);
            return json;
        }

        /** The same settings without methods the block does not offer (any more); never fails. */
        public Settings restrictTo(Set<String> getters, Set<String> numeric, Set<String> setters) {
            var s = show.stream().filter(getters::contains).limit(MAX_VALUES).toList();
            var c = new LinkedHashSet<String>();
            for (String m : s) if (charts.contains(m) && numeric.contains(m) && c.size() < MAX_CHARTS) c.add(m);
            return new Settings(s, Set.copyOf(c), toggle != null && setters.contains(toggle) ? toggle : null, pin);
        }
    }

    /** A parse result: the settings or an error code ({@code invalid_settings}, {@code unknown_method}, {@code too_many}). */
    public record Parsed(@Nullable Settings settings, @Nullable String error) {
    }

    /**
     * Validates settings sent by the dashboard: {@code {"show":[getter…], "charts":[getter…], "toggle":setter|null,
     * "pin":bool}}. Every name must be one the block offers; at most {@link #MAX_VALUES} values and
     * {@link #MAX_CHARTS} charts (numeric getters only); no other keys.
     */
    public static Parsed parse(JsonObject json, Set<String> getters, Set<String> numeric, Set<String> setters) {
        for (String key : json.keySet()) if (!Set.of("show", "charts", "toggle", "pin").contains(key)) return fail("invalid_settings");
        var show = names(json.get("show"));
        var charts = json.has("charts") ? names(json.get("charts")) : List.<String>of();
        if (show == null || charts == null) return fail("invalid_settings");
        if (show.size() > MAX_VALUES || charts.size() > MAX_CHARTS) return fail("too_many");
        if (new LinkedHashSet<>(show).size() != show.size()) return fail("invalid_settings");
        for (String m : show) if (!getters.contains(m)) return fail("unknown_method");
        for (String m : charts) if (!show.contains(m) || !numeric.contains(m)) return fail("unknown_method");
        String toggle = null;
        var t = json.get("toggle");
        if (t != null && !t.isJsonNull()) {
            if (!t.isJsonPrimitive() || !t.getAsJsonPrimitive().isString()) return fail("invalid_settings");
            toggle = t.getAsString();
            if (!setters.contains(toggle)) return fail("unknown_method");
        }
        boolean pin = true;
        var p = json.get("pin");
        if (p != null) {
            if (!p.isJsonPrimitive() || !p.getAsJsonPrimitive().isBoolean()) return fail("invalid_settings");
            pin = p.getAsBoolean();
        }
        return new Parsed(new Settings(List.copyOf(show), Set.copyOf(charts), toggle, pin), null);
    }

    /** Settings saved with the uplink (lenient: unknown or broken parts are dropped, never an error). */
    public static Settings load(@Nullable JsonObject json) {
        if (json == null) return Settings.EMPTY;
        var show = names(json.get("show"));
        var charts = names(json.get("charts"));
        var t = json.get("toggle");
        String toggle = t != null && t.isJsonPrimitive() && t.getAsJsonPrimitive().isString() ? t.getAsString() : null;
        var p = json.get("pin");
        boolean pin = p == null || !p.isJsonPrimitive() || !p.getAsJsonPrimitive().isBoolean() || p.getAsBoolean();
        return new Settings(show == null ? List.of() : show.stream().distinct().limit(MAX_VALUES).toList(),
            charts == null ? Set.of() : Set.copyOf(charts), toggle, pin);
    }

    private static @Nullable List<String> names(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return List.of();
        if (!e.isJsonArray() || e.getAsJsonArray().size() > 16) return null;
        var out = new ArrayList<String>();
        for (var item : e.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) return null;
            String s = item.getAsString();
            if (s.isEmpty() || s.length() > MAX_NAME) return null;
            out.add(s);
        }
        return out;
    }

    private static Parsed fail(String error) {
        return new Parsed(null, error);
    }
}
