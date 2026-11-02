package ai.ezfox.cccloud.layout;

import java.util.List;
import java.util.Locale;
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
        if (css.length() > MAX_CSS) return "too_long";
        for (var f : FORBIDDEN) if (f.pattern().matcher(css).find()) return f.reason();
        if (!CHARS.matcher(css).matches()) return "chars";
        String lower = css.toLowerCase(Locale.ROOT);
        var functions = FUNCTION.matcher(lower);
        while (functions.find()) if (!FUNCTIONS.contains(functions.group(1))) return "function";
        var tokens = TOKEN.matcher(lower);
        while (tokens.find()) if (!TOKENS.contains(tokens.group())) return "token";
        for (String decl : lower.split(";", -1)) {
            if (decl.isBlank()) continue;
            var m = DECLARATION.matcher(decl);
            if (!m.lookingAt() || !PROPERTIES.contains(m.group(1)) || decl.indexOf(':') != decl.lastIndexOf(':')) return "property";
        }
        return null;
    }
}
