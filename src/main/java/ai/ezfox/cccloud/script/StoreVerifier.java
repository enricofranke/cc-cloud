package ai.ezfox.cccloud.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The server is the authority for Script Store installs (security review L9, docs/SPEC.md "Script Store – server side"):
 * before a file with {@code ?store=<id>@<version>} is written, the server fetches {@code index.json} and
 * {@code index.json.sig} from {@code scriptStoreUrl}, verifies the Ed25519 signature with {@code scriptStoreKey} and
 * checks the file's SHA-256 against the signed index. Fetching is injected (the mod uses the SSRF-safe push client);
 * verified indexes are cached for {@link #CACHE_MS}. Pure Java apart from the JDK's Ed25519, unit tested.
 */
public final class StoreVerifier {
    public static final long CACHE_MS = 5 * 60 * 1000L;
    public static final int MAX_INDEX_BYTES = 2 * 1024 * 1024;
    /** X.509 header of a raw 32-byte Ed25519 public key. */
    private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    /** Downloads one file of the store (null = not available). Blocking: call off the server thread. */
    public interface Fetcher {
        byte @Nullable [] get(String url) throws java.io.IOException;
    }

    public record Result(@Nullable String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private record Cached(JsonObject index, long at) {
    }

    private final Fetcher fetcher;
    private final LongSupplier clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public StoreVerifier(Fetcher fetcher, LongSupplier clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    /** A raw 32-byte Ed25519 public key (base64) as a JDK key, or null if malformed. */
    public static @Nullable PublicKey publicKey(@Nullable String base64) {
        if (base64 == null || base64.isBlank()) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(base64.trim());
            if (raw.length != 32) return null;
            byte[] spki = new byte[SPKI_PREFIX.length + 32];
            System.arraycopy(SPKI_PREFIX, 0, spki, 0, SPKI_PREFIX.length);
            System.arraycopy(raw, 0, spki, SPKI_PREFIX.length, 32);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            return null;
        }
    }

    /** Whether {@code signatureBase64} is a valid Ed25519 signature of {@code data}. */
    public static boolean verifySignature(byte[] data, byte[] signatureBase64, PublicKey key) {
        try {
            byte[] sig = Base64.getDecoder().decode(new String(signatureBase64, StandardCharsets.US_ASCII).trim());
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(data);
            return verifier.verify(sig);
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    /**
     * Checks one file of an install: {@code null} error = the signed index lists {@code id@version} with this path and
     * SHA-256. Errors: {@code store_not_configured} (no key), {@code store_unreachable}, {@code store_bad_signature},
     * {@code store_unknown_script}, {@code store_mismatch}.
     */
    public Result check(String storeUrl, @Nullable String publicKeyBase64, String id, String version, String path, String sha256Hex) {
        var key = publicKey(publicKeyBase64);
        if (key == null) return new Result("store_not_configured");
        JsonObject index;
        // keyed by URL and public key: an index verified with an old key (rotated by a config reload) is never reused
        String cacheKey = storeUrl + "\n" + publicKeyBase64;
        var cached = cache.get(cacheKey);
        if (cached != null && clock.getAsLong() - cached.at() < CACHE_MS) {
            index = cached.index();
        } else {
            byte[] data, sig;
            try {
                data = fetcher.get(storeUrl + "index.json");
                sig = fetcher.get(storeUrl + "index.json.sig");
            } catch (java.io.IOException e) {
                return new Result("store_unreachable");
            }
            if (data == null || sig == null || data.length > MAX_INDEX_BYTES) return new Result("store_unreachable");
            if (!verifySignature(data, sig, key)) return new Result("store_bad_signature");
            try {
                index = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException e) {
                return new Result("store_bad_signature");
            }
            cache.put(cacheKey, new Cached(index, clock.getAsLong()));
        }
        return checkFile(index, id, version, path, sha256Hex);
    }

    static Result checkFile(JsonObject index, String id, String version, String path, String sha256Hex) {
        if (!(index.get("scripts") instanceof com.google.gson.JsonArray scripts)) return new Result("store_unknown_script");
        for (var e : scripts) {
            if (!(e instanceof JsonObject s) || !id.equals(str(s, "id")) || !version.equals(str(s, "version"))) continue;
            if (!(s.get("files") instanceof com.google.gson.JsonArray files)) return new Result("store_unknown_script");
            for (var f : files) {
                if (f instanceof JsonObject fo && path.equals(str(fo, "path"))) {
                    return sha256Hex.equalsIgnoreCase(str(fo, "sha256")) ? new Result(null) : new Result("store_mismatch");
                }
            }
            return new Result("store_mismatch"); // not a file of that script
        }
        return new Result("store_unknown_script");
    }

    private static @Nullable String str(JsonObject o, String key) {
        return o.get(key) instanceof com.google.gson.JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    public void clearCache() {
        cache.clear();
    }
}
