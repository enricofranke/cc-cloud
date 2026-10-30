package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The opt-in client jar download against a mock HTTP transport: verification, host and size limits, the cache. */
class VanillaAssetsTest {
    private static final String VERSION = "26.1.2";
    private static final URI VERSION_URI = URI.create("https://piston-meta.mojang.com/v1/packages/abc/26.1.2.json");
    private static final URI JAR_URI = URI.create("https://piston-data.mojang.com/v1/objects/def/client.jar");

    @TempDir
    Path cache;

    /** URI → canned response (status, location, body); every request is recorded. */
    private final Map<URI, Object[]> routes = new HashMap<>();
    private final List<URI> requests = new ArrayList<>();
    private final VanillaAssets.Transport transport = (uri, timeout) -> {
        requests.add(uri);
        var r = routes.get(uri);
        if (r == null) return new VanillaAssets.Response(404, null, 0, new ByteArrayInputStream(new byte[0]));
        byte[] body = (byte[]) r[2];
        long length = r.length > 3 ? (long) r[3] : body.length;
        return new VanillaAssets.Response((int) r[0], (String) r[1], length, new ByteArrayInputStream(body));
    };
    private byte[] jar;

    /** A tiny stand-in "client jar" (our own bytes, no Mojang content). */
    private static byte[] fakeJar() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            var px = new int[16 * 16];
            java.util.Arrays.fill(px, 0xFF808080);
            for (var e : Map.of("assets/minecraft/textures/block/stone.png", PngCodec.encode(16, 16, px),
                "assets/minecraft/models/block/cube.json", "{}".getBytes(StandardCharsets.UTF_8),
                "assets/minecraft/sounds/ambient.ogg", new byte[]{1, 2, 3},
                "net/minecraft/client/Main.class", new byte[]{(byte) 0xCA, (byte) 0xFE}).entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private void route(URI uri, byte[] body) {
        routes.put(uri, new Object[]{200, null, body});
    }

    private void serve(String jarSha1, long jarSize, String versionSha1) {
        String versionJson = "{\"id\":\"26.1.2\",\"downloads\":{\"client\":{\"sha1\":\"" + jarSha1 + "\",\"size\":" + jarSize + ",\"url\":\"" + JAR_URI + "\"},"
            + "\"server\":{\"sha1\":\"0\",\"size\":1,\"url\":\"https://piston-data.mojang.com/x/server.jar\"}}}";
        byte[] versionBytes = versionJson.getBytes(StandardCharsets.UTF_8);
        String manifest = "{\"latest\":{},\"versions\":[{\"id\":\"26.1.1\",\"url\":\"https://piston-meta.mojang.com/v1/packages/x/26.1.1.json\",\"sha1\":\"x\"},"
            + "{\"id\":\"26.1.2\",\"type\":\"release\",\"url\":\"" + VERSION_URI + "\",\"sha1\":\"" + (versionSha1 != null ? versionSha1 : VanillaAssets.sha1(versionBytes)) + "\"}]}";
        route(VanillaAssets.MANIFEST, manifest.getBytes(StandardCharsets.UTF_8));
        route(VERSION_URI, versionBytes);
        route(JAR_URI, jar);
    }

    @BeforeEach
    void setUp() throws IOException {
        jar = fakeJar();
    }

    @Test
    void downloadsVerifiesCachesAndReadsOnlyTexturesAndModels() throws Exception {
        serve(VanillaAssets.sha1(jar), jar.length, null);
        var path = VanillaAssets.ensureClientJar(cache, VERSION, transport);
        assertEquals(cache.resolve("minecraft-26.1.2-client.jar"), path);
        assertArrayEquals(jar, Files.readAllBytes(path));
        assertEquals(List.of(VanillaAssets.MANIFEST, VERSION_URI, JAR_URI), requests);
        assertTrue(Files.readString(cache.resolve("minecraft-26.1.2-client.json")).contains(VanillaAssets.sha1(jar)));

        try (var assets = VanillaAssets.open(path)) {
            assertNotNull(assets.read("assets/minecraft/textures/block/stone.png"));
            assertNotNull(assets.read("assets/minecraft/models/block/cube.json"));
            assertNull(assets.read("assets/minecraft/sounds/ambient.ogg"), "only items, models and textures");
            assertNull(assets.read("net/minecraft/client/Main.class"));
            assertNull(assets.read("assets/minecraft/textures/../../net/minecraft/client/Main.class"));
        }

        // The next start uses the cache without any network access (and re-checks its hash).
        requests.clear();
        routes.clear();
        assertEquals(path, VanillaAssets.ensureClientJar(cache, VERSION, transport));
        assertTrue(requests.isEmpty());

        // A cached jar that was changed on disk is downloaded again.
        Files.write(path, new byte[]{1, 2, 3});
        serve(VanillaAssets.sha1(jar), jar.length, null);
        VanillaAssets.ensureClientJar(cache, VERSION, transport);
        assertArrayEquals(jar, Files.readAllBytes(path));
    }

    @Test
    void rejectsAJarWithTheWrongHashAndKeepsNothing() throws Exception {
        serve("0123456789abcdef0123456789abcdef01234567", jar.length, null);
        var e = assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport));
        assertTrue(e.getMessage().contains("SHA-1"), e.getMessage());
        try (var files = Files.list(cache)) {
            assertEquals(List.of(), files.toList(), "no jar, no partial file, no cache info");
        }
    }

    @Test
    void rejectsAVersionJsonWithTheWrongHash() {
        serve(VanillaAssets.sha1(jar), jar.length, "0000000000000000000000000000000000000000");
        var e = assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport));
        assertTrue(e.getMessage().contains("SHA-1"), e.getMessage());
        assertFalse(requests.contains(JAR_URI), "the jar is never fetched");
    }

    @Test
    void followsRedirectsOnlyToMojangOverHttps() throws Exception {
        serve(VanillaAssets.sha1(jar), jar.length, null);
        routes.put(JAR_URI, new Object[]{302, "https://evil.example.com/client.jar", new byte[0]});
        var e = assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport));
        assertTrue(e.getMessage().contains("evil.example.com"), e.getMessage());
        assertTrue(requests.stream().noneMatch(u -> u.getHost().contains("evil")), "never contacted");

        routes.put(JAR_URI, new Object[]{301, "http://piston-data.mojang.com/v1/objects/def/client.jar", new byte[0]});
        assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport), "no downgrade to HTTP");

        var mirror = URI.create("https://launcher.mojang.com/v1/objects/def/client.jar");
        routes.put(JAR_URI, new Object[]{302, mirror.toString(), new byte[0]});
        route(mirror, jar);
        assertArrayEquals(jar, Files.readAllBytes(VanillaAssets.ensureClientJar(cache, VERSION, transport)), "Mojang to Mojang is fine");
    }

    @Test
    void refusesUrlsThatAreNotMojangsHttps() {
        for (var bad : List.of("http://piston-meta.mojang.com/x.json", "https://piston-meta.mojang.com.evil.com/x.json", "https://evil.com/piston-meta.mojang.com",
            "https://user@piston-data.mojang.com/x", "https://piston-data.mojang.com:8443/x", "ftp://piston-data.mojang.com/x", "file:///etc/passwd")) {
            assertThrows(IOException.class, () -> VanillaAssets.checkUri(URI.create(bad)), bad);
        }
        assertDoesNotThrow(() -> VanillaAssets.checkUri(JAR_URI));
        assertDoesNotThrow(() -> VanillaAssets.checkUri(URI.create("https://PISTON-META.mojang.com:443/x")));
    }

    @Test
    void refusesAManifestPointingElsewhere() {
        serve(VanillaAssets.sha1(jar), jar.length, null);
        route(VanillaAssets.MANIFEST, ("{\"versions\":[{\"id\":\"26.1.2\",\"url\":\"https://example.com/26.1.2.json\",\"sha1\":\"x\"}]}")
            .getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport));
        assertEquals(List.of(VanillaAssets.MANIFEST), requests);
    }

    @Test
    void enforcesSizeLimits() {
        // Declared bigger than the version JSON says: refused before reading.
        serve(VanillaAssets.sha1(jar), jar.length, null);
        routes.put(JAR_URI, new Object[]{200, null, jar, (long) jar.length + 100});
        assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport));
        // Sends more than declared.
        routes.put(JAR_URI, new Object[]{200, null, java.util.Arrays.copyOf(jar, jar.length + 10), -1L});
        assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport));
        // A version JSON claiming an absurd jar size.
        serve(VanillaAssets.sha1(jar), VanillaAssets.MAX_JAR_BYTES + 1, null);
        assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport));
    }

    @Test
    void failsForUnknownVersionsAndHttpErrors() {
        serve(VanillaAssets.sha1(jar), jar.length, null);
        var e = assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, "99.9", transport));
        assertTrue(e.getMessage().contains("99.9"), e.getMessage());
        routes.put(VanillaAssets.MANIFEST, new Object[]{503, null, new byte[0]});
        assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, VERSION, transport));
        assertThrows(IOException.class, () -> VanillaAssets.ensureClientJar(cache, "../../etc", transport), "odd version names");
    }
}
