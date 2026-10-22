package ai.ezfox.cccloud.gui;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.uplink.ComputerCard;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Every screen packet type: screen-open check, rights check and argument validation happen before anything runs. */
class GuiActionsTest {
    private static final Map<GuiActions.Mainframe, String> VALID_ARGS = Map.of(
        GuiActions.Mainframe.TOGGLE_DASHBOARD, "",
        GuiActions.Mainframe.NEW_CODE, "",
        GuiActions.Mainframe.APPROVE, "AbC_123-xyz",
        GuiActions.Mainframe.DENY, "AbC_123-xyz",
        GuiActions.Mainframe.REVOKE, "0123456789abcdef",
        GuiActions.Mainframe.REVOKE_ALL, "",
        GuiActions.Mainframe.TRUST, "Smokey_de",
        GuiActions.Mainframe.UNTRUST, "112a047d-444c-4422-b20e-4f86afe7f0cb",
        GuiActions.Mainframe.PIN_SET, "1234",
        GuiActions.Mainframe.PIN_CLEAR, ""
    );

    /** Records which operation ran. */
    private static final class Recorder implements GuiActions.MainframeOps, GuiActions.UplinkOps {
        final List<String> calls = new ArrayList<>();

        private @Nullable String call(String what) {
            calls.add(what);
            return null;
        }

        @Override public @Nullable String toggleDashboard() { return call("toggle_dashboard"); }
        @Override public @Nullable String newCode() { return call("new_code"); }
        @Override public @Nullable String approve(String id) { return call("approve:" + id); }
        @Override public @Nullable String deny(String id) { return call("deny:" + id); }
        @Override public @Nullable String revoke(String id) { return call("revoke:" + id); }
        @Override public @Nullable String revokeAll() { return call("revoke_all"); }
        @Override public @Nullable String trust(String name) { return call("trust:" + name); }
        @Override public @Nullable String untrust(UUID player) { return call("untrust:" + player); }
        @Override public @Nullable String setPin(String pin) { return call("pin_set"); }
        @Override public @Nullable String clearPin() { return call("pin_clear"); }
        @Override public @Nullable String unlink() { return call("unlink"); }
    }

    @Test
    void everyMainframeActionNeedsTheScreenAndManageRights() {
        assertEquals(GuiActions.Mainframe.values().length, VALID_ARGS.size(), "test covers every action");
        for (var action : GuiActions.Mainframe.values()) {
            String arg = VALID_ARGS.get(action);
            var ops = new Recorder();
            assertEquals("no_screen", GuiActions.mainframe(action.id(), arg, false, true, ops), action + " without open screen");
            assertEquals("not_owner", GuiActions.mainframe(action.id(), arg, true, false, ops), action + " without rights");
            assertEquals("no_screen", GuiActions.mainframe(action.id(), arg, false, false, ops), action + " without both");
            assertTrue(ops.calls.isEmpty(), action + " must not run when denied: " + ops.calls);

            assertNull(GuiActions.mainframe(action.id(), arg, true, true, ops), action + " allowed for managers");
            assertEquals(1, ops.calls.size(), action + " ran once");
        }
    }

    @Test
    void argumentsAreValidated() {
        String[][] bad = {
            {"toggle_dashboard", "x"}, {"new_code", "x"}, {"revoke_all", "all"}, {"pin_clear", "1"},
            {"approve", ""}, {"approve", "<script>"}, {"approve", "a".repeat(65)}, {"deny", "id with space"},
            {"revoke", "0123"}, {"revoke", "0123456789ABCDEF"}, {"revoke", "0123456789abcdeg"},
            {"trust", ""}, {"trust", "a-b"}, {"trust", "x".repeat(17)}, {"trust", "§cRed"},
            {"untrust", "not-a-uuid"}, {"untrust", ""},
        };
        for (var b : bad) {
            var ops = new Recorder();
            assertEquals("invalid_argument", GuiActions.mainframe(b[0], b[1], true, true, ops), b[0] + " '" + b[1] + "'");
            assertTrue(ops.calls.isEmpty());
        }
        for (var pin : new String[]{"", "123", "123456789", "12a4", "١٢٣٤"}) {
            var ops = new Recorder();
            assertEquals("invalid_pin", GuiActions.mainframe("pin_set", pin, true, true, ops), pin);
            assertTrue(ops.calls.isEmpty());
        }
        var ops = new Recorder();
        assertEquals("invalid_argument", GuiActions.mainframe("trust", "x".repeat(GuiActions.MAX_ARG + 1), true, true, ops));
    }

    @Test
    void unknownActionsAreRejectedFirst() {
        var ops = new Recorder();
        for (var action : new String[]{"", "TOGGLE_DASHBOARD", "op", "unlink", "set_owner"}) {
            assertEquals("unknown_action", GuiActions.mainframe(action, "", true, true, ops), action);
        }
        assertEquals("unknown_action", GuiActions.uplink("toggle_dashboard", "", true, true, ops));
        assertTrue(ops.calls.isEmpty());
    }

    @Test
    void uplinkUnlinkNeedsScreenAndRights() {
        var ops = new Recorder();
        assertEquals("no_screen", GuiActions.uplink("unlink", "", false, true, ops));
        assertEquals("not_owner", GuiActions.uplink("unlink", "", true, false, ops));
        assertEquals("invalid_argument", GuiActions.uplink("unlink", "x", true, true, ops));
        assertTrue(ops.calls.isEmpty());
        assertNull(GuiActions.uplink("unlink", "", true, true, ops));
        assertEquals(List.of("unlink"), ops.calls);
    }

    @Test
    void computerCardActions() {
        assertEquals(22, ComputerCard.widgets().size());
        assertEquals(new ComputerCard.Power("reboot"), ComputerCard.action(ComputerCard.REBOOT, new JsonPrimitive(true)));
        assertEquals(new ComputerCard.SetOutput("left", 15), ComputerCard.action("left_out", new JsonPrimitive(true)));
        assertEquals(new ComputerCard.SetOutput("left", 0), ComputerCard.action("left_out", new JsonPrimitive(false)));
        assertEquals(new ComputerCard.SetOutput("back", 7), ComputerCard.action("back_level", new JsonPrimitive(7)));
        assertNull(ComputerCard.action(ComputerCard.POWER, new JsonPrimitive(true)), "indicators are read-only");
        assertNull(ComputerCard.action("top_in", new JsonPrimitive(true)));
        var reboot = ComputerCard.widgets().stream().filter(w -> w.id().equals("reboot")).findFirst().orElseThrow();
        assertTrue(reboot.opts().get("confirm").getAsBoolean(), "reboot asks for confirmation");
    }
}
