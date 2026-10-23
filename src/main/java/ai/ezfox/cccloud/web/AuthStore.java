package ai.ezfox.cccloud.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * Pairing codes (per mainframe, and v0.6 account codes per player), pending device approvals, device tokens and PINs.
 * Roles live in the {@link ai.ezfox.cccloud.access.AccessStore}. Plain Java (no Minecraft classes) so it can be unit
 * tested.
 * <p>
 * Reads are thread-safe (concurrent maps of immutable records). Mutations are synchronized; in the mod they happen on
 * the server thread, except {@link #verifyPin} which runs on a background thread because hashing is slow on purpose.
 */
public final class AuthStore {
    public static final long CODE_TTL_MS = 10 * 60 * 1000L;
    public static final long APPROVAL_TTL_MS = 2 * 60 * 1000L;
    public static final long DEVICE_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000L;
    public static final long LAST_USED_GRANULARITY_MS = 60 * 60 * 1000L;
    public static final int MAX_DEVICE_NAME = 40;
    public static final int MAX_PENDING = 64;
    public static final int PIN_MAX_FAILURES = 5;
    public static final long PIN_LOCK_MS = 5 * 60 * 1000L;
    public static final int PIN_ITERATIONS = 120_000;
    /** No 0/O, 1/I/L. */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    public record PairingCode(String code, long expiresAt, @Nullable UUID issuer) {
    }

    /**
     * A browser ("device": phone, tablet or PC). Only the SHA-256 of its token is stored; it acts with the role of
     * {@code player} (resolved live by the {@link ai.ezfox.cccloud.access.AccessResolver}).
     *
     * @param mainframeId null for an account token (every mainframe where the player has a role); set for tokens from a
     *                    mainframe pairing code and for v0.3 device tokens, which are limited to that one mainframe
     * @param canControl  false caps the role at viewer (read-only devices)
     * @param type        desktop|phone|tablet|other from the User-Agent at pairing time (informational only)
     */
    public record Device(String tokenHash, @Nullable UUID mainframeId, String name, boolean canControl, long created, long lastUsed,
                         UUID player, String playerName, String type, String lastIp) {
        /** v0.3 shape: a device limited to one mainframe. */
        public Device(String tokenHash, UUID mainframeId, String name, boolean canControl, long created, long lastUsed, UUID player, String playerName) {
            this(tokenHash, mainframeId, name, canControl, created, lastUsed, player, playerName, "other", "");
        }

        Device withLastUsed(long time, String ip) {
            return new Device(tokenHash, mainframeId, name, canControl, created, time, player, playerName, type, ip);
        }

        public boolean isAccount() {
            return mainframeId == null;
        }

        /** The public id (see {@link AuthStore#deviceId}). */
        public String id() {
            return deviceId(this);
        }
    }

    public enum PendingState { PENDING, APPROVED, DENIED }

    /**
     * A device waiting for in-game approval (memory only).
     *
     * @param mainframeId the mainframe of a mainframe pairing code, null for an account code
     * @param account     the player of an account code (only that player may approve), null for mainframe codes
     */
    public record Pending(String id, @Nullable UUID mainframeId, @Nullable UUID account, String deviceName, String ip, @Nullable UUID issuer,
                          long expiresAt, PendingState state, @Nullable String token, @Nullable Device device, String type) {
        Pending with(PendingState state, @Nullable String token, @Nullable Device device) {
            return new Pending(id, mainframeId, account, deviceName, ip, issuer, expiresAt, state, token, device, type);
        }

        public boolean isAccount() {
            return account != null;
        }
    }

    /** v0.3 owner + trusted list (only read from old saves and migrated into the AccessStore). */
    public record Acl(@Nullable UUID owner, Set<UUID> trusted) {
    }

    public record PinHash(String salt, String hash, int iterations) {
    }

    public enum PinResult { OK, WRONG, LOCKED, NOT_SET }

    /** Result of a PIN check: attempts left before the lockout (WRONG) or seconds until it ends (LOCKED). */
    public record PinCheck(PinResult result, int attemptsLeft, long retryAfterSeconds) {
    }

    private record PinFailures(int count, long lockedUntil) {
    }

    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    private final Runnable onChange;
    private final Map<UUID, PairingCode> codes = new ConcurrentHashMap<>();
    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    /** v0.6 account codes per player (memory only, 10 minutes). */
    private final Map<UUID, PairingCode> accountCodes = new ConcurrentHashMap<>();
    private final Map<UUID, PinHash> pins = new ConcurrentHashMap<>();
    private final Map<String, PinFailures> pinFailures = new ConcurrentHashMap<>();

    public AuthStore(LongSupplier clock, Runnable onChange) {
        this.clock = clock;
        this.onChange = onChange;
    }

    // ---- persistence views -------------------------------------------------------------------------------------------

    public Map<UUID, PairingCode> codes() {
        return codes;
    }

    public Map<String, Device> deviceMap() {
        return devices;
    }

    public Map<UUID, PinHash> pins() {
        return pins;
    }

    public synchronized void load(Map<UUID, PairingCode> codes, Iterable<Device> devices, Map<UUID, PinHash> pins) {
        this.codes.putAll(codes);
        for (var device : devices) this.devices.put(device.tokenHash(), device);
        this.pins.putAll(pins);
    }

    // ---- pairing codes -----------------------------------------------------------------------------------------------

    /** Returns the current (still valid) pairing code of a mainframe, creating a fresh one if needed. */
    public synchronized PairingCode currentCode(UUID mainframeId, @Nullable UUID issuer) {
        var existing = codes.get(mainframeId);
        long now = clock.getAsLong();
        if (existing != null && existing.expiresAt() > now) {
            if (issuer != null && !issuer.equals(existing.issuer())) {
                existing = new PairingCode(existing.code(), existing.expiresAt(), issuer);
                codes.put(mainframeId, existing);
                onChange.run();
            }
            return existing;
        }
        var code = new PairingCode(newCode(), now + CODE_TTL_MS, issuer);
        codes.put(mainframeId, code);
        onChange.run();
        return code;
    }

    public synchronized void dropCode(UUID mainframeId) {
        if (codes.remove(mainframeId) != null) onChange.run();
    }

    /** v0.6: the current account code of a player ({@code /cccloud link}), creating a fresh one if needed. */
    public synchronized PairingCode accountCode(UUID player) {
        var existing = accountCodes.get(player);
        long now = clock.getAsLong();
        if (existing != null && existing.expiresAt() > now) return existing;
        var code = new PairingCode(newCode(), now + CODE_TTL_MS, player);
        accountCodes.put(player, code);
        return code;
    }

    /** The player's account code if one is active (does not create one). */
    public @Nullable PairingCode activeAccountCode(UUID player) {
        var code = accountCodes.get(player);
        return code != null && code.expiresAt() > clock.getAsLong() ? code : null;
    }

    public synchronized void dropAccountCode(UUID player) {
        accountCodes.remove(player);
    }
    /**
     * Step 1 of pairing: checks the code (single use, 10 minutes) and creates a pending approval.
     *
     * @return the pending request, or null if the code is unknown or expired.
     */
    public @Nullable Pending pair(String input, String deviceName, String ip) {
        return pair(input, deviceName, ip, "");
    }

    /**
     * Step 1 of pairing: checks the code (single use, 10 minutes) and creates a pending approval. Accepts mainframe
     * pairing codes and v0.6 account codes.
     *
     * @param userAgent only used to guess the device type (informational)
     * @return the pending request, or null if the code is unknown or expired.
     */
    public synchronized @Nullable Pending pair(String input, String deviceName, String ip, String userAgent) {
        String normalized = normalizeCode(input);
        if (normalized.length() != 8) return null;
        long now = clock.getAsLong();
        pending.values().removeIf(p -> p.expiresAt() <= now && p.state() != PendingState.APPROVED);
        if (pending.size() >= MAX_PENDING) return null;
        byte[] given = normalized.getBytes(StandardCharsets.US_ASCII);
        UUID match = null;
        PairingCode matched = null;
        for (var entry : codes.entrySet()) {
            boolean equal = MessageDigest.isEqual(entry.getValue().code().getBytes(StandardCharsets.US_ASCII), given);
            if (equal && entry.getValue().expiresAt() > now) {
                match = entry.getKey();
                matched = entry.getValue();
            }
        }
        UUID account = null;
        for (var entry : accountCodes.entrySet()) {
            boolean equal = MessageDigest.isEqual(entry.getValue().code().getBytes(StandardCharsets.US_ASCII), given);
            if (equal && entry.getValue().expiresAt() > now) account = entry.getKey();
        }
        String type = deviceType(userAgent);
        if (match != null) {
            codes.remove(match);
            onChange.run();
            var request = new Pending(randomId(), match, null, sanitizeName(deviceName), ip, matched.issuer(), now + APPROVAL_TTL_MS,
                PendingState.PENDING, null, null, type);
            pending.put(request.id(), request);
            return request;
        }
        if (account != null) {
            accountCodes.remove(account);
            var request = new Pending(randomId(), null, account, sanitizeName(deviceName), ip, account, now + APPROVAL_TTL_MS,
                PendingState.PENDING, null, null, type);
            pending.put(request.id(), request);
            return request;
        }
        return null;
    }

    public @Nullable Pending pending(String id) {
        var p = pending.get(id);
        if (p == null) return null;
        if (p.expiresAt() <= clock.getAsLong() && p.state() == PendingState.PENDING) {
            pending.remove(id);
            return null;
        }
        return p;
    }

    /**
     * Step 2: a player approves the device. The token is bound to that player. Account requests can only be approved
     * by the player whose account code was used.
     */
    public synchronized @Nullable Pending approve(String id, UUID player, String playerName, boolean canControl) {
        var p = pending(id);
        if (p == null || p.state() != PendingState.PENDING) return null;
        if (p.isAccount() && !player.equals(p.account())) return null;
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long now = clock.getAsLong();
        var device = new Device(hash(token), p.mainframeId(), uniqueName(p.mainframeId(), player, p.deviceName()), canControl, now, now, player, playerName,
            p.type(), p.ip());
        devices.put(device.tokenHash(), device);
        // Keep the result around briefly so the browser can pick up the token.
        var approved = new Pending(p.id(), p.mainframeId(), p.account(), device.name(), p.ip(), p.issuer(), now + APPROVAL_TTL_MS, PendingState.APPROVED,
            token, device, p.type());
        pending.put(id, approved);
        onChange.run();
        return approved;
    }

    public synchronized @Nullable Pending deny(String id) {
        var p = pending(id);
        if (p == null || p.state() != PendingState.PENDING) return null;
        var denied = p.with(PendingState.DENIED, null, null);
        pending.put(id, denied);
        return denied;
    }

    /** Step 3: the browser polls. An approved token is handed out exactly once. */
    public synchronized @Nullable Pending poll(String id) {
        var p = pending(id);
        if (p == null) return null;
        if (p.state() != PendingState.PENDING) pending.remove(id);
        if (p.expiresAt() <= clock.getAsLong()) return null;
        return p;
    }

    public List<Pending> pendingFor(UUID mainframeId) {
        return pending.values().stream().filter(p -> mainframeId.equals(p.mainframeId()) && p.state() == PendingState.PENDING).toList();
    }

    // ---- devices -----------------------------------------------------------------------------------------------------

    /** Looks up a device by its raw token (constant-time hash comparison, expired devices are rejected). */
    public @Nullable Device authenticate(@Nullable String token) {
        if (token == null || token.isEmpty() || token.length() > 128) return null;
        String hash = hash(token);
        var device = devices.get(hash);
        if (device == null) return null;
        if (!MessageDigest.isEqual(device.tokenHash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII))) return null;
        if (device.lastUsed() + DEVICE_EXPIRY_MS < clock.getAsLong()) return null;
        return device;
    }

    public boolean isValid(String tokenHash) {
        var device = devices.get(tokenHash);
        return device != null && device.lastUsed() + DEVICE_EXPIRY_MS >= clock.getAsLong();
    }

    /** Records use of a device (at most once per hour or when the IP changed, to keep saves cheap). */
    public synchronized void touch(String tokenHash, @Nullable String ip) {
        var device = devices.get(tokenHash);
        long now = clock.getAsLong();
        if (device == null) return;
        boolean ipChanged = ip != null && !ip.isEmpty() && !ip.equals(device.lastIp());
        if (ipChanged || now - device.lastUsed() > LAST_USED_GRANULARITY_MS) {
            devices.put(tokenHash, device.withLastUsed(now, ipChanged ? ip : device.lastIp()));
            onChange.run();
        }
    }

    public void touch(String tokenHash) {
        touch(tokenHash, null);
    }

    /** Removes devices unused for 30 days. Returns the number removed. */
    public synchronized int expireDevices() {
        long cutoff = clock.getAsLong() - DEVICE_EXPIRY_MS;
        int before = devices.size();
        devices.values().removeIf(d -> d.lastUsed() < cutoff);
        int removed = before - devices.size();
        if (removed > 0) onChange.run();
        return removed;
    }

    /** Devices limited to this mainframe (mainframe pairing codes and v0.3 tokens). */
    public List<Device> devices(UUID mainframeId) {
        var list = new ArrayList<Device>();
        for (var device : devices.values()) if (mainframeId.equals(device.mainframeId())) list.add(device);
        list.sort((a, b) -> Long.compare(a.created(), b.created()));
        return list;
    }

    /** v0.6: every browser of a player (account tokens and mainframe-limited tokens). */
    public List<Device> devicesOf(UUID player) {
        var list = new ArrayList<Device>();
        for (var device : devices.values()) if (device.player().equals(player)) list.add(device);
        list.sort((a, b) -> Long.compare(a.created(), b.created()));
        return list;
    }

    /** Revokes a device by its public id, if {@code allowed} accepts it. Returns the removed device or null. */
    public synchronized @Nullable Device revokeDevice(String id, java.util.function.Predicate<Device> allowed) {
        if (id.length() != DEVICE_ID_LENGTH) return null;
        Device match = null;
        for (var device : devices.values()) {
            if (!device.tokenHash().startsWith(id)) continue;
            if (match != null) return null;
            match = device;
        }
        if (match == null || !allowed.test(match)) return null;
        devices.remove(match.tokenHash());
        onChange.run();
        return match;
    }

    /** Revokes one device by name (case-insensitive) or all devices with "all". Returns the removed devices. */
    public synchronized List<Device> revoke(UUID mainframeId, String nameOrAll) {
        var removed = new ArrayList<Device>();
        boolean all = nameOrAll.equalsIgnoreCase("all");
        for (var device : devices(mainframeId)) {
            if (all || device.name().equalsIgnoreCase(nameOrAll.trim())) {
                devices.remove(device.tokenHash());
                removed.add(device);
            }
        }
        if (!removed.isEmpty()) onChange.run();
        return removed;
    }

    /** Revokes one device by the id shown in the in-game screen (first {@link #DEVICE_ID_LENGTH} hex chars of its hash). */
    public synchronized @Nullable Device revokeById(UUID mainframeId, String id) {
        if (id.length() != DEVICE_ID_LENGTH) return null;
        Device match = null;
        for (var device : devices(mainframeId)) {
            if (!device.tokenHash().startsWith(id)) continue;
            if (match != null) return null; // ambiguous (practically impossible with 64 bits)
            match = device;
        }
        if (match == null) return null;
        devices.remove(match.tokenHash());
        onChange.run();
        return match;
    }

    public static final int DEVICE_ID_LENGTH = 16;

    /** The public id of a device for the in-game screen. Not secret: it is a prefix of the token's SHA-256. */
    public static String deviceId(Device device) {
        return device.tokenHash().substring(0, DEVICE_ID_LENGTH);
    }

    public synchronized void forgetMainframe(UUID mainframeId) {
        pins.remove(mainframeId);
        codes.remove(mainframeId);
        devices.values().removeIf(d -> mainframeId.equals(d.mainframeId()));
        pending.values().removeIf(p -> mainframeId.equals(p.mainframeId()));
        onChange.run();
    }

    // ---- PIN ---------------------------------------------------------------------------------------------------------

    public static boolean isValidPin(@Nullable String pin) {
        return pin != null && pin.matches("[0-9]{4,8}");
    }

    /** Hashes a PIN (slow, call off the server thread). */
    public PinHash hashPin(String pin) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return new PinHash(HexFormat.of().formatHex(salt), HexFormat.of().formatHex(pbkdf2(pin, salt, PIN_ITERATIONS)), PIN_ITERATIONS);
    }

    public synchronized void setPin(UUID mainframeId, @Nullable PinHash pin) {
        if (pin == null) pins.remove(mainframeId);
        else pins.put(mainframeId, pin);
        onChange.run();
    }

    public boolean hasPin(UUID mainframeId) {
        return pins.containsKey(mainframeId);
    }

    /** Verifies a PIN for a device, with lockout after 5 failures. Slow on purpose: call off the server thread. */
    public PinResult verifyPin(UUID mainframeId, String deviceHash, @Nullable String pin) {
        return checkPin(mainframeId, deviceHash, pin).result();
    }

    /** Like {@link #verifyPin} but with attempts left / retry-after details for the client. */
    public PinCheck checkPin(UUID mainframeId, String deviceHash, @Nullable String pin) {
        var stored = pins.get(mainframeId);
        if (stored == null) return new PinCheck(PinResult.NOT_SET, 0, 0);
        long now = clock.getAsLong();
        var failures = pinFailures.get(deviceHash);
        if (failures != null && failures.lockedUntil() > now) return locked(failures.lockedUntil(), now);
        boolean ok = pin != null && isValidPin(pin) && MessageDigest.isEqual(
            HexFormat.of().parseHex(stored.hash()),
            pbkdf2(pin, HexFormat.of().parseHex(stored.salt()), stored.iterations()));
        if (ok) {
            pinFailures.remove(deviceHash);
            return new PinCheck(PinResult.OK, PIN_MAX_FAILURES, 0);
        }
        var updated = pinFailures.compute(deviceHash, (k, f) -> {
            int count = (f == null || f.lockedUntil() != 0 && f.lockedUntil() <= now ? 0 : f.count()) + 1;
            return new PinFailures(count, count >= PIN_MAX_FAILURES ? now + PIN_LOCK_MS : 0);
        });
        return updated.lockedUntil() > now ? locked(updated.lockedUntil(), now)
            : new PinCheck(PinResult.WRONG, PIN_MAX_FAILURES - updated.count(), 0);
    }

    private static PinCheck locked(long until, long now) {
        return new PinCheck(PinResult.LOCKED, 0, Math.max(1, (until - now + 999) / 1000));
    }

    /** Seconds until a device's PIN lockout ends, or 0 if it is not locked. */
    public long pinLockedFor(String deviceHash) {
        var failures = pinFailures.get(deviceHash);
        long now = clock.getAsLong();
        return failures != null && failures.lockedUntil() > now ? (failures.lockedUntil() - now + 999) / 1000 : 0;
    }

    private static byte[] pbkdf2(String pin, byte[] salt, int iterations) {
        try {
            var spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String formatCode(String code) {
        return code.length() == 8 ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }

    public static String normalizeCode(String input) {
        var sb = new StringBuilder();
        for (int i = 0; i < input.length() && sb.length() <= 8; i++) {
            char c = input.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    /** Trims, removes control characters and limits the length of a device name. */
    public static String sanitizeName(@Nullable String name) {
        if (name == null) return "Device";
        var sb = new StringBuilder();
        name.trim().codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT).forEach(sb::appendCodePoint);
        String clean = sb.toString().trim();
        if (clean.length() > MAX_DEVICE_NAME) clean = clean.substring(0, MAX_DEVICE_NAME).trim();
        return clean.isEmpty() ? "Device" : clean;
    }

    /** Device type from the User-Agent (informational only, never used for decisions). */
    public static String deviceType(@Nullable String ua) {
        if (ua == null || ua.isEmpty()) return "other";
        if (ua.contains("iPad") || ua.contains("Tablet") || (ua.contains("Android") && !ua.contains("Mobile"))) return "tablet";
        if (ua.contains("iPhone") || ua.contains("Mobile")) return "phone";
        if (ua.contains("Windows") || ua.contains("Macintosh") || ua.contains("Linux") || ua.contains("X11") || ua.contains("CrOS")) return "desktop";
        return "other";
    }

    private String uniqueName(@Nullable UUID mainframeId, UUID player, String name) {
        var scope = mainframeId != null ? devices(mainframeId) : devicesOf(player).stream().filter(Device::isAccount).toList();
        var existing = scope.stream().map(d -> d.name().toLowerCase()).toList();
        if (!existing.contains(name.toLowerCase()) && !name.equalsIgnoreCase("all")) return name;
        for (int i = 2; ; i++) {
            String candidate = name + " (" + i + ")";
            if (!existing.contains(candidate.toLowerCase())) return candidate;
        }
    }

    private String randomId() {
        byte[] raw = new byte[18];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String newCode() {
        while (true) {
            var sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            String code = sb.toString();
            if (codes.values().stream().noneMatch(c -> c.code().equals(code)) && accountCodes.values().stream().noneMatch(c -> c.code().equals(code))) return code;
        }
    }
}
