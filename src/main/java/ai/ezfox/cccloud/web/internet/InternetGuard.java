package ai.ezfox.cccloud.web.internet;

import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.FailureLimiter;
import ai.ezfox.cccloud.web.IpUtil;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The extra rules of plain-HTTP internet access ({@code internetAccess = http}, docs/SECURITY.md "Internet access modes")
 * for clients with an internet address (not loopback/LAN, after the trusted-proxy rules):
 * <ul>
 *   <li>every write (control action, layout, rules, access, shares, scripts, …) needs the mainframe PIN: a correct PIN
 *       unlocks writes for {@link #UNLOCK_MS} for that browser, mainframe and address;</li>
 *   <li>a device token that was not used for {@code publicTokenIdleHours} is refused there (and dropped);</li>
 *   <li>stricter limits: {@link #PUBLIC_FAILURES} failed logins, pairing codes or PINs per {@link #FAILURE_WINDOW_MS}
 *       block the address, fewer actions and API requests.</li>
 * </ul>
 * In every other mode (and for LAN addresses) nothing here applies. One guard per web server; a config reload swaps its
 * settings ({@link #update}). Pure Java and thread-safe, unit tested.
 */
public final class InternetGuard {
    /** How long a correct PIN unlocks remote writes (per browser, mainframe and address). */
    public static final long UNLOCK_MS = 10 * 60 * 1000L;
    /** Failed logins / pairing codes / PINs per internet address before it is blocked for the rest of the window. */
    public static final int PUBLIC_FAILURES = 5;
    public static final long FAILURE_WINDOW_MS = 15 * 60 * 1000L;
    /** Actions per second per WebSocket from an internet address (LAN: 10). */
    public static final int PUBLIC_ACTIONS_PER_SECOND = 5;
    public static final int LAN_ACTIONS_PER_SECOND = 10;
    /** Device API requests per minute per internet address (LAN: 120). */
    public static final int PUBLIC_API_PER_MINUTE = 60;
    public static final int LAN_API_PER_MINUTE = 120;

    private volatile InternetSettings settings;
    private final LongSupplier clock;
    private final FailureLimiter failures;
    private final Map<String, Long> unlocks = new ConcurrentHashMap<>();

    public InternetGuard(InternetSettings settings, LongSupplier clock) {
        this.settings = settings;
        this.clock = clock;
        this.failures = new FailureLimiter(PUBLIC_FAILURES, FAILURE_WINDOW_MS, clock);
    }

    public InternetSettings settings() {
        return settings;
    }

    /**
     * A config reload: the new mode applies to the next message and request. Unlocks and failure counters stay (a reload
     * never gives an address a fresh budget); leaving plain-HTTP mode makes them irrelevant.
     */
    public void update(InternetSettings next) {
        settings = next;
    }

    public InternetMode mode() {
        return settings.mode();
    }

    /** True for an address outside loopback and the private ranges (the resolved client IP). */
    public static boolean isPublic(String ip) {
        return !IpUtil.isLan(ip);
    }

    /** Whether the plain-HTTP rules apply to this client. */
    public boolean strict(String ip) {
        return settings.strictPublic() && isPublic(ip);
    }

    /** Too many failed logins, pairing codes or PINs from this internet address. */
    public boolean blocked(String ip) {
        return strict(ip) && failures.isBlocked(ip);
    }

    /** Counts a failed login, pairing code, share secret or PIN (only for strict clients). */
    public void failure(String ip) {
        if (strict(ip)) failures.recordFailure(ip);
    }

    private static String key(String tokenHash, UUID mainframeId, String ip) {
        return tokenHash + "|" + mainframeId + "|" + ip;
    }

    /** True if writes are allowed: not strict, or a correct PIN was entered within {@link #UNLOCK_MS}. */
    public boolean unlocked(String tokenHash, UUID mainframeId, String ip) {
        if (!strict(ip)) return true;
        Long until = unlocks.get(key(tokenHash, mainframeId, ip));
        return until != null && until > clock.getAsLong();
    }

    /** Seconds the unlock still lasts (0 = locked or not strict). */
    public long unlockedFor(String tokenHash, UUID mainframeId, String ip) {
        if (!strict(ip)) return 0;
        Long until = unlocks.get(key(tokenHash, mainframeId, ip));
        long left = until == null ? 0 : until - clock.getAsLong();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    /** After a correct PIN from a strict client: writes are allowed for {@link #UNLOCK_MS}. Returns the seconds. */
    public long unlock(String tokenHash, UUID mainframeId, String ip) {
        if (!strict(ip)) return 0;
        long now = clock.getAsLong();
        if (unlocks.size() > 10_000) unlocks.values().removeIf(until -> until <= now);
        unlocks.put(key(tokenHash, mainframeId, ip), now + UNLOCK_MS);
        return UNLOCK_MS / 1000;
    }

    /** A strict client shows a token that was not used for {@code publicTokenIdleHours}: refuse it. */
    public boolean tooIdle(AuthStore.Device device, String ip) {
        return strict(ip) && clock.getAsLong() - device.lastUsed() > settings.publicTokenIdleMs();
    }

    public int actionsPerSecond(String ip) {
        return strict(ip) ? PUBLIC_ACTIONS_PER_SECOND : LAN_ACTIONS_PER_SECOND;
    }

    public int apiPerMinute(String ip) {
        return strict(ip) ? PUBLIC_API_PER_MINUTE : LAN_API_PER_MINUTE;
    }

    /** Drops expired unlocks and old failures (once a minute). */
    public void cleanup() {
        long now = clock.getAsLong();
        unlocks.values().removeIf(until -> until <= now);
        failures.cleanup();
    }
}
