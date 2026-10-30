package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CloudConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The {@code [web]} config as one immutable value: what the dashboard server was started or last reconfigured with
 * ({@link CloudWebServer#reconfigure}). A reload compares the file against it and applies all of it or nothing.
 *
 * @param port           the requested port ({@code cccloud.devWebPort} wins in dev runs; 0 = any free port)
 * @param tlsFingerprint SHA-256 of the certificate and key files, so renewed files are picked up by a reload
 */
public record WebSettings(boolean enabled, String bind, int port, String publicUrl, boolean allowInternet, List<String> trustedProxies,
                          boolean trustCloudflare, boolean compression, List<String> allowedHosts, String certPath, String keyPath,
                          String tlsFingerprint) {
    public WebSettings {
        trustedProxies = List.copyOf(trustedProxies);
        allowedHosts = List.copyOf(allowedHosts);
    }

    /** The current {@code [web]} section of the server config. */
    public static WebSettings fromConfig() {
        String cert = CloudConfig.get(CloudConfig.TLS_CERT_PATH).trim();
        String key = CloudConfig.get(CloudConfig.TLS_KEY_PATH).trim();
        var proxies = CloudConfig.get(CloudConfig.WEB_TRUSTED_PROXIES).stream().map(String::valueOf).toList();
        var hosts = CloudConfig.get(CloudConfig.WEB_ALLOWED_HOSTS).stream().map(String::valueOf).toList();
        return new WebSettings(CloudConfig.get(CloudConfig.WEB_ENABLED), CloudConfig.get(CloudConfig.WEB_BIND).trim(),
            // Dev runs (build.gradle) use another port so they do not collide with a game running on the same machine.
            Integer.getInteger("cccloud.devWebPort", CloudConfig.get(CloudConfig.WEB_PORT)), CloudConfig.get(CloudConfig.PUBLIC_URL).trim(),
            CloudConfig.get(CloudConfig.WEB_ALLOW_INTERNET), proxies, CloudConfig.get(CloudConfig.WEB_TRUST_CLOUDFLARE_HEADER),
            CloudConfig.get(CloudConfig.WEB_COMPRESSION), hosts, cert, key, fingerprint(cert, key));
    }

    public boolean tls() {
        return !certPath.isEmpty() || !keyPath.isEmpty();
    }

    /** Listens on the same address (bind + requested port). */
    public boolean sameAddress(WebSettings other) {
        return bind.equals(other.bind) && port == other.port;
    }

    /**
     * Everything an existing connection was accepted under: address, TLS on/off and its files, the LAN rule, trusted
     * proxies, the Cloudflare header and the Host list (with publicUrl). A change closes the open connections so they
     * reconnect under the new rules, like after a fresh start. Compression and renewed certificate files only concern new
     * connections.
     */
    public boolean sameConnectionRules(WebSettings other) {
        return sameAddress(other) && certPath.equals(other.certPath) && keyPath.equals(other.keyPath) && allowInternet == other.allowInternet
            && trustedProxies.equals(other.trustedProxies) && trustCloudflare == other.trustCloudflare && allowedHosts.equals(other.allowedHosts)
            && publicUrl.equals(other.publicUrl);
    }

    /** "" without TLS; otherwise a hash of both files ("unreadable" if one cannot be read: loading it fails anyway). */
    static String fingerprint(String cert, String key) {
        if (cert.isEmpty() && key.isEmpty()) return "";
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String file : new String[]{cert, key}) {
                digest.update(Files.readAllBytes(Path.of(file)));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | RuntimeException | NoSuchAlgorithmException e) {
            return "unreadable";
        }
    }
}
