package ai.ezfox.cccloud.web;

import com.google.gson.JsonObject;
import java.net.URI;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What the dashboard may know before login ({@code GET /api/config}, docs/SPEC.md "Script Store – server side"): the
 * mod version, the Script Store URL and public signing key when the store is enabled, and feature switches. No secrets. Pure Java, unit tested.
 */
public final class PublicConfig {
    /** The current settings (replaced by the game with the live config; tests set their own). */
    public record Settings(String version, boolean store, String storeUrl, String storeKey, boolean terminal, boolean voxels, boolean push,
                           boolean rules, boolean map, boolean scenes, boolean production, boolean api) {
        /** Without the v0.10 switches (scenes, production statistics, public API). */
        public Settings(String version, boolean store, String storeUrl, String storeKey, boolean terminal, boolean voxels, boolean push, boolean rules,
                        boolean map) {
            this(version, store, storeUrl, storeKey, terminal, voxels, push, rules, map, false, false, false);
        }

        /** Without a store signing key. */
        public Settings(String version, boolean store, String storeUrl, boolean terminal, boolean voxels, boolean push, boolean rules, boolean map) {
            this(version, store, storeUrl, "", terminal, voxels, push, rules, map);
        }
    }

    static volatile Supplier<Settings> source = () -> new Settings("dev", false, "", false, false, false, false, false);

    private PublicConfig() {
    }

    public static void setSource(Supplier<Settings> settings) {
        source = settings;
    }

    /** {@code https://host[:port]/path/} – null if the URL is not a valid store URL. */
    public static @Nullable String validStoreUrl(@Nullable String url) {
        if (url == null || url.length() > 256 || !url.endsWith("/")) return null;
        try {
            var uri = new URI(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) return null;
            return url;
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    /** The store URL when the store is enabled and the URL valid, else null. */
    public static @Nullable String storeUrl() {
        var s = source.get();
        return s.store() ? validStoreUrl(s.storeUrl()) : null;
    }

    /**
     * The store's Ed25519 public key (config {@code scriptStoreKey}, base64 of the 32 raw bytes) when the store is enabled
     * and the key is well-formed, else null. Public by nature: the dashboard uses it to pre-check the index signature;
     * the server stays the authority on installs.
     */
    public static @Nullable String storeKey() {
        var s = source.get();
        if (storeUrl() == null || ai.ezfox.cccloud.script.StoreVerifier.publicKey(s.storeKey()) == null) return null;
        return s.storeKey().trim();
    }

    /** Origin for the CSP {@code connect-src} ({@code https://host[:port]}), or null. */
    public static @Nullable String storeOrigin() {
        String url = storeUrl();
        if (url == null) return null;
        var uri = URI.create(url);
        return "https://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }

    public static JsonObject json() {
        var s = source.get();
        var json = new JsonObject();
        json.addProperty("version", s.version());
        var store = new JsonObject();
        String url = storeUrl();
        store.addProperty("enabled", url != null);
        if (url != null) store.addProperty("url", url);
        else store.add("url", com.google.gson.JsonNull.INSTANCE);
        String key = storeKey();
        if (key != null) store.addProperty("key", key);
        else store.add("key", com.google.gson.JsonNull.INSTANCE);
        json.add("store", store);
        var features = new JsonObject();
        features.addProperty("terminal", s.terminal());
        features.addProperty("voxels", s.voxels());
        features.addProperty("push", s.push());
        features.addProperty("rules", s.rules());
        features.addProperty("map", s.map());
        features.addProperty("scenes", s.scenes());
        features.addProperty("production", s.production());
        features.addProperty("api", s.api());
        json.add("features", features);
        return json;
    }
}
