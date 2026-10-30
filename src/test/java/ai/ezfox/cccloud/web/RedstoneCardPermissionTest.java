package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Controls on a Cloud Redstone card need act.redstone; viewers stay read-only and share links get nothing. */
class RedstoneCardPermissionTest {
    @Test
    void redstoneCardsNeedActRedstone() {
        for (String type : new String[]{"toggle", "slider", "button"}) {
            assertEquals(Perm.ACT_REDSTONE, AccessGate.actionPerm(type, "direct", "d_0123456789ab", "redstone"));
        }
        assertEquals(Perm.ACT_REDSTONE, AccessGate.actionPerm("button", "direct", "all_on", "redstone"));
        // the same widgets elsewhere stay act.buttons
        assertEquals(Perm.ACT_BUTTONS, AccessGate.actionPerm("toggle", "direct", "d_0123456789ab", "script"));
        assertEquals(Perm.ACT_BUTTONS, AccessGate.actionPerm("toggle", "lua", "d_0123456789ab", "redstone"), "only the mainframe's own redstone cards");
        assertEquals(Perm.ACT_BUTTONS, AccessGate.actionPerm("toggle", "direct", "lamp"));
    }

    @Test
    void rolesOnARedstoneCard() {
        var need = AccessGate.actionPerm("toggle", "direct", "d_0123456789ab", "redstone");
        assertEquals("read_only", AccessGate.check(Role.VIEWER, Set.of(Perm.VIEW, Perm.STORAGE_SEARCH), need));
        assertEquals(null, AccessGate.check(Role.OPERATOR, Set.of(Perm.VIEW, Perm.ACT_BUTTONS, Perm.ACT_REDSTONE), need));
        assertEquals("not_allowed", AccessGate.check(Role.OPERATOR, Set.of(Perm.VIEW, Perm.ACT_BUTTONS), need));
        assertEquals("forbidden", AccessGate.check(Role.SHARE, Set.of(Perm.VIEW), need));
    }
}
