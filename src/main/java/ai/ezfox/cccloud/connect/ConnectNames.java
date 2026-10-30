package ai.ezfox.cccloud.connect;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Dashboard names for CC: Cloud Connect ({@code https://<name>.cccloud.dev}): the format check for quick feedback in
 * game and the suggestion when no name is given. The relay is the authority (it also refuses reserved and impersonation
 * names, see the website repository's {@code connect/src/names.ts}). Pure Java, unit tested.
 */
public final class ConnectNames {
    /** 3 to 32 characters, a-z, 0-9 and "-", letter or digit at both ends (no "--", checked separately). */
    public static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{1,30}[a-z0-9]");
    /** The most obvious reserved names, so the command can answer at once (the relay knows the full list). */
    static final Set<String> RESERVED = Set.of("www", "api", "connect", "relay", "tunnel", "admin", "store", "docs", "help", "support",
        "status", "app", "dash", "dashboard", "cloud", "cccloud", "cc-cloud", "minecraft", "mojang", "microsoft", "login", "test", "release");
    private static final SecureRandom RANDOM = new SecureRandom();

    private ConnectNames() {
    }

    public static boolean valid(@Nullable String name) {
        return name != null && NAME.matcher(name).matches() && !name.contains("--");
    }

    public static boolean reserved(String name) {
        return RESERVED.contains(name) || name.contains("cccloud") || name.contains("login") || name.contains("mojang") || name.contains("microsoft");
    }

    /**
     * A name to suggest: the world or server name made URL-friendly, plus four random characters so it is likely free
     * ("Enrico's Base" → "enricos-base-k3f9").
     */
    public static String suggest(@Nullable String from) {
        String base = slug(from == null ? "" : from);
        if (base.length() < 3 || reserved(base)) base = "base";
        if (base.length() > 27) base = base.substring(0, 27).replaceAll("-+$", "");
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        var suffix = new StringBuilder(4);
        for (int i = 0; i < 4; i++) suffix.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return base + "-" + suffix;
    }

    /** Lower case, accents removed, every other run of characters becomes one "-". */
    static String slug(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        s = s.replace("'", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.replaceAll("-{2,}", "-");
    }
}
