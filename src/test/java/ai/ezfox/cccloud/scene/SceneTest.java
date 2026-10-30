package ai.ezfox.cccloud.scene;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.rules.Rule;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** v0.10 scenes: the strict schema and the step runner (order, tick-based delays, per-step checks, aborts). */
class SceneTest {
    static final String U = "0a1b2c3d-0000-4000-8000-000000000001";

    static SceneCodec.Result parse(String json) {
        return SceneCodec.parse(JsonParser.parseString(json), 4096);
    }

    static Scene scene(String steps) {
        var r = parse("{\"id\":\"0a1b2c3d\",\"name\":\"Night\",\"steps\":[" + steps + "]}");
        assertNull(r.error(), r.error());
        return r.scene();
    }

    static String widget(String id, String value) {
        return "{\"type\":\"widget\",\"uplinkId\":\"" + U + "\",\"widgetId\":\"" + id + "\",\"value\":" + value + "}";
    }

    @Test
    void schema() {
        var r = parse("{\"name\":\"  Night mode \",\"icon\":\"star\",\"color\":\"red\",\"confirm\":true,\"pin\":false,\"steps\":["
            + widget("lamp", "false") + ",{\"type\":\"delay\",\"seconds\":2.5},{\"type\":\"redstone\",\"uplinkId\":\"" + U + "\",\"side\":\"back\",\"value\":0},"
            + "{\"type\":\"alert\",\"text\":\"Good night\",\"level\":\"info\"}]}");
        assertNull(r.error(), r.error());
        var s = r.scene();
        assertEquals("Night mode", s.name(), "trimmed plain text");
        assertEquals("", s.id(), "new scene: the server assigns the id");
        assertTrue(s.confirm());
        assertEquals(4, s.steps().size());
        assertTrue(s.steps().get(1).isDelay());
        assertEquals(50, s.steps().get(1).delayTicks(), "2.5 s = 50 ticks");
        assertEquals(1, Scene.Step.delay(0.05).delayTicks(), "the shortest delay is one tick");
        assertEquals(2, Scene.Step.delay(0.06).delayTicks(), "rounded up");
        // Round trip, with the fields the server adds being ignored when sent back.
        var json = SceneCodec.toJson(s.withId("0a1b2c3d"));
        json.addProperty("owner", "Steve");
        json.addProperty("needsPin", true);
        json.add("lastRun", com.google.gson.JsonNull.INSTANCE);
        var again = SceneCodec.parse(json, 4096);
        assertNull(again.error(), again.error());
        assertEquals(SceneCodec.toJson(s.withId("0a1b2c3d")), SceneCodec.toJson(again.scene()));
    }

    @Test
    void schemaRefusals() {
        String ok = widget("lamp", "true");
        var bad = new String[]{
            "{\"name\":\"x\",\"steps\":[]}",
            "{\"name\":\"\",\"steps\":[" + ok + "]}",
            "{\"name\":\"" + "n".repeat(33) + "\",\"steps\":[" + ok + "]}",
            "{\"name\":\"x\",\"steps\":[" + ok + "],\"extra\":1}",
            "{\"name\":\"x\",\"icon\":\"nope\",\"steps\":[" + ok + "]}",
            "{\"name\":\"x\",\"color\":\"pink\",\"steps\":[" + ok + "]}",
            "{\"name\":\"x\",\"confirm\":\"yes\",\"steps\":[" + ok + "]}",
            "{\"name\":\"x\",\"id\":\"XYZ\",\"steps\":[" + ok + "]}",
            "{\"name\":\"x\",\"steps\":[{\"type\":\"delay\",\"seconds\":1}]}",
            "{\"name\":\"x\",\"steps\":[" + ok + ",{\"type\":\"delay\",\"seconds\":301}]}",
            "{\"name\":\"x\",\"steps\":[" + ok + ",{\"type\":\"delay\",\"seconds\":0.01}]}",
            "{\"name\":\"x\",\"steps\":[" + ok + ",{\"type\":\"delay\",\"seconds\":1,\"x\":1}]}",
            "{\"name\":\"x\",\"steps\":[" + ok + ",{\"type\":\"delay\",\"seconds\":300},{\"type\":\"delay\",\"seconds\":300},{\"type\":\"delay\",\"seconds\":1}]}",
            "{\"name\":\"x\",\"steps\":[{\"type\":\"scene\",\"sceneId\":\"0a1b2c3d\"}]}",
            "{\"name\":\"x\",\"steps\":[{\"type\":\"widget\",\"uplinkId\":\"nope\",\"widgetId\":\"a\",\"value\":1}]}",
            "{\"name\":\"x\",\"steps\":[" + (ok + ",").repeat(16) + ok + "]}",
        };
        for (String json : bad) assertNotNull(parse(json).error(), json);
        assertTrue(parse("{\"name\":\"x\",\"steps\":[{\"type\":\"scene\",\"sceneId\":\"0a1b2c3d\"}]}").error().contains("scene"));
        assertNotNull(SceneCodec.parse(JsonParser.parseString("{\"name\":\"" + "x".repeat(5000) + "\",\"steps\":[" + ok + "]}"), 4096).error(), "≤ 4 KB");
    }

    /** A test environment: records what ran, refuses what is in {@code denied}. */
    static class Env implements SceneRunner.Env {
        boolean available = true;
        final List<String> denied = new ArrayList<>();
        final List<String> ran = new ArrayList<>();

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public @Nullable String check(Rule.Action action) {
            return action instanceof Rule.WidgetAction w && denied.contains(w.widgetId()) ? "not_allowed" : null;
        }

        @Override
        public @Nullable String run(Rule.Action action) {
            ran.add(action instanceof Rule.WidgetAction w ? w.widgetId() : action.getClass().getSimpleName());
            return null;
        }
    }

    @Test
    void stepsRunInOrderWithTickDelays() {
        var s = scene(widget("a", "true") + ",{\"type\":\"delay\",\"seconds\":1}," + widget("b", "true") + "," + widget("c", "true")
            + ",{\"type\":\"delay\",\"seconds\":0.1}," + widget("d", "true"));
        var run = new SceneRunner.Run(s, "Steve (Phone)", "dashboard", 1000);
        var env = new Env();
        assertTrue(SceneRunner.advance(run, 100, 1000, env));
        assertEquals(List.of("a"), env.ran, "up to the first delay in the first tick");
        assertFalse(SceneRunner.advance(run, 119, 1001, env), "waiting");
        assertEquals(List.of("a"), env.ran);
        assertTrue(SceneRunner.advance(run, 120, 1002, env));
        assertEquals(List.of("a", "b", "c"), env.ran, "20 ticks later the next steps up to the next delay");
        assertFalse(run.done());
        SceneRunner.advance(run, 121, 1003, env);
        assertEquals(3, env.ran.size(), "0.1 s = 2 ticks");
        SceneRunner.advance(run, 122, 1004, env);
        assertEquals(List.of("a", "b", "c", "d"), env.ran);
        assertTrue(run.done());
        assertNull(run.firstError());
        assertFalse(SceneRunner.advance(run, 200, 2000, env), "a finished run does nothing");
    }

    @Test
    void refusedStepsDoNotStopTheScene() {
        var s = scene(widget("a", "true") + "," + widget("secret", "true") + "," + widget("c", "true"));
        var run = new SceneRunner.Run(s, "Opal (tablet)", "tablet", 1000);
        var env = new Env();
        env.denied.add("secret");
        SceneRunner.advance(run, 1, 1000, env);
        assertEquals(List.of("a", "c"), env.ran, "the refused step never ran, the others did");
        assertArrayEquals(new String[]{"ok", "not_allowed", "ok"}, run.results());
        assertEquals("not_allowed", run.firstError());
    }

    @Test
    void anUnavailableMainframeEndsTheRun() {
        var s = scene(widget("a", "true") + ",{\"type\":\"delay\",\"seconds\":1}," + widget("b", "true"));
        var run = new SceneRunner.Run(s, "x", "rule", 1000);
        var env = new Env();
        SceneRunner.advance(run, 1, 1000, env);
        env.available = false;
        SceneRunner.advance(run, 30, 1001, env);
        assertTrue(run.done());
        assertArrayEquals(new String[]{"ok", "ok", "offline"}, run.results());
        var other = new SceneRunner.Run(s, "x", "rule", 1000);
        SceneRunner.advance(other, 1, 1000, new Env());
        assertTrue(SceneRunner.abort(other, 1002, "offline"), "aborted while waiting");
        assertArrayEquals(new String[]{"ok", "offline", "offline"}, other.results());
        assertFalse(SceneRunner.abort(other, 1003, "offline"));
    }

    @Test
    void anExceptionIsAStepError() {
        var s = scene(widget("a", "true") + "," + widget("b", "true"));
        var run = new SceneRunner.Run(s, "x", "dashboard", 1);
        var env = new Env() {
            @Override
            public @Nullable String run(Rule.Action action) {
                if (action instanceof Rule.WidgetAction w && w.widgetId().equals("a")) throw new IllegalStateException("boom");
                return super.run(action);
            }
        };
        SceneRunner.advance(run, 1, 1, env);
        assertArrayEquals(new String[]{"internal_error", "ok"}, run.results());
    }
}
