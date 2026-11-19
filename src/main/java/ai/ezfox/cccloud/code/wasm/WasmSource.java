package ai.ezfox.cccloud.code.wasm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

/**
 * How a WebAssembly script is stored: the saved "source" of a script of language {@code wasm} is a small JSON object
 * with the compiled module in base64 and, optionally, the text it was compiled from, so the dashboard can show what
 * runs:
 *
 * <pre>{"format":"cccloud-wasm","wasm":"AGFzbQEAAAA…","source":"export function main(): void {…}","sourceLanguage":"assemblyscript"}</pre>
 *
 * The module is the truth: the source text is never compiled on the server and may be missing.
 */
public record WasmSource(byte[] module, @Nullable String source, @Nullable String sourceLanguage) {
    public static final String FORMAT = "cccloud-wasm";
    private static final byte[] MAGIC = {0, 'a', 's', 'm'};

    /** A stored source that cannot be read: what is wrong, in words for the editor. */
    public static final class Invalid extends Exception {
        public Invalid(String message) {
            super(message, null, false, false);
        }
    }

    public String encode() {
        var json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("wasm", Base64.getEncoder().encodeToString(module));
        if (source != null) json.addProperty("source", source);
        if (sourceLanguage != null) json.addProperty("sourceLanguage", sourceLanguage);
        return json.toString();
    }

    /** Reads a stored source and checks the sizes ({@code maxModule} bytes of WebAssembly, {@code maxText} chars of text). */
    public static WasmSource decode(String stored, int maxModule, int maxText) throws Invalid {
        JsonObject json;
        try {
            json = JsonParser.parseString(stored) instanceof JsonObject o ? o : null;
        } catch (JsonParseException | IllegalStateException e) {
            json = null;
        }
        if (json == null || !(json.get("format") != null && json.get("format").isJsonPrimitive() && FORMAT.equals(json.get("format").getAsString()))) {
            throw new Invalid("not a WebAssembly script (upload a .wasm file)");
        }
        String wasm = string(json, "wasm");
        if (wasm == null) throw new Invalid("the WebAssembly module is missing");
        if (wasm.length() > (maxModule + 2L) / 3 * 4) throw new Invalid("the module is larger than " + maxModule / 1024 + " KB");
        byte[] module;
        try {
            module = Base64.getDecoder().decode(wasm);
        } catch (IllegalArgumentException e) {
            throw new Invalid("the module is not valid base64");
        }
        if (module.length > maxModule) throw new Invalid("the module is larger than " + maxModule / 1024 + " KB");
        if (module.length < 8 || module[0] != MAGIC[0] || module[1] != MAGIC[1] || module[2] != MAGIC[2] || module[3] != MAGIC[3]) {
            throw new Invalid("not a WebAssembly module (a .wasm file starts with \\0asm)");
        }
        String source = string(json, "source");
        if (source != null && source.length() > maxText) throw new Invalid("the source text is longer than " + maxText / 1024 + " KB");
        String language = string(json, "sourceLanguage");
        if (language != null && (language.length() > 32 || !language.matches("[a-z0-9_+-]*"))) language = null;
        return new WasmSource(module, source, language);
    }

    private static @Nullable String string(JsonObject json, String key) {
        var e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}
