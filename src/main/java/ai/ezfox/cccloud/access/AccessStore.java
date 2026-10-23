package ai.ezfox.cccloud.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Roles, team assignments, share links and the audit log of every mainframe. Plain Java (unit tested), persisted by
 * {@code WebAuthData}. Reads are thread-safe (immutable values in concurrent maps); mutations are synchronized and
 * happen on the server thread.
 */
public final class AccessStore {
    public static final int MAX_SHARES_PER_MAINFRAME = 16;
    public static final int MAX_SHARE_PAGES = 12;
    public static final int MAX_SHARE_CARDS = 64;
    public static final int MAX_LABEL = 48;
    public static final int MAX_VIEWERS = 50;
    public static final int AUDIT_SIZE = 50;
    public static final Set<Long> SHARE_EXPIRIES = Set.of(3600L, 86400L, 604800L);
    static final Pattern PAGE_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    static final Pattern CARD_KEY = Pattern.compile("[A-Za-z0-9_.:~/-]{1,160}");
    static final Pattern SHARE_ID = Pattern.compile("[0-9a-f]{16}");

    /**
     * A share link. Only the SHA-256 of its 128-bit secret is stored.
     *
     * @param expires ms since the epoch, 0 = never
     */
    /** @param creator the player who created it (null for shares from before L3); it stops working when they lose shares.create */
    public record Share(String id, UUID mainframeId, String secretHash, String label, String createdBy, long created, long expires,
                        int maxViewers, List<String> pages, List<String> cards, boolean hideNumbers, @Nullable UUID creator) {
        public Share {
            pages = List.copyOf(pages);
            cards = List.copyOf(cards);
        }

        public Share(String id, UUID mainframeId, String secretHash, String label, String createdBy, long created, long expires,
                     int maxViewers, List<String> pages, List<String> cards, boolean hideNumbers) {
            this(id, mainframeId, secretHash, label, createdBy, created, expires, maxViewers, pages, cards, hideNumbers, null);
        }
    }

    public record NewShare(Share share, String secret) {
    }

    public enum ShareStatus { OK, INVALID, EXPIRED }

    public record ShareCheck(ShareStatus status, @Nullable Share share) {
    }

    public record AuditEntry(long time, String text) {
    }

    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    private final Runnable onChange;
    private final Map<UUID, MainframeAccess> access = new ConcurrentHashMap<>();
    private final Map<String, Share> shares = new ConcurrentHashMap<>();
    private final Map<UUID, List<AuditEntry>> audit = new ConcurrentHashMap<>();

    public AccessStore(LongSupplier clock, Runnable onChange) {
        this.clock = clock;
        this.onChange = onChange;
    }

    // ---- persistence -------------------------------------------------------------------------------------------------

    public Map<UUID, MainframeAccess> accessMap() {
        return access;
    }

    public Map<String, Share> shareMap() {
        return shares;
    }

    public Map<UUID, List<AuditEntry>> auditMap() {
        return audit;
    }

    public synchronized void load(Map<UUID, MainframeAccess> access, Iterable<Share> shares, Map<UUID, List<AuditEntry>> audit) {
        this.access.putAll(access);
        for (var share : shares) this.shares.put(share.id(), share);
        for (var e : audit.entrySet()) this.audit.put(e.getKey(), List.copyOf(e.getValue()));
    }

    /**
     * Migration from v0.3 (owner + trusted list): trusted players become admins (they could manage everything before).
     * Only applies if the mainframe has no v0.6 access entry yet, so it runs once per mainframe.
     *
     * @return true if something was migrated
     */
    public synchronized boolean migrateLegacy(UUID mainframeId, @Nullable UUID owner, String ownerName, Map<UUID, String> trusted) {
        var existing = access.get(mainframeId);
        if (existing != null) {
            // The access entry exists (e.g. created from the SavedData ACL); fill in names and missing trusted players.
            var updated = existing;
            if (owner != null && existing.owner() == null) updated = updated.withOwner(owner, ownerName);
            if (owner != null && owner.equals(updated.owner()) && updated.ownerName().isEmpty() && !ownerName.isEmpty()) updated = updated.withOwner(owner, ownerName);
            for (var e : trusted.entrySet()) {
                var grant = updated.grants().get(e.getKey());
                if (grant == null && !e.getKey().equals(updated.owner())) updated = updated.withGrant(e.getKey(), e.getValue(), Role.ADMIN);
                else if (grant != null && grant.name().isEmpty() && !e.getValue().isEmpty()) updated = updated.withGrant(e.getKey(), e.getValue(), grant.role());
            }
            if (updated.equals(existing)) return false;
            access.put(mainframeId, updated);
            onChange.run();
            return true;
        }
        var created = MainframeAccess.empty().withOwner(owner, ownerName);
        for (var e : trusted.entrySet()) {
            if (!e.getKey().equals(owner)) created = created.withGrant(e.getKey(), e.getValue(), Role.ADMIN);
        }
        access.put(mainframeId, created);
        onChange.run();
        return true;
    }

    // ---- roles -------------------------------------------------------------------------------------------------------

    public @Nullable MainframeAccess access(UUID mainframeId) {
        return access.get(mainframeId);
    }

    public Set<UUID> mainframes() {
        return access.keySet();
    }

    public synchronized void setOwner(UUID mainframeId, @Nullable UUID owner, String name) {
        var current = access.getOrDefault(mainframeId, MainframeAccess.empty());
        var updated = current.withOwner(owner, name);
        if (!updated.equals(current) || !access.containsKey(mainframeId)) {
            access.put(mainframeId, updated);
            onChange.run();
        }
    }

    /**
     * Sets a direct role ({@link Role#NONE} removes it). The owner's role cannot be changed here.
     *
     * @return null on success, else {@code invalid} (owner/role) or {@code too_many}
     */
    public synchronized @Nullable String setRole(UUID mainframeId, UUID player, String name, Role role) {
        if (role == Role.OWNER || role == Role.SHARE) return "invalid";
        var current = access.getOrDefault(mainframeId, MainframeAccess.empty());
        if (player.equals(current.owner())) return "invalid";
        if (role != Role.NONE && !current.grants().containsKey(player) && current.grants().size() >= MainframeAccess.MAX_GRANTS) return "too_many";
        var updated = current.withGrant(player, name, role);
        if (!updated.equals(current)) {
            access.put(mainframeId, updated);
            onChange.run();
        }
        return null;
    }

    public synchronized void setTeam(UUID mainframeId, MainframeAccess.@Nullable TeamBinding binding) {
        var current = access.getOrDefault(mainframeId, MainframeAccess.empty());
        access.put(mainframeId, current.withTeam(binding));
        onChange.run();
    }

    /** Changes one rank's role of the assigned team. Returns false if no team is assigned or the rank is unknown. */
    public synchronized boolean setMapping(UUID mainframeId, String rank, Role role) {
        var current = access.get(mainframeId);
        if (current == null || current.team() == null || !current.team().mapping().containsKey(rank) || role == Role.OWNER || role == Role.SHARE) return false;
        var mapping = new LinkedHashMap<>(current.team().mapping());
        mapping.put(rank, role);
        access.put(mainframeId, current.withTeam(new MainframeAccess.TeamBinding(current.team().provider(), current.team().teamId(), mapping,
            current.team().overrides())));
        onChange.run();
        return true;
    }

    /** Custom permissions of a direct grant (the player must have a grant). Returns false if there is none. */
    public synchronized boolean setOverrides(UUID mainframeId, UUID player, MainframeAccess.Overrides overrides) {
        var current = access.get(mainframeId);
        var grant = current == null ? null : current.grants().get(player);
        if (grant == null) return false;
        access.put(mainframeId, current.withGrant(player, grant.name(), grant.role(), overrides));
        onChange.run();
        return true;
    }

    /** Custom permissions of a team rank of the assigned team. */
    public synchronized boolean setRankOverrides(UUID mainframeId, String rank, MainframeAccess.Overrides overrides) {
        var current = access.get(mainframeId);
        if (current == null || current.team() == null || !current.team().mapping().containsKey(rank)) return false;
        var map = new LinkedHashMap<>(current.team().overrides());
        if (overrides.isEmpty()) map.remove(rank);
        else map.put(rank, overrides);
        var t = current.team();
        access.put(mainframeId, current.withTeam(new MainframeAccess.TeamBinding(t.provider(), t.teamId(), t.mapping(), map)));
        onChange.run();
        return true;
    }

    /** The mainframe was broken: its roles, shares and audit log go away. */
    public synchronized void forget(UUID mainframeId) {
        boolean changed = access.remove(mainframeId) != null;
        changed |= shares.values().removeIf(s -> s.mainframeId().equals(mainframeId));
        changed |= audit.remove(mainframeId) != null;
        if (changed) onChange.run();
    }

    /** Number of mainframes the player owns (for {@code cccloud.limit.mainframes}). */
    public int ownedBy(UUID player) {
        int count = 0;
        for (var a : access.values()) if (player.equals(a.owner())) count++;
        return count;
    }

    // ---- share links -------------------------------------------------------------------------------------------------

    /**
     * Creates a share link. Inputs must already be validated with {@link #validateShare}.
     */
    public synchronized NewShare createShare(UUID mainframeId, String label, String createdBy, @Nullable Long expiresIn, int maxViewers,
                                             List<String> pages, List<String> cards, boolean hideNumbers) {
        return createShare(mainframeId, label, createdBy, null, expiresIn, maxViewers, pages, cards, hideNumbers);
    }

    public synchronized NewShare createShare(UUID mainframeId, String label, String createdBy, @Nullable UUID creator, @Nullable Long expiresIn,
                                             int maxViewers, List<String> pages, List<String> cards, boolean hideNumbers) {
        byte[] raw = new byte[16]; // 128 bit
        random.nextBytes(raw);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String id;
        do {
            byte[] idRaw = new byte[8];
            random.nextBytes(idRaw);
            id = HexFormat.of().formatHex(idRaw);
        } while (shares.containsKey(id));
        long now = clock.getAsLong();
        var share = new Share(id, mainframeId, hash(secret), label, createdBy, now, expiresIn == null ? 0 : now + expiresIn * 1000, maxViewers,
            pages, cards, hideNumbers, creator);
        shares.put(id, share);
        onChange.run();
        return new NewShare(share, secret);
    }

    /** Null if the inputs are fine, else {@code invalid} or {@code too_many}. */
    public @Nullable String validateShare(UUID mainframeId, String label, @Nullable Long expiresIn, int maxViewers, List<String> pages, List<String> cards) {
        if (label.length() > MAX_LABEL) return "invalid";
        if (expiresIn != null && !SHARE_EXPIRIES.contains(expiresIn)) return "invalid";
        if (maxViewers < 1 || maxViewers > MAX_VIEWERS) return "invalid";
        if (pages.isEmpty() && cards.isEmpty()) return "invalid";
        if (pages.size() > MAX_SHARE_PAGES || cards.size() > MAX_SHARE_CARDS) return "invalid";
        for (String p : pages) if (!PAGE_ID.matcher(p).matches()) return "invalid";
        for (String c : cards) if (!CARD_KEY.matcher(c).matches()) return "invalid";
        if (sharesOf(mainframeId).size() >= MAX_SHARES_PER_MAINFRAME) return "too_many";
        return null;
    }

    public List<Share> sharesOf(UUID mainframeId) {
        var list = new ArrayList<Share>();
        for (var s : shares.values()) if (s.mainframeId().equals(mainframeId)) list.add(s);
        list.sort((a, b) -> Long.compare(a.created(), b.created()));
        return list;
    }

    public @Nullable Share share(String id) {
        return shares.get(id);
    }

    /** Checks a share id + secret (constant-time hash comparison). */
    public ShareCheck checkShare(@Nullable String id, @Nullable String secret) {
        if (id == null || secret == null || !SHARE_ID.matcher(id).matches() || secret.isEmpty() || secret.length() > 64) {
            return new ShareCheck(ShareStatus.INVALID, null);
        }
        var share = shares.get(id);
        if (share == null) return new ShareCheck(ShareStatus.INVALID, null);
        if (!MessageDigest.isEqual(share.secretHash().getBytes(StandardCharsets.US_ASCII), hash(secret).getBytes(StandardCharsets.US_ASCII))) {
            return new ShareCheck(ShareStatus.INVALID, null);
        }
        if (share.expires() != 0 && share.expires() <= clock.getAsLong()) return new ShareCheck(ShareStatus.EXPIRED, share);
        return new ShareCheck(ShareStatus.OK, share);
    }

    public synchronized @Nullable Share revokeShare(UUID mainframeId, String id) {
        var share = shares.get(id);
        if (share == null || !share.mainframeId().equals(mainframeId)) return null;
        shares.remove(id);
        onChange.run();
        return share;
    }

    /** Drops share links that expired more than a day ago (expired ones still answer "share_expired" for a while). */
    public synchronized int expireShares() {
        long cutoff = clock.getAsLong() - 24L * 60 * 60 * 1000;
        int before = shares.size();
        shares.values().removeIf(s -> s.expires() != 0 && s.expires() < cutoff);
        int removed = before - shares.size();
        if (removed > 0) onChange.run();
        return removed;
    }

    // ---- audit log ---------------------------------------------------------------------------------------------------

    public synchronized void audit(UUID mainframeId, String text) {
        var list = new ArrayList<>(audit.getOrDefault(mainframeId, List.of()));
        list.add(new AuditEntry(clock.getAsLong(), text.length() > 200 ? text.substring(0, 200) : text));
        while (list.size() > AUDIT_SIZE) list.removeFirst();
        audit.put(mainframeId, List.copyOf(list));
        onChange.run();
    }

    public List<AuditEntry> audit(UUID mainframeId) {
        return audit.getOrDefault(mainframeId, List.of());
    }

    static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
