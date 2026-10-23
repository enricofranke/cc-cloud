package ai.ezfox.cccloud.access;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.web.AuthStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Role resolution from every source: owner, direct grants, team ranks, server-admin nodes; bans and cccloud.use. */
class AccessResolverTest {
    final UUID mf = UUID.randomUUID();
    final UUID owner = UUID.randomUUID(), direct = UUID.randomUUID(), teamMember = UUID.randomUUID(), mod = UUID.randomUUID(), stranger = UUID.randomUUID();
    final AccessStore store = new AccessStore(System::currentTimeMillis, () -> {
    });
    final FakePermissions perms = new FakePermissions();
    final FakeTeams teams = new FakeTeams();
    final AccessResolver resolver = new AccessResolver(store, perms, id -> id.equals("fake") ? teams : null);

    AccessResolverTest() {
        store.setOwner(mf, owner, "Owner");
    }

    @Test
    void eachSource() {
        store.setRole(mf, direct, "Direct", Role.OPERATOR);
        teams.put("t1", teamMember, "ally");
        store.setTeam(mf, new MainframeAccess.TeamBinding("fake", "t1", Map.of("owner", Role.ADMIN, "officer", Role.ADMIN, "member", Role.OPERATOR, "ally", Role.VIEWER)));
        perms.grant(mod, ServerPermissions.Node.ADMIN_VIEW);

        assertEquals(new AccessResolver.Resolution(Role.OWNER, AccessResolver.Source.OWNER, null), resolver.resolve(owner, mf));
        assertEquals(new AccessResolver.Resolution(Role.OPERATOR, AccessResolver.Source.DIRECT, null), resolver.resolve(direct, mf));
        assertEquals(new AccessResolver.Resolution(Role.VIEWER, AccessResolver.Source.TEAM, "ally"), resolver.resolve(teamMember, mf));
        assertEquals(new AccessResolver.Resolution(Role.VIEWER, AccessResolver.Source.SERVER_ADMIN, null), resolver.resolve(mod, mf));
        assertEquals(Role.NONE, resolver.role(stranger, mf));
        assertEquals(Role.NONE, resolver.role(owner, UUID.randomUUID()), "other mainframe");
    }

    @Test
    void highestRoleWins() {
        store.setRole(mf, direct, "Direct", Role.VIEWER);
        teams.put("t1", direct, "officer");
        store.setTeam(mf, new MainframeAccess.TeamBinding("fake", "t1", Map.of("officer", Role.ADMIN)));
        assertEquals(new AccessResolver.Resolution(Role.ADMIN, AccessResolver.Source.TEAM, "officer"), resolver.resolve(direct, mf));
        perms.grant(direct, ServerPermissions.Node.ADMIN_OPERATE);
        assertEquals(AccessResolver.Source.TEAM, resolver.resolve(direct, mf).source(), "a lower admin node does not replace a higher role");
        perms.grant(stranger, ServerPermissions.Node.ADMIN_VIEW, ServerPermissions.Node.ADMIN_OPERATE, ServerPermissions.Node.ADMIN_MANAGE);
        assertEquals(Role.ADMIN, resolver.role(stranger, mf), "manage node = admin, never owner");
    }

    @Test
    void teamMappingAndLiveChanges() {
        teams.put("t1", teamMember, "member");
        var mapping = new LinkedHashMap<String, Role>(Map.of("member", Role.OPERATOR, "ally", Role.NONE));
        store.setTeam(mf, new MainframeAccess.TeamBinding("fake", "t1", mapping));
        assertEquals(Role.OPERATOR, resolver.role(teamMember, mf));
        assertTrue(store.setMapping(mf, "member", Role.VIEWER));
        assertEquals(Role.VIEWER, resolver.role(teamMember, mf), "mapping changes apply immediately");
        teams.put("t1", teamMember, "ally");
        assertEquals(Role.NONE, resolver.role(teamMember, mf), "rank → none");
        teams.teams.get("t1").remove(teamMember);
        assertEquals(Role.NONE, resolver.role(teamMember, mf), "left the team");
        assertFalse(store.setMapping(mf, "unknown_rank", Role.ADMIN));
        assertFalse(store.setMapping(mf, "member", Role.OWNER), "owner can never be mapped");
        store.setTeam(mf, null);
        teams.put("t1", teamMember, "member");
        assertEquals(Role.NONE, resolver.role(teamMember, mf), "unassigned");
    }

    @Test
    void bansAndUseNodeOverrideEverything() {
        perms.banned.add(owner);
        assertEquals(Role.NONE, resolver.role(owner, mf), "banned owner");
        perms.banned.clear();
        perms.noUse.add(owner);
        assertEquals(Role.NONE, resolver.role(owner, mf), "cccloud.use = false");
    }

    @Test
    void devicesAreLimitedAndCapped() {
        var scoped = new AuthStore.Device("h1", mf, "Phone", true, 0, 0, owner, "Owner");
        var readOnly = new AuthStore.Device("h2", mf, "TV", false, 0, 0, owner, "Owner");
        var account = new AuthStore.Device("h3", null, "Laptop", true, 0, 0, owner, "Owner", "desktop", "");
        var other = UUID.randomUUID();
        store.setOwner(other, owner, "Owner");
        assertEquals(Role.OWNER, resolver.deviceRole(scoped, mf));
        assertEquals(Role.NONE, resolver.deviceRole(scoped, other), "v0.3 tokens stay limited to their mainframe");
        assertEquals(Role.VIEWER, resolver.deviceRole(readOnly, mf), "read-only devices are capped at viewer");
        assertEquals(Role.OWNER, resolver.deviceRole(account, other), "account tokens reach every mainframe of the player");
    }

    @Test
    void ownerCannotBeGrantedOrChanged() {
        assertEquals("invalid", store.setRole(mf, owner, "Owner", Role.VIEWER));
        assertEquals("invalid", store.setRole(mf, direct, "Direct", Role.OWNER));
        assertEquals("invalid", store.setRole(mf, direct, "Direct", Role.SHARE));
        assertEquals(Role.OWNER, resolver.role(owner, mf));
    }

    @Test
    void roleOrdering() {
        assertTrue(Role.OWNER.atLeast(Role.ADMIN));
        assertFalse(Role.SHARE.atLeast(Role.VIEWER), "share never satisfies a check");
        assertFalse(Role.NONE.atLeast(Role.NONE));
        assertTrue(Role.OPERATOR.canControl());
        assertFalse(Role.VIEWER.canControl());
        assertNull(Role.parse("share"), "share is never parsed from input");
    }
}
