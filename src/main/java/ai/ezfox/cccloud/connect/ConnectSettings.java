package ai.ezfox.cccloud.connect;

import ai.ezfox.cccloud.CloudConfig;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

/**
 * What the config says about CC: Cloud Connect: on when {@code internetAccess = "connect"}, the name
 * ({@code [connect] connectName}) and the relay ({@code connectRelay}).
 *
 * @param enabled {@code internetAccess} is {@code connect}
 * @param name    the claimed or wanted name ("" = none yet)
 * @param relay   the relay's base URI ({@code wss://connect.cccloud.dev})
 */
public record ConnectSettings(boolean enabled, String name, URI relay) {
    public static final String DEFAULT_RELAY = "wss://connect.cccloud.dev";
    /** The {@code internetAccess} value of this mode. */
    public static final String MODE = "connect";

    public static ConnectSettings fromConfig() {
        URI relay = relayUri(CloudConfig.get(CloudConfig.CONNECT_RELAY));
        return new ConnectSettings(MODE.equals(internetAccess()), CloudConfig.get(CloudConfig.CONNECT_NAME).trim().toLowerCase(Locale.ROOT),
            relay != null ? relay : URI.create(DEFAULT_RELAY));
    }

    /** The configured {@code internetAccess} mode ("off" when the setting is missing). */
    public static String internetAccess() {
        try {
            Object value = CloudConfig.SPEC.getValues().get(List.of("web", "internetAccess"));
            if (value instanceof ModConfigSpec.ConfigValue<?> config) {
                Object v = CloudConfig.SPEC.isLoaded() ? config.get() : config.getDefault();
                if (v instanceof String s) return s.trim().toLowerCase(Locale.ROOT);
            }
        } catch (RuntimeException ignored) {
            // config not built yet
        }
        return "off";
    }

    /** The tunnel URL for {@code name}: {@code wss://connect.cccloud.dev/tunnel/<name>}. */
    public URI tunnelUri(String name) {
        return URI.create(relay.toString().replaceAll("/+$", "") + "/tunnel/" + name);
    }

    /**
     * A relay base URI: {@code wss://host[:port]} (plain {@code ws://} only for localhost, for development), no user
     * info, path, query or fragment. Null if invalid.
     */
    public static @Nullable URI relayUri(@Nullable String text) {
        if (text == null || text.isBlank() || text.length() > 200) return null;
        try {
            var uri = new URI(text.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/")) return null;
            boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.endsWith(".localhost");
            if (!scheme.equals("wss") && !(scheme.equals("ws") && local)) return null;
            return new URI(scheme, null, host, uri.getPort(), null, null, null);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
