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
}
