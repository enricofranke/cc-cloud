package ai.ezfox.cccloud.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Dashboard presets of the store (docs/SPEC.md "Dashboard presets"). The dashboard asks the mod, never the store: this
 * class reads the {@code presets} list of the index {@link StoreVerifier} has verified, fetches a preset and checks its
 * size and SHA-256 against that list. A preset is layout data; the dashboard parses it strictly and whatever it saves
 * passes {@code LayoutValidator} like any other layout. Fetching is injected (the mod uses the SSRF-safe push client).
 * Pure Java, unit tested. Blocking: call off the server thread.
 */
public final class PresetStore {
    public static final int MAX_PRESET_BYTES = 64 * 1024;
    /** More entries than this are not read: the index is signed, but the answer should stay small all the same. */
    static final int MAX_ENTRIES = 500;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,38}[a-z0-9]");
    /** Where the index may send us: below {@code presets/}, no way up. */
    private static final Pattern FILE = Pattern.compile("presets/[a-z0-9][a-z0-9-]{1,38}[a-z0-9]/[0-9A-Za-z][0-9A-Za-z.-]{0,63}\\.json");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    /** What the dashboard gets of an index entry. */
    private static final Set<String> LISTED = Set.of("id", "version", "name", "description", "author", "license", "tags", "icon", "color", "scripts", "pages", "cards", "slots");

    /** The HTTP status and the JSON body of an answer. */
    public record Result(int status, JsonObject body) {
        public static Result failed(int status, String error) {
            var body = new JsonObject();
            body.addProperty("error", error);
            return new Result(status, body);
        }

        public @Nullable String error() {
            return body.get("error") instanceof JsonPrimitive p ? p.getAsString() : null;
        }
    }

    private final StoreVerifier verifier;
    private final StoreVerifier.Fetcher fetcher;

    public PresetStore(StoreVerifier verifier, StoreVerifier.Fetcher fetcher) {
        this.verifier = verifier;
        this.fetcher = fetcher;
    }

    private static Result failure(String error) {
        return Result.failed(error.equals("store_not_configured") ? 503 : 502, error);
    }

    private static JsonArray entries(JsonObject index) {
        // a store from before presets has no such list: that is an empty list, not an error
        return index.get("presets") instanceof JsonArray list ? list : new JsonArray();
    }

    private static @Nullable String str(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    /** The presets of the signed index, without the fields only the server needs. */
    public Result list(String storeUrl, @Nullable String publicKeyBase64) {
        var index = verifier.index(storeUrl, publicKeyBase64);
        if (index.error() != null) return failure(index.error());
        var out = new JsonArray();
        for (JsonElement e : entries(index.json())) {
            if (out.size() >= MAX_ENTRIES) break;
            if (!(e instanceof JsonObject o) || str(o, "id") == null || !ID.matcher(str(o, "id")).matches()) continue;
            var entry = new JsonObject();
            for (var field : o.entrySet()) if (LISTED.contains(field.getKey())) entry.add(field.getKey(), field.getValue());
            out.add(entry);
        }
        var body = new JsonObject();
        body.add("presets", out);
        return new Result(200, body);
    }

    /**
     * One preset, only if it is what the signed index says it is. Errors: those of {@link StoreVerifier#index},
     * {@code unknown_preset}, {@code store_unreachable}, {@code store_mismatch}.
     */
    public Result get(String storeUrl, @Nullable String publicKeyBase64, String id) {
        var index = verifier.index(storeUrl, publicKeyBase64);
        if (index.error() != null) return failure(index.error());
        JsonObject entry = null;
        for (JsonElement e : entries(index.json())) {
            if (e instanceof JsonObject o && id.equals(str(o, "id"))) {
                entry = o;
                break;
            }
        }
        if (entry == null || !ID.matcher(id).matches()) return Result.failed(404, "unknown_preset");
        String file = str(entry, "file");
        String sha = str(entry, "sha256");
        String version = str(entry, "version");
        if (file == null || sha == null || version == null || !FILE.matcher(file).matches() || !SHA256.matcher(sha).matches()) return failure("store_mismatch");
        byte[] data;
        try {
            data = fetcher.get(storeUrl + file);
        } catch (java.io.IOException e) {
            return failure("store_unreachable");
        }
        if (data == null) return failure("store_unreachable");
        if (data.length > MAX_PRESET_BYTES || !sha.equals(ScriptFiles.sha256(data))) return failure("store_mismatch");
        JsonObject preset;
        try {
            preset = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            return failure("store_mismatch");
        }
        if (!id.equals(str(preset, "id")) || !version.equals(str(preset, "version"))) return failure("store_mismatch");
        var body = new JsonObject();
        body.add("preset", preset);
        return new Result(200, body);
    }
}
