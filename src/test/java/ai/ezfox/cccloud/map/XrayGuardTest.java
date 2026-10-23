package ai.ezfox.cccloud.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Slice depth and ore radius rules (security review M4). */
class XrayGuardTest {
    @Test
    void sliceRangeAndOreRadius() {
        int[][] nodes = {{100, 64, 100}, {400, -20, 400}};
        assertTrue(XrayGuard.sliceInRange(nodes, 40));
        assertTrue(XrayGuard.sliceInRange(nodes, -52), "the second node is deep");
        assertFalse(XrayGuard.sliceInRange(nodes, -53));
        assertFalse(XrayGuard.sliceInRange(new int[0][], 64), "no nodes, no slices");
        assertTrue(XrayGuard.nearNode(nodes, 110, 64, 110));
        assertFalse(XrayGuard.nearNode(nodes, 112, 64, 112), "17 blocks away");
        assertFalse(XrayGuard.nearNode(new int[0][], 0, 0, 0));
    }
}
