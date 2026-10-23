package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.AccessResolver;
import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.FakePermissions;
import ai.ezfox.cccloud.access.FakeTeams;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.access.ServerPermissions;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The v0.6 HTTP API: mainframe lists, devices, access changes (with the escalation rules) and share links. */
class AccessApiTest {
    final UUID mf = UUID.randomUUID(), mf2 = UUID.randomUUID();
    final UUID owner = UUID.randomUUID(), admin = UUID.randomUUID(), viewer = UUID.randomUUID(), friend = UUID.randomUUID();
    final AuthStore auth = new AuthStore(System::currentTimeMillis, () -> {
    });
    final AccessStore store = new AccessStore(System::currentTimeMillis, () -> {
    });
    final FakePermissions perms = new FakePermissions();
    final FakeTeams teams = new FakeTeams();
    final AccessResolver resolver = new AccessResolver(store, perms, id -> id.equals("fake") ? teams : null);
    final List<String> effects = new ArrayList<>();
    final Map<UUID, String> names = Map.of(owner, "Owner", admin, "Ada", viewer, "Vic", friend, "Fred");
    final AccessApi api = new AccessApi(auth, resolver, new AccessApi.Players() {
        @Override
        public AccessApi.@Nullable Known byName(String name) {
            for (var e : names.entrySet()) if (e.getValue().equalsIgnoreCase(name)) return new AccessApi.Known(e.getKey(), e.getValue());
            return null;
        }

        @Override
        public String name(UUID uuid) {
            return names.getOrDefault(uuid, "");
        }
    }, new AccessApi.Hooks() {
        @Override
        public void accessChanged(UUID mainframeId) {
            effects.add("changed:" + mainframeId);
        }

        @Override
        public void closeDevice(String tokenHash) {
            effects.add("closeDevice");
        }

        @Override
        public void closeShare(String shareId, String reason) {
            effects.add("closeShare:" + reason);
        }

        @Override
        public int shareViewers(String shareId) {
            return 0;
        }

        @Override
        public WebHub.@Nullable MainframeInfo info(UUID mainframeId) {
            return new WebHub.MainframeInfo(mainframeId, mainframeId.equals(mf) ? "Base" : "Outpost", true, true);
        }

        @Override
        public Set<UUID> mainframes() {
            return Set.of();
        }

        @Override
        public void log(String message) {
        }
    }, id -> id.equals("fake") ? teams : null, () -> List.of(teams));

    AccessApiTest() {
        store.setOwner(mf, owner, "Owner");
        store.setOwner(mf2, owner, "Owner");
        store.setRole(mf, admin, "Ada", Role.ADMIN);
        store.setRole(mf, viewer, "Vic", Role.VIEWER);
    }

    AuthStore.Device account(UUID player, String name) {
        var pending = auth.pair(auth.accountCode(player).code(), name + "'s laptop", "10.0.0.9");
        return auth.authenticate(auth.approve(pending.id(), player, name, true).token());
    }

    String accessPath(UUID id) {
        return "/api/mainframes/" + id + "/access";
    }

    @Test
    void mainframeListFollowsRoles() {
        var body = api.handle(Router.Id.MAINFRAMES, List.of(account(owner, "Owner")), "/api/mainframes", "").body();
        assertEquals(2, body.getAsJsonArray("mainframes").size(), "account tokens see every mainframe with a role");
        assertEquals("Owner", body.getAsJsonObject("account").get("player").getAsString());
        var v = api.handle(Router.Id.MAINFRAMES, List.of(account(viewer, "Vic")), "/api/mainframes", "").body().getAsJsonArray("mainframes");
        assertEquals(1, v.size());
        assertEquals("viewer", v.get(0).getAsJsonObject().get("role").getAsString());
        assertFalse(v.get(0).getAsJsonObject().get("canControl").getAsBoolean());
    }

    @Test
    void accessNeedsAdmin() {
        assertEquals(403, api.handle(Router.Id.ACCESS_GET, List.of(account(viewer, "Vic")), accessPath(mf), "").status());
        assertEquals("not_allowed", api.handle(Router.Id.ACCESS_GET, List.of(account(viewer, "Vic")), accessPath(mf), "").body().get("error").getAsString());
        assertEquals("no_access", api.handle(Router.Id.ACCESS_GET, List.of(account(friend, "Fred")), accessPath(mf), "").body().get("error").getAsString());
        var doc = api.handle(Router.Id.ACCESS_GET, List.of(account(admin, "Ada")), accessPath(mf), "");
        assertEquals(200, doc.status());
        assertEquals("admin", doc.body().getAsJsonObject("you").get("role").getAsString());
        assertEquals("owner", doc.body().getAsJsonArray("players").get(0).getAsJsonObject().get("source").getAsString());
    }

    @Test
    void roleChangesAndEscalationRules() {
        var adminDevice = account(admin, "Ada");
        var ok = api.handle(Router.Id.ACCESS_POST, List.of(adminDevice), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Fred\",\"role\":\"operator\"}");
        assertEquals(200, ok.status());
        assertEquals(Role.OPERATOR, resolver.role(friend, mf));
        assertTrue(effects.contains("changed:" + mf), "open sessions are re-checked");
        assertEquals("unknown_player", api.handle(Router.Id.ACCESS_POST, List.of(adminDevice), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Nobody\",\"role\":\"viewer\"}").body().get("error").getAsString());
        assertEquals("invalid", api.handle(Router.Id.ACCESS_POST, List.of(adminDevice), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Owner\",\"role\":\"viewer\"}").body().get("error").getAsString(), "owner cannot be changed");
        assertEquals("invalid", api.handle(Router.Id.ACCESS_POST, List.of(adminDevice), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Fred\",\"role\":\"owner\"}").body().get("error").getAsString());
        assertEquals("invalid", api.handle(Router.Id.ACCESS_POST, List.of(adminDevice), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Fred\",\"role\":\"viewer\",\"extra\":1}").body().get("error").getAsString(), "unknown fields");
        assertEquals("invalid", api.handle(Router.Id.ACCESS_POST, List.of(adminDevice), accessPath(mf), "[]").body().get("error").getAsString());
        // Admins cannot demote other admins (only the owner or a moderator can).
        store.setRole(mf, friend, "Fred", Role.ADMIN);
        assertEquals(403, api.handle(Router.Id.ACCESS_POST, List.of(adminDevice), accessPath(mf), "{\"op\":\"remove\",\"player\":\"Fred\"}").status());
        assertEquals(200, api.handle(Router.Id.ACCESS_POST, List.of(account(owner, "Owner")), accessPath(mf), "{\"op\":\"remove\",\"player\":\"Fred\"}").status());
        assertEquals(Role.NONE, resolver.role(friend, mf));
        perms.grant(admin, ServerPermissions.Node.ADMIN_MANAGE);
        store.setRole(mf, friend, "Fred", Role.ADMIN);
        assertEquals(200, api.handle(Router.Id.ACCESS_POST, List.of(adminDevice), accessPath(mf), "{\"op\":\"remove\",\"player\":\"Fred\"}").status(), "moderators may");
    }

    @Test
    void teamAssignment() {
        var ownerDevice = account(owner, "Owner");
        teams.put("t1", owner, "owner").put("t1", friend, "member").put("t2", viewer, "owner");
        assertEquals(400, api.handle(Router.Id.ACCESS_POST, List.of(ownerDevice), accessPath(mf), "{\"op\":\"set_team\",\"provider\":\"fake\",\"teamId\":\"nope\"}").status());
        assertEquals(403, api.handle(Router.Id.ACCESS_POST, List.of(ownerDevice), accessPath(mf), "{\"op\":\"set_team\",\"provider\":\"fake\",\"teamId\":\"t2\"}").status(), "only own teams");
        var doc = api.handle(Router.Id.ACCESS_POST, List.of(ownerDevice), accessPath(mf), "{\"op\":\"set_team\",\"provider\":\"fake\",\"teamId\":\"t1\"}").body();
        assertEquals("t1", doc.getAsJsonObject("team").get("id").getAsString());
        assertEquals(Role.OPERATOR, resolver.role(friend, mf), "member → operator by default");
        api.handle(Router.Id.ACCESS_POST, List.of(ownerDevice), accessPath(mf), "{\"op\":\"set_mapping\",\"rank\":\"member\",\"role\":\"none\"}");
        assertEquals(Role.NONE, resolver.role(friend, mf));
        api.handle(Router.Id.ACCESS_POST, List.of(ownerDevice), accessPath(mf), "{\"op\":\"set_team\",\"teamId\":null}");
        assertNull(store.access(mf).team());
    }

    @Test
    void devicesAndRevocation() {
        var laptop = account(owner, "Owner");
        var phone = account(owner, "Owner");
        var pending = auth.pair(auth.currentCode(mf, owner).code(), "TV", "10.0.0.3");
        var tv = auth.authenticate(auth.approve(pending.id(), owner, "Owner", false).token());
        var list = api.handle(Router.Id.DEVICES, List.of(laptop), "/api/devices", "").body().getAsJsonArray("devices");
        assertEquals(3, list.size(), "account tokens see every browser of the player");
        assertEquals(1, api.handle(Router.Id.DEVICES, List.of(tv), "/api/devices", "").body().getAsJsonArray("devices").size(), "a mainframe token only itself");
        assertEquals(404, api.handle(Router.Id.DEVICE_DELETE, List.of(tv), "/api/devices/" + phone.id(), "").status(), "a mainframe token cannot revoke others");
        assertEquals(404, api.handle(Router.Id.DEVICE_DELETE, List.of(account(viewer, "Vic")), "/api/devices/" + phone.id(), "").status(), "other players' devices");
        assertEquals(200, api.handle(Router.Id.DEVICE_DELETE, List.of(laptop), "/api/devices/" + phone.id(), "").status());
        assertTrue(effects.contains("closeDevice"));
        assertNull(auth.authenticate(null));
    }

    @Test
    void shareLinks() {
        var adminDevice = account(admin, "Ada");
        String path = "/api/mainframes/" + mf + "/shares";
        assertEquals(400, api.handle(Router.Id.SHARES_POST, List.of(adminDevice), path, "{\"label\":\"x\",\"pages\":[],\"cards\":[],\"expiresIn\":null,\"maxViewers\":3}").status());
        assertEquals(400, api.handle(Router.Id.SHARES_POST, List.of(adminDevice), path, "{\"pages\":[\"main\"],\"expiresIn\":42,\"maxViewers\":3}").status());
        assertEquals(400, api.handle(Router.Id.SHARES_POST, List.of(adminDevice), path, "{\"pages\":[\"main\"],\"expiresIn\":null,\"maxViewers\":3,\"secret\":\"mine\"}").status());
        var created = api.handle(Router.Id.SHARES_POST, List.of(adminDevice), path, "{\"label\":\"Reactor\",\"pages\":[\"main\"],\"expiresIn\":86400,\"maxViewers\":3,\"hideNumbers\":true}");
        assertEquals(200, created.status());
        String id = created.body().get("id").getAsString();
        String secret = created.body().get("secret").getAsString();
        assertEquals("/s/" + id + "#" + secret, created.body().get("url").getAsString());
        var listed = api.handle(Router.Id.SHARES_GET, List.of(adminDevice), path, "").body().toString();
        assertFalse(listed.contains(secret), "the secret is returned exactly once");
        assertEquals(403, api.handle(Router.Id.SHARES_GET, List.of(account(viewer, "Vic")), path, "").status());
        assertEquals(200, api.handle(Router.Id.SHARE_DELETE, List.of(adminDevice), path + "/" + id, "").status());
        assertTrue(effects.contains("closeShare:share_revoked"));
        assertEquals(404, api.handle(Router.Id.SHARE_DELETE, List.of(adminDevice), path + "/" + id, "").status());
    }

    @Test
    void shareFilterResolvesPages() {
        var uplink = new JsonObject();
        uplink.addProperty("uplinkId", "u1");
        var widgets = new com.google.gson.JsonArray();
        widgets.add(widget("a", "gauge", null));
        widgets.add(widget("b", "gauge", "power"));
        uplink.add("widgets", widgets);
        var pages = new com.google.gson.JsonArray();
        var p = new JsonObject();
        p.addProperty("id", "power");
        pages.add(p);
        uplink.add("pages", pages);
        var share = new AccessStore.Share("0123456789abcdef", mf, "h", "x", "a", 0, 0, 1, List.of("power"), List.of(), false);
        assertEquals(Set.of("u1~power"), ShareFilter.cardKeys(List.of(uplink), null, share));
        var filtered = ShareFilter.uplink(uplink, ShareFilter.Scope.of(Set.of("u1~power")), false);
        assertEquals(1, filtered.getAsJsonArray("widgets").size());
        var layout = com.google.gson.JsonParser.parseString("{\"v\":1,\"pages\":[{\"id\":\"main\",\"title\":\"M\"},{\"id\":\"power\",\"title\":\"P\"}],"
            + "\"cards\":{\"u1\":{\"page\":\"power\"}},\"widgets\":{}}").getAsJsonObject();
        assertEquals(Set.of("u1", "u1~power"), ShareFilter.cardKeys(List.of(uplink), layout, share), "the edited layout moves cards between pages");
        uplink.addProperty("restricted", true);
        assertNull(ShareFilter.uplink(uplink, ShareFilter.Scope.of(Set.of("u1", "u1~power")), false), "restricted sections are never shared");
    }

    @Test
    void shareFilterFollowsV2CustomCards() {
        var uplink = new JsonObject();
        uplink.addProperty("uplinkId", "u1");
        var widgets = new com.google.gson.JsonArray();
        widgets.add(widget("a", "gauge", null));
        widgets.add(widget("b", "gauge", null));
        widgets.add(widget("c", "gauge", null));
        uplink.add("widgets", widgets);
        var layout = com.google.gson.JsonParser.parseString("{\"v\":2,\"pages\":[{\"id\":\"main\",\"title\":\"M\"},{\"id\":\"tv\",\"title\":\"TV\"}],"
            + "\"cards\":{\"u1\":{\"page\":\"main\"},\"c:wall\":{\"page\":\"tv\",\"x\":0,\"y\":0,\"w\":6,\"h\":4}},"
            + "\"widgets\":{\"u1/a\":{\"card\":\"c:wall\"},\"u1/c\":{\"card\":\"c:gone\"}}}").getAsJsonObject();
        var byPage = new AccessStore.Share("0123456789abcdef", mf, "h", "x", "a", 0, 0, 1, List.of("tv"), List.of(), false);
        var scope = ShareFilter.scope(List.of(uplink), layout, byPage);
        assertEquals(Set.of("c:wall"), scope.cards(), "custom cards are shared by their page");
        var filtered = ShareFilter.uplink(uplink, scope, false);
        assertEquals(1, filtered.getAsJsonArray("widgets").size());
        assertEquals("a", filtered.getAsJsonArray("widgets").get(0).getAsJsonObject().get("id").getAsString(), "only the widget moved into the shared card");
        var byKey = new AccessStore.Share("0123456789abcdef", mf, "h", "x", "a", 0, 0, 1, List.of(), List.of("u1"), false);
        var home = ShareFilter.uplink(uplink, ShareFilter.scope(List.of(uplink), layout, byKey), false);
        assertEquals(List.of("b", "c"), home.getAsJsonArray("widgets").asList().stream().map(w -> w.getAsJsonObject().get("id").getAsString()).toList(),
            "a moved out; c points at a missing card and stays home");
    }

    @Test
    void securityReviewEscalationsAreRefused() {
        // H1 on the web path: a viewer with a custom access.manage cannot make anyone (or themselves) more than a viewer
        store.setOverrides(mf, viewer, new ai.ezfox.cccloud.access.MainframeAccess.Overrides(Set.of(ai.ezfox.cccloud.access.Perm.ACCESS_MANAGE), Set.of()));
        var vic = account(viewer, "Vic");
        assertEquals(403, api.handle(Router.Id.ACCESS_POST, List.of(vic), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Vic\",\"role\":\"admin\"}").status());
        assertEquals(403, api.handle(Router.Id.ACCESS_POST, List.of(vic), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Fred\",\"role\":\"operator\"}").status());
        assertEquals(200, api.handle(Router.Id.ACCESS_POST, List.of(vic), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Fred\",\"role\":\"viewer\"}").status());
        // M1: an admin with terminal/scripts denied gets nothing back through a team or a rank mapping
        store.setOverrides(mf, admin, new ai.ezfox.cccloud.access.MainframeAccess.Overrides(Set.of(),
            Set.of(ai.ezfox.cccloud.access.Perm.TERMINAL, ai.ezfox.cccloud.access.Perm.SCRIPTS_UPLOAD)));
        teams.put("party", admin, "owner");
        var ada = account(admin, "Ada");
        assertEquals(403, api.handle(Router.Id.ACCESS_POST, List.of(ada), accessPath(mf), "{\"op\":\"set_team\",\"provider\":\"fake\",\"teamId\":\"party\"}").status(),
            "the team's default mapping would grant the full admin preset");
        assertEquals(403, api.handle(Router.Id.ACCESS_POST, List.of(ada), accessPath(mf), "{\"op\":\"set_mapping\",\"rank\":\"owner\",\"role\":\"admin\"}").status());
        assertEquals(403, api.handle(Router.Id.ACCESS_POST, List.of(ada), accessPath(mf), "{\"op\":\"set_role\",\"player\":\"Fred\",\"role\":\"admin\"}").status());
        assertFalse(resolver.resolve(admin, mf).perms().contains(ai.ezfox.cccloud.access.Perm.TERMINAL));
    }

    private static JsonObject widget(String id, String type, @Nullable String page) {
        var w = new JsonObject();
        w.addProperty("id", id);
        w.addProperty("type", type);
        if (page != null) {
            var opts = new JsonObject();
            opts.addProperty("page", page);
            w.add("opts", opts);
        }
        return w;
    }
}
