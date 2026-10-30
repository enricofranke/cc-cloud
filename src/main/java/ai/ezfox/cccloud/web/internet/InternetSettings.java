package ai.ezfox.cccloud.web.internet;

import ai.ezfox.cccloud.web.IpUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What {@code internetAccess} means for the web server: the effective settings after mapping the mode onto the advanced
 * {@code [web]} keys. The advanced keys stay overrides: {@code publicUrl} always wins, TLS files keep working in every
 * mode, and with {@code internetAccess = off} everything is exactly as configured (also {@code webAllowInternet = true}
 * of older setups). Pure Java, unit tested.
 *
 * @param allowInternet     whether addresses outside the LAN may connect at all
 * @param publicUrl         the URL shown to players ("" = computed from the server's addresses)
 * @param domain            the host name or IP players use from the internet ({@code internetDomain}), or null
 * @param strictPublic      plain-HTTP mode: short token expiry for internet addresses, stricter rate limits, no control
 *                          through API keys ({@link InternetGuard})
 * @param requirePin        plain-HTTP mode with {@code internetRequirePin}: control from internet addresses only after
 *                          the mainframe PIN; mainframes without a PIN are view-only from there
 * @param publicTokenIdleMs a device token unused this long is refused (and dropped) from an internet address
 * @param notes             explanations for the server log (overrides that apply, conflicts)
 */
public record InternetSettings(InternetMode mode, boolean allowInternet, String publicUrl, @Nullable String domain, boolean strictPublic,
                               boolean requirePin, long publicTokenIdleMs, List<String> notes) {
    public static final long HOUR_MS = 60 * 60 * 1000L;
    /** CC: Cloud Connect: a browser from the internet keeps its login as long as on the LAN (HTTPS, unlike plain HTTP). */
    public static final long CONNECT_IDLE_MS = 30 * 24 * HOUR_MS;
    /** LAN only, nothing strict (unit tests and servers without the setting). */
    public static final InternetSettings OFF = new InternetSettings(InternetMode.OFF, false, "", null, false, 24 * HOUR_MS, List.of());

    public InternetSettings {
        notes = List.copyOf(notes);
    }

    /** Without the PIN requirement. */
    public InternetSettings(InternetMode mode, boolean allowInternet, String publicUrl, @Nullable String domain, boolean strictPublic,
                            long publicTokenIdleMs, List<String> notes) {
        this(mode, allowInternet, publicUrl, domain, strictPublic, false, publicTokenIdleMs, notes);
    }

    /** The raw config values the mapping reads. */
    public record Inputs(String mode, String domain, int port, boolean allowInternet, String publicUrl, boolean tls, int publicTokenIdleHours,
                         boolean requirePin) {
        public Inputs(String mode, String domain, int port, boolean allowInternet, String publicUrl, boolean tls, int publicTokenIdleHours) {
            this(mode, domain, port, allowInternet, publicUrl, tls, publicTokenIdleHours, false);
        }
    }

    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    public static InternetSettings resolve(Inputs in) {
        var notes = new ArrayList<String>();
        var mode = InternetMode.parse(in.mode());
        if (mode == null) {
            notes.add("Unknown internetAccess '" + in.mode() + "', using off");
            mode = InternetMode.OFF;
        }
        String domain = null;
        if (!in.domain().isBlank()) {
            if (validDomain(in.domain())) domain = normalizeDomain(in.domain());
            else notes.add("Ignoring invalid internetDomain '" + in.domain() + "' (a host name like mybase.example.org or an IP address)");
        }
        long idle = Math.max(1, in.publicTokenIdleHours()) * HOUR_MS;
        String configuredUrl = in.publicUrl().trim();
        return switch (mode) {
            case OFF -> new InternetSettings(mode, in.allowInternet(), configuredUrl, domain, false, idle, notes);
            case HTTP -> {
                if (in.tls()) notes.add("internetAccess = http with tlsCertPath/tlsKeyPath: connections are encrypted with your certificate, "
                    + "the plain-HTTP rules (short token expiry for internet addresses, stricter limits) still apply");
                String url = !configuredUrl.isEmpty() ? configuredUrl : domain != null && in.port() > 0 ? url(in.tls() ? "https" : "http", domain, in.port()) : "";
                yield new InternetSettings(mode, true, url, domain, true, in.requirePin(), idle, notes);
            }
            // TLS ends at the relay; the port keeps its own rule (LAN only unless webAllowInternet). The tunnel's browsers are
            // internet addresses (stricter limits, lockout after wrong codes); the PIN only with internetRequirePin, as for
            // http. HTTPS: logins keep the usual 30 days instead of plain HTTP's short idle time.
            case CONNECT -> new InternetSettings(mode, in.allowInternet(), configuredUrl, domain, true, in.requirePin(), CONNECT_IDLE_MS, notes);
        };
    }

    /** {@code http://host:port} (IPv6 in brackets, the default port of the scheme left out). */
    public static String url(String scheme, String host, int port) {
        String h = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        boolean defaultPort = scheme.equals("http") && port == 80 || scheme.equals("https") && port == 443;
        return scheme + "://" + h + (defaultPort ? "" : ":" + port);
    }

    /** A DNS host name with at least two labels (no scheme, port or path) or an IP address. */
    public static boolean validDomain(@Nullable String text) {
        if (text == null) return false;
        String s = text.trim();
        if (s.isEmpty() || s.length() > 253) return false;
        if (IpUtil.literal(s) != null) return true;
        s = s.toLowerCase(Locale.ROOT);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        String[] labels = s.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) if (!LABEL.matcher(label).matches()) return false;
        // the top-level label is never all digits (that would be a malformed IPv4 address)
        return !labels[labels.length - 1].matches("[0-9]+");
    }

    static String normalizeDomain(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
