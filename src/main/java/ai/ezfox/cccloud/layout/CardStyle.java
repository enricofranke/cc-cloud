package ai.ezfox.cccloud.layout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The server's check of a card's design in a v2 layout (docs/SPEC.md "Layout schema v2"): the background option, its
 * colour and the custom CSS. Defence in depth: the dashboard sanitises the CSS itself, every time it draws a card
 * (cloud/web/src/lib/cardstyle.ts, {@code sanitizeCardCss}); this refuses a layout whose CSS could not have come from that
 * sanitiser, whoever sends it. Same rules as {@code serverCssProblem} there; the shared fixture
 * src/test/resources/layout/card_style_fixture.json keeps both in step. Number ranges and what the browser supports are
 * left to the browser. Pure Java, unit tested.
 *
 * <p>Parts ({@link #PARTS}) are named pieces of a card (the title bar, the gauges' fill, the buttons …), each with its own
 * declaration list and its own short property list, checked the same way ({@link #partProblem}). {@link #designProblem}
 * checks a whole design, as a Lua script ({@code cloud_uplink.setStyle}) or a Cloud Script ({@code cloud.setStyle})
 * sends it.
 */
public final class CardStyle {
    /** Background options; {@code custom} uses the card's {@code bgColor}. */
    public static final Set<String> BACKGROUNDS = Set.of(
        "tint-gold", "tint-cyan", "tint-green", "tint-yellow", "tint-red", "tint-purple", "tint-orange", "tint-gray",
        "grad-accent", "grad-aurora", "grad-ember", "grad-lagoon",
        "pat-grid", "pat-stripes", "pat-dots", "pat-circuit",
        "custom");
    /** Longest custom CSS of one card, in characters (UTF-16 units, like JavaScript's length). */
    public static final int MAX_CSS = 2048;
    /** Properties a card's CSS may set. Nothing that moves, covers or sizes anything. */
    public static final Set<String> PROPERTIES = Set.of(
        "background", "background-color", "background-image", "background-size", "background-position", "background-repeat",
        "background-blend-mode", "border", "border-top", "border-right", "border-bottom", "border-left", "border-width",
        "border-color", "border-style", "box-shadow", "opacity", "filter", "border-radius", "color", "font-family",
        "font-weight", "font-style", "letter-spacing", "text-transform", "text-shadow");
    /** Functions a value may call: colours, gradients, var() and filters. Nothing that loads or reads anything. */
    public static final Set<String> FUNCTIONS = Set.of(
        "rgb", "rgba", "hsl", "hsla", "hwb", "lab", "lch", "oklab", "oklch", "color", "color-mix",
        "linear-gradient", "radial-gradient", "conic-gradient", "repeating-linear-gradient", "repeating-radial-gradient",
        "repeating-conic-gradient", "var", "blur", "brightness", "contrast", "grayscale", "hue-rotate", "saturate", "sepia",
        "opacity");
    /** The dashboard's theme tokens a value may read with var(). */
    public static final Set<String> TOKENS = Set.of(
        "--accent", "--bg", "--panel", "--panel-2", "--panel-3", "--screen", "--line", "--line-2", "--text", "--muted",
        "--faint", "--gold", "--gold-hi", "--gold-dk", "--gold-text", "--cyan", "--cyan-hi", "--cyan-dk", "--green",
        "--yellow", "--red", "--red-text", "--link", "--gray", "--purple", "--orange", "--on-color");

    /** Parts of a card and the properties each may set, in the dashboard's order (PART_PROPERTIES in cardstyle.ts). */
    public static final Map<String, Set<String>> PARTS = parts();
    /** Longest CSS of one part, in characters. */
    public static final int MAX_PART_CSS = 512;
    /** Functions only a part may call (a glow); together with {@link #FUNCTIONS}. */
    public static final Set<String> PART_FUNCTIONS = Set.of("drop-shadow");
    private static final Set<String> ALL_FUNCTIONS = union(FUNCTIONS, PART_FUNCTIONS);
    /** The keys of a design. */
    public static final Set<String> DESIGN_KEYS = Set.of("bg", "bgColor", "css", "parts");

    private static Map<String, Set<String>> parts() {
        var fill = List.of("background", "background-color", "background-image");
        var text = List.of("color", "font-family", "font-weight", "font-style", "letter-spacing", "text-transform", "text-shadow");
        var map = new LinkedHashMap<String, Set<String>>();
        map.put("title_bar", set(fill, List.of("border-bottom", "box-shadow")));
        map.put("title", set(text, List.of("font-size")));
        map.put("screen", set(fill, List.of("border", "border-color", "border-radius", "box-shadow")));
        map.put("row", set(fill, List.of("border", "border-color", "border-radius", "box-shadow")));
        map.put("label", set(text));
        map.put("value", set(text));
        map.put("gauge_track", set(List.of("color"), fill, List.of("border", "border-color", "border-radius", "box-shadow")));
        map.put("gauge_fill", set(List.of("color"), fill, List.of("border-radius", "box-shadow", "filter")));
        map.put("chart_line", set(List.of("color", "filter")));
        map.put("button", set(List.of("background-color", "background-image", "color", "border", "border-color", "border-radius", "box-shadow",
            "font-family", "font-weight", "letter-spacing", "text-transform", "text-shadow")));
        map.put("toggle", set(fill, List.of("border", "border-color", "border-radius", "box-shadow")));
        map.put("toggle_on", set(fill, List.of("border-color", "box-shadow")));
        return java.util.Collections.unmodifiableMap(map);
    }

    @SafeVarargs
    private static Set<String> set(List<String>... lists) {
        var out = new java.util.LinkedHashSet<String>();
        for (var list : lists) out.addAll(list);
        return java.util.Collections.unmodifiableSet(out);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        var out = new java.util.HashSet<>(a);
        out.addAll(b);
        return Set.copyOf(out);
    }

    private record Forbidden(Pattern pattern, String reason) {
    }

    /** Never allowed anywhere, in this order (FORBIDDEN in cardstyle.ts). */
    private static final List<Forbidden> FORBIDDEN = List.of(
        new Forbidden(Pattern.compile("\\\\"), "escape"),
        new Forbidden(Pattern.compile("/\\*|\\*/"), "comment"),
        new Forbidden(Pattern.compile("[{}@]"), "block"),
        new Forbidden(Pattern.compile("!"), "important"),
        new Forbidden(Pattern.compile("url\\s*\\(|image-set|image\\s*\\(|src\\s*\\(|element\\s*\\(|cross-fade\\s*\\(|paint\\s*\\(",
            Pattern.CASE_INSENSITIVE), "network"),
        new Forbidden(Pattern.compile("attr\\s*\\(|expression|-moz-binding|behaviou?r|javascript\\s*:", Pattern.CASE_INSENSITIVE), "script"));
    /** Plain ASCII letters, digits and the characters of the values; no quotes, escapes, comments or blocks. */
    private static final Pattern CHARS = Pattern.compile("[A-Za-z0-9 \t\n\r#%.,()+\\-/:;]*");
    private static final Pattern FUNCTION = Pattern.compile("([a-z-]*)\\s*\\(");
    private static final Pattern TOKEN = Pattern.compile("--[a-z0-9-]+");
    private static final Pattern DECLARATION = Pattern.compile("\\s*([a-z-]+)\\s*:\\s*\\S");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private CardStyle() {
    }

    /** Whether a {@code bgColor} is a colour the dashboard accepts ({@code #rrggbb}). */
    public static boolean isHexColor(String value) {
        return HEX.matcher(value).matches();
    }

    /**
     * The reason a card's CSS is refused ({@code too_long}, {@code escape}, {@code comment}, {@code block},
     * {@code important}, {@code network}, {@code script}, {@code chars}, {@code function}, {@code token},
     * {@code property}), or null when it may be stored.
     */
    public static @Nullable String cssProblem(String css) {
        return problem(css, PROPERTIES, FUNCTIONS, MAX_CSS);
    }

    /**
     * The reason one part's CSS is refused: {@code part} for a part that does not exist, else the reasons of
     * {@link #cssProblem} against that part's properties (and {@link #PART_FUNCTIONS}); null when it may be stored.
     */
    public static @Nullable String partProblem(String part, String css) {
        var properties = PARTS.get(part);
        if (properties == null) return "part";
        return problem(css, properties, ALL_FUNCTIONS, MAX_PART_CSS);
    }

    /** The reason a card's {@code parts} are refused ({@code parts} when it is no object of strings), or null. */
    public static @Nullable String partsProblem(JsonElement parts) {
        if (!parts.isJsonObject()) return "parts";
        for (var entry : parts.getAsJsonObject().entrySet()) {
            var css = entry.getValue();
            if (!css.isJsonPrimitive() || !css.getAsJsonPrimitive().isString()) return "parts";
            String problem = partProblem(entry.getKey(), css.getAsString());
            if (problem != null) return problem;
        }
        return null;
    }

    /**
     * The problem of a whole design ({@code {bg, bgColor, css, parts}}, as scripts send it) as {@code "<key>: <reason>"}
     * (e.g. {@code "parts.title: network"}), or null when it is fine.
     */
    public static @Nullable String designProblem(JsonObject design) {
        for (var key : design.keySet()) if (!DESIGN_KEYS.contains(key)) return key + ": unknown";
        if (design.has("bg") && !(isString(design.get("bg")) && BACKGROUNDS.contains(design.get("bg").getAsString()))) return "bg: background";
        if (design.has("bgColor") && !(isString(design.get("bgColor")) && isHexColor(design.get("bgColor").getAsString()))) return "bgColor: colour";
        if (design.has("css")) {
            if (!isString(design.get("css"))) return "css: string";
            String problem = cssProblem(design.get("css").getAsString());
            if (problem != null) return "css: " + problem;
        }
        if (design.has("parts")) {
            var parts = design.get("parts");
            if (!parts.isJsonObject()) return "parts: object";
            for (var entry : parts.getAsJsonObject().entrySet()) {
                if (!isString(entry.getValue())) return "parts." + entry.getKey() + ": string";
                String problem = partProblem(entry.getKey(), entry.getValue().getAsString());
                if (problem != null) return "parts." + entry.getKey() + ": " + problem;
            }
        }
        return null;
    }

    /**
     * Replaces a layout card's design keys with those of {@code design} (null: removes them), the way the editor saves
     * a design: as a whole. Returns whether anything changed.
     */
    public static boolean putDesign(JsonObject card, @Nullable JsonObject design) {
        var before = card.deepCopy();
        for (var key : DESIGN_KEYS) card.remove(key);
        if (design != null) {
            for (var key : DESIGN_KEYS) if (design.has(key)) card.add(key, design.get(key).deepCopy());
        }
        return !card.equals(before);
    }

    private static boolean isString(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    private static @Nullable String problem(String css, Set<String> properties, Set<String> functions, int max) {
        if (css.length() > max) return "too_long";
        for (var f : FORBIDDEN) if (f.pattern().matcher(css).find()) return f.reason();
        if (!CHARS.matcher(css).matches()) return "chars";
        String lower = css.toLowerCase(Locale.ROOT);
        var calls = FUNCTION.matcher(lower);
        while (calls.find()) if (!functions.contains(calls.group(1))) return "function";
        var tokens = TOKEN.matcher(lower);
        while (tokens.find()) if (!TOKENS.contains(tokens.group())) return "token";
        for (String decl : lower.split(";", -1)) {
            if (decl.isBlank()) continue;
            var m = DECLARATION.matcher(decl);
            if (!m.lookingAt() || !properties.contains(m.group(1)) || decl.indexOf(':') != decl.lastIndexOf(':')) return "property";
        }
        return null;
    }
}
