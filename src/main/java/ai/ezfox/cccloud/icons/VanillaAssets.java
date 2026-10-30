package ai.ezfox.cccloud.icons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla item models and textures for the server-side icon renderer on a dedicated server, whose jar has none
 * (docs/SECURITY.md "Vanilla textures for item icons"). Two sources, both opt-in by the server admin:
 * <ul>
 *   <li>{@code vanillaAssetsJar}: a Minecraft client jar the admin already has;</li>
 *   <li>{@code downloadVanillaAssets = true}: the official client jar of the running version, downloaded once from
 *       Mojang's launcher servers ({@link #ensureClientJar}) and cached under {@code <server>/cccloud/cache/}.</li>
 * </ul>
 * The download only talks HTTPS to Mojang's hosts ({@link #HOSTS}, also for redirects), with timeouts and size
 * limits, and checks the SHA-1 the version manifest gives for the version JSON and the jar. The jar is only read
 * locally, and only {@code assets/minecraft/items|models|textures} ({@link #read}); nothing from it is redistributed
 * or served, only our 32×32 renders are.
 */
public final class VanillaAssets implements AutoCloseable {
    /** Mojang's official launcher meta endpoint (version manifest v2). */
    public static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    /** The only hosts the download talks to (also after a redirect). */
    public static final Set<String> HOSTS = Set.of("piston-meta.mojang.com", "piston-data.mojang.com", "launchermeta.mojang.com", "launcher.mojang.com");
    /** The only parts of the jar that are ever read. */
    public static final List<String> READABLE = List.of("assets/minecraft/items/", "assets/minecraft/models/", "assets/minecraft/textures/");
    static final long MAX_MANIFEST_BYTES = 8L * 1024 * 1024;
    static final long MAX_VERSION_BYTES = 4L * 1024 * 1024;
    static final long MAX_JAR_BYTES = 256L * 1024 * 1024;
    static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
    static final int MAX_REDIRECTS = 3;
    static final Duration META_TIMEOUT = Duration.ofSeconds(30);
    static final Duration JAR_TIMEOUT = Duration.ofMinutes(10);

    private final ZipFile zip;
    private final Path path;

    private VanillaAssets(ZipFile zip, Path path) {
        this.zip = zip;
        this.path = path;
    }

    /** Opens a client jar for reading; fails if it is not a zip with vanilla textures. */
    public static VanillaAssets open(Path jar) throws IOException {
        var zip = new ZipFile(jar.toFile());
        if (zip.getEntry("assets/minecraft/textures/block/stone.png") == null && zip.getEntry("assets/minecraft/models/block/cube.json") == null) {
            zip.close();
            throw new IOException(jar + " has no Minecraft textures (is it the client jar?)");
        }
        return new VanillaAssets(zip, jar);
    }

    public Path path() {
        return path;
    }

    /** A file under {@link #READABLE}; null for anything else, missing files and files over 4 MB. */
    public byte @Nullable [] read(String file) {
        if (!readable(file)) return null;
        try {
            var entry = zip.getEntry(file);
            if (entry == null || entry.isDirectory() || entry.getSize() > MAX_ENTRY_BYTES) return null;
            try (var in = zip.getInputStream(entry)) {
                var bytes = in.readNBytes(MAX_ENTRY_BYTES + 1);
                return bytes.length > MAX_ENTRY_BYTES ? null : bytes;
            }
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    static boolean readable(String file) {
        if (file.contains("..") || file.contains("\\") || file.contains("//")) return false;
        for (var prefix : READABLE) if (file.startsWith(prefix)) return true;
        return false;
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    // ---- download ----------------------------------------------------------------------------------------------------

    /** One HTTP GET without following redirects. Swappable for tests. */
    public interface Transport {
        Response get(URI uri, Duration timeout) throws IOException, InterruptedException;
    }

    /** A response: status, the Location header (redirects), the declared length (−1 if unknown) and the body. */
    public record Response(int status, @Nullable String location, long length, InputStream body) implements Closeable {
        @Override
        public void close() throws IOException {
            body.close();
        }
    }

    /** What the cache holds next to the jar: where it came from and its hash, checked on every start. */
    record CacheInfo(String version, String sha1, long size, String url) {
    }

    /** The real transport: java.net.http over HTTPS, redirects not followed (the caller checks them). */
    public static Transport https() {
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build();
        return (uri, timeout) -> {
            var request = HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", "cccloud-icons").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return new Response(response.statusCode(), response.headers().firstValue("Location").orElse(null),
                response.headers().firstValueAsLong("Content-Length").orElse(-1), response.body());
        };
    }

    /**
     * The client jar of {@code version} in {@code cacheDir}: the cached copy if its SHA-1 still matches, else
     * downloaded from Mojang and verified (version manifest → version JSON → client jar).
     *
     * @throws IOException if the version is unknown, a host or redirect is not Mojang's, a size limit is exceeded or a
     *                     hash does not match (nothing is left in the cache then)
     */
    public static Path ensureClientJar(Path cacheDir, String version, Transport transport) throws IOException, InterruptedException {
        if (!version.matches("[A-Za-z0-9._+-]{1,64}")) throw new IOException("odd version name " + version);
        Files.createDirectories(cacheDir);
        var jar = cacheDir.resolve("minecraft-" + version + "-client.jar");
        var infoFile = cacheDir.resolve("minecraft-" + version + "-client.json");
        var cached = readInfo(infoFile);
        if (cached != null && Files.isRegularFile(jar) && Files.size(jar) == cached.size() && cached.sha1().equalsIgnoreCase(sha1(jar))) return jar;
        Files.deleteIfExists(jar);
        Files.deleteIfExists(infoFile);

        // 1. The version manifest (HTTPS from Mojang; it lists every version with the URL and SHA-1 of its JSON).
        var manifest = parse(fetch(transport, MANIFEST, MAX_MANIFEST_BYTES, META_TIMEOUT));
        String versionUrl = null, versionSha1 = null;
        for (var e : manifest.getAsJsonArray("versions")) {
            var v = e.getAsJsonObject();
            if (version.equals(v.get("id").getAsString())) {
                versionUrl = v.get("url").getAsString();
                versionSha1 = v.has("sha1") ? v.get("sha1").getAsString() : null;
                break;
            }
        }
        if (versionUrl == null) throw new IOException("Minecraft " + version + " is not in Mojang's version manifest");
        var versionUri = checkUri(URI.create(versionUrl));

        // 2. The version JSON, checked against the manifest's SHA-1.
        byte[] versionJson = fetch(transport, versionUri, MAX_VERSION_BYTES, META_TIMEOUT);
        if (versionSha1 != null && !versionSha1.equalsIgnoreCase(sha1(versionJson))) throw new IOException("SHA-1 mismatch for the version JSON of " + version);
        var client = parse(versionJson).getAsJsonObject("downloads").getAsJsonObject("client");
        String jarSha1 = client.get("sha1").getAsString();
        long size = client.get("size").getAsLong();
        var jarUri = checkUri(URI.create(client.get("url").getAsString()));
        if (!jarSha1.matches("[0-9a-fA-F]{40}")) throw new IOException("bad SHA-1 in the version JSON");
        if (size <= 0 || size > MAX_JAR_BYTES) throw new IOException("client jar size " + size + " is over the limit of " + MAX_JAR_BYTES);

        // 3. The jar itself: streamed to a temp file, exact size and SHA-1, then moved into place.
        var temp = cacheDir.resolve(jar.getFileName() + ".part");
        try {
            String got;
            try (var out = Files.newOutputStream(temp)) {
                got = download(transport, jarUri, size, out);
            }
            if (Files.size(temp) != size) throw new IOException("client jar has " + Files.size(temp) + " bytes, expected " + size);
            if (!jarSha1.equalsIgnoreCase(got)) throw new IOException("SHA-1 mismatch for the client jar of " + version);
            Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        var info = new JsonObject();
        info.addProperty("version", version);
        info.addProperty("sha1", jarSha1.toLowerCase(Locale.ROOT));
        info.addProperty("size", size);
        info.addProperty("url", jarUri.toString());
        info.addProperty("note", "Official Minecraft client jar from Mojang, downloaded because downloadVanillaAssets = true. Read locally "
            + "for item icon textures only; do not redistribute.");
        Files.writeString(infoFile, info.toString(), StandardCharsets.UTF_8);
        return jar;
    }

    /** HTTPS and one of Mojang's hosts, no user info, default port; anything else is refused. */
    static URI checkUri(URI uri) throws IOException {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOSTS.contains(host) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IOException("refusing to download from " + uri + " (only HTTPS from Mojang's servers)");
        }
        return uri;
    }

    /** GET with redirects only to Mojang's hosts over HTTPS; the body must fit {@code limit}. */
    static byte[] fetch(Transport transport, URI uri, long limit, Duration timeout) throws IOException, InterruptedException {
        var out = new ByteArrayOutputStream();
        stream(transport, uri, limit, false, timeout, out);
        return out.toByteArray();
    }

    /** Streams the jar into {@code out} (at most {@code size} bytes) and returns its SHA-1 (hex). */
    static String download(Transport transport, URI uri, long size, OutputStream out) throws IOException, InterruptedException {
        return stream(transport, uri, size, true, JAR_TIMEOUT, out);
    }

    private static String stream(Transport transport, URI uri, long limit, boolean hash, Duration timeout, OutputStream out) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        URI current = checkUri(uri);
        for (int hop = 0; ; hop++) {
            try (var response = transport.get(current, timeout)) {
                int status = response.status();
                if (status >= 300 && status < 400) {
                    if (hop >= MAX_REDIRECTS || response.location() == null) throw new IOException("too many redirects from " + uri);
                    current = checkUri(current.resolve(response.location()));
                    continue;
                }
                if (status != 200) throw new IOException("HTTP " + status + " from " + current);
                if (response.length() > limit) throw new IOException(current + " is " + response.length() + " bytes, over the limit of " + limit);
                return copy(response.body(), out, limit, deadline, hash, current);
            }
        }
    }

    private static String copy(InputStream in, OutputStream out, long limit, long deadline, boolean hash, URI uri) throws IOException {
        MessageDigest digest = hash ? sha1Digest() : null;
        // A stalled connection is cut at the deadline (closing the stream ends a blocked read).
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "cccloud-download-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.schedule(() -> {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }, Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        try {
            var buf = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = in.read(buf)) > 0; ) {
                total += n;
                if (total > limit) throw new IOException(uri + " sent more than " + limit + " bytes");
                if (System.nanoTime() > deadline) throw new IOException("download from " + uri + " timed out");
                if (digest != null) digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
            if (System.nanoTime() > deadline) throw new IOException("download from " + uri + " timed out");
            return digest != null ? HexFormat.of().formatHex(digest.digest()) : "";
        } finally {
            watchdog.shutdownNow();
        }
    }

    private static @Nullable CacheInfo readInfo(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            var json = parse(Files.readAllBytes(file));
            return new CacheInfo(json.get("version").getAsString(), json.get("sha1").getAsString(), json.get("size").getAsLong(), json.get("url").getAsString());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static JsonObject parse(byte[] json) throws IOException {
        try {
            return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("not valid JSON: " + e.getMessage());
        }
    }

    static String sha1(byte[] bytes) {
        return HexFormat.of().formatHex(sha1Digest().digest(bytes));
    }

    static String sha1(Path file) throws IOException {
        var digest = sha1Digest();
        try (var in = Files.newInputStream(file)) {
            var buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) digest.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
