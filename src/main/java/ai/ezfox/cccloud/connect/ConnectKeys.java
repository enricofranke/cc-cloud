package ai.ezfox.cccloud.connect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * The server's Connect key: an Ed25519 key pair made by the mod the first time Connect is used. The relay binds a
 * dashboard name to the public key and the mod proves it holds the private key by signing the relay's challenge, so
 * nobody else can take over the name. The private key never leaves this folder:
 * {@code <server folder>/cccloud/connect/server-key.pem} (PKCS#8, mode 600) and {@code server-key.pub} (the raw public
 * key, base64). It lives next to the server, not in the world, so a shared world download does not carry it. Moving a
 * server means copying this folder too. Pure Java, unit tested.
 */
public final class ConnectKeys {
    static final String PRIVATE_FILE = "server-key.pem";
    static final String PUBLIC_FILE = "server-key.pub";
    /** DER prefix of an X.509 SubjectPublicKeyInfo for Ed25519; the raw 32-byte key follows. */
    private static final byte[] X509_PREFIX = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    private final PrivateKey privateKey;
    private final byte[] rawPublic;

    private ConnectKeys(PrivateKey privateKey, byte[] rawPublic) {
        this.privateKey = privateKey;
        this.rawPublic = rawPublic;
    }

    /** The public key as the relay knows it (base64 of the 32 raw bytes). */
    public String publicKey() {
        return Base64.getEncoder().encodeToString(rawPublic);
    }

    /** A short fingerprint for messages (never the key itself). */
    public String fingerprint() {
        return BundleId.sha256(("cccloud-connect-key\n" + publicKey()).getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    /** Base64 Ed25519 signature of {@code text}. */
    public String sign(String text) {
        try {
            var s = Signature.getInstance("Ed25519");
            s.initSign(privateKey);
            s.update(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    /** Loads the key pair from {@code dir}, or makes a new one there if none exists yet. */
    public static ConnectKeys loadOrCreate(Path dir) throws IOException {
        Path priv = dir.resolve(PRIVATE_FILE);
        Path pub = dir.resolve(PUBLIC_FILE);
        if (Files.exists(priv) || Files.exists(pub)) return load(priv, pub);
        return create(dir, priv, pub);
    }

    private static ConnectKeys load(Path priv, Path pub) throws IOException {
        if (!Files.isRegularFile(priv) || !Files.isRegularFile(pub)) {
            throw new IOException("Connect key incomplete: both " + priv.getFileName() + " and " + pub.getFileName() + " are needed in " + priv.getParent());
        }
        try {
            String pem = Files.readString(priv, StandardCharsets.US_ASCII);
            String body = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            var key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
            byte[] raw = Base64.getDecoder().decode(Files.readString(pub, StandardCharsets.US_ASCII).trim());
            if (raw.length != 32) throw new IOException("Connect public key has the wrong length");
            var keys = new ConnectKeys(key, raw);
            if (!keys.matches()) throw new IOException("Connect key files do not belong together (" + priv.getParent() + ")");
            restrict(priv);
            return keys;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Connect key unreadable: " + e.getMessage(), e);
        }
    }

    private static ConnectKeys create(Path dir, Path priv, Path pub) throws IOException {
        try {
            var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] raw = raw(pair.getPublic());
            Files.createDirectories(dir);
            restrictDir(dir);
            String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
            write(priv, pem, true);
            write(pub, Base64.getEncoder().encodeToString(raw) + "\n", false);
            return new ConnectKeys(pair.getPrivate(), raw);
        } catch (GeneralSecurityException e) {
            throw new IOException("Ed25519 is not available in this Java runtime", e);
        }
    }

    /** The private file is written owner-only from the start (temp file with mode 600, then moved into place). */
    private static void write(Path target, String text, boolean secret) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        try {
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(secret ? "rw-------" : "rw-r--r--")));
        } catch (UnsupportedOperationException e) {
            Files.createFile(tmp); // Windows: the folder's ACL applies
        }
        Files.writeString(tmp, text, StandardCharsets.US_ASCII);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException ignored) {
            // not a POSIX file system
        }
    }

    private static void restrictDir(Path dir) {
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException | UnsupportedOperationException ignored) {
            // not a POSIX file system
        }
    }

    static byte[] raw(PublicKey key) {
        byte[] der = key.getEncoded();
        if (der.length != 44 || !Arrays.equals(Arrays.copyOf(der, 12), X509_PREFIX)) throw new IllegalStateException("unexpected Ed25519 key encoding");
        return Arrays.copyOfRange(der, 12, 44);
    }

    /** True if the stored public key belongs to the private key (a signature check). */
    boolean matches() {
        try {
            byte[] der = new byte[44];
            System.arraycopy(X509_PREFIX, 0, der, 0, 12);
            System.arraycopy(rawPublic, 0, der, 12, 32);
            PublicKey pub = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
            var v = Signature.getInstance("Ed25519");
            v.initVerify(pub);
            v.update("check".getBytes(StandardCharsets.UTF_8));
            return v.verify(Base64.getDecoder().decode(sign("check")));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
