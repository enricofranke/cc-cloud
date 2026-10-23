package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthStoreTest {
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private AuthStore store;
    private final UUID mainframe = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new AuthStore(now::get, () -> {
        });
    }

    private AuthStore.Pending pairNewDevice(String name) {
        var code = store.currentCode(mainframe, owner);
        return store.pair(AuthStore.formatCode(code.code()), name, "192.168.1.5");
    }

    private String approvedToken(String name) {
        var pending = pairNewDevice(name);
        return store.approve(pending.id(), owner, "Owner", true).token();
    }

    @Test
    void tokenIsStoredOnlyAsSha256() {
        String token = approvedToken("Phone");
        assertEquals(43, token.length(), "32 random bytes, base64url");
        assertFalse(store.deviceMap().containsKey(token));
        String hash = AuthStore.hash(token);
        assertEquals(64, hash.length());
        assertTrue(store.deviceMap().containsKey(hash));
        assertEquals(hash, AuthStore.hash(token), "hashing is deterministic");
        assertNotNull(store.authenticate(token));
        assertNull(store.authenticate(token + "x"));
        assertNull(store.authenticate(null));
        assertNull(store.authenticate("x".repeat(500)));
    }

    @Test
    void deviceIsBoundToApprovingPlayer() {
        var pending = pairNewDevice("Tablet");
        var approver = UUID.randomUUID();
        var approved = store.approve(pending.id(), approver, "Alex", true);
        assertEquals(approver, approved.device().player());
        assertEquals("Alex", approved.device().playerName());
        assertTrue(approved.device().canControl());
    }

    @Test
    void pairingCodeIsValidForTenMinutes() {
        var code = store.currentCode(mainframe, owner);
        now.addAndGet(AuthStore.CODE_TTL_MS - 1);
        assertEquals(code, store.currentCode(mainframe, owner), "same code while valid");
        now.addAndGet(2);
        assertNull(store.pair(code.code(), "Late", "10.0.0.1"), "expired code is rejected");
        assertNotEquals(code.code(), store.currentCode(mainframe, owner).code(), "a fresh code is generated after expiry");
    }

    @Test
    void pairingCodeIsSingleUse() {
        var code = store.currentCode(mainframe, owner);
        assertNotNull(store.pair(code.code().toLowerCase(), "One", "10.0.0.1"), "case-insensitive, separator optional");
        assertNull(store.pair(code.code(), "Two", "10.0.0.1"));
    }

    @Test
    void wrongCodeIsRejected() {
        store.currentCode(mainframe, owner);
        assertNull(store.pair("AAAA-AAAA", "x", "1.2.3.4"));
        assertNull(store.pair("", "x", "1.2.3.4"));
        assertNull(store.pair("ABC", "x", "1.2.3.4"));
    }

    @Test
    void pendingApprovalFlow() {
        var pending = pairNewDevice("Phone");
        assertEquals(AuthStore.PendingState.PENDING, store.poll(pending.id()).state());
        assertTrue(store.deviceMap().isEmpty(), "no token before approval");
        store.approve(pending.id(), owner, "Owner", true);
        var result = store.poll(pending.id());
        assertEquals(AuthStore.PendingState.APPROVED, result.state());
        assertNotNull(result.token());
        assertNull(store.poll(pending.id()), "the token is handed out exactly once");
    }

    @Test
    void deniedAndExpiredApprovals() {
        var denied = pairNewDevice("Bad");
        store.deny(denied.id());
        assertEquals(AuthStore.PendingState.DENIED, store.poll(denied.id()).state());
        assertNull(store.approve(denied.id(), owner, "Owner", true), "cannot approve after deny");

        var late = pairNewDevice("Late");
        now.addAndGet(AuthStore.APPROVAL_TTL_MS + 1);
        assertNull(store.poll(late.id()));
        assertNull(store.approve(late.id(), owner, "Owner", true));
        assertTrue(store.deviceMap().isEmpty());
    }

    @Test
    void devicesExpireAfterThirtyDaysWithoutUse() {
        String token = approvedToken("Old");
        now.addAndGet(AuthStore.DEVICE_EXPIRY_MS + 1);
        assertNull(store.authenticate(token));
        assertEquals(1, store.expireDevices());
        assertTrue(store.deviceMap().isEmpty());
    }

    @Test
    void touchKeepsDevicesAlive() {
        String token = approvedToken("Used");
        now.addAndGet(AuthStore.DEVICE_EXPIRY_MS - 1000);
        store.touch(AuthStore.hash(token));
        now.addAndGet(5000);
        assertNotNull(store.authenticate(token));
    }

    @Test
    void revokeByNameAndAll() {
        approvedToken("Phone");
        approvedToken("Phone");
        approvedToken("Tablet");
        var names = store.devices(mainframe).stream().map(AuthStore.Device::name).sorted().toList();
        assertEquals(java.util.List.of("Phone", "Phone (2)", "Tablet"), names);
        assertEquals(1, store.revoke(mainframe, "phone").size());
        assertEquals(2, store.revoke(mainframe, "all").size());
        assertTrue(store.devices(mainframe).isEmpty());
    }

    @Test
    void deviceNamesAreSanitized() {
        assertEquals("Device", AuthStore.sanitizeName("   "));
        assertEquals("ab", AuthStore.sanitizeName("a\u0000‮b"));
        assertEquals(AuthStore.MAX_DEVICE_NAME, AuthStore.sanitizeName("x".repeat(100)).length());
    }

    @Test
    void pinLockoutAfterFiveFailures() {
        store.setPin(mainframe, store.hashPin("4711"));
        String device = "hash";
        assertEquals(AuthStore.PinResult.OK, store.verifyPin(mainframe, device, "4711"));
        for (int i = 0; i < AuthStore.PIN_MAX_FAILURES - 1; i++) {
            var check = store.checkPin(mainframe, device, "0000");
            assertEquals(AuthStore.PinResult.WRONG, check.result());
            assertEquals(AuthStore.PIN_MAX_FAILURES - 1 - i, check.attemptsLeft());
        }
        var locked = store.checkPin(mainframe, device, "0000");
        assertEquals(AuthStore.PinResult.LOCKED, locked.result());
        assertEquals(AuthStore.PIN_LOCK_MS / 1000, locked.retryAfterSeconds());
        assertTrue(store.pinLockedFor(device) > 0);
        assertEquals(AuthStore.PinResult.LOCKED, store.verifyPin(mainframe, device, "4711"), "locked even with the right PIN");
        assertEquals(AuthStore.PinResult.OK, store.verifyPin(mainframe, "other-device", "4711"), "lockout is per device");
        now.addAndGet(AuthStore.PIN_LOCK_MS + 1);
        assertEquals(AuthStore.PinResult.OK, store.verifyPin(mainframe, device, "4711"));
    }

    @Test
    void pinIsSaltedAndValidated() {
        var a = store.hashPin("1234");
        var b = store.hashPin("1234");
        assertNotEquals(a.salt(), b.salt());
        assertNotEquals(a.hash(), b.hash());
        assertTrue(AuthStore.isValidPin("1234"));
        assertTrue(AuthStore.isValidPin("12345678"));
        assertFalse(AuthStore.isValidPin("123"));
        assertFalse(AuthStore.isValidPin("123456789"));
        assertFalse(AuthStore.isValidPin("12a4"));
        assertEquals(AuthStore.PinResult.NOT_SET, store.verifyPin(UUID.randomUUID(), "d", "1234"));
    }

    @Test
    void accountCodesCreateAccountTokensOnlyTheirPlayerMayApprove() {
        var player = UUID.randomUUID();
        var code = store.accountCode(player);
        var pending = store.pair(code.code(), "Laptop", "10.0.0.2", "Mozilla/5.0 (Macintosh; Intel Mac OS X)");
        assertNotNull(pending);
        assertTrue(pending.isAccount());
        assertNull(pending.mainframeId());
        assertNull(store.approve(pending.id(), owner, "Owner", true), "another player cannot approve an account code");
        var approved = store.approve(pending.id(), player, "Alex", true);
        assertNotNull(approved);
        var device = store.authenticate(approved.token());
        assertTrue(device.isAccount());
        assertEquals("desktop", device.type());
        assertEquals("10.0.0.2", device.lastIp());
        assertNull(store.pair(code.code(), "Again", "10.0.0.2"), "single use");
        assertEquals(1, store.devicesOf(player).size());
        assertNull(store.revokeDevice(device.id(), d -> false), "predicate denies");
        assertNotNull(store.revokeDevice(device.id(), d -> d.player().equals(player)));
        assertNull(store.authenticate(approved.token()));
    }

    @Test
    void mainframeCodesStayLimitedToTheirMainframe() {
        var pending = pairNewDevice("Phone");
        var approved = store.approve(pending.id(), owner, "Owner", true);
        assertEquals(mainframe, store.authenticate(approved.token()).mainframeId());
        assertFalse(store.authenticate(approved.token()).isAccount());
        assertEquals("phone", AuthStore.deviceType("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Mobile"));
        assertEquals("tablet", AuthStore.deviceType("Mozilla/5.0 (Linux; Android 14; Tablet)"));
        assertEquals("other", AuthStore.deviceType(""));
    }
}
