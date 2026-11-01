package ai.ezfox.cccloud.reports;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place that removes personal data from text before it may leave the server (docs/PRIVACY.md). Everything that
 * goes into a report passes through here: exception messages, class names of foreign frames and feedback texts.
 * <p>
 * Pure Java, no game classes. An instance knows the names of this server (players, world, address); everything else is
 * recognised by its shape. It errs on the side of removing too much: a report that lost a detail is still a report.
 * No pattern repeats a group, so a long hostile text cannot overflow the stack; should anything throw anyway, callers
 * drop the report ({@link ReportPayload}).
 * <p>
 * Replacements are the {@code <...>} tokens of {@link #TOKENS}, and scrubbing is idempotent: {@link #clean} uses that as
 * the last check of a finished payload.
 */
public final class ReportScrubber {
    /** Only this much of a text is looked at; the rest is cut before the patterns run, which bounds their work. */
    public static final int MAX_INPUT = 8 * 1024;
    /** An exception message after scrubbing. */
    public static final int MAX_MESSAGE = 300;

    /** A scrubber that knows no names of this server (patterns only). */
    public static final ReportScrubber EMPTY = new ReportScrubber(List.of(), List.of());

    static final List<String> TOKENS = List.of("url", "email", "token", "secret", "uuid", "path", "ip", "host", "coords", "id", "file", "number", "player",
        "name");

    private static final Pattern CONTROL = Pattern.compile("§.|[\\p{Cc}\\p{Cf}&&[^\\n\\t]]");
    private static final Pattern URL = Pattern.compile("(?<![\\p{L}\\p{N}])(?:[a-zA-Z][a-zA-Z0-9+.-]{1,20}://|www\\.|(?:file|jar|mailto):)[^\\s\"'<>)\\]}]+");
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-][\\p{L}\\p{N}.-]*");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9._~+/=-]{4,}");
    private static final Pattern PREFIXED_TOKEN = Pattern.compile("\\b(?:cck|ghp|gho|ghs|ghu|github_pat|xox[abeoprs]|sk|pk|rk)[_-][A-Za-z0-9_-]{10,}");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}(?:\\.[A-Za-z0-9_-]+)?");
    private static final Pattern SECRET_VALUE = Pattern.compile(
        "(?i)\\b(pin|password|passwd|pwd|pass|passphrase|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|auth|authorization|session|cookie|set-cookie)\\b"
            + "[\"']?\\s*[:=]\\s*[\"']?[^\\s\"',;}&]+");
    private static final Pattern SPOKEN_PIN = Pattern.compile("(?i)\\bpin\\b[^\\d\\n]{0,16}\\d{4,8}\\b");
    private static final Pattern PAIRING_CODE = Pattern.compile("\\b[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}\\b");
    private static final Pattern UUID_DASHED = Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern UUID_PLAIN = Pattern.compile("\\b[0-9a-fA-F]{32}\\b");
    private static final Pattern HEX_BLOB = Pattern.compile("\\b[0-9a-fA-F]{16,}\\b");
    private static final Pattern BASE64_BLOB = Pattern.compile("(?<![\\p{L}\\p{N}._/$-])[A-Za-z0-9+/_-]{24,}={0,2}");

    // Paths under a user's home: everything up to the end of the line goes, because the folder name may hold spaces.
    private static final Pattern WINDOWS_HOME = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])[a-z]:[\\\\/]+(?:users|documents and settings)[\\\\/]+[^\\r\\n\"'<>|:*?]*");
    private static final Pattern UNIX_HOME = Pattern.compile("(?<![\\p{L}\\p{N}_.-])/(?:Users|home|root|var/home|Volumes|mnt|media|srv|run/user)(?:/[^\\r\\n\"'<>|:]*)?");
    private static final Pattern ENCODED_PATH = Pattern.compile("(?i)(?:[a-z]%3a(?:%5c|%2f)|%2f(?:users|home)%2f)\\S*");
    private static final Pattern WINDOWS_PATH = Pattern.compile(
        "(?<![\\p{L}\\p{N}])[A-Za-z]:[\\\\/]+(?:[^:*?\"<>|\\r\\n]*[\\\\/])?[^\\\\/:*?\"<>|\\r\\n\\s,;)]*");
    private static final Pattern UNC_PATH = Pattern.compile("\\\\\\\\[^\\s\\\\/]+[\\\\/][^\\r\\n\"'<>|]*");
    private static final Pattern HOME_SHORTHAND = Pattern.compile(
        "(?i)(?:~[\\\\/]|\\$\\{?HOME\\}?|%(?:USERPROFILE|APPDATA|LOCALAPPDATA|HOMEPATH|TEMP|TMP)%)[^\\s\"'<>]*");
    private static final Pattern UNIX_PATH = Pattern.compile("(?<![\\p{L}\\p{N}_.:/<-])/[^\\s/\"'<>:;,|()\\[\\]{}][^\\s\"'<>:;,|()\\[\\]{}]*/[^\\s/\"'<>:;,|()\\[\\]{}]*");
    private static final Pattern RELATIVE_PATH = Pattern.compile("(?<![\\p{L}\\p{N}_<.-])[\\p{L}\\p{N}._$~-]+[\\\\/](?:[\\p{L}\\p{N}._$~\\\\/-]*[\\p{L}\\p{N}._$~-])[\\\\/]?");
    private static final Set<String> PATH_ROOTS = Set.of("saves", "world", "worlds", "config", "serverconfig", "defaultconfigs", "mods", "logs", "crash-reports",
        "kubejs", "computercraft", "cccloud", "versions", "libraries", ".minecraft", "minecraft", "..", ".", "~");

    private static final Pattern MAC = Pattern.compile("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b");
    private static final Pattern IPV6_BRACKETED = Pattern.compile("\\[([0-9A-Fa-f:.]{2,45})(?:%[\\p{L}\\p{N}_.-]+)?](?::\\d{1,5})?");
    private static final Pattern IPV6_PLAIN = Pattern.compile("(?<![\\p{L}\\p{N}_:.])([0-9A-Fa-f:.]{2,45})(?:%[\\p{L}\\p{N}_.-]+)?(?![\\p{L}\\p{N}_:])");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?::\\d{1,5})?(?![\\d.])");
    private static final Pattern HOST = Pattern.compile(
        "(?i)(?<![\\p{L}\\p{N}_.@-])[a-z0-9][a-z0-9.-]*\\."
            + "(?:com|net|org|info|biz|de|dev|io|gg|eu|at|ch|uk|us|fr|nl|pl|ru|cz|es|it|se|no|fi|dk|ca|au|br|jp|cn|xyz|me|tv|cc|co|app|cloud|online|site|fun|club|host|"
            + "server|local|lan|internal|home|arpa)(?::\\d{1,5})?(?![\\p{L}\\p{N}_.-])");
    private static final Pattern HOST_PORT = Pattern.compile("(?i)(?<![\\p{L}\\p{N}_.@<-])[a-z0-9][a-z0-9.-]{0,252}:\\d{2,5}(?![\\p{L}\\p{N}_.-])");
    private static final Pattern SOURCE_LINE = Pattern.compile("(?i).*\\.(?:java|kt|kts|groovy|lua):\\d+");

    private static final String NUM = "-?\\d{1,9}(?:\\.\\d+)?";
    private static final Pattern COORD_KEYS = Pattern.compile("(?i)(?:\\b(?:mutable)?(?:blockpos|vec3i?|vector3[a-z]*|chunkpos|sectionpos|globalpos|pos|position)\\s*)?"
        + "[{\\[(]?\\s*(?<![\\p{L}\\p{N}_.])x\\s*[=:]\\s*" + NUM + "[,;\\s]+(?:y\\s*[=:]\\s*" + NUM + "[,;\\s]+)?z\\s*[=:]\\s*" + NUM + "\\s*[}\\])]?");
    private static final Pattern COORD_TRIPLE_BRACKET = Pattern.compile("[{\\[(]\\s*" + NUM + "\\s*[,;]\\s*" + NUM + "\\s*[,;]\\s*" + NUM + "\\s*[}\\])]");
    private static final Pattern COORD_TRIPLE = Pattern.compile("(?<![\\p{L}\\p{N}_.,-])" + NUM + "(?:\\s*,\\s*|[ \\t]+)" + NUM + "(?:\\s*,\\s*|[ \\t]+)" + NUM + "(?![\\p{L}\\p{N}_.,])");
    private static final Pattern COORD_CHUNK = Pattern.compile("(?i)(?:\\b(?:chunk|chunkpos|region|section)\\b\\s*[\\[(]?\\s*" + NUM + "\\s*[,\\s]\\s*" + NUM + "\\s*[\\])]?"
        + "|\\[\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*])");

    private static final Pattern NAMESPACED_ID = Pattern.compile("(?<![\\p{L}\\p{N}_:/.<-])[a-z_][a-z0-9_.-]{0,62}:[a-z0-9_][a-z0-9_./-]{0,127}(?![\\p{L}\\p{N}_:])");
    private static final Pattern FILE_NAME = Pattern.compile("(?i)(?<![\\p{L}\\p{N}_.<-])[\\p{L}\\p{N}_-][\\p{L}\\p{N}._-]*\\."
        + "(?:dat|dat_old|mca|mcc|mcr|nbt|snbt|txt|log|gz|json|json5|toml|zip|rar|7z|tar|png|jpe?g|gif|webp|jar|lua|properties|cfg|conf|ini|ya?ml|db|sqlite|mcmeta|"
        + "mcworld|pem|key|p12|bak|old|tmp|lock|csv|xml|html?|exe|bat|sh|cmd|ps1|hprof)(?![\\p{L}\\p{N}_-])");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![\\d.])\\d{7,}(?![\\d.])");

    private static final Pattern WORD_OR_TOKEN = Pattern.compile("<(?:" + String.join("|", TOKENS) + ")>|[\\p{L}\\p{N}_]+");
    private static final Pattern SPACES = Pattern.compile("[ \\t]{2,}");

    private final Set<String> names = new HashSet<>();
    private final List<String> literals = new ArrayList<>();

    /**
     * @param players  known player names, matched as whole words in any case
     * @param literals other names of this server (world, address, Connect name, mainframe names), matched in any case
     */
    public ReportScrubber(Collection<String> players, Collection<String> literals) {
        for (String player : players) {
            if (player != null && !player.isBlank()) names.add(player.strip().toLowerCase(Locale.ROOT));
        }
        for (String literal : literals) {
            if (literal == null) continue;
            String lower = literal.strip().toLowerCase(Locale.ROOT);
            // one or two characters would tear ordinary text apart
            if (lower.length() >= 3 && !this.literals.contains(lower)) this.literals.add(lower);
        }
        // longest first: "my world backup" before "my world"
        this.literals.sort(Comparator.comparingInt(String::length).reversed());
    }

    /** Scrubs a whole text (at most {@link #MAX_INPUT} characters are looked at; the rest is cut off). */
    public String text(String input) {
        String s = input.length() > MAX_INPUT ? input.substring(0, MAX_INPUT) : input;
        s = CONTROL.matcher(s).replaceAll(" ");
        s = literals(s);
        s = URL.matcher(s).replaceAll("<url>");
        s = EMAIL.matcher(s).replaceAll("<email>");
        s = BEARER.matcher(s).replaceAll("<token>");
        s = PREFIXED_TOKEN.matcher(s).replaceAll("<token>");
        s = JWT.matcher(s).replaceAll("<token>");
        s = SECRET_VALUE.matcher(s).replaceAll("$1=<secret>");
        s = SPOKEN_PIN.matcher(s).replaceAll("pin <secret>");
        s = UUID_DASHED.matcher(s).replaceAll("<uuid>");
        s = UUID_PLAIN.matcher(s).replaceAll("<uuid>");
        s = PAIRING_CODE.matcher(s).replaceAll("<token>");
        s = ENCODED_PATH.matcher(s).replaceAll("<path>");
        s = WINDOWS_HOME.matcher(s).replaceAll("<path>");
        s = UNIX_HOME.matcher(s).replaceAll("<path>");
        s = UNC_PATH.matcher(s).replaceAll("<path>");
        s = WINDOWS_PATH.matcher(s).replaceAll("<path>");
        s = HOME_SHORTHAND.matcher(s).replaceAll("<path>");
        s = UNIX_PATH.matcher(s).replaceAll("<path>");
        s = MAC.matcher(s).replaceAll("<ip>");
        s = IPV6_BRACKETED.matcher(s).replaceAll(m -> isIpv6(m.group(1)) ? "<ip>" : Matcher.quoteReplacement(m.group()));
        s = IPV6_PLAIN.matcher(s).replaceAll(m -> isIpv6(m.group(1)) ? "<ip>" : Matcher.quoteReplacement(m.group()));
        s = IPV4.matcher(s).replaceAll(m -> octets(m) ? "<ip>" : Matcher.quoteReplacement(m.group()));
        s = HEX_BLOB.matcher(s).replaceAll("<token>");
        s = BASE64_BLOB.matcher(s).replaceAll(m -> looksRandom(m.group()) ? "<token>" : Matcher.quoteReplacement(m.group()));
        s = RELATIVE_PATH.matcher(s).replaceAll(m -> relativePath(m.group()) ? "<path>" : Matcher.quoteReplacement(m.group()));
        s = HOST.matcher(s).replaceAll("<host>");
        s = HOST_PORT.matcher(s).replaceAll(m -> SOURCE_LINE.matcher(m.group()).matches() ? Matcher.quoteReplacement(m.group()) : "<host>");
        s = COORD_KEYS.matcher(s).replaceAll("<coords>");
        s = COORD_TRIPLE_BRACKET.matcher(s).replaceAll("<coords>");
        s = COORD_CHUNK.matcher(s).replaceAll("<coords>");
        s = COORD_TRIPLE.matcher(s).replaceAll("<coords>");
        s = NAMESPACED_ID.matcher(s).replaceAll("<id>");
        s = FILE_NAME.matcher(s).replaceAll("<file>");
        s = LONG_NUMBER.matcher(s).replaceAll("<number>");
        s = names(s);
        return SPACES.matcher(s).replaceAll(" ").strip();
    }

    /** An exception message for a report: scrubbed, on one line, cut to {@link #MAX_MESSAGE} without leaving half a token. */
    public String message(String message) {
        return cut(text(message).replace('\n', ' ').replace('\t', ' '), MAX_MESSAGE);
    }

    /** A dotted class name: only the names of this server are replaced, so the structure stays readable. */
    public String className(String name) {
        return names(literals(name));
    }

    /** True if scrubbing would not change this text: nothing the patterns or names recognise is left in it. */
    public boolean clean(String text) {
        return text(text).equals(SPACES.matcher(text.strip()).replaceAll(" "));
    }

    /** Cuts after scrubbing, so no half token can survive, and drops a {@code <tok} left at the end. */
    static String cut(String text, int max) {
        if (text.length() <= max) return text;
        String cut = text.substring(0, max);
        int open = cut.lastIndexOf('<');
        if (open >= 0 && cut.indexOf('>', open) < 0) cut = cut.substring(0, open);
        return cut.stripTrailing() + "…";
    }

    private String literals(String text) {
        if (literals.isEmpty()) return text;
        String lower = text.toLowerCase(Locale.ROOT);
        // Case folding can change the length of a text (İ); then the indexes would not fit, so go through a slower path.
        if (lower.length() != text.length()) {
            for (String literal : literals) text = Pattern.compile(Pattern.quote(literal), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).replaceAll("<name>");
            return text;
        }
        for (String literal : literals) {
            int at = lower.indexOf(literal);
            if (at < 0) continue;
            var out = new StringBuilder();
            int from = 0;
            while (at >= 0) {
                out.append(text, from, at).append("<name>");
                from = at + literal.length();
                at = lower.indexOf(literal, from);
            }
            text = out.append(text, from, text.length()).toString();
            lower = text.toLowerCase(Locale.ROOT);
        }
        return text;
    }

    private String names(String text) {
        if (names.isEmpty()) return text;
        return WORD_OR_TOKEN.matcher(text).replaceAll(m -> {
            String word = m.group();
            return word.charAt(0) != '<' && names.contains(word.toLowerCase(Locale.ROOT)) ? "<player>" : Matcher.quoteReplacement(word);
        });
    }

    private static boolean octets(java.util.regex.MatchResult m) {
        for (int i = 1; i <= 4; i++) {
            if (Integer.parseInt(m.group(i)) > 255) return false;
        }
        return true;
    }

    /** Only long strings that mix digits with both letter cases: class and method names rarely do, keys always. */
    private static boolean looksRandom(String s) {
        boolean digit = false, upper = false, lower = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            digit |= c >= '0' && c <= '9';
            upper |= c >= 'A' && c <= 'Z';
            lower |= c >= 'a' && c <= 'z';
        }
        return digit && upper && lower;
    }

    /** "and/or" and "on/off" are words; three parts, a file at the end or a known game folder make it a path. */
    private static boolean relativePath(String s) {
        var parts = s.split("[\\\\/]");
        if (parts.length >= 3) return true;
        return parts[parts.length - 1].contains(".") || PATH_ROOTS.contains(parts[0].toLowerCase(Locale.ROOT));
    }

    /** A strict IPv6 check: at most one "::", groups of 1 to 4 hex digits, an optional IPv4 tail. */
    static boolean isIpv6(String text) {
        String s = text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
        if (s.indexOf(':') < 0 || s.equals("::") || s.equals(":")) return false;
        int compressed = s.indexOf("::");
        if (compressed >= 0 && s.indexOf("::", compressed + 1) >= 0) return false;
        int lastColon = s.lastIndexOf(':');
        int groups = 0;
        String head = s;
        if (s.indexOf('.') >= 0) {
            var tail = s.substring(lastColon + 1).split("\\.", -1);
            if (tail.length != 4) return false;
            for (String octet : tail) {
                if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit) || Integer.parseInt(octet) > 255) return false;
            }
            groups = 2;
            head = s.substring(0, lastColon + 1);
            if (!head.endsWith("::")) head = head.substring(0, head.length() - 1);
        }
        for (String part : head.split(":", -1)) {
            if (part.isEmpty()) continue;
            if (part.length() > 4 || !part.chars().allMatch(c -> Character.digit(c, 16) >= 0)) return false;
            groups++;
        }
        boolean edgesOk = compressed >= 0 || (!s.startsWith(":") && !head.endsWith(":"));
        return edgesOk && (compressed >= 0 ? groups <= 7 : groups == 8);
    }
}
