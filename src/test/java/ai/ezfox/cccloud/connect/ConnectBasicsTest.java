package ai.ezfox.cccloud.connect;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pure parts of CC: Cloud Connect: frames, bundle ids, names, keys, settings. */
class ConnectBasicsTest {
    /**
     * The frame the relay's protocol.ts builds for the same request (see its "shared test vector" test):
     * [01][01020304][head length][head JSON][body 01 02].
     */
    @Test
    void decodesTheRelaysRequestFrame() {
        String head = "{\"m\":\"GET\",\"p\":\"/api/config\",\"h\":[[\"accept\",\"*/*\"]],\"ip\":\"203.0.113.7\",\"host\":\"abc.cccloud.dev\"}";
        byte[] json = head.getBytes(StandardCharsets.UTF_8);
        var buf = Unpooled.buffer();
        buf.writeByte(0x01).writeInt(0x01020304).writeInt(json.length).writeBytes(json).writeByte(1).writeByte(2);
        var frame = TunnelProtocol.parse(buf);
        assertNotNull(frame);
        assertEquals(TunnelProtocol.HTTP_REQUEST, frame.type());
        assertEquals(0x01020304L, frame.stream());
        var request = TunnelProtocol.request(frame.payload());
        assertNotNull(request);
        assertEquals("GET", request.head().method());
        assertEquals("/api/config", request.head().path());
        assertEquals("203.0.113.7", request.head().ip());
        assertEquals("abc.cccloud.dev", request.head().host());
        assertEquals(List.of(Map.entry("accept", "*/*")), request.head().headers());
        assertEquals(2, request.body().readableBytes());
        // high stream ids stay unsigned
        var high = TunnelProtocol.frame(ByteBufAllocator.DEFAULT, TunnelProtocol.CANCEL, 0xFFFFFFF0L, (io.netty.buffer.ByteBuf) null);
        assertEquals(0xFFFFFFF0L, TunnelProtocol.parse(high).stream());
        high.release();
    }

    @Test
    void refusesMalformedHeads() {
        assertNull(TunnelProtocol.head("{}"));
        assertNull(TunnelProtocol.head("not json"));
        // header injection through a value or a name
        assertNull(TunnelProtocol.head("{\"m\":\"GET\",\"p\":\"/\",\"h\":[[\"x\",\"a\\r\\nHost: evil\"]],\"ip\":\"1.2.3.4\",\"host\":\"a.cccloud.dev\"}"));
        assertNull(TunnelProtocol.head("{\"m\":\"GET\",\"p\":\"/\",\"h\":[[\"x y\",\"a\"]],\"ip\":\"1.2.3.4\",\"host\":\"a.cccloud.dev\"}"));
        assertNull(TunnelProtocol.head("{\"m\":\"GET\",\"p\":\"http://evil/\",\"h\":[],\"ip\":\"1.2.3.4\",\"host\":\"a.cccloud.dev\"}"));
        assertNull(TunnelProtocol.head("{\"m\":\"G T\",\"p\":\"/\",\"h\":[],\"ip\":\"1.2.3.4\",\"host\":\"a.cccloud.dev\"}"));
        var buf = Unpooled.buffer().writeInt(1000).writeBytes("{}".getBytes(StandardCharsets.UTF_8));
        assertNull(TunnelProtocol.request(buf), "a head length beyond the frame");
    }

    @Test
    void encodesResponsesAndCloses() {
        var head = TunnelProtocol.responseHead(200, List.of(Map.entry("content-type", "application/json")));
        assertEquals("{\"s\":200,\"h\":[[\"content-type\",\"application/json\"]]}", head.toString());
        var close = TunnelProtocol.closeFrame(ByteBufAllocator.DEFAULT, 7, 4403, "no");
        var f = TunnelProtocol.parse(close);
        assertEquals(TunnelProtocol.WS_CLOSE, f.type());
        assertEquals(4403, TunnelProtocol.closeCode(f.payload()));
        assertEquals("no", TunnelProtocol.closeReason(f.payload()));
        close.release();
        assertEquals("cccloud-connect-v1\nabc\nn0nce\na2V5", TunnelProtocol.signedText("abc", "n0nce", "a2V5"));
    }

    /** The same vector as the relay's add-bundle.mjs computes for these three files. */
    @Test
    void bundleIdMatchesTheReleaseScript() {
        var files = Map.of(
            "index.html", "<!doctype html><title>x</title>".getBytes(StandardCharsets.UTF_8),
            "assets/a-1.js", "console.log(1)".getBytes(StandardCharsets.UTF_8),
            "sw.js", "self.x=1".getBytes(StandardCharsets.UTF_8));
        assertEquals("86f6e5593521c7f8", BundleId.of(files));
    }

    @Test
    void names() {
        for (String ok : List.of("abc", "my-base", "x".repeat(32), "a1b")) assertTrue(ConnectNames.valid(ok), ok);
        for (String bad : List.of("ab", "x".repeat(33), "-abc", "abc-", "a--b", "ABC", "a_b", "a.b", "")) assertFalse(ConnectNames.valid(bad), bad);
        assertTrue(ConnectNames.reserved("www"));
        assertTrue(ConnectNames.reserved("my-cccloud"));
        assertFalse(ConnectNames.reserved("my-base"));
        assertEquals("enricos-base", ConnectNames.slug("Enrico's Base"));
        assertEquals("uber-welt", ConnectNames.slug("  Über  Welt!! "));
        for (String from : List.of("Enrico's Base", "", "§§§", "x".repeat(80), "Minecraft-Login")) {
            String s = ConnectNames.suggest(from);
            assertTrue(ConnectNames.valid(s), s);
            assertFalse(ConnectNames.reserved(s), s);
        }
        assertTrue(ConnectNames.suggest("Enrico's Base").startsWith("enricos-base-"));
    }

    @Test
    void keysAreMadeOnceOwnerOnlyAndSign(@TempDir Path dir) throws Exception {
        Path keyDir = dir.resolve("cccloud/connect");
        var keys = ConnectKeys.loadOrCreate(keyDir);
        Path priv = keyDir.resolve(ConnectKeys.PRIVATE_FILE);
        assertTrue(Files.readString(priv).startsWith("-----BEGIN PRIVATE KEY-----"));
        if (Files.getFileAttributeView(priv, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(priv)));
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(keyDir)));
        }
        var again = ConnectKeys.loadOrCreate(keyDir);
        assertEquals(keys.publicKey(), again.publicKey(), "the same key after a restart");
        assertEquals(32, Base64.getDecoder().decode(keys.publicKey()).length);
        // the relay verifies with the raw public key
        byte[] raw = Base64.getDecoder().decode(keys.publicKey());
        byte[] der = new byte[44];
        System.arraycopy(HexFormat.of().parseHex("302a300506032b6570032100"), 0, der, 0, 12);
        System.arraycopy(raw, 0, der, 12, 32);
        var v = Signature.getInstance("Ed25519");
        v.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der)));
        v.update("hello".getBytes(StandardCharsets.UTF_8));
        assertTrue(v.verify(Base64.getDecoder().decode(keys.sign("hello"))));
        assertEquals(16, keys.fingerprint().length());

        // a public key of another pair is noticed, a missing half is an error (never silently a new key)
        var other = ConnectKeys.loadOrCreate(dir.resolve("other"));
        Files.writeString(keyDir.resolve(ConnectKeys.PUBLIC_FILE), other.publicKey() + "\n");
        assertThrows(java.io.IOException.class, () -> ConnectKeys.loadOrCreate(keyDir));
        Files.delete(keyDir.resolve(ConnectKeys.PUBLIC_FILE));
        assertThrows(java.io.IOException.class, () -> ConnectKeys.loadOrCreate(keyDir));
    }

    @Test
    void relayAddresses() {
        assertEquals(URI.create("wss://connect.cccloud.dev"), ConnectSettings.relayUri("wss://connect.cccloud.dev/"));
        assertEquals(URI.create("ws://127.0.0.1:8788"), ConnectSettings.relayUri("ws://127.0.0.1:8788"));
        for (String bad : List.of("ws://connect.cccloud.dev", "https://connect.cccloud.dev", "wss://user@x.dev", "wss://x.dev/path", "wss://x.dev?q=1", "", "wss://")) {
            assertNull(ConnectSettings.relayUri(bad), bad);
        }
        var settings = new ConnectSettings(true, "abc", URI.create("wss://connect.cccloud.dev"));
        assertEquals(URI.create("wss://connect.cccloud.dev/tunnel/abc"), settings.tunnelUri("abc"));
        assertEquals("https://abc.cccloud.dev", ConnectClient.expectedUrl(settings, "abc"));
        assertEquals("http://abc.localhost:8788", ConnectClient.expectedUrl(new ConnectSettings(true, "abc", URI.create("ws://connect.localhost:8788")), "abc"));
    }
}
