package ai.ezfox.cccloud.script;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Security review L9: the signed store index (a dev key pair generated for the test only). */
class StoreVerifierTest {
    static final String URL = "https://store.example.org/store/";
    static final String LUA = "print('guard')\n";

    record Store(String publicKey, Map<String, byte[]> files) {
    }

    static Store store(String fileSha) throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] spki = keys.getPublic().getEncoded();
        String pub = Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(spki, spki.length - 32, spki.length));
        byte[] index = ("{\"format\":1,\"scripts\":[{\"id\":\"reactor-guard\",\"version\":\"1.2.0\",\"files\":[{\"path\":\"main.lua\",\"size\":15,\"sha256\":\""
            + fileSha + "\"}]}]}").getBytes(StandardCharsets.UTF_8);
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(index);
        var files = new HashMap<String, byte[]>();
        files.put(URL + "index.json", index);
        files.put(URL + "index.json.sig", Base64.getEncoder().encode(signer.sign()));
        return new Store(pub, files);
    }

    @Test
    void signedIndexDecidesInstalls() throws Exception {
        String sha = ScriptFiles.sha256(LUA.getBytes(StandardCharsets.UTF_8));
        var s = store(sha);
        var fetches = new AtomicInteger();
        var now = new AtomicLong();
        var verifier = new StoreVerifier(url -> {
            fetches.incrementAndGet();
            return s.files().get(url);
        }, now::get);
        assertTrue(verifier.check(URL, s.publicKey(), "reactor-guard", "1.2.0", "main.lua", sha).ok());
        assertEquals("store_mismatch", verifier.check(URL, s.publicKey(), "reactor-guard", "1.2.0", "main.lua", "00".repeat(32)).error(), "changed content");
        assertEquals("store_mismatch", verifier.check(URL, s.publicKey(), "reactor-guard", "1.2.0", "other.lua", sha).error());
        assertEquals("store_unknown_script", verifier.check(URL, s.publicKey(), "reactor-guard", "9.9.9", "main.lua", sha).error());
        assertEquals(2, fetches.get(), "index and signature fetched once, then cached");
        now.addAndGet(StoreVerifier.CACHE_MS + 1);
        verifier.check(URL, s.publicKey(), "reactor-guard", "1.2.0", "main.lua", sha);
        assertEquals(4, fetches.get(), "fetched again after the cache time");
        assertEquals("store_not_configured", verifier.check(URL, "", "reactor-guard", "1.2.0", "main.lua", sha).error());
    }

    @Test
    void tamperedIndexOrWrongKeyIsRefused() throws Exception {
        String sha = ScriptFiles.sha256(LUA.getBytes(StandardCharsets.UTF_8));
        var s = store(sha);
        var other = store(sha);
        var wrongKey = new StoreVerifier(url -> s.files().get(url), () -> 0);
        assertEquals("store_bad_signature", wrongKey.check(URL, other.publicKey(), "reactor-guard", "1.2.0", "main.lua", sha).error());
        var tampered = new HashMap<>(s.files());
        tampered.put(URL + "index.json", new String(s.files().get(URL + "index.json"), StandardCharsets.UTF_8).replace(sha, "11".repeat(32))
            .getBytes(StandardCharsets.UTF_8));
        var v = new StoreVerifier(tampered::get, () -> 0);
        assertEquals("store_bad_signature", v.check(URL, s.publicKey(), "reactor-guard", "1.2.0", "main.lua", "11".repeat(32)).error());
        var down = new StoreVerifier(url -> null, () -> 0);
        assertEquals("store_unreachable", down.check(URL, s.publicKey(), "reactor-guard", "1.2.0", "main.lua", sha).error());
        assertNull(StoreVerifier.publicKey("not base64!"));
        assertNull(StoreVerifier.publicKey(Base64.getEncoder().encodeToString(new byte[31])));
    }
}
