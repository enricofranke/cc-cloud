package ai.ezfox.cccloud.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Edge triggers, conditions, cooldown, interval and time triggers, the per-mainframe run limit. */
class RuleEngineTest {
    static final String U = "0a1b2c3d-0000-4000-8000-000000000001";

    static final class FakeWorld implements RuleEngine.World {
        final Map<String, Double> widgets = new HashMap<>();
        long now = 1_000_000;
        long day = 0;
        double power = 100;
        boolean online = true;
        final List<String> ran = new ArrayList<>();

        @Override
        public @Nullable Double widget(String uplinkId, String widgetId) {
            return widgets.get(widgetId);
        }

        @Override
        public @Nullable Long items(String uplinkId, String item) {
            return null;
        }

        @Override
        public @Nullable Double powerPercent() {
            return power;
        }

        @Override
        public boolean online(String uplinkId) {
            return online;
        }

        @Override
        public long dayTime() {
            return day;
        }

        @Override
        public long now() {
            return now;
        }

        @Override
        public @Nullable String run(Rule rule, Rule.Action action) {
            ran.add(rule.name());
            return null;
        }
    }

    static Rule rule(String json) {
        var r = RuleCodec.parse(JsonParser.parseString(json), 4096);
        assertNull(r.error(), r.error());
        return r.rule().withId(Integer.toHexString(json.hashCode() | 0x10000000).substring(0, 8));
    }

    static final String ALERT = "[{\"type\":\"alert\",\"text\":\"t\"}]";

    @Test
    void firesOnTheEdgeOnlyAndRespectsCooldownAndConditions() {
        var hot = rule("{\"name\":\"hot\",\"cooldown\":5,\"when\":{\"type\":\"widget\",\"uplinkId\":\"" + U + "\",\"widgetId\":\"temp\",\"op\":\">\",\"value\":90},"
            + "\"if\":[{\"type\":\"power\",\"op\":\">\",\"value\":50}],\"then\":" + ALERT + "}");
        var engine = new RuleEngine();
        var world = new FakeWorld();
        world.widgets.put("temp", 95.0);
        assertTrue(engine.tick(List.of(hot), world).isEmpty(), "first evaluation only records the state (no firing on start)");
        world.widgets.put("temp", 80.0);
        engine.tick(List.of(hot), world);
        world.widgets.put("temp", 95.0);
        assertEquals(1, engine.tick(List.of(hot), world).size(), "false → true fires");
        assertTrue(engine.tick(List.of(hot), world).isEmpty(), "staying true does not fire again");
        world.widgets.put("temp", 80.0);
        engine.tick(List.of(hot), world);
        world.widgets.put("temp", 95.0);
        world.now += 1000;
        assertTrue(engine.tick(List.of(hot), world).isEmpty(), "cooldown 5 s");
        world.widgets.put("temp", 80.0);
        engine.tick(List.of(hot), world);
        world.widgets.put("temp", 95.0);
        world.now += 10_000;
        world.power = 10;
        assertTrue(engine.tick(List.of(hot), world).isEmpty(), "condition power > 50 fails");
        world.widgets.remove("temp");
        assertTrue(engine.tick(List.of(hot), world).isEmpty(), "unknown value keeps the state");
    }

    @Test
    void intervalTimeAndDisabled() {
        var every = rule("{\"name\":\"every\",\"when\":{\"type\":\"interval\",\"minutes\":1},\"then\":" + ALERT + "}");
        var dusk = rule("{\"name\":\"dusk\",\"when\":{\"type\":\"time\",\"time\":13000},\"then\":" + ALERT + "}");
        var engine = new RuleEngine();
        var world = new FakeWorld();
        world.day = 12_000;
        engine.tick(List.of(every, dusk), world);
        world.now += 60_000;
        world.day = 13_500;
        var runs = engine.tick(List.of(every, dusk), world);
        assertEquals(List.of("every", "dusk"), runs.stream().map(RuleEngine.Run::name).toList());
        world.day = 500; // next morning (wrapped): passing 13000 again only after another day
        assertTrue(engine.tick(List.of(dusk), world).isEmpty());
        var off = every.withEnabled(false);
        world.now += 120_000;
        assertTrue(engine.tick(List.of(off), world).isEmpty(), "disabled rules do not run");
    }

    @Test
    void runLimitPerSecond() {
        var engine = new RuleEngine();
        var world = new FakeWorld();
        var rules = new ArrayList<Rule>();
        for (int i = 0; i < 30; i++) rules.add(rule("{\"name\":\"r" + i + "\",\"when\":{\"type\":\"interval\",\"minutes\":1},\"then\":" + ALERT + "}"));
        engine.tick(rules, world);
        world.now += 60_000;
        var runs = engine.tick(rules, world);
        long ok = runs.stream().filter(RuleEngine.Run::ok).count();
        assertEquals(RuleEngine.MAX_RUNS_PER_SECOND, ok, "at most 20 runs per second per mainframe");
        assertTrue(runs.stream().anyMatch(r -> "rate_limited".equals(r.error())), "the first throttled run is reported");
    }
}
