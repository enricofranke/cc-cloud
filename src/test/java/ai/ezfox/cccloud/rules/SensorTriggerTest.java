package ai.ezfox.cccloud.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.sensor.SensorEntry;
import ai.ezfox.cccloud.sensor.SensorKind;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The rule trigger "Advanced Sensor detects …": schema, kind and player filters, {who} in the run's context. */
class SensorTriggerTest {
    static final String U = RuleEngineTest.U;
    static final SensorEntry STEVE = new SensorEntry(SensorKind.PLAYER, "minecraft:player", "Steve");
    static final SensorEntry ZOMBIE = new SensorEntry(SensorKind.HOSTILE, "minecraft:zombie", "Zombie");
    static final SensorEntry COW = new SensorEntry(SensorKind.PASSIVE, "minecraft:cow", "Cow");

    static final class World extends RuleEngineTest.FakeWorld {
        long seq;
        final List<RuleEngine.SensorHit> hits = new ArrayList<>();
        final List<String> alerts = new ArrayList<>();
        boolean loaded = true;

        void enter(SensorEntry e) {
            hits.add(new RuleEngine.SensorHit(++seq, e));
        }

        @Override
        public RuleEngine.@Nullable SensorFeed sensor(String uplinkId) {
            return loaded ? new RuleEngine.SensorFeed(seq, List.copyOf(hits)) : null;
        }

        @Override
        public @Nullable String run(Rule rule, Rule.Action action, RuleEngine.Context context) {
            if (action instanceof Rule.AlertAction a) alerts.add(context.fill(a.text(), true) + " | " + context.fill(a.text(), false));
            return super.run(rule, action);
        }
    }

    static Rule rule(String when) {
        return RuleEngineTest.rule("{\"name\":\"r\",\"cooldown\":5,\"when\":" + when + ",\"then\":[{\"type\":\"alert\",\"text\":\"{who} betritt den Reaktorraum\"}]}");
    }

    @Test
    void schema() {
        var parsed = RuleCodec.parse(JsonParser.parseString("{\"name\":\"n\",\"when\":{\"type\":\"sensor\",\"uplinkId\":\"" + U
            + "\",\"kind\":\"player\",\"player\":\"Steve\"},\"then\":[{\"type\":\"alert\",\"text\":\"t\"}]}"), 1);
        assertNull(parsed.error());
        var t = (Rule.SensorTrigger) parsed.rule().when();
        assertEquals("player", t.kind());
        assertEquals("Steve", t.player());
        var withId = parsed.rule().withId("0a1b2c3d");
        assertEquals(withId, RuleCodec.parse(RuleCodec.toJson(withId), 1).rule(), "round trip");
        var any = (Rule.SensorTrigger) RuleCodec.parse(JsonParser.parseString("{\"name\":\"n\",\"when\":{\"type\":\"sensor\",\"uplinkId\":\"" + U
            + "\"},\"then\":[{\"type\":\"alert\",\"text\":\"t\"}]}"), 1).rule().when();
        assertEquals("any", any.kind(), "kind defaults to any");
        for (String bad : List.of("\"kind\":\"dragon\"", "\"kind\":\"mob\",\"player\":\"Steve\"", "\"player\":\"no name\"", "\"extra\":1")) {
            var r = RuleCodec.parse(JsonParser.parseString("{\"name\":\"n\",\"when\":{\"type\":\"sensor\",\"uplinkId\":\"" + U + "\"," + bad
                + "},\"then\":[{\"type\":\"alert\",\"text\":\"t\"}]}"), 1);
            assertNotNull(r.error(), "refused: " + bad);
        }
    }

    @Test
    void firesOnNewEntriesWithWhoAndOnlyForMatchingKinds() {
        var players = rule("{\"type\":\"sensor\",\"uplinkId\":\"" + U + "\",\"kind\":\"player\"}");
        var mobs = rule("{\"type\":\"sensor\",\"uplinkId\":\"" + U + "\",\"kind\":\"mob\"}");
        var alex = rule("{\"type\":\"sensor\",\"uplinkId\":\"" + U + "\",\"player\":\"alex\"}");
        var engine = new RuleEngine();
        var world = new World();
        world.enter(STEVE); // before the rules looked: not reported
        assertTrue(engine.tick(List.of(players, mobs, alex), world).isEmpty(), "the first look only records where the log stands");
        world.enter(STEVE);
        world.now += 1000;
        var runs = engine.tick(List.of(players, mobs, alex), world);
        assertEquals(1, runs.size(), "only the player rule: " + runs);
        assertEquals("Steve", runs.getFirst().who());
        assertEquals(List.of("Steve betritt den Reaktorraum | a player betritt den Reaktorraum"), world.alerts);
        assertTrue(engine.tick(List.of(players, mobs, alex), world).isEmpty(), "no new events: nothing");

        world.alerts.clear();
        world.enter(ZOMBIE);
        world.enter(COW);
        world.enter(ZOMBIE);
        world.now += 10_000;
        runs = engine.tick(List.of(players, mobs, alex), world);
        assertEquals(1, runs.size());
        assertEquals(List.of("2 Zombies and a Cow betritt den Reaktorraum | 2 Zombies and a Cow betritt den Reaktorraum"), world.alerts, "mobs summed up");

        world.enter(new SensorEntry(SensorKind.PLAYER, "minecraft:player", "Alex"));
        world.now += 10_000;
        runs = engine.tick(List.of(players, mobs, alex), world);
        assertEquals(2, runs.size(), "the player rule and the rule for Alex (case-insensitive)");

        world.loaded = false;
        world.enter(STEVE);
        world.now += 10_000;
        assertTrue(engine.tick(List.of(players), world).isEmpty(), "sensor not loaded: keeps its place");
        world.loaded = true;
        assertEquals(1, engine.tick(List.of(players), world).size(), "and fires once it is back");
    }

    @Test
    void namesOffNeverMatchAPlayerName() {
        var alex = new Rule.SensorTrigger(U, "player", "Alex");
        assertTrue(alex.matches(new SensorEntry(SensorKind.PLAYER, "minecraft:player", "ALEX")));
        assertTrue(!alex.matches(new SensorEntry(SensorKind.PLAYER, "minecraft:player", null)), "names off: no name, no match");
        assertTrue(new Rule.SensorTrigger(U, "any", null).matches(COW));
        assertTrue(!new Rule.SensorTrigger(U, "hostile", null).matches(COW));
        assertEquals("someone", RuleEngine.Context.NONE.fill("{who}", true), "without a sensor trigger: someone");
    }
}
