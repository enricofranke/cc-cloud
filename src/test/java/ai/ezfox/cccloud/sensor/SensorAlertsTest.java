package ai.ezfox.cccloud.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The alert rate limit: the first alert at once, then one summary per cooldown, bounded; and the summary text. */
class SensorAlertsTest {
    static final SensorEntry ZOMBIE = new SensorEntry(SensorKind.HOSTILE, "minecraft:zombie", "Zombie");
    static final SensorEntry SKELETON = new SensorEntry(SensorKind.HOSTILE, "minecraft:skeleton", "Skeleton");
    static final SensorEntry STEVE = new SensorEntry(SensorKind.PLAYER, "minecraft:player", "Steve");
    static final SensorEntry ALEX = new SensorEntry(SensorKind.PLAYER, "minecraft:player", "Alex");
    static final long COOLDOWN = 10_000;

    @Test
    void firstAtOnceThenOneSummaryPerCooldown() {
        var alerts = new SensorAlerts();
        var first = alerts.entered(List.of(ZOMBIE), 0, COOLDOWN);
        assertNotNull(first, "the first entry goes out at once");
        assertEquals("A Zombie entered Reactor", SensorText.entered(first.entries(), true, first.more(), "Reactor"));
        assertNull(alerts.entered(List.of(ZOMBIE), 1_000, COOLDOWN), "collected during the cooldown");
        assertNull(alerts.entered(List.of(ZOMBIE, SKELETON), 2_000, COOLDOWN));
        assertNull(alerts.due(9_999, COOLDOWN), "not before the cooldown is over");
        var summary = alerts.due(10_000, COOLDOWN);
        assertNotNull(summary);
        assertEquals("2 Zombies and a Skeleton entered Reactor", SensorText.entered(summary.entries(), true, summary.more(), "Reactor"));
        assertNull(alerts.due(30_000, COOLDOWN), "nothing more to send");
        assertNull(alerts.entered(List.of(STEVE), 15_000, COOLDOWN), "the summary started a new cooldown");
        assertNotNull(alerts.due(20_000, COOLDOWN));
        assertNotNull(alerts.entered(List.of(STEVE), 40_000, COOLDOWN), "quiet for a cooldown: at once again");
    }

    @Test
    void boundedAndClearedWhenDisarmed() {
        var alerts = new SensorAlerts();
        alerts.entered(List.of(ZOMBIE), 0, COOLDOWN);
        var many = new ArrayList<SensorEntry>();
        for (int i = 0; i < SensorAlerts.MAX_PENDING + 40; i++) many.add(ZOMBIE);
        alerts.entered(many, 1, COOLDOWN);
        assertEquals(SensorAlerts.MAX_PENDING + 40, alerts.pending(), "counted");
        var summary = alerts.due(COOLDOWN, COOLDOWN);
        assertEquals(SensorAlerts.MAX_PENDING, summary.entries().size(), "at most " + SensorAlerts.MAX_PENDING + " kept");
        assertEquals(40, summary.more());
        assertEquals("64 Zombies and 40 more entered R", SensorText.entered(summary.entries(), true, summary.more(), "R"));
        alerts.entered(List.of(ZOMBIE), COOLDOWN + 1, COOLDOWN);
        alerts.clear();
        assertNull(alerts.due(10 * COOLDOWN, COOLDOWN), "disarmed: nothing collected goes out");
    }

    @Test
    void whoInPlainEnglish() {
        assertEquals("Steve", SensorText.who(List.of(STEVE), true));
        assertEquals("a player", SensorText.who(List.of(STEVE), false), "public form");
        assertEquals("Steve and Alex", SensorText.who(List.of(STEVE, ALEX, STEVE), true), "each player once");
        assertEquals("2 players and a Zombie", SensorText.who(List.of(STEVE, ZOMBIE, ALEX), false));
        assertEquals("Steve, Alex and 3 Zombies", SensorText.who(List.of(ZOMBIE, STEVE, ZOMBIE, ALEX, ZOMBIE), true), "players first");
        assertEquals("a player", SensorText.who(List.of(new SensorEntry(SensorKind.PLAYER, "minecraft:player", null)), true), "names off");
        var five = List.of(STEVE, ALEX, ZOMBIE, SKELETON, new SensorEntry(SensorKind.PASSIVE, "minecraft:cow", "Cow"),
            new SensorEntry(SensorKind.PASSIVE, "minecraft:cow", "Cow"));
        assertEquals("Steve, Alex, a Zombie and 3 more", SensorText.who(five, true), "at most four parts, the rest counted");
        assertEquals("a Zombie and 5 more", SensorText.who(List.of(ZOMBIE), true, 5));
        assertEquals("nobody", SensorText.who(List.of(), true));
        assertEquals("A player entered X", SensorText.entered(List.of(STEVE), false, "X"));
        assertEquals("Steve entered X", SensorText.entered(List.of(STEVE), true, "X"), "a name keeps its case");
        assertEquals("an Enderman", SensorText.who(List.of(new SensorEntry(SensorKind.HOSTILE, "minecraft:enderman", "Enderman")), true));
    }

    @Test
    void plurals() {
        assertEquals("Zombies", SensorText.plural("Zombie"));
        assertEquals("Endermen", SensorText.plural("Enderman"));
        assertEquals("Wolves", SensorText.plural("Wolf"));
        assertEquals("Sheep", SensorText.plural("Sheep"));
        assertEquals("Witches", SensorText.plural("Witch"));
        assertEquals("Foxes", SensorText.plural("Fox"));
        assertEquals("Bunnies", SensorText.plural("Bunny"));
        assertEquals("Allays", SensorText.plural("Allay"));
        assertEquals("Iron Golems", SensorText.plural("Iron Golem"));
        assertEquals("Tropical Fish", SensorText.plural("Tropical Fish"));
        assertEquals("Silverfish", SensorText.plural("Silverfish"));
    }
}
