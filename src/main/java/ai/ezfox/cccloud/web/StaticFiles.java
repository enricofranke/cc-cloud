package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudLog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Serves the bundled dashboard from the classpath folder {@code /cccloud_web/}. */
final class StaticFiles {
    static final String ROOT = "/cccloud_web";
    private static final int MAX_CACHED_FILE = 2 * 1024 * 1024;

    record File(byte[] data, String contentType, boolean immutable) {
    }

    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    static final byte[] PLACEHOLDER = """
        <!doctype html>
        <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>CC: Cloud</title></head>
        <body><h1>CC: Cloud</h1>
        <p>The dashboard server is running, but this build of the mod does not contain the web app
        (<code>/cccloud_web/index.html</code> is missing). Build it with <code>pnpm build</code> in <code>cloud/</code>.</p>
        </body></html>
        """.getBytes(StandardCharsets.UTF_8);

    private StaticFiles() {
    }

    /** Resolves a request path. Returns null for "not found" (only for paths that look like files). */
    static @Nullable File resolve(String path) {
        if (!isSafe(path)) return null;
        String clean = path.isEmpty() || path.endsWith("/") ? path + "index.html" : path;
        byte[] data = load(clean);
        if (data != null) return new File(data, contentType(clean), clean.startsWith("/assets/"));

        // SPA fallback: unknown paths without a file extension get index.html.
        String last = clean.substring(clean.lastIndexOf('/') + 1);
        if (last.contains(".") && !clean.endsWith("/index.html")) return null;
        byte[] index = load("/index.html");
        return new File(index != null ? index : PLACEHOLDER, "text/html; charset=utf-8", false);
    }

    static boolean isSafe(String path) {
        if (!path.startsWith("/") || path.length() > 256) return false;
        if (path.contains("..") || path.contains("\\") || path.contains("//") || path.contains("\0")) return false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "/._-~@+".indexOf(c) >= 0;
            if (!ok) return false;
        }
        return true;
    }

    private static byte @Nullable [] load(String path) {
        byte[] cached = CACHE.get(path);
        if (cached != null) return cached;
        try (InputStream in = StaticFiles.class.getResourceAsStream(ROOT + path)) {
            if (in == null) return null; // misses are not cached, so random paths cannot fill the cache
            byte[] data = in.readAllBytes();
            if (data.length <= MAX_CACHED_FILE && CACHE.size() < 512) CACHE.put(path, data);
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    static String contentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".json") || lower.endsWith(".webmanifest")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
