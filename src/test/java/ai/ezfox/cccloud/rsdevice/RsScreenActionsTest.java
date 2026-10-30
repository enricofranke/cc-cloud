package ai.ezfox.cccloud.rsdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Every screen action of the redstone devices: parsing, limits, which need linking rights, which fit which device. */
class RsScreenActionsTest {
    @Test
    void parsesValidActions() {
        assertEquals(new RsScreenActions.Rename("Hall lights"), RsScreenActions.parse("name", "  Hall lights "));
        assertEquals(new RsScreenActions.Regroup(""), RsScreenActions.parse("group", ""), "an empty group = the automatic card");
        assertEquals(new RsScreenActions.Mode(true), RsScreenActions.parse("mode", "analog"));
        assertEquals(new RsScreenActions.Mode(false), RsScreenActions.parse("mode", "toggle"));
        assertEquals(new RsScreenActions.Level(15), RsScreenActions.parse("level", "15"));
        assertEquals(new RsScreenActions.Power(true), RsScreenActions.parse("power", "true"));
        assertEquals(new RsScreenActions.Press(), RsScreenActions.parse("press", ""));
        assertEquals(new RsScreenActions.Pulse(40), RsScreenActions.parse("pulse", "40"));
        assertEquals(new RsScreenActions.Follow(false), RsScreenActions.parse("follow", "false"));
        assertEquals(new RsScreenActions.Pin(true), RsScreenActions.parse("pin", "true"));
        assertEquals(new RsScreenActions.Confirm(true), RsScreenActions.parse("confirm", "true"));
        assertEquals(new RsScreenActions.Unlink(), RsScreenActions.parse("unlink", ""));
    }

    @Test
    void refusesInvalidInput() {
        for (String[] bad : new String[][]{{"name", "x".repeat(33)}, {"name", "a\u0007b"}, {"group", "line\nbreak"}, {"mode", "pulse"},
            {"level", "16"}, {"level", "-1"}, {"level", "7.5"}, {"level", ""}, {"pulse", "1"}, {"pulse", "41"}, {"power", "yes"}, {"press", "x"},
            {"pin", "1"}, {"unlink", "now"}, {"redstone", "receive:door"}, {"", ""}}) {
            assertNull(RsScreenActions.parse(bad[0], bad[1]), bad[0] + " " + bad[1]);
        }
    }

    @Test
    void settingsNeedLinkingRightsOperatingDoesNot() {
        assertTrue(RsScreenActions.needsManage(new RsScreenActions.Rename("a")));
        assertTrue(RsScreenActions.needsManage(new RsScreenActions.Pin(true)));
        assertTrue(RsScreenActions.needsManage(new RsScreenActions.Unlink()));
        assertTrue(RsScreenActions.needsManage(new RsScreenActions.Follow(true)));
        assertFalse(RsScreenActions.needsManage(new RsScreenActions.Power(true)), "like flipping it by hand");
        assertFalse(RsScreenActions.needsManage(new RsScreenActions.Level(3)));
        assertFalse(RsScreenActions.needsManage(new RsScreenActions.Press()));
    }

    @Test
    void actionsFitTheirDevices() {
        assertTrue(RsScreenActions.fits(new RsScreenActions.Mode(true), RsKind.SWITCH));
        assertFalse(RsScreenActions.fits(new RsScreenActions.Mode(true), RsKind.LAMP));
        assertTrue(RsScreenActions.fits(new RsScreenActions.Press(), RsKind.BUTTON));
        assertFalse(RsScreenActions.fits(new RsScreenActions.Press(), RsKind.SWITCH));
        assertTrue(RsScreenActions.fits(new RsScreenActions.Level(0), RsKind.SWITCH));
        assertFalse(RsScreenActions.fits(new RsScreenActions.Level(0), RsKind.LAMP), "lamp brightness is 1–15 (off is the power button)");
        assertFalse(RsScreenActions.fits(new RsScreenActions.Power(true), RsKind.SENSOR));
        assertFalse(RsScreenActions.fits(new RsScreenActions.Pin(true), RsKind.SENSOR), "a sensor has no control to protect");
        assertTrue(RsScreenActions.fits(new RsScreenActions.Follow(true), RsKind.LAMP));
        assertTrue(RsScreenActions.fits(new RsScreenActions.Rename("x"), RsKind.SENSOR));
    }
}
