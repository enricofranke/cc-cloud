package ai.ezfox.cccloud.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The Advanced Cloud Sensor's filter: modes, "ignore my team", allow and deny lists, and parsing the name lists. */
class SensorFilterTest {
    static final SensorFilter.Candidate STEVE = new SensorFilter.Candidate(SensorKind.PLAYER, "Steve", false);
    static final SensorFilter.Candidate MATE = new SensorFilter.Candidate(SensorKind.PLAYER, "Mate", true);
    static final SensorFilter.Candidate ZOMBIE = new SensorFilter.Candidate(SensorKind.HOSTILE, null, false);
    static final SensorFilter.Candidate COW = new SensorFilter.Candidate(SensorKind.PASSIVE, null, false);
    static final SensorFilter.Candidate ITEM = new SensorFilter.Candidate(SensorKind.ITEM, null, false);
    static final SensorFilter.Candidate CART = new SensorFilter.Candidate(SensorKind.OTHER, null, false);

    private static List<Boolean> all(SensorFilter f) {
        return List.of(f.test(STEVE), f.test(MATE), f.test(ZOMBIE), f.test(COW), f.test(ITEM), f.test(CART));
    }

    @Test
    void modes() {
        var f = SensorFilter.DEFAULT.withIgnoreTeam(false);
        assertEquals(List.of(true, true, true, true, true, true), all(f), "everyone: players, mobs, items and other things");
        assertEquals(List.of(true, true, false, false, false, false), all(f.withMode(SensorFilter.Mode.PLAYERS)), "players only");
        assertEquals(List.of(false, false, true, true, false, false), all(f.withMode(SensorFilter.Mode.MOBS)), "mobs only: hostile and passive");
        assertEquals(List.of(false, false, true, false, false, false), all(f.withMode(SensorFilter.Mode.HOSTILE)), "hostile only");
    }

    @Test
    void ignoreMyTeamIsOnByDefault() {
        assertTrue(SensorFilter.DEFAULT.ignoreTeam());
        assertTrue(SensorFilter.DEFAULT.test(STEVE));
        assertFalse(SensorFilter.DEFAULT.test(MATE), "the owner and the owner's team are ignored");
        assertTrue(SensorFilter.DEFAULT.withIgnoreTeam(false).test(MATE));
    }

    @Test
    void denyAlwaysWinsAndAllowListsOnlyTheNamedPlayers() {
        var f = SensorFilter.DEFAULT.withDeny(List.of("steve"));
        assertFalse(f.test(STEVE), "deny list, any case");
        assertTrue(f.test(ZOMBIE), "lists only affect players");
        var only = SensorFilter.DEFAULT.withAllow(List.of("MATE"));
        assertTrue(only.test(MATE), "an allowed name is reported even from the owner's team");
        assertFalse(only.test(STEVE), "the allow list leaves out everyone else");
        assertTrue(only.test(COW), "and does not affect mobs");
        assertFalse(only.withDeny(List.of("mate")).test(MATE), "deny beats allow");
        assertFalse(only.withMode(SensorFilter.Mode.MOBS).test(MATE), "the mode comes first");
    }

    @Test
    void nameLists() {
        assertEquals(List.of("Steve", "Alex", "Notch_2"), SensorFilter.parseNames(" Steve, Alex;notch_2  steve Notch_2 ".replace("notch_2", "Notch_2")));
        assertEquals(List.of(), SensorFilter.parseNames(""));
        assertEquals(List.of(), SensorFilter.parseNames(" , ; "));
        assertNull(SensorFilter.parseNames("Steve, bad-name"), "only Minecraft names");
        assertNull(SensorFilter.parseNames("a".repeat(17)), "16 characters at most");
        var many = new StringBuilder();
        for (int i = 0; i < SensorFilter.MAX_NAMES + 1; i++) many.append("p").append(i).append(',');
        assertNull(SensorFilter.parseNames(many.toString()), "at most " + SensorFilter.MAX_NAMES + " names");
        assertNull(SensorFilter.parseNames("x".repeat(300)), "bounded input");
        assertEquals("Steve, Alex", SensorFilter.join(List.of("Steve", "Alex")));
    }

    @Test
    void modesByIdAndLabel() {
        for (var m : SensorFilter.Mode.values()) {
            assertEquals(m, SensorFilter.Mode.byId(m.id));
            assertEquals(m, SensorFilter.Mode.byLabel(m.label));
        }
        assertNull(SensorFilter.Mode.byId("nobody"));
    }
}
