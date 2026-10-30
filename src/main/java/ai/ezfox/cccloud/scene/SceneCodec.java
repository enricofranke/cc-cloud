package ai.ezfox.cccloud.scene;

import ai.ezfox.cccloud.rules.Rule;
import ai.ezfox.cccloud.rules.RuleCodec;
import ai.ezfox.cccloud.uplink.Icons;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Strict JSON schema of a {@link Scene} (docs/SPEC.md v0.10 "Scenes"): unknown keys, wrong types and out-of-range
 * numbers are rejected; steps use the rule action schema ({@link RuleCodec#parseAction}) plus {@code delay}. Pure
 * Java, unit tested.
 */
public final class SceneCodec {
    public static final int MAX_BYTES = 4096;
    static final Pattern ID = Pattern.compile("[0-9a-f]{8}");
    /** Fields the server adds to the document; a client may send them back, they are ignored. */
    private static final Set<String> SERVER_FIELDS = Set.of("owner", "needsPin", "perms", "runs", "lastRun");
    private static final Set<String> FIELDS = Set.of("id", "name", "icon", "color", "confirm", "pin", "steps");

    public record Result(@Nullable Scene scene, @Nullable String error) {
        static Result fail(String detail) {
            return new Result(null, detail);
        }
    }

    private SceneCodec() {
    }

    /** Parses a scene from the browser. A missing {@code id} is allowed (new scene, the server assigns one). */
    public static Result parse(@Nullable JsonElement element, long maxCraft) {
        if (!(element instanceof JsonObject obj)) return Result.fail("scene must be an object");
        if (element.toString().length() > MAX_BYTES) return Result.fail("scene too large");
        for (String key : obj.keySet()) if (!FIELDS.contains(key) && !SERVER_FIELDS.contains(key)) return Result.fail("unknown field scene." + key);
        String id = "";
        if (obj.has("id")) {
            if (!(obj.get("id") instanceof JsonPrimitive p) || !p.isString() || !ID.matcher(p.getAsString()).matches()) return Result.fail("id");
            id = p.getAsString();
        }
        if (!(obj.get("name") instanceof JsonPrimitive n) || !n.isString()) return Result.fail("name must be a string");
        String name = plain(n.getAsString());
        if (name.isEmpty()) return Result.fail("name is empty");
        if (name.length() > Scene.MAX_NAME) return Result.fail("name longer than " + Scene.MAX_NAME);
        String icon = optional(obj, "icon");
        if (icon != null && !Icons.isValid(icon)) return Result.fail("icon");
        String color = optional(obj, "color");
        if (color != null && !WidgetFactory.COLORS.contains(color)) return Result.fail("color must be cyan, green, yellow, red or gray");
        Boolean confirm = flag(obj, "confirm");
        Boolean pin = flag(obj, "pin");
        if (confirm == null || pin == null) return Result.fail("confirm and pin must be true or false");
        if (!(obj.get("steps") instanceof JsonArray array)) return Result.fail("steps must be an array");
        if (array.isEmpty()) return Result.fail("at least one step");
        if (array.size() > Scene.MAX_STEPS) return Result.fail("at most " + Scene.MAX_STEPS + " steps");
        var steps = new ArrayList<Scene.Step>();
        double totalDelay = 0;
        for (int i = 0; i < array.size(); i++) {
            var e = array.get(i);
            if (e instanceof JsonObject step && step.get("type") instanceof JsonPrimitive t && t.isString() && t.getAsString().equals("delay")) {
                for (String key : step.keySet()) if (!key.equals("type") && !key.equals("seconds")) return Result.fail("step " + (i + 1) + ": unknown field " + key);
                if (!(step.get("seconds") instanceof JsonPrimitive s) || !s.isNumber()) return Result.fail("step " + (i + 1) + ": seconds must be a number");
                double seconds = s.getAsDouble();
                if (!Double.isFinite(seconds) || seconds < Scene.MIN_DELAY || seconds > Scene.MAX_DELAY) {
                    return Result.fail("step " + (i + 1) + ": a delay is " + Scene.MIN_DELAY + "–" + (int) Scene.MAX_DELAY + " seconds");
                }
                totalDelay += seconds;
                steps.add(Scene.Step.delay(seconds));
                continue;
            }
            var action = RuleCodec.parseAction(e, maxCraft, false);
            if (action.action() == null) return Result.fail("step " + (i + 1) + ": " + action.error());
            steps.add(Scene.Step.of(action.action()));
        }
        if (totalDelay > Scene.MAX_TOTAL_DELAY) return Result.fail("all delays together at most " + (int) Scene.MAX_TOTAL_DELAY + " seconds");
        if (steps.stream().allMatch(Scene.Step::isDelay)) return Result.fail("at least one step that does something");
        return new Result(new Scene(id, name, icon, color, confirm, pin, steps, null, ""), null);
    }

    public static JsonObject toJson(Scene scene) {
        var json = new JsonObject();
        json.addProperty("id", scene.id());
        json.addProperty("name", scene.name());
        if (scene.icon() != null) json.addProperty("icon", scene.icon());
        if (scene.color() != null) json.addProperty("color", scene.color());
        json.addProperty("confirm", scene.confirm());
        json.addProperty("pin", scene.pin());
        var steps = new JsonArray();
        for (var step : scene.steps()) {
            if (step.isDelay()) {
                var delay = new JsonObject();
                delay.addProperty("type", "delay");
                delay.addProperty("seconds", step.seconds());
                steps.add(delay);
            } else {
                steps.add(RuleCodec.actionJson(step.action()));
            }
        }
        json.add("steps", steps);
        return json;
    }

    private static @Nullable String optional(JsonObject obj, String key) {
        var e = obj.get(key);
        if (e == null || e.isJsonNull()) return null;
        return e instanceof JsonPrimitive p && p.isString() ? p.getAsString() : "\u0000"; // never valid
    }

    /** Missing = false; anything but a boolean = null (invalid). */
    private static @Nullable Boolean flag(JsonObject obj, String key) {
        var e = obj.get(key);
        if (e == null) return false;
        return e instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : null;
    }

    /** Control and format characters removed, trimmed. */
    static String plain(String raw) {
        var sb = new StringBuilder();
        raw.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT).forEach(sb::appendCodePoint);
        return sb.toString().trim();
    }
}
