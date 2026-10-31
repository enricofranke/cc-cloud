package ai.ezfox.cccloud.ingame;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConfirmArmTest {
    @Test
    void secondClickOnTheSameControlGoesThrough() {
        var arm = new ConfirmArm();
        assertFalse(arm.press("a/scram", true, 1000), "the first click only arms");
        assertTrue(arm.armed("a/scram", 1000));
        assertTrue(arm.press("a/scram", true, 1000 + ConfirmArm.WINDOW_MS - 1));
        assertFalse(arm.armed("a/scram", 1500), "used up");
    }

    @Test
    void theArmExpires() {
        var arm = new ConfirmArm();
        arm.press("a/scram", true, 1000);
        assertFalse(arm.armed("a/scram", 1000 + ConfirmArm.WINDOW_MS));
        assertFalse(arm.press("a/scram", true, 1000 + ConfirmArm.WINDOW_MS), "armed again, not sent");
    }

    @Test
    void anotherControlNeverConfirmsAndDisarms() {
        var arm = new ConfirmArm();
        arm.press("a/shutdown", true, 1000);
        assertFalse(arm.press("a/reboot", true, 1100), "the neighbour arms itself, the first press does not carry over");
        assertFalse(arm.armed("a/shutdown", 1100));
        arm.press("a/shutdown", true, 1200);
        assertTrue(arm.press("a/plain", false, 1300), "a plain button goes at once");
        assertFalse(arm.armed("a/shutdown", 1300), "and disarms");
    }
}
