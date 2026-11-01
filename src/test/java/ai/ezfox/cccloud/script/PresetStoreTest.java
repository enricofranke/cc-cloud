package ai.ezfox.cccloud.script;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Store presets: what reaches the dashboard is what the signed index vouches for, and nothing else. */
class PresetStoreTest {
    static final String URL = "https://store.example.org/store/";
    static final String PRESET = "{\"format\":1,\"id\":\"door-panel\",\"version\":\"1.0.0\",\"name\":\"Doors\",\"slots\":[],\"pages\":[]}";

    final Map<String, byte[]> files = new HashMap<>();
    final AtomicInteger fetches = new AtomicInteger();
    String key;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Signs an index with a dev key pair generated for the test only. */
    void publish(String index) throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] spki = keys.getPublic().getEncoded();
        key = Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(spki, spki.length - 32, spki.length));
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(bytes(index));
        files.put(URL + "index.json", bytes(index));
        files.put(URL + "index.json.sig", Base64.getEncoder().encode(signer.sign()));
    }

    String entry(String id, String version, String file, String sha) {
        return "{\"id\":\"" + id + "\",\"version\":\"" + version + "\",\"name\":{\"en\":\"Doors\",\"de\":\"Tueren\"},\"tags\":[\"door\"],\"pages\":1,\"cards\":1,\"slots\":[],"
            + "\"sha256\":\"" + sha + "\",\"size\":10,\"file\":\"" + file + "\",\"secret\":\"x\"}";
    }

    void publishPreset() throws Exception {
        files.put(URL + "presets/door-panel/1.0.0.json", bytes(PRESET));
        publish("{\"format\":1,\"scripts\":[],\"presets\":[" + entry("door-panel", "1.0.0", "presets/door-panel/1.0.0.json", ScriptFiles.sha256(bytes(PRESET))) + "]}");
    }

    PresetStore store() {
        StoreVerifier.Fetcher fetch = url -> {
            fetches.incrementAndGet();
            return files.get(url);
        };
        return new PresetStore(new StoreVerifier(fetch, () -> 0), fetch);
    }

    @Test
    void listsWhatTheSignedIndexSays() throws Exception {
        publishPreset();
        var result = store().list(URL, key);
        assertEquals(200, result.status());
        var entry = result.body().getAsJsonArray("presets").get(0).getAsJsonObject();
        assertEquals("door-panel", entry.get("id").getAsString());
        assertEquals("1.0.0", entry.get("version").getAsString());
        assertFalse(entry.has("file"), "the store's paths are the server's business");
        assertFalse(entry.has("sha256"));
        assertFalse(entry.has("secret"), "unknown fields do not pass");
    }

    @Test
    void aStoreWithoutPresetsIsEmptyNotBroken() throws Exception {
        publish("{\"format\":1,\"scripts\":[]}");
        var result = store().list(URL, key);
        assertEquals(200, result.status());
        assertEquals(0, result.body().getAsJsonArray("presets").size());
        assertEquals("unknown_preset", store().get(URL, key, "door-panel").error());
    }

    @Test
    void scriptInstallsIgnoreTheNewList() throws Exception {
        // what an older mod does with an index that has presets: it reads `scripts` and finds its file
        String sha = "ab".repeat(32);
        publish("{\"format\":1,\"scripts\":[{\"id\":\"a-script\",\"version\":\"1.0.0\",\"files\":[{\"path\":\"a.lua\",\"sha256\":\"" + sha + "\"}]}],\"presets\":[{\"id\":\"x\"}]}");
        var verifier = new StoreVerifier(files::get, () -> 0);
        assertTrue(verifier.check(URL, key, "a-script", "1.0.0", "a.lua", sha).ok());
    }

    @Test
    void returnsAVerifiedPreset() throws Exception {
        publishPreset();
        var result = store().get(URL, key, "door-panel");
        assertEquals(200, result.status());
        assertEquals("door-panel", result.body().getAsJsonObject("preset").get("id").getAsString());
    }

    @Test
    void refusesAPresetThatIsNotTheOneInTheIndex() throws Exception {
        publishPreset();
        files.put(URL + "presets/door-panel/1.0.0.json", bytes(PRESET.replace("Doors", "Lights")));
        var result = store().get(URL, key, "door-panel");
        assertEquals("store_mismatch", result.error());
        assertEquals(502, result.status());
    }

    @Test
    void refusesWhatOnlyLooksSigned() throws Exception {
        publishPreset();
        var bad = store().get(URL, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "door-panel");
        assertEquals("store_bad_signature", bad.error());
        assertEquals("store_bad_signature", store().list(URL, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=").error());
        assertEquals("store_not_configured", store().list(URL, "").error());
        assertEquals(503, store().list(URL, "").status());
    }

    @Test
    void anIndexEntryCannotPointOutOfThePresetsFolder() throws Exception {
        String sha = ScriptFiles.sha256(bytes(PRESET));
        for (String file : new String[] {"presets/door-panel/../../secret.json", "../secret.json", "https://evil.example/x.json", "presets/door-panel/1.0.0.lua"}) {
            files.put(URL + file, bytes(PRESET));
            publish("{\"format\":1,\"presets\":[" + entry("door-panel", "1.0.0", file, sha) + "]}");
            fetches.set(0);
            assertEquals("store_mismatch", store().get(URL, key, "door-panel").error(), file);
            assertEquals(2, fetches.get(), "only the index was fetched for " + file);
        }
    }

    @Test
    void thePresetMustCarryItsOwnIdAndVersion() throws Exception {
        String other = PRESET.replace("door-panel", "other-preset");
        files.put(URL + "presets/door-panel/1.0.0.json", bytes(other));
        publish("{\"format\":1,\"presets\":[" + entry("door-panel", "1.0.0", "presets/door-panel/1.0.0.json", ScriptFiles.sha256(bytes(other))) + "]}");
        assertEquals("store_mismatch", store().get(URL, key, "door-panel").error());
        String older = PRESET.replace("1.0.0", "0.9.0");
        files.put(URL + "presets/door-panel/1.0.0.json", bytes(older));
        publish("{\"format\":1,\"presets\":[" + entry("door-panel", "1.0.0", "presets/door-panel/1.0.0.json", ScriptFiles.sha256(bytes(older))) + "]}");
        assertEquals("store_mismatch", store().get(URL, key, "door-panel").error(), "no quiet downgrade");
    }

    @Test
    void aPresetPastTheLimitIsRefused() throws Exception {
        String big = PRESET.replace("\"Doors\"", "\"" + "x".repeat(PresetStore.MAX_PRESET_BYTES) + "\"");
        files.put(URL + "presets/door-panel/1.0.0.json", bytes(big));
        publish("{\"format\":1,\"presets\":[" + entry("door-panel", "1.0.0", "presets/door-panel/1.0.0.json", ScriptFiles.sha256(bytes(big))) + "]}");
        assertEquals("store_mismatch", store().get(URL, key, "door-panel").error());
    }

    @Test
    void garbageInsideTheHashIsNoCrash() throws Exception {
        String junk = "not json";
        files.put(URL + "presets/door-panel/1.0.0.json", bytes(junk));
        publish("{\"format\":1,\"presets\":[" + entry("door-panel", "1.0.0", "presets/door-panel/1.0.0.json", ScriptFiles.sha256(bytes(junk))) + "]}");
        assertEquals("store_mismatch", store().get(URL, key, "door-panel").error());
    }

    @Test
    void anUnreachableStoreIsSaidSo() throws Exception {
        publishPreset();
        files.remove(URL + "presets/door-panel/1.0.0.json");
        assertEquals("store_unreachable", store().get(URL, key, "door-panel").error());
        files.clear();
        assertEquals("store_unreachable", store().list(URL, key).error());
    }

    @Test
    void bodyOfAFailureIsJustTheError() {
        JsonObject body = PresetStore.Result.failed(502, "store_mismatch").body();
        assertEquals(1, body.size());
    }
}
