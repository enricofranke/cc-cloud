package ai.ezfox.cccloud.script;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.push.PushHttp;
import ai.ezfox.cccloud.web.GameBridge;
import org.jspecify.annotations.Nullable;

/**
 * The game side of store install verification (security review L9): the signed index is fetched with the SSRF-safe
 * HTTP client (https, public addresses unless the host is in {@code pushAllowedHosts}, no redirects), the signature is
 * checked with {@code scriptStoreKey}. Blocking: called on a web worker thread, never on the server thread.
 */
public final class StoreTrust {
    private static final PushHttp HTTP = new PushHttp();
    private static final StoreVerifier VERIFIER = new StoreVerifier(url -> {
        var result = HTTP.get(url, ai.ezfox.cccloud.push.PushService.allowedHosts(), StoreVerifier.MAX_INDEX_BYTES);
        return result.ok() ? result.body() : null;
    }, System::currentTimeMillis);

    private StoreTrust() {
    }

    /** Config reload of {@code [store]}: verified indexes are fetched and checked again. */
    public static void clearCache() {
        VERIFIER.clearCache();
    }

    /**
     * Null if the upload may be recorded as store file {@code store=<id>@<version>} ({@code storeFile} = the file's path
     * in the store, default the install path), else an error code.
     */
    public static @Nullable String verify(GameBridge.ScriptRequest request) {
        var ref = ScriptFiles.storeRef(request.query().get("store"));
        if (ref == null) return "invalid_store_ref";
        String url = ai.ezfox.cccloud.web.PublicConfig.storeUrl();
        if (url == null) return "store_disabled";
        String storeFile = request.query().getOrDefault("storeFile", request.file());
        if (storeFile == null || ScriptFiles.validate(storeFile) == null) return "invalid_path";
        return VERIFIER.check(url, CloudConfig.get(CloudConfig.SCRIPT_STORE_KEY), ref[0], ref[1], storeFile, ScriptFiles.sha256(request.body())).error();
    }
}
