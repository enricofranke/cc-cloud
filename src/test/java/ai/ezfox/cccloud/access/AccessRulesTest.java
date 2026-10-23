package ai.ezfox.cccloud.access;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The shared rank rule (security review H1/M1/L17). */
class AccessRulesTest {
    static AccessRules.Caller caller(Role role, Set<Perm> extraAllow, Set<Perm> deny, boolean moderator) {
        return new AccessRules.Caller(role, Perm.effective(role, extraAllow, deny), moderator);
    }

    @Test
    void viewerWithAccessManageCannotEscalate() {
        var v = caller(Role.VIEWER, Set.of(Perm.ACCESS_MANAGE), Set.of(), false);
        assertEquals("not_allowed", AccessRules.changeGrant(v, Role.VIEWER, false, true, Role.ADMIN, Set.of()), "not themselves");
        assertEquals("not_allowed", AccessRules.changeGrant(v, Role.NONE, false, false, Role.ADMIN, Set.of()), "not above their role");
        assertEquals("not_allowed", AccessRules.changeGrant(v, Role.NONE, false, false, Role.OPERATOR, Set.of()));
        assertNull(AccessRules.changeGrant(v, Role.NONE, false, false, Role.VIEWER, Set.of()), "a viewer may add viewers");
        assertEquals("not_allowed", AccessRules.changeGrant(v, Role.NONE, false, false, Role.VIEWER, Set.of(Perm.TERMINAL)), "no allow they lack");
        assertEquals(Role.VIEWER, AccessRules.trustRole(v, Role.ADMIN), "trust never above the caller");
        assertNull(AccessRules.changeGrant(v, Role.VIEWER, false, true, Role.NONE, Set.of()), "leaving is fine");
    }

    @Test
    void adminWithDeniesCannotGrantThePreset() {
        var a = caller(Role.ADMIN, Set.of(), Set.of(Perm.TERMINAL, Perm.SCRIPTS_UPLOAD), false);
        assertEquals("not_allowed", AccessRules.changeGrant(a, Role.NONE, false, false, Role.ADMIN, Set.of()), "the admin preset contains terminal");
        assertNull(AccessRules.changeGrant(a, Role.NONE, false, false, Role.OPERATOR, Set.of()));
        assertEquals("not_allowed", AccessRules.changeGrant(a, Role.ADMIN, false, false, Role.NONE, Set.of()), "not equal ranks");
        assertEquals("not_allowed", AccessRules.changeMapping(a, Role.ADMIN, Set.of()), "no rank mapping to the full admin preset");
        assertEquals("not_allowed", AccessRules.bindTeam(a, Map.of("owner", Role.ADMIN, "member", Role.OPERATOR)), "team default mapping counts");
        assertNull(AccessRules.bindTeam(a, Map.of("member", Role.OPERATOR, "ally", Role.VIEWER)));
        assertEquals("not_allowed", AccessRules.changeGrant(caller(Role.ADMIN, Set.of(), Set.of(Perm.ACCESS_MANAGE), false), Role.NONE, false, false,
            Role.VIEWER, Set.of()), "needs access.manage");
    }

    @Test
    void moderatorsAndOwner() {
        var owner = new AccessRules.Caller(Role.OWNER, Perm.all(), true);
        assertNull(AccessRules.changeGrant(owner, Role.ADMIN, false, false, Role.ADMIN, EnumSet.allOf(Perm.class)));
        assertEquals("not_allowed", AccessRules.changeGrant(owner, Role.NONE, true, false, Role.ADMIN, Set.of()), "the owner is never changed");
        assertEquals("not_allowed", AccessRules.changeGrant(owner, Role.NONE, false, false, Role.OWNER, Set.of()));
        assertEquals(Role.OPERATOR, AccessRules.trustRole(owner, Role.OPERATOR));
    }
}
