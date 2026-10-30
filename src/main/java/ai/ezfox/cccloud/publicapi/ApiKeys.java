package ai.ezfox.cccloud.publicapi;

import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.IpUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * API keys of the public API (docs/SPEC.md v0.10): {@code cck_} + 32 random bytes (base64url). Only the SHA-256 of a
 * key is stored, the key itself is returned exactly once by {@link #create}. Each key belongs to one player and one
 * mainframe and carries scopes, an optional expiry and an optional IP allow-list. Plain Java (no Minecraft classes),
 * unit tested. Reads are thread-safe (a concurrent map of immutable records); mutations are synchronized.
 */
public final class ApiKeys {
    public static final String PREFIX = "cck_";
    public static final int MAX_NAME = 40;
    public static final int MAX_IPS = 8;
    /** Allowed lifetimes in seconds (0 = never expires). */
    public static final Set<Long> EXPIRES_IN = Set.of(3_600L, 86_400L, 604_800L, 2_592_000L, 7_776_000L);
    /** Expired keys are listed (as expired) for this long, then deleted. */
    public static final long EXPIRED_KEEP_MS = 7L * 24 * 3_600_000L;
    public static final long LAST_USED_GRANULARITY_MS = 60_000L;
    private static final Pattern TOKEN = Pattern.compile("cck_[A-Za-z0-9_-]{43}");

    /**
     * One key. {@code id} = the first 16 hex characters of its hash (public, used to revoke it).
     *
     * @param expires ms since epoch, 0 = never
     * @param ips     allowed client IPs or CIDRs, empty = any
     */
    public record Key(String id, String hash, String name, UUID player, String playerName, UUID mainframeId, Set<ApiScope> scopes, long created,
                      long expires, List<String> ips, long lastUsed, String lastIp) {
        public Key {
            scopes = scopes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(scopes));
            ips = List.copyOf(ips);
        }

        public boolean expired(long now) {
            return expires != 0 && expires <= now;
        }

        Key used(long time, String ip) {
            return new Key(id, hash, name, player, playerName, mainframeId, scopes, created, expires, ips, time, ip);
        }
    }

    public record Created(String secret, Key key) {
    }

    public enum Status { OK, UNKNOWN, EXPIRED }

    public record Check(Status status, @Nullable Key key) {
    }

    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    private final Runnable onChange;
    private final Map<String, Key> keys = new ConcurrentHashMap<>();

    public ApiKeys(LongSupplier clock, Runnable onChange) {
        this.clock = clock;
        this.onChange = onChange;
    }

    public Collection<Key> all() {
        return keys.values();
    }

    public synchronized void load(Iterable<Key> stored) {
        for (var key : stored) keys.put(key.hash(), key);
    }

    /** A token that looks like an API key ({@code cck_} + 43 base64url characters). Anything else is never looked up. */
    public static boolean looksLikeKey(@Nullable String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    /** Looks a key up by hash and compares in constant time; expired keys are reported as such. */
    public Check authenticate(@Nullable String token) {
        if (!looksLikeKey(token)) return new Check(Status.UNKNOWN, null);
        String hash = AuthStore.hash(token);
        var key = keys.get(hash);
        if (key == null || !MessageDigest.isEqual(key.hash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII))) {
            return new Check(Status.UNKNOWN, null);
        }
        if (key.expired(clock.getAsLong())) return new Check(Status.EXPIRED, key);
        return new Check(Status.OK, key);
    }

    /** The stored key with this hash (open event streams re-check that their key still exists), or null. */
    public @Nullable Key byHash(String hash) {
        return keys.get(hash);
    }

    /** The client IP is allowed by the key's list (empty list: any IP). */
    public static boolean ipAllowed(Key key, @Nullable String ip) {
        if (key.ips().isEmpty()) return true;
        var cidrs = IpUtil.parseList(key.ips(), bad -> {
        });
        return IpUtil.matches(cidrs, ip);
    }

    /** A valid allow-list entry: an IP or a CIDR. */
    public static boolean validIp(@Nullable String entry) {
        if (entry == null || entry.isEmpty() || entry.length() > 64) return false;
        try {
            IpUtil.Cidr.parse(entry.trim());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public int count(UUID player) {
        int n = 0;
        for (var key : keys.values()) if (key.player().equals(player)) n++;
        return n;
    }

    /** Keys of a mainframe, oldest first. */
    public List<Key> forMainframe(UUID mainframeId) {
        var list = new ArrayList<Key>();
        for (var key : keys.values()) if (key.mainframeId().equals(mainframeId)) list.add(key);
        list.sort((a, b) -> Long.compare(a.created(), b.created()));
        return list;
    }

    /**
     * Creates a key. The caller validated name, scopes, lifetime and IPs and checked the per-player limit.
     *
     * @param expiresIn seconds, 0 = never
     */
    public synchronized Created create(UUID player, String playerName, UUID mainframeId, String name, Set<ApiScope> scopes, long expiresIn, List<String> ips) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash = AuthStore.hash(secret);
        long now = clock.getAsLong();
        var key = new Key(hash.substring(0, 16), hash, name, player, playerName, mainframeId, scopes, now, expiresIn > 0 ? now + expiresIn * 1000 : 0,
            ips, 0, "");
        keys.put(hash, key);
        onChange.run();
        return new Created(secret, key);
    }

    /** Removes a key by its public id if {@code allowed} accepts it. Returns the removed key or null. */
    public synchronized @Nullable Key revoke(String id, Predicate<Key> allowed) {
        if (id.length() != 16) return null;
        for (var key : keys.values()) {
            if (!key.id().equals(id)) continue;
            if (!allowed.test(key)) return null;
            keys.remove(key.hash());
            onChange.run();
            return key;
        }
        return null;
    }

    /** Records a use (saved at most once a minute per key, or when the IP changed). */
    public synchronized void touch(String hash, String ip) {
        var key = keys.get(hash);
        if (key == null) return;
        long now = clock.getAsLong();
        if (!ip.equals(key.lastIp()) || now - key.lastUsed() > LAST_USED_GRANULARITY_MS) {
            keys.put(hash, key.used(now, ip));
            onChange.run();
        }
    }

    /** Deletes keys that expired more than a week ago. Returns the number removed. */
    public synchronized int expire() {
        long now = clock.getAsLong();
        int before = keys.size();
        keys.values().removeIf(k -> k.expires() != 0 && k.expires() + EXPIRED_KEEP_MS < now);
        int removed = before - keys.size();
        if (removed > 0) onChange.run();
        return removed;
    }

    /** The mainframe was broken: its keys are deleted. */
    public synchronized void forgetMainframe(UUID mainframeId) {
        if (keys.values().removeIf(k -> k.mainframeId().equals(mainframeId))) onChange.run();
    }
}
