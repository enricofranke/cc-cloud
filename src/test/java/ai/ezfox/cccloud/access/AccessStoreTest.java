package ai.ezfox.cccloud.access;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Migration from v0.3, share links (secret, expiry, limits) and the audit log. */
class AccessStoreTest {
    final AtomicLong now = new AtomicLong(1_000_000);
    final AccessStore store = new AccessStore(now::get, () -> {
    });
    final UUID mf = UUID.randomUUID(), owner = UUID.randomUUID(), trusted = UUID.randomUUID();

    @Test
    void migratesV03TrustListOnce() {
        // SavedData ACL first (no names), then the block entity with names.
        assertTrue(store.migrateLegacy(mf, owner, "", Map.of(trusted, "")));
        assertEquals(Role.ADMIN, store.access(mf).grants().get(trusted).role(), "trusted players become admins");
        assertTrue(store.migrateLegacy(mf, owner, "Alex", Map.of(trusted, "Sam")), "names are filled in");
        assertEquals("Alex", store.access(mf).ownerName());
        assertEquals("Sam", store.access(mf).grants().get(trusted).name());
        assertFalse(store.migrateLegacy(mf, owner, "Alex", Map.of(trusted, "Sam")), "idempotent");
        // A later demotion is not undone by another load.
        store.setRole(mf, trusted, "Sam", Role.VIEWER);
        assertFalse(store.migrateLegacy(mf, owner, "Alex", Map.of()));
        assertEquals(Role.VIEWER, store.access(mf).grants().get(trusted).role());
        assertEquals(1, store.ownedBy(owner));
    }

    @Test
    void shareLinks() {
        assertNull(store.validateShare(mf, "Base", 3600L, 5, List.of("main"), List.of()));
        assertEquals("invalid", store.validateShare(mf, "Base", 60L, 5, List.of("main"), List.of()), "only 1 h / 24 h / 7 d / never");
        assertEquals("invalid", store.validateShare(mf, "Base", null, 0, List.of("main"), List.of()));
        assertEquals("invalid", store.validateShare(mf, "Base", null, 51, List.of("main"), List.of()));
        assertEquals("invalid", store.validateShare(mf, "Base", null, 5, List.of(), List.of()), "pick something");
        assertEquals("invalid", store.validateShare(mf, "Base", null, 5, List.of("../x"), List.of()));
        assertEquals("invalid", store.validateShare(mf, "Base", null, 5, List.of(), List.of("a b")));

        var created = store.createShare(mf, "Base", "Alex", 3600L, 5, List.of("main"), List.of(), false);
        var share = created.share();
        assertEquals(22, created.secret().length(), "128-bit secret, base64url");
        assertNotEquals(created.secret(), share.secretHash(), "only the hash is stored");
        assertEquals(AccessStore.ShareStatus.OK, store.checkShare(share.id(), created.secret()).status());
        assertEquals(AccessStore.ShareStatus.INVALID, store.checkShare(share.id(), created.secret() + "x").status());
        assertEquals(AccessStore.ShareStatus.INVALID, store.checkShare("nothex!", created.secret()).status());
        assertEquals(AccessStore.ShareStatus.INVALID, store.checkShare(null, null).status());
        now.addAndGet(3600_001);
        assertEquals(AccessStore.ShareStatus.EXPIRED, store.checkShare(share.id(), created.secret()).status());
        assertNull(store.revokeShare(UUID.randomUUID(), share.id()), "only from its own mainframe");
        assertNotNull(store.revokeShare(mf, share.id()));
        assertEquals(AccessStore.ShareStatus.INVALID, store.checkShare(share.id(), created.secret()).status());

        for (int i = 0; i < AccessStore.MAX_SHARES_PER_MAINFRAME; i++) store.createShare(mf, "s" + i, "Alex", null, 1, List.of("main"), List.of(), false);
        assertEquals("too_many", store.validateShare(mf, "x", null, 1, List.of("main"), List.of()));
        store.forget(mf);
        assertTrue(store.sharesOf(mf).isEmpty(), "breaking the mainframe deletes its share links");
    }

    @Test
    void expiredSharesAreDroppedAfterADay() {
        var share = store.createShare(mf, "x", "a", 3600L, 1, List.of("main"), List.of(), false).share();
        now.addAndGet(3600_000 + 23L * 3600_000);
        assertEquals(0, store.expireShares());
        now.addAndGet(2 * 3600_000);
        assertEquals(1, store.expireShares());
        assertNull(store.share(share.id()));
    }

    @Test
    void auditLogIsBounded() {
        for (int i = 0; i < AccessStore.AUDIT_SIZE + 10; i++) store.audit(mf, "entry " + i);
        var log = store.audit(mf);
        assertEquals(AccessStore.AUDIT_SIZE, log.size());
        assertEquals("entry " + (AccessStore.AUDIT_SIZE + 9), log.getLast().text());
    }
}
