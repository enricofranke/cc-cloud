package ai.ezfox.cccloud.publicapi;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.web.AuthStore;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** v0.10 API keys: hashing and lookup, expiry, IP allow-lists, revocation, the management API and its escalation rule. */
class ApiKeysTest {
    final long[] now = {1_000_000};
    int changes;
    final ApiKeys keys = new ApiKeys(() -> now[0], () -> changes++);
    final UUID mainframe = UUID.randomUUID();
    final UUID steve = UUID.randomUUID();
    final UUID alex = UUID.randomUUID();
    static final ApiSettings ON = new ApiSettings(true, 2, 60, 120, 2048, List.of(), true, 2);

    @Test
    void keysAreStoredAsHashesAndShownOnce() {
        var created = keys.create(steve, "Steve", mainframe, "bot", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of());
        String secret = created.secret();
        assertTrue(secret.startsWith("cck_") && secret.length() == 47 && ApiKeys.looksLikeKey(secret), secret);
        assertEquals(AuthStore.hash(secret), created.key().hash());
        assertEquals(created.key().hash().substring(0, 16), created.key().id());
        for (var key : keys.all()) assertFalse(key.toString().contains(secret.substring(4)), "the key itself is never stored");
        assertEquals(ApiKeys.Status.OK, keys.authenticate(secret).status());
        assertEquals(ApiKeys.Status.UNKNOWN, keys.authenticate(secret.substring(0, 46) + (secret.endsWith("A") ? "B" : "A")).status());
        assertEquals(ApiKeys.Status.UNKNOWN, keys.authenticate("not a key").status());
        assertEquals(ApiKeys.Status.UNKNOWN, keys.authenticate(null).status());
        assertFalse(ApiKeys.looksLikeKey("cck_short"));
        assertEquals(1, changes, "saved");
    }

    @Test
    void expiryRevocationAndIps() {
        var day = keys.create(steve, "Steve", mainframe, "day", EnumSet.of(ApiScope.READ_SECTIONS), 86_400, List.of("192.168.1.0/24", "10.0.0.5"));
        now[0] += 86_400_000L;
        var check = keys.authenticate(day.secret());
        assertEquals(ApiKeys.Status.EXPIRED, check.status(), "expired keys are recognised as such");
        assertTrue(ApiKeys.ipAllowed(day.key(), "192.168.1.77"));
        assertTrue(ApiKeys.ipAllowed(day.key(), "10.0.0.5"));
        assertFalse(ApiKeys.ipAllowed(day.key(), "10.0.0.6"));
        assertFalse(ApiKeys.ipAllowed(day.key(), "garbage"));
        var any = keys.create(steve, "Steve", mainframe, "any", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of());
        assertTrue(ApiKeys.ipAllowed(any.key(), "203.0.113.9"), "no list: any IP");
        assertTrue(ApiKeys.validIp("fd00::/8") && ApiKeys.validIp("127.0.0.1") && !ApiKeys.validIp("example.org") && !ApiKeys.validIp("10.0.0.0/40"));
        // Expired keys are listed for a week, then deleted.
        now[0] += 6 * 86_400_000L;
        assertEquals(0, keys.expire());
        now[0] += 2 * 86_400_000L;
        assertEquals(1, keys.expire());
        assertNull(keys.revoke(any.key().id(), k -> false), "the predicate decides");
        assertNotNull(keys.revoke(any.key().id(), k -> true));
        assertEquals(ApiKeys.Status.UNKNOWN, keys.authenticate(any.secret()).status(), "revoked = gone");
        keys.create(steve, "Steve", mainframe, "a", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of());
        keys.forgetMainframe(mainframe);
        assertTrue(keys.forMainframe(mainframe).isEmpty(), "a broken mainframe takes its keys along");
    }

    @Test
    void usageIsRecordedSparingly() {
        var created = keys.create(steve, "Steve", mainframe, "bot", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of());
        int before = changes;
        keys.touch(created.key().hash(), "10.0.0.2");
        assertEquals(before + 1, changes);
        keys.touch(created.key().hash(), "10.0.0.2");
        assertEquals(before + 1, changes, "at most once a minute");
        keys.touch(created.key().hash(), "10.0.0.3");
        assertEquals(before + 2, changes, "or when the IP changes");
        assertEquals("10.0.0.3", keys.byHash(created.key().hash()).lastIp());
    }

    @Test
    void scopesOnlyNarrowRights() {
        assertEquals(EnumSet.of(Perm.VIEW, Perm.VIEW_POSITIONS), ApiScope.cap(EnumSet.of(ApiScope.READ_SECTIONS)));
        assertEquals(EnumSet.of(Perm.VIEW, Perm.STORAGE_SEARCH), ApiScope.cap(EnumSet.of(ApiScope.READ_STORAGE)));
        assertTrue(ApiScope.cap(EnumSet.of(ApiScope.ACTION_WIDGETS)).containsAll(EnumSet.of(Perm.ACT_BUTTONS, Perm.ACT_REDSTONE)));
        assertTrue(ApiScope.cap(EnumSet.allOf(ApiScope.class)).stream().noneMatch(p -> p == Perm.ACCESS_MANAGE || p == Perm.SCRIPTS_UPLOAD
            || p == Perm.TERMINAL || p == Perm.API_KEYS || p == Perm.DEVICES_MANAGE || p == Perm.LAYOUT_EDIT || p == Perm.RULES_EDIT), "never admin rights");
        assertTrue(ApiScope.cap(EnumSet.noneOf(ApiScope.class)).isEmpty());
        // A viewer's rights with every scope: still only what a viewer has.
        var viewer = EnumSet.copyOf(Perm.preset(Role.VIEWER));
        var capped = EnumSet.copyOf(viewer);
        capped.retainAll(ApiScope.cap(EnumSet.allOf(ApiScope.class)));
        assertFalse(capped.contains(Perm.ACT_BUTTONS));
        assertEquals(EnumSet.of(Perm.VIEW, Perm.STORAGE_SEARCH), capped);
        assertNull(ApiScope.byId("admin:all"));
        assertEquals(ApiScope.RUN_SCENES, ApiScope.byId("run:scenes"));
    }

    @Test
    void managementApi() {
        var admin = new ApiKeysApi.Caller(steve, "Steve", EnumSet.copyOf(Perm.preset(Role.ADMIN)));
        var audit = new ArrayList<String>();
        var created = ApiKeysApi.post(keys, ON, mainframe, admin,
            "{\"op\":\"create\",\"name\":\"Home Assistant\",\"scopes\":[\"read:sections\",\"run:scenes\"],\"expiresIn\":2592000,\"ips\":[\"192.168.1.0/24\"]}",
            now[0], audit::add);
        assertEquals(200, created.status(), created.body().toString());
        String secret = created.body().get("key").getAsString();
        assertTrue(ApiKeys.looksLikeKey(secret));
        assertEquals(1, audit.size());
        assertFalse(audit.getFirst().contains(secret.substring(4)), "the key never reaches a log");
        var list = ApiKeysApi.get(keys, ON, mainframe, admin, now[0]).body();
        assertEquals(1, list.getAsJsonArray("keys").size());
        assertFalse(list.toString().contains(secret.substring(4)), "listing never shows the key");
        assertTrue(list.getAsJsonArray("keys").get(0).getAsJsonObject().get("own").getAsBoolean());

        // Validation
        for (var bad : new String[]{"{\"op\":\"create\",\"name\":\"\",\"scopes\":[\"read:sections\"]}", "{\"op\":\"create\",\"name\":\"x\",\"scopes\":[]}",
            "{\"op\":\"create\",\"name\":\"x\",\"scopes\":[\"admin:all\"]}", "{\"op\":\"create\",\"name\":\"x\",\"scopes\":[\"read:sections\",\"read:sections\"]}",
            "{\"op\":\"create\",\"name\":\"x\",\"scopes\":[\"read:sections\"],\"expiresIn\":5}", "{\"op\":\"create\",\"name\":\"x\",\"scopes\":[\"read:sections\"],\"ips\":[\"host\"]}",
            "{\"op\":\"create\",\"name\":\"x\",\"scopes\":[\"read:sections\"],\"secret\":\"mine\"}", "{\"op\":\"steal\"}", "[]", "{"}) {
            assertEquals(400, ApiKeysApi.post(keys, ON, mainframe, admin, bad, now[0], audit::add).status(), bad);
        }
        // No key reaches past its creator: a viewer cannot create an action key.
        var viewer = new ApiKeysApi.Caller(alex, "Alex", EnumSet.copyOf(Perm.preset(Role.VIEWER)));
        assertEquals(403, ApiKeysApi.post(keys, ON, mainframe, viewer, "{\"op\":\"create\",\"name\":\"x\",\"scopes\":[\"action:widgets\"]}", now[0], audit::add).status());
        assertEquals(200, ApiKeysApi.post(keys, ON, mainframe, viewer, "{\"op\":\"create\",\"name\":\"read\",\"scopes\":[\"read:sections\"]}", now[0], audit::add).status());
        // Limit per player, switched off.
        assertEquals(200, ApiKeysApi.post(keys, ON, mainframe, admin, "{\"op\":\"create\",\"name\":\"two\",\"scopes\":[\"read:stats\"]}", now[0], audit::add).status());
        assertEquals(409, ApiKeysApi.post(keys, ON, mainframe, admin, "{\"op\":\"create\",\"name\":\"three\",\"scopes\":[\"read:stats\"]}", now[0], audit::add).status());
        var off = new ApiSettings(false, 5, 60, 120, 2048, List.of(), true, 2);
        assertEquals("disabled", ApiKeysApi.post(keys, off, mainframe, viewer, "{\"op\":\"create\",\"name\":\"x\",\"scopes\":[\"read:sections\"]}", now[0], audit::add)
            .body().get("error").getAsString());
        // Listing: own keys; moderators (access.manage) see every key of the mainframe.
        assertEquals(1, ApiKeysApi.get(keys, ON, mainframe, viewer, now[0]).body().getAsJsonArray("keys").size());
        assertEquals(3, ApiKeysApi.get(keys, ON, mainframe, admin, now[0]).body().getAsJsonArray("keys").size());
        // Revoking: own keys, or anything on this mainframe with access.manage.
        String alexKey = null, steveKey = null;
        for (var k : keys.forMainframe(mainframe)) {
            if (k.player().equals(alex)) alexKey = k.id();
            else steveKey = k.id();
        }
        assertEquals(404, ApiKeysApi.post(keys, ON, mainframe, viewer, "{\"op\":\"revoke\",\"id\":\"" + steveKey + "\"}", now[0], audit::add).status());
        assertEquals(404, ApiKeysApi.post(keys, ON, UUID.randomUUID(), admin, "{\"op\":\"revoke\",\"id\":\"" + alexKey + "\"}", now[0], audit::add).status(),
            "only keys of this mainframe");
        assertEquals(200, ApiKeysApi.post(keys, ON, mainframe, admin, "{\"op\":\"revoke\",\"id\":\"" + alexKey + "\"}", now[0], audit::add).status());
        assertEquals(400, ApiKeysApi.post(keys, ON, mainframe, admin, "{\"op\":\"revoke\",\"id\":\"xyz\"}", now[0], audit::add).status());
        assertTrue(audit.getLast().contains("revoked the API key 'read' of Alex"), audit.getLast());
    }

    /**
     * Security review 2026-10-30: a stolen browser could mint an API key that kept working after the browser was
     * revoked. A key now ends with the browser that created it, on every revocation path; expiry is not a revocation.
     */
    @Test
    void revokingTheCreatingBrowserRevokesItsKeys() {
        var auth = new AuthStore(() -> now[0], () -> {
        });
        var revoked = new ArrayList<String>();
        keys.followRevocations(auth, (device, key) -> revoked.add(key.name() + "@" + device.name()));
        String phone = browser(auth, "Phone"), laptop = browser(auth, "Laptop"), tablet = browser(auth, "Tablet");
        var admin = new ApiKeysApi.Caller(steve, "Steve", EnumSet.copyOf(Perm.preset(Role.ADMIN)), AuthStore.hash(phone));
        String stolen = ApiKeysApi.post(keys, ON, mainframe, admin, "{\"op\":\"create\",\"name\":\"thief\",\"scopes\":[\"action:widgets\"]}", now[0], line -> {
        }).body().get("key").getAsString();
        assertEquals(AuthStore.hash(phone), keys.authenticate(stolen).key().device());
        var other = keys.create(steve, "Steve", mainframe, "ha", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of(), AuthStore.hash(laptop));
        // The account page / DELETE /api/devices/<id>
        assertNotNull(auth.revokeDevice(AuthStore.hash(phone).substring(0, 16), d -> true));
        assertEquals(ApiKeys.Status.UNKNOWN, keys.authenticate(stolen).status(), "revoked with its browser");
        assertEquals(ApiKeys.Status.OK, keys.authenticate(other.secret()).status(), "other browsers' keys stay");
        assertEquals(List.of("thief@Phone"), revoked);
        // In game: the device list (by id) and /cccloud devices revoke <name|all>
        var byId = keys.create(steve, "Steve", mainframe, "t", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of(), AuthStore.hash(tablet));
        assertNotNull(auth.revokeById(mainframe, AuthStore.hash(tablet).substring(0, 16)));
        assertEquals(ApiKeys.Status.UNKNOWN, keys.authenticate(byId.secret()).status());
        assertEquals(1, auth.revoke(mainframe, "all").size());
        assertEquals(ApiKeys.Status.UNKNOWN, keys.authenticate(other.secret()).status());
        // A browser that merely expired after 30 days without use does not take the key with it.
        String old = browser(auth, "Old");
        var kept = keys.create(steve, "Steve", mainframe, "kept", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of(), AuthStore.hash(old));
        now[0] += AuthStore.DEVICE_EXPIRY_MS + 1;
        assertEquals(1, auth.expireDevices());
        assertEquals(ApiKeys.Status.OK, keys.authenticate(kept.secret()).status());
        // Keys without a known browser (created before 0.10 shipped, tests) are never matched by an empty hash.
        assertTrue(keys.revokeCreatedBy("").isEmpty());
    }

    private String browser(AuthStore auth, String name) {
        var code = auth.currentCode(mainframe, steve);
        var pending = auth.pair(AuthStore.formatCode(code.code()), name, "192.168.1.5");
        return auth.approve(pending.id(), steve, "Steve", true).token();
    }

    /** Moderators see other players' keys to revoke them, but not those players' IP addresses. */
    @Test
    void otherPlayersAddressesAreNotListed() {
        var mine = keys.create(steve, "Steve", mainframe, "mine", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of("192.168.1.0/24"));
        var theirs = keys.create(alex, "Alex", mainframe, "theirs", EnumSet.of(ApiScope.READ_SECTIONS), 0, List.of("203.0.113.7"));
        keys.touch(mine.key().hash(), "192.168.1.20");
        keys.touch(theirs.key().hash(), "203.0.113.7");
        var admin = new ApiKeysApi.Caller(steve, "Steve", EnumSet.copyOf(Perm.preset(Role.ADMIN)));
        var list = ApiKeysApi.get(keys, ON, mainframe, admin, now[0]).body().getAsJsonArray("keys");
        assertEquals(2, list.size());
        for (var e : list) {
            var k = e.getAsJsonObject();
            if (k.get("own").getAsBoolean()) {
                assertEquals("192.168.1.20", k.get("lastIp").getAsString());
                assertEquals(1, k.getAsJsonArray("ips").size());
            } else {
                assertTrue(k.get("lastIp").isJsonNull(), k.toString());
                assertTrue(k.getAsJsonArray("ips").isEmpty(), k.toString());
                assertTrue(k.has("lastUsed") && !k.get("lastUsed").isJsonNull(), "when it was used still shows");
            }
        }
        assertFalse(list.toString().contains("203.0.113.7"));
    }
}
