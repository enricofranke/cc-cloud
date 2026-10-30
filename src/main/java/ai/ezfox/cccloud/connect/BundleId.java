package ai.ezfox.cccloud.connect;

import ai.ezfox.cccloud.CloudLog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * The id of the dashboard bundle in this mod jar ({@code /cccloud_web/}). The Connect relay serves the official
 * dashboard itself and picks the files by this id, so a server can never put its own pages on its
 * {@code *.cccloud.dev} address: {@code sha256(for each file, sorted by path: "<path>\0<sha256 hex of the file>\n")},
 * first 16 hex digits. The website repository's {@code connect/scripts/add-bundle.mjs} computes the same for every
 * release it publishes. Pure Java, unit tested.
 */
public final class BundleId {
    private static volatile String current;

    private BundleId() {
    }

    /** The id of the given files (path relative to {@code cccloud_web/}, with forward slashes → content). */
    public static String of(Map<String, byte[]> files) {
        var sorted = new TreeMap<String, byte[]>(files); // String order = the script's byte order for ASCII paths
        var manifest = new StringBuilder();
        for (var e : sorted.entrySet()) manifest.append(e.getKey()).append('\0').append(sha256(e.getValue())).append('\n');
        return sha256(manifest.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The id of this jar's dashboard (computed once; "0000000000000000" if the files cannot be read). */
    public static String current() {
        String id = current;
        if (id == null) {
            id = compute();
            current = id;
        }
        return id;
    }

    private static String compute() {
        var files = new TreeMap<String, byte[]>();
        try {
            var info = net.neoforged.fml.ModList.get().getModFileById(ai.ezfox.cccloud.CCCloud.MODID);
            if (info != null) {
                info.getFile().getContents().visitContent("cccloud_web", (path, resource) -> {
                    String rel = path.startsWith("cccloud_web/") ? path.substring("cccloud_web/".length()) : path;
                    if (rel.isEmpty() || rel.endsWith("/")) return;
                    try {
                        files.put(rel, resource.readAllBytes());
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }
        } catch (RuntimeException e) {
            CloudLog.LOGGER.warn("[cccloud] Could not read the dashboard files for Connect: {}", e.toString());
        }
        if (files.isEmpty()) return "0000000000000000";
        return of(files);
    }
}
