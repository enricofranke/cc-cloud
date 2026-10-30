package ai.ezfox.cccloud.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * v0.10 schedules: the weekday trigger's schema and occurrences (time zones, daylight saving), missed runs after a
 * restart (at most one, within the window), skipped runs while the mainframe is not available, the scene action.
 */
class ScheduleTest {
    static final String ALERT = "[{\"type\":\"alert\",\"text\":\"t\"}]";
    static final long MONDAY_0959 = Instant.parse("2026-03-02T09:59:00Z").toEpochMilli();
    static final long MINUTE = 60_000L;
    static final RuleEngine.Settings UTC = new RuleEngine.Settings(ZoneOffset.UTC, true, 24 * 3_600_000L, 1);

    static final class World extends RuleEngineTest.FakeWorld {
        boolean available = true;
        String reason = "unloaded";

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public String unavailable() {
            return reason;
        }
    }

    static Rule rule(String json) {
        return RuleEngineTest.rule(json);
    }

    static Rule weekly(String days, String at) {
        return rule("{\"name\":\"weekly\",\"when\":{\"type\":\"schedule\",\"days\":[" + days + "],\"at\":\"" + at + "\"},\"then\":" + ALERT + "}");
    }

    @Test
    void scheduleSchema() {
        var ok = RuleCodec.parse(JsonParser.parseString("{\"name\":\"n\",\"when\":{\"type\":\"schedule\",\"days\":[\"sat\",\"mon\"],\"at\":\"07:05\"},\"then\":" + ALERT + "}"), 64);
        assertNull(ok.error(), ok.error());
        var t = (Rule.ScheduleTrigger) ok.rule().when();
        assertEquals(0b100001, t.days(), "mon = bit 0, sat = bit 5");
        assertEquals(7 * 60 + 5, t.minute());
        assertTrue(ok.rule().isSchedule());
        var json = RuleCodec.toJson(ok.rule()).getAsJsonObject("when");
        assertEquals("[\"mon\",\"sat\"]", json.get("days").toString(), "days come back in week order");
        assertEquals("07:05", json.get("at").getAsString());
        for (var bad : new String[]{"\"days\":[],\"at\":\"07:00\"", "\"days\":[\"mon\",\"mon\"],\"at\":\"07:00\"", "\"days\":[\"monday\"],\"at\":\"07:00\"",
            "\"days\":[\"mon\"],\"at\":\"24:00\"", "\"days\":[\"mon\"],\"at\":\"7:00\"", "\"days\":[\"mon\"]", "\"days\":[\"mon\"],\"at\":\"07:00\",\"tz\":\"UTC\"",
            "\"days\":[\"mon\",\"tue\",\"wed\",\"thu\",\"fri\",\"sat\",\"sun\",\"mon\"],\"at\":\"07:00\""}) {
            var r = RuleCodec.parse(JsonParser.parseString("{\"name\":\"n\",\"when\":{\"type\":\"schedule\"," + bad + "},\"then\":" + ALERT + "}"), 64);
            assertNull(r.rule(), bad);
        }
        assertFalse(rule("{\"name\":\"n\",\"when\":{\"type\":\"power\",\"op\":\"<\",\"value\":5},\"then\":" + ALERT + "}").isSchedule());
        assertTrue(rule("{\"name\":\"n\",\"when\":{\"type\":\"time\",\"time\":5},\"then\":" + ALERT + "}").isSchedule());
    }

    @Test
    void sceneAction() {
        var r = RuleCodec.parse(JsonParser.parseString("{\"name\":\"n\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[{\"type\":\"scene\",\"sceneId\":\"0a1b2c3d\"}]}"), 64);
        assertNull(r.error(), r.error());
        assertEquals(new Rule.SceneAction("0a1b2c3d"), r.rule().actions().getFirst());
        assertEquals("{\"type\":\"scene\",\"sceneId\":\"0a1b2c3d\"}", RuleCodec.actionJson(r.rule().actions().getFirst()).toString());
        assertNotNull(RuleCodec.parse(JsonParser.parseString("{\"name\":\"n\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":[{\"type\":\"scene\",\"sceneId\":\"x\"}]}"), 64).error());
        assertNotNull(RuleCodec.parseAction(JsonParser.parseString("{\"type\":\"scene\",\"sceneId\":\"0a1b2c3d\"}"), 64, false).error(), "scenes do not run scenes");
        assertNotNull(RuleCodec.parseAction(JsonParser.parseString("{\"type\":\"scene\",\"sceneId\":\"0a1b2c3d\"}"), 64, true).action(), "rules do");
    }

    @Test
    void occurrencesInTimeZones() {
        var weekdays = new Rule.ScheduleTrigger(0b0011111, 7 * 60);
        // Saturday 2026-03-07 12:00 UTC: last = Friday 07:00, next = Monday 07:00
        long sat = Instant.parse("2026-03-07T12:00:00Z").toEpochMilli();
        assertEquals(Instant.parse("2026-03-06T07:00:00Z").toEpochMilli(), ScheduleMath.last(weekdays, sat, ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-03-09T07:00:00Z").toEpochMilli(), ScheduleMath.next(weekdays, sat, ZoneOffset.UTC));
        // An occurrence exactly now is the last one, not the next.
        long fri = Instant.parse("2026-03-06T07:00:00Z").toEpochMilli();
        assertEquals(fri, ScheduleMath.last(weekdays, fri, ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-03-09T07:00:00Z").toEpochMilli(), ScheduleMath.next(weekdays, fri, ZoneOffset.UTC));
        // Europe/Berlin: 07:00 local is 06:00 UTC in winter, 05:00 UTC in summer.
        var berlin = ZoneId.of("Europe/Berlin");
        var daily = new Rule.ScheduleTrigger(0b1111111, 7 * 60);
        assertEquals(Instant.parse("2026-03-02T06:00:00Z").toEpochMilli(), ScheduleMath.next(daily, Instant.parse("2026-03-02T00:00:00Z").toEpochMilli(), berlin));
        assertEquals(Instant.parse("2026-07-01T05:00:00Z").toEpochMilli(), ScheduleMath.next(daily, Instant.parse("2026-07-01T00:00:00Z").toEpochMilli(), berlin));
        // Spring forward (29 March 2026, 02:00 → 03:00): 02:30 does not exist and moves to 03:30 local = 01:30 UTC.
        var night = new Rule.ScheduleTrigger(0b1111111, 2 * 60 + 30);
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), ScheduleMath.next(night, Instant.parse("2026-03-28T23:00:00Z").toEpochMilli(), berlin));
        // Fall back (25 October 2026, 03:00 → 02:00): 02:30 exists twice and counts once, at its first instant.
        long first = ScheduleMath.next(night, Instant.parse("2026-10-24T23:00:00Z").toEpochMilli(), berlin);
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), first);
        assertEquals(Instant.parse("2026-10-26T01:30:00Z").toEpochMilli(), ScheduleMath.next(night, first, berlin), "not again an hour later");
    }

    @Test
    void weeklyScheduleFiresOncePerOccurrence() {
        var r = weekly("\"mon\",\"wed\"", "10:00");
        var engine = new RuleEngine();
        var world = new World();
        world.now = MONDAY_0959;
        assertTrue(engine.tick(List.of(r), world, UTC).isEmpty(), "first evaluation computes the next occurrence");
        assertEquals(Instant.parse("2026-03-02T10:00:00Z").toEpochMilli(), engine.state(r.id()).nextAt());
        world.now = MONDAY_0959 + 2 * MINUTE;
        assertEquals(1, engine.tick(List.of(r), world, UTC).size(), "10:00 passed");
        assertEquals(world.now, engine.fired().get(r.id()), "the occurrence is remembered for restarts");
        assertTrue(engine.takeFiredDirty());
        assertFalse(engine.takeFiredDirty());
        world.now += MINUTE;
        assertTrue(engine.tick(List.of(r), world, UTC).isEmpty());
        assertEquals(Instant.parse("2026-03-04T10:00:00Z").toEpochMilli(), engine.state(r.id()).nextAt(), "next: Wednesday");
        // A big jump over several occurrences fires once.
        world.now = Instant.parse("2026-03-20T12:00:00Z").toEpochMilli();
        assertEquals(1, engine.tick(List.of(r), world, UTC).size());
        assertTrue(engine.tick(List.of(r), world, UTC).isEmpty());
    }

    @Test
    void restartCatchesUpAtMostOneMissedRun() {
        var r = weekly("\"mon\",\"tue\",\"wed\",\"thu\",\"fri\",\"sat\",\"sun\"", "10:00");
        var every = rule("{\"name\":\"every\",\"when\":{\"type\":\"interval\",\"minutes\":5},\"then\":" + ALERT + "}");
        long lastRun = Instant.parse("2026-03-02T10:00:10Z").toEpochMilli();
        var world = new World();
        // Down for 26 hours: Tuesday 10:00 and ~300 intervals were missed.
        world.now = lastRun + 26 * 60 * MINUTE;
        var engine = new RuleEngine(Map.of(r.id(), lastRun, every.id(), lastRun));
        world.available = false; // the mainframe loads a bit later
        assertTrue(engine.tick(List.of(r, every), world, UTC).isEmpty(), "first evaluation only plans");
        assertTrue(engine.tick(List.of(r, every), world, UTC).isEmpty(), "catch-up waits until the mainframe is available");
        world.available = true;
        world.now += 2 * MINUTE;
        var runs = engine.tick(List.of(r, every), world, UTC);
        assertEquals(2, runs.size(), "one catch-up run each: " + runs);
        assertTrue(runs.stream().noneMatch(RuleEngine.Run::skipped));
        assertTrue(engine.tick(List.of(r, every), world, UTC).isEmpty(), "no storm");
        assertEquals(Instant.parse("2026-03-04T10:00:00Z").toEpochMilli(), engine.state(r.id()).nextAt(), "then the normal rhythm");
    }

    @Test
    void missedRunsOutsideTheWindowOrSwitchedOffAreDropped() {
        var r = weekly("\"mon\"", "10:00");
        long lastRun = Instant.parse("2026-03-02T10:00:00Z").toEpochMilli();
        var world = new World();
        world.now = Instant.parse("2026-03-10T11:00:00Z").toEpochMilli(); // the Monday 10:00 a week later was 25 h ago
        var engine = new RuleEngine(Map.of(r.id(), lastRun));
        engine.tick(List.of(r), world, UTC);
        assertTrue(engine.tick(List.of(r), world, UTC).isEmpty(), "older than 24 h");
        var off = new RuleEngine.Settings(ZoneOffset.UTC, false, 24 * 3_600_000L, 1);
        world.now = Instant.parse("2026-03-09T12:00:00Z").toEpochMilli();
        var engine2 = new RuleEngine(Map.of(r.id(), lastRun));
        engine2.tick(List.of(r), world, off);
        assertTrue(engine2.tick(List.of(r), world, off).isEmpty(), "runMissed=false never catches up");
        // A rule edited after the restart (fresh state, not the engine's first evaluation) does not catch up either.
        var engine3 = new RuleEngine(Map.of());
        engine3.tick(List.of(), world, UTC);
        var edited = new RuleEngine(Map.of(r.id(), lastRun));
        edited.tick(List.of(), world, UTC); // the engine's first evaluation happened before the rule existed
        edited.tick(List.of(r), world, UTC);
        assertTrue(edited.tick(List.of(r), world, UTC).isEmpty(), "only the engine's first evaluation catches up");
    }

    @Test
    void intervalKeepsItsRhythmAcrossARestart() {
        var every = rule("{\"name\":\"every\",\"when\":{\"type\":\"interval\",\"minutes\":10},\"then\":" + ALERT + "}");
        long lastRun = 1_000_000_000L;
        var world = new World();
        world.now = lastRun + 4 * MINUTE;
        var engine = new RuleEngine(Map.of(every.id(), lastRun));
        engine.tick(List.of(every), world, UTC);
        assertEquals(lastRun + 10 * MINUTE, engine.state(every.id()).nextAt(), "the next run stays where it was");
        world.now = lastRun + 10 * MINUTE;
        assertEquals(1, engine.tick(List.of(every), world, UTC).size());
        // The server's minimum interval wins over a stored shorter one.
        var fast = rule("{\"name\":\"fast\",\"when\":{\"type\":\"interval\",\"minutes\":1},\"then\":" + ALERT + "}");
        var min5 = new RuleEngine.Settings(ZoneOffset.UTC, true, 24 * 3_600_000L, 5);
        var e2 = new RuleEngine();
        e2.tick(List.of(fast), world, min5);
        assertEquals(world.now + 5 * MINUTE, e2.state(fast.id()).nextAt());
    }

    @Test
    void unavailableMainframesSkipAndLogOncePerOutage() {
        var r = weekly("\"mon\",\"tue\",\"wed\",\"thu\",\"fri\",\"sat\",\"sun\"", "10:00");
        var dusk = rule("{\"name\":\"dusk\",\"when\":{\"type\":\"time\",\"time\":13000},\"then\":" + ALERT + "}");
        var hot = rule("{\"name\":\"hot\",\"when\":{\"type\":\"widget\",\"uplinkId\":\"" + RuleEngineTest.U + "\",\"widgetId\":\"t\",\"op\":\">\",\"value\":5},\"then\":" + ALERT + "}");
        var world = new World();
        world.available = false;
        world.now = MONDAY_0959;
        world.day = 12_000;
        world.online = false; // would look like an offline edge if edge triggers were evaluated
        var engine = new RuleEngine();
        assertTrue(engine.tick(List.of(r, dusk, hot), world, UTC).isEmpty());
        world.now += 2 * MINUTE;
        world.day = 13_100;
        var runs = engine.tick(List.of(r, dusk, hot), world, UTC);
        assertEquals(2, runs.size(), "both schedules came due: " + runs);
        assertTrue(runs.stream().allMatch(run -> run.skipped() && "skipped_unloaded".equals(run.error()) && run.actions() == 0));
        assertTrue(world.ran.isEmpty(), "nothing ran");
        world.now += 24 * 60 * MINUTE;
        world.day = 12_000;
        engine.tick(List.of(r, dusk, hot), world, UTC);
        world.day = 13_100;
        assertTrue(engine.tick(List.of(r, dusk, hot), world, UTC).isEmpty(), "one log entry per outage");
        // Back online: the next occurrence runs normally, nothing is caught up.
        world.available = true;
        world.online = true;
        assertTrue(engine.tick(List.of(r, dusk, hot), world, UTC).isEmpty(), "no catch-up of skipped occurrences");
        world.now += 24 * 60 * MINUTE;
        var back = engine.tick(List.of(r, dusk, hot), world, UTC);
        assertEquals(1, back.size());
        assertFalse(back.getFirst().skipped());
        // Offline again: a new outage logs again.
        world.available = false;
        world.reason = "offline";
        world.now += 24 * 60 * MINUTE;
        var again = engine.tick(List.of(r, dusk, hot), world, UTC);
        assertEquals(1, again.size());
        assertEquals("skipped_offline", again.getFirst().error());
    }

}
