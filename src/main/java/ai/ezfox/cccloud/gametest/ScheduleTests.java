package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.rules.RuleData;
import ai.ezfox.cccloud.rules.RuleService;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/**
 * v0.10 schedules through the real rule service (manual clocks per mainframe, so no other test is affected): a weekday
 * schedule fires at its time and stores the occurrence; after a "restart" with a missed occurrence exactly one run is
 * caught up (also for an interval missed many times); an in-game time trigger fires when the day time passes it; a
 * mainframe that is not loaded skips its schedules and logs that once per outage.
 */
public final class ScheduleTests {
    /** Monday 2026-03-02 09:59 UTC. */
    private static final long MONDAY_0959 = Instant.parse("2026-03-02T09:59:00Z").toEpochMilli();
    private static final long MINUTE = 60_000L;

    private ScheduleTests() {
    }

    private static int logCount(JsonArray log, String ruleId, String result) {
        int n = 0;
        for (var e : log) {
            var o = e.getAsJsonObject();
            if (o.get("rule").getAsString().equals(ruleId) && o.get("result").getAsString().equals(result)) n++;
        }
        return n;
    }

    public static void clocks(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        var server = helper.getLevel().getServer();
        long[] clock = {MONDAY_0959};
        long[] day = {12_900};
        UUID unloaded = UUID.randomUUID(); // a mainframe the server knows nothing of: never loaded
        long[] unloadedClock = {MONDAY_0959};
        String[] ids = new String[5];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                UUID mf = mainframe.getMainframeId();
                RuleService.setZoneForTest(ZoneOffset.UTC);
                RuleService.setClockForTest(mf, () -> clock[0]);
                RuleService.setDayTimeForTest(mf, () -> day[0]);
                var weekly = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"Morning\",\"cooldown\":5,\"when\":{\"type\":\"schedule\","
                    + "\"days\":[\"mon\",\"tue\",\"wed\",\"thu\",\"fri\",\"sat\",\"sun\"],\"at\":\"10:00\"},\"then\":[{\"type\":\"alert\",\"text\":\"morning\"}]}"));
                helper.assertTrue(weekly.error() == null, "schedule saved: " + weekly.error());
                ids[0] = weekly.id();
                var every = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"Every 5\",\"cooldown\":5,\"when\":{\"type\":\"interval\",\"minutes\":5},"
                    + "\"then\":[{\"type\":\"alert\",\"text\":\"tick\"}]}"));
                helper.assertTrue(every.error() == null, "interval saved: " + every.error());
                ids[1] = every.id();
                var dusk = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"Dusk\",\"cooldown\":5,\"when\":{\"type\":\"time\",\"time\":13000},"
                    + "\"then\":[{\"type\":\"alert\",\"text\":\"dusk\"}]}"));
                helper.assertTrue(dusk.error() == null, "in-game time saved: " + dusk.error());
                ids[2] = dusk.id();
                var mondays = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"Mondays\",\"cooldown\":5,\"when\":{\"type\":\"schedule\","
                    + "\"days\":[\"mon\"],\"at\":\"10:00\"},\"then\":[{\"type\":\"alert\",\"text\":\"monday\"}]}"));
                helper.assertTrue(mondays.error() == null, "weekly schedule saved: " + mondays.error());
                ids[4] = mondays.id();
                var schedules = RuleService.document(server, mf).getAsJsonObject("schedules");
                helper.assertTrue(schedules.get("count").getAsInt() == 4 && "Z".equals(schedules.get("timezone").getAsString()), "schedules: " + schedules);
                // An unloaded mainframe's schedule.
                RuleService.setClockForTest(unloaded, () -> unloadedClock[0]);
                var away = RuleService.set(server, unloaded, JsonParser.parseString("{\"name\":\"Away\",\"when\":{\"type\":\"schedule\",\"days\":[\"mon\",\"tue\"],"
                    + "\"at\":\"10:00\"},\"then\":[{\"type\":\"alert\",\"text\":\"away\"}]}"));
                helper.assertTrue(away.error() == null, "schedule of an unloaded mainframe saved: " + away.error());
                ids[3] = away.id();
            })
            .thenIdle(25) // the engines compute the next occurrences and record the day time
            .thenExecute(() -> {
                var log = RuleService.document(server, mainframe.getMainframeId()).getAsJsonArray("log");
                helper.assertTrue(log.isEmpty(), "nothing is due yet: " + log);
                clock[0] = MONDAY_0959 + 90_000; // 10:00:30
                day[0] = 13_050; // the day time passes 13000
                unloadedClock[0] = MONDAY_0959 + 90_000;
            })
            .thenWaitUntil(() -> {
                var log = RuleService.document(server, mainframe.getMainframeId()).getAsJsonArray("log");
                helper.assertTrue(logCount(log, ids[0], "ok") == 1, "the weekday schedule ran at 10:00: " + log);
                helper.assertTrue(logCount(log, ids[2], "ok") == 1, "the in-game time trigger ran at 13000: " + log);
                helper.assertTrue(RuleData.get(server).fired(mainframe.getMainframeId()).getOrDefault(ids[0], 0L) == clock[0], "the occurrence is stored");
                var away = RuleService.document(server, unloaded).getAsJsonArray("log");
                helper.assertTrue(away.size() == 1 && away.get(0).getAsJsonObject().get("skipped").getAsBoolean()
                    && "skipped_unloaded".equals(away.get(0).getAsJsonObject().get("result").getAsString()), "unloaded: skipped and logged: " + away);
            })
            .thenExecute(() -> {
                // Another occurrence while the mainframe is still unloaded: no second log entry (once per outage), nothing ran.
                unloadedClock[0] = MONDAY_0959 + 24 * 60 * MINUTE + 90_000; // Tuesday 10:00:30
                // The interval ran once by now? Not yet: it was set up at 09:59 and fires 5 minutes later.
                clock[0] = MONDAY_0959 + 6 * MINUTE + 30_000; // 10:05:30
            })
            .thenWaitUntil(() -> helper.assertTrue(logCount(RuleService.document(server, mainframe.getMainframeId()).getAsJsonArray("log"), ids[1], "ok") == 1,
                "the interval ran after 5 minutes"))
            .thenIdle(25)
            .thenExecute(() -> {
                var away = RuleService.document(server, unloaded).getAsJsonArray("log");
                helper.assertTrue(away.size() == 1, "one skip entry per outage: " + away);
                // A server "restart" 26 hours later: Tuesday 10:00 was missed, the interval ~ 300 times.
                clock[0] = MONDAY_0959 + 26 * 60 * MINUTE; // Tuesday 11:59
                RuleService.restartForTest(server, mainframe.getMainframeId());
            })
            .thenWaitUntil(() -> {
                var log = RuleService.document(server, mainframe.getMainframeId()).getAsJsonArray("log");
                helper.assertTrue(logCount(log, ids[0], "ok") == 2, "the missed 10:00 ran once after the restart: " + log);
                helper.assertTrue(logCount(log, ids[1], "ok") == 2, "the interval caught up exactly one run: " + log);
            })
            .thenIdle(40)
            .thenExecute(() -> {
                var log = RuleService.document(server, mainframe.getMainframeId()).getAsJsonArray("log");
                helper.assertTrue(logCount(log, ids[0], "ok") == 2 && logCount(log, ids[1], "ok") == 2, "no catch-up storm: " + log);
                var rules = RuleService.document(server, mainframe.getMainframeId()).getAsJsonArray("rules");
                for (var r : rules) {
                    var rule = r.getAsJsonObject();
                    if (rule.get("id").getAsString().equals(ids[0])) {
                        helper.assertTrue(rule.get("nextRun").getAsLong() == Instant.parse("2026-03-04T10:00:00Z").toEpochMilli(), "next run Wednesday 10:00: " + rule);
                    }
                }
                helper.assertTrue(logCount(log, ids[4], "ok") == 1, "the Monday schedule ran on Monday only: " + log);
                // A restart beyond the window (24 h): the missed Monday 10:00 a week later is 25 h ago, nothing is caught up.
                clock[0] = MONDAY_0959 + 8 * 24 * 60 * MINUTE + 61 * MINUTE; // the Tuesday after, 11:00
                RuleService.restartForTest(server, mainframe.getMainframeId());
            })
            .thenIdle(40)
            .thenExecute(() -> {
                var log = RuleService.document(server, mainframe.getMainframeId()).getAsJsonArray("log");
                helper.assertTrue(logCount(log, ids[4], "ok") == 1, "an occurrence older than the window is not caught up: " + log);
                RuleService.forget(server, unloaded);
                RuleService.forget(server, mainframe.getMainframeId());
                RuleService.setZoneForTest(null);
            })
            .thenSucceed();
    }
}
