package ai.ezfox.cccloud.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** The Advanced Cloud Sensor's event log: capped, sequence numbers, today's count at midnight, saving and loading. */
class SensorLogTest {
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    static final SensorEntry ZOMBIE = new SensorEntry(SensorKind.HOSTILE, "minecraft:zombie", "Zombie");
    static final SensorEntry STEVE = new SensorEntry(SensorKind.PLAYER, "minecraft:player", "Steve");

    static long at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 10, day, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }

    @Test
    void cappedWithSequenceNumbers() {
        var log = new SensorLog();
        for (int i = 0; i < SensorLog.MAX_EVENTS + 15; i++) log.add(i % 2 == 0, ZOMBIE, at(30, 12, 0) + i, ZONE);
        assertEquals(SensorLog.MAX_EVENTS, log.events().size(), "old entries are dropped");
        assertEquals(SensorLog.MAX_EVENTS + 15, log.lastSeq());
        assertEquals(16, log.events().getFirst().seq(), "the oldest kept is #16");
        assertEquals(3, log.after(log.lastSeq() - 3).size(), "events after a sequence number (rules)");
        assertEquals(SensorLog.MAX_EVENTS, log.after(0).size(), "older ones are gone");
    }

    @Test
    void todayCountsEntriesAndRollsOverAtMidnight() {
        var log = new SensorLog();
        log.add(true, STEVE, at(30, 23, 50), ZONE);
        log.add(false, STEVE, at(30, 23, 55), ZONE);
        log.add(true, ZOMBIE, at(30, 23, 58), ZONE);
        assertEquals(2, log.today(at(30, 23, 59), ZONE), "two entries (leaving does not count)");
        assertEquals(0, log.today(at(31, 0, 1), ZONE), "midnight in the server's zone");
        log.add(true, ZOMBIE, at(31, 0, 2), ZONE);
        assertEquals(1, log.today(at(31, 8, 0), ZONE));
        assertEquals(4, log.events().size(), "the events stay");
    }

    @Test
    void savesAndLoadsAndSurvivesGarbage() {
        var log = new SensorLog();
        log.add(true, STEVE, at(30, 10, 0), ZONE);
        log.add(true, new SensorEntry(SensorKind.PLAYER, "minecraft:player", null), at(30, 10, 1), ZONE);
        log.add(false, ZOMBIE, at(30, 10, 2), ZONE);
        var copy = SensorLog.fromJson(JsonParser.parseString(log.toJson().toString()).getAsJsonObject());
        assertEquals(log.events(), copy.events());
        assertEquals(log.lastSeq(), copy.lastSeq());
        assertEquals(2, copy.today(at(30, 11, 0), ZONE));
        assertNull(copy.events().get(1).entry().name(), "a player without a name (names were off) stays without one");

        var broken = SensorLog.fromJson(JsonParser.parseString("{\"seq\":5,\"events\":[{\"s\":1,\"k\":\"dragon\",\"id\":\"x\"},{\"s\":9,\"k\":\"player\",\"id\":\"p\"},"
            + "{\"s\":2,\"k\":\"hostile\",\"id\":\"minecraft:zombie\",\"n\":\"Zombie\",\"in\":true}, 7]}").getAsJsonObject());
        assertEquals(1, broken.events().size(), "unknown kinds and sequence numbers beyond seq are dropped");
        assertTrue(broken.events().getFirst().entered());
        assertTrue(SensorLog.fromJson(null).events().isEmpty());
        assertFalse(SensorLog.fromJson(JsonParser.parseString("{}").getAsJsonObject()).lastSeq() > 0);
    }
}
