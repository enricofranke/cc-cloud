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
    private static final Map<GuiActions.Mainframe, String> VALID_ARGS = Map.ofEntries(
        Map.entry(GuiActions.Mainframe.TOGGLE_DASHBOARD, ""),
        Map.entry(GuiActions.Mainframe.NEW_CODE, ""),
        Map.entry(GuiActions.Mainframe.APPROVE, "AbC_123-xyz"),
        Map.entry(GuiActions.Mainframe.DENY, "AbC_123-xyz"),
        Map.entry(GuiActions.Mainframe.REVOKE, "0123456789abcdef"),
        Map.entry(GuiActions.Mainframe.REVOKE_ALL, ""),
        Map.entry(GuiActions.Mainframe.TRUST, "Smokey_de"),
        Map.entry(GuiActions.Mainframe.UNTRUST, "112a047d-444c-4422-b20e-4f86afe7f0cb"),
        Map.entry(GuiActions.Mainframe.PIN_SET, "1234"),
        Map.entry(GuiActions.Mainframe.PIN_CLEAR, ""),
        Map.entry(GuiActions.Mainframe.SET_ROLE, "112a047d-444c-4422-b20e-4f86afe7f0cb:operator"),
        Map.entry(GuiActions.Mainframe.LINK_ACCOUNT, "")
    );

    static final java.util.Set<ai.ezfox.cccloud.access.Perm> ALL = ai.ezfox.cccloud.access.Perm.all();
    static final java.util.Set<ai.ezfox.cccloud.access.Perm> NONE = java.util.Set.of(ai.ezfox.cccloud.access.Perm.VIEW);

    @Test
    void everyActionNeedsItsOwnPermission() {
        for (var action : GuiActions.Mainframe.values()) {
            if (action.perm == null) continue;
            var withoutIt = java.util.EnumSet.copyOf(ALL);
            withoutIt.remove(action.perm);
            var ops = new Recorder();
            assertEquals("not_owner", GuiActions.mainframe(action.id(), VALID_ARGS.get(action), true, withoutIt, ops), action + " without " + action.perm.id);
            assertTrue(ops.calls.isEmpty());
            assertNull(GuiActions.mainframe(action.id(), VALID_ARGS.get(action), true, java.util.Set.of(ai.ezfox.cccloud.access.Perm.VIEW, action.perm), ops),
                action + " with only " + action.perm.id);
        }
    }

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
        @Override public @Nullable String setRole(UUID player, ai.ezfox.cccloud.access.Role role) { return call("set_role:" + player + ":" + role.id); }
        @Override public @Nullable String linkAccount() { return call("link_account"); }
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
            assertEquals("no_screen", GuiActions.mainframe(action.id(), arg, false, ALL, ops), action + " without open screen");
            if (action == GuiActions.Mainframe.LINK_ACCOUNT) {
                // "Link this browser" is for every player (cccloud.use is checked by the op itself).
                assertNull(GuiActions.mainframe(action.id(), arg, true, NONE, ops));
                assertEquals(List.of("link_account"), ops.calls);
                continue;
            }
            assertEquals("not_owner", GuiActions.mainframe(action.id(), arg, true, NONE, ops), action + " without rights");
            assertEquals("no_screen", GuiActions.mainframe(action.id(), arg, false, NONE, ops), action + " without both");
            assertTrue(ops.calls.isEmpty(), action + " must not run when denied: " + ops.calls);

            assertNull(GuiActions.mainframe(action.id(), arg, true, ALL, ops), action + " allowed for managers");
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
            {"set_role", "112a047d-444c-4422-b20e-4f86afe7f0cb"}, {"set_role", "112a047d-444c-4422-b20e-4f86afe7f0cb:owner"},
            {"set_role", "112a047d-444c-4422-b20e-4f86afe7f0cb:god"}, {"set_role", "x:admin"}, {"link_account", "x"},
        };
        for (var b : bad) {
            var ops = new Recorder();
            assertEquals("invalid_argument", GuiActions.mainframe(b[0], b[1], true, ALL, ops), b[0] + " '" + b[1] + "'");
            assertTrue(ops.calls.isEmpty());
        }
        for (var pin : new String[]{"", "123", "123456789", "12a4", "١٢٣٤"}) {
            var ops = new Recorder();
            assertEquals("invalid_pin", GuiActions.mainframe("pin_set", pin, true, ALL, ops), pin);
            assertTrue(ops.calls.isEmpty());
        }
        var ops = new Recorder();
        assertEquals("invalid_argument", GuiActions.mainframe("trust", "x".repeat(GuiActions.MAX_ARG + 1), true, ALL, ops));
    }

    @Test
    void unknownActionsAreRejectedFirst() {
        var ops = new Recorder();
        for (var action : new String[]{"", "TOGGLE_DASHBOARD", "op", "unlink", "set_owner"}) {
            assertEquals("unknown_action", GuiActions.mainframe(action, "", true, ALL, ops), action);
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
        var widgets = ComputerCard.widgets();
        assertEquals(List.of("power", "turn_on", "reboot", "shutdown", "redstone"), widgets.stream().map(w -> w.id()).toList());
        assertEquals(new ComputerCard.Power("reboot"), ComputerCard.action(ComputerCard.REBOOT, new JsonPrimitive(true), null));
        assertEquals(new ComputerCard.SetOutput("front", 15),
            ComputerCard.action(ComputerCard.REDSTONE, null, new ai.ezfox.cccloud.uplink.Op.SetOutput("front", 15)));
        assertNull(ComputerCard.action(ComputerCard.REDSTONE, new JsonPrimitive(15), null), "redstone needs the set_output op");
        assertNull(ComputerCard.action(ComputerCard.POWER, new JsonPrimitive(true), null), "indicators are read-only");
        var reboot = widgets.stream().filter(w -> w.id().equals("reboot")).findFirst().orElseThrow();
        assertTrue(reboot.opts().get("confirm").getAsBoolean(), "reboot asks for confirmation");
        var sides = ComputerCard.sides(new int[]{0, 0, 0, 0, 0, 15}, new int[]{0, 0, 15, 0, 0, 0});
        assertEquals("[{\"side\":\"top\",\"out\":0,\"in\":0},{\"side\":\"bottom\",\"out\":0,\"in\":0},{\"side\":\"left\",\"out\":0,\"in\":15},"
            + "{\"side\":\"right\",\"out\":0,\"in\":0},{\"side\":\"front\",\"out\":0,\"in\":0},{\"side\":\"back\",\"out\":15,\"in\":0}]", sides.toString());
    }

    @Test
    void redstoneSidesActionsAreValidated() {
        var redstone = ComputerCard.widgets().getLast();
        java.util.function.Function<String, ai.ezfox.cccloud.uplink.ActionValidator.Result> act = json -> {
            var value = com.google.gson.JsonParser.parseString(json);
            var op = value.isJsonObject() ? ai.ezfox.cccloud.uplink.Op.parse(value.getAsJsonObject()) : null;
            return ai.ezfox.cccloud.uplink.ActionValidator.validate(redstone, value, op, false);
        };
        assertTrue(act.apply("{\"op\":\"set_output\",\"side\":\"front\",\"value\":15}").ok());
        assertTrue(act.apply("{\"op\":\"set_output\",\"side\":\"back\",\"value\":0}").ok());
        for (var bad : new String[]{
            "{\"op\":\"set_output\",\"side\":\"north\",\"value\":15}",
            "{\"op\":\"set_output\",\"side\":\"front\",\"value\":16}",
            "{\"op\":\"set_output\",\"side\":\"front\",\"value\":-1}",
            "{\"op\":\"set_output\",\"side\":\"front\",\"value\":7.5}",
            "{\"op\":\"set_output\",\"side\":\"front\"}",
            "{\"op\":\"set_output\",\"side\":\"front\",\"value\":1,\"x\":1}",
            "{\"op\":\"craft\",\"name\":\"minecraft:glass\",\"count\":1}",
            "true", "15",
        }) {
            assertEquals("invalid_value", act.apply(bad).error(), bad);
        }
        // set_output is rejected on every other widget type
        var watch = new ai.ezfox.cccloud.uplink.Widget("w", ai.ezfox.cccloud.uplink.WidgetType.WATCHLIST, "W", new com.google.gson.JsonObject(), null);
        var value = com.google.gson.JsonParser.parseString("{\"op\":\"set_output\",\"side\":\"front\",\"value\":15}");
        assertEquals("invalid_value", ai.ezfox.cccloud.uplink.ActionValidator.validate(watch, value, ai.ezfox.cccloud.uplink.Op.parse(value.getAsJsonObject()), false).error());
        assertThrows(ai.ezfox.cccloud.uplink.WidgetFactory.WidgetException.class,
            () -> ai.ezfox.cccloud.uplink.WidgetFactory.create(ai.ezfox.cccloud.uplink.WidgetType.REDSTONE_SIDES, "r", "R", null), "not available from Lua");
    }
}
