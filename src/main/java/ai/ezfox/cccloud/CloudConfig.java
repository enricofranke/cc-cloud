package ai.ezfox.cccloud;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config ({@code serverconfig/cccloud-server.toml} in the world folder).
 */
public final class CloudConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ALLOW_CLOUD;
    public static final ModConfigSpec.BooleanValue WEB_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> WEB_BIND;
    public static final ModConfigSpec.IntValue WEB_PORT;
    public static final ModConfigSpec.ConfigValue<String> PUBLIC_URL;
    public static final ModConfigSpec.BooleanValue WEB_ALLOW_INTERNET;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WEB_TRUSTED_PROXIES;
    public static final ModConfigSpec.ConfigValue<String> TLS_CERT_PATH;
    public static final ModConfigSpec.ConfigValue<String> TLS_KEY_PATH;
    public static final ModConfigSpec.IntValue UPLINK_RANGE;
    public static final ModConfigSpec.IntValue MAX_WIDGETS_PER_UPLINK;

    static {
        var b = new ModConfigSpec.Builder();
        ALLOW_CLOUD = b.comment("Master switch. When false, every mainframe on this server stays offline.")
            .translation("cccloud.configuration.allowCloud")
            .define("allowCloud", true);

        b.push("web");
        WEB_ENABLED = b.comment("Run the embedded dashboard web server (HTTP + WebSocket).")
            .translation("cccloud.configuration.webEnabled")
            .define("webEnabled", true);
        WEB_BIND = b.comment("Address the web server binds to. Use 127.0.0.1 to only allow local access.")
            .translation("cccloud.configuration.webBind")
            .define("webBind", "0.0.0.0");
        WEB_PORT = b.comment("TCP port of the web server. On a hosted server this port must be opened in the firewall.")
            .translation("cccloud.configuration.webPort")
            .defineInRange("webPort", 8787, 1, 65535);
        PUBLIC_URL = b.comment("URL shown to players, e.g. https://dash.example.com. Empty = http://<server-ip>:<port>.")
            .translation("cccloud.configuration.publicUrl")
            .define("publicUrl", "");
        WEB_ALLOW_INTERNET = b.comment("false (default): only loopback and private/LAN addresses may use the dashboard. "
                + "true: anyone who can reach the port may open it (pairing still needs a code and in-game approval). Use TLS when enabling this.")
            .translation("cccloud.configuration.webAllowInternet")
            .define("webAllowInternet", false);
        WEB_TRUSTED_PROXIES = b.comment("IPs/CIDRs of reverse proxies (e.g. 127.0.0.1 for Caddy/cloudflared on the same host). "
                + "X-Forwarded-For / CF-Connecting-IP are only honoured when the direct peer is in this list.")
            .translation("cccloud.configuration.webTrustedProxies")
            .defineListAllowEmpty("webTrustedProxies", java.util.List.of(), () -> "127.0.0.1", o -> o instanceof String);
        TLS_CERT_PATH = b.comment("Optional TLS: path to the certificate chain (PEM). Internet-facing servers should use TLS or a reverse proxy/tunnel.")
            .translation("cccloud.configuration.tlsCertPath")
            .define("tlsCertPath", "");
        TLS_KEY_PATH = b.comment("Optional TLS: path to the private key (PKCS#8 PEM). Both paths must be set to enable HTTPS.")
            .translation("cccloud.configuration.tlsKeyPath")
            .define("tlsKeyPath", "");
        b.pop();

        b.push("uplinks");
        UPLINK_RANGE = b.comment("Maximum distance (blocks) between a normal Cloud Uplink and its mainframe. Advanced uplinks are unlimited and work across dimensions.")
            .translation("cccloud.configuration.uplinkRange")
            .defineInRange("uplinkRange", 128, 1, 100_000);
        MAX_WIDGETS_PER_UPLINK = b.comment("Maximum number of widgets one uplink may register (the dashboard supports at most 32).")
            .translation("cccloud.configuration.maxWidgetsPerUplink")
            .defineInRange("maxWidgetsPerUplink", 32, 1, 32);
        b.pop();

        SPEC = b.build();
    }

    private CloudConfig() {
    }

    /** Reads a config value, falling back to the default while the server config is not loaded (e.g. on a client). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
