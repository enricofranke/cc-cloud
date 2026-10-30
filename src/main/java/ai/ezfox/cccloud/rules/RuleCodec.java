package ai.ezfox.cccloud.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Strict JSON schema of a {@link Rule} (docs/SPEC.md "v0.5 C – rules protocol"): unknown keys, wrong types and
 * out-of-range numbers are rejected. Pure Java, unit tested.
 */
public final class RuleCodec {
    public static final int MAX_NAME = 48;
    public static final int MAX_ALERT = 120;
    public static final int MAX_BYTES = 4096;
    static final Pattern ID = Pattern.compile("[0-9a-f]{8}");
    static final Pattern UUID_RE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    static final Pattern WIDGET_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    static final Pattern ITEM = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
    static final Set<String> SIDES = Set.of("top", "bottom", "left", "right", "front", "back");
    static final Set<String> LEVELS = Set.of("info", "warn", "error");
    /** Weekday names of a schedule trigger; index = bit (Monday = bit 0). */
    public static final java.util.List<String> DAYS = java.util.List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    static final Pattern CLOCK = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]");
    static final Pattern PLAYER = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public record Result(@Nullable Rule rule, @Nullable String error) {
        static Result fail(String detail) {
            return new Result(null, detail);
        }
    }

    private static final class Invalid extends RuntimeException {
        Invalid(String message) {
            super(message, null, false, false);
        }
    }

    private RuleCodec() {
    }

    /**
     * Parses a rule from the browser. A missing {@code id} is allowed (new rule, the server assigns one).
     *
     * @return the rule, or an error detail (the protocol answers {@code invalid_rule} with this detail)
     */
    public static Result parse(@Nullable JsonElement element, long maxCraft) {
        if (!(element instanceof JsonObject obj)) return Result.fail("rule must be an object");
        if (element.toString().length() > MAX_BYTES) return Result.fail("rule too large");
        try {
            keys(obj, Set.of("id", "name", "enabled", "cooldown", "when", "if", "then", "notify", "lastRun", "lastResult", "runs"), "rule");
            String id = obj.has("id") ? string(obj, "id", ID, "id") : "";
            String name = text(obj, "name", MAX_NAME, true);
            boolean enabled = !obj.has("enabled") || bool(obj, "enabled");
            int cooldown = obj.has("cooldown") ? (int) number(obj, "cooldown", Rule.MIN_COOLDOWN, Rule.MAX_COOLDOWN, true) : 30;
            var when = trigger(object(obj, "when"));
            var conditions = new ArrayList<Rule.Condition>();
            if (obj.has("if")) {
                var array = array(obj, "if", Rule.MAX_CONDITIONS);
                for (var e : array) conditions.add(new Rule.Condition(check(asObject(e, "condition"), true)));
            }
            var actions = new ArrayList<Rule.Action>();
            var then = array(obj, "then", Rule.MAX_ACTIONS);
            if (then.isEmpty()) throw new Invalid("at least one action");
            for (var e : then) actions.add(action(asObject(e, "action"), maxCraft));
            boolean push = obj.has("notify") && bool(obj, "notify");
            var rule = new Rule(id, name, enabled, cooldown, when, conditions, actions, push);
            String self = selfTrigger(rule);
            if (self != null) throw new Invalid(self);
            return new Result(rule, null);
        } catch (Invalid e) {
            return Result.fail(e.getMessage());
        }
    }

    /** Loop protection: a rule must not write the value its own trigger watches. */
    static @Nullable String selfTrigger(Rule rule) {
        String uplink = null, widget = null;
        if (rule.when() instanceof Rule.CheckTrigger t && t.check() instanceof Rule.WidgetCheck w) {
            uplink = w.uplinkId();
            widget = w.widgetId();
        }
        if (uplink == null) return null;
        for (var action : rule.actions()) {
            if (action instanceof Rule.WidgetAction a && a.uplinkId().equals(uplink) && a.widgetId().equals(widget)) return "a rule cannot change the widget that triggers it";
            if (action instanceof Rule.RedstoneAction a && a.uplinkId().equals(uplink) && "redstone".equals(widget)) return "a rule cannot change the redstone it is triggered by";
        }
        return null;
    }

    private static Rule.Trigger trigger(JsonObject obj) {
        String type = string(obj, "type", null, "when.type");
        return switch (type) {
            case "widget", "items", "power" -> new Rule.CheckTrigger(check(obj, false));
            case "offline" -> {
                keys(obj, Set.of("type", "uplinkId"), "when");
                yield new Rule.OfflineTrigger(string(obj, "uplinkId", UUID_RE, "uplinkId"));
            }
            case "interval" -> {
                keys(obj, Set.of("type", "minutes"), "when");
                yield new Rule.IntervalTrigger((int) number(obj, "minutes", 1, 1440, true));
            }
            case "time" -> {
                keys(obj, Set.of("type", "time"), "when");
                yield new Rule.TimeTrigger((int) number(obj, "time", 0, 23_999, true));
            }
            case "schedule" -> {
                keys(obj, Set.of("type", "days", "at"), "when");
                var days = array(obj, "days", DAYS.size());
                int mask = 0;
                for (var e : days) {
                    int bit = e instanceof JsonPrimitive p && p.isString() ? DAYS.indexOf(p.getAsString()) : -1;
                    if (bit < 0 || (mask & 1 << bit) != 0) throw new Invalid("days must be unique weekdays (mon … sun)");
                    mask |= 1 << bit;
                }
                if (mask == 0) throw new Invalid("at least one day");
                String at = string(obj, "at", CLOCK, "at (HH:MM)");
                yield new Rule.ScheduleTrigger(mask, Integer.parseInt(at.substring(0, 2)) * 60 + Integer.parseInt(at.substring(3)));
            }
            case "sensor" -> {
                keys(obj, Set.of("type", "uplinkId", "kind", "player"), "when");
                String kind = obj.has("kind") ? string(obj, "kind", null, "kind") : "any";
                if (!Rule.SensorTrigger.KINDS.contains(kind)) throw new Invalid("kind must be one of " + String.join(", ", Rule.SensorTrigger.KINDS));
                String player = obj.has("player") ? string(obj, "player", PLAYER, "player (a Minecraft name)") : null;
                if (player != null && !kind.equals("any") && !kind.equals("player")) throw new Invalid("a player name needs kind player");
                yield new Rule.SensorTrigger(string(obj, "uplinkId", UUID_RE, "uplinkId"), player != null ? "player" : kind, player);
            }
            default -> throw new Invalid("unknown trigger " + type);
        };
    }

    private static Rule.Check check(JsonObject obj, boolean allowOnline) {
        String type = string(obj, "type", null, "type");
        return switch (type) {
            case "widget" -> {
                keys(obj, Set.of("type", "uplinkId", "widgetId", "op", "value"), "widget check");
                yield new Rule.WidgetCheck(string(obj, "uplinkId", UUID_RE, "uplinkId"), string(obj, "widgetId", WIDGET_ID, "widgetId"), cmp(obj),
                    number(obj, "value", -1e15, 1e15, false));
            }
            case "items" -> {
                keys(obj, Set.of("type", "uplinkId", "item", "op", "value"), "items check");
                yield new Rule.ItemsCheck(string(obj, "uplinkId", UUID_RE, "uplinkId"), string(obj, "item", ITEM, "item"), cmp(obj),
                    number(obj, "value", 0, 1e15, true));
            }
            case "power" -> {
                keys(obj, Set.of("type", "op", "value"), "power check");
                yield new Rule.PowerCheck(cmp(obj), number(obj, "value", 0, 100, false));
            }
            case "online" -> {
                if (!allowOnline) throw new Invalid("use the offline trigger");
                keys(obj, Set.of("type", "uplinkId"), "online check");
                yield new Rule.OnlineCheck(string(obj, "uplinkId", UUID_RE, "uplinkId"));
            }
            default -> throw new Invalid("unknown check " + type);
        };
    }

    /** One rule action (also a scene step, v0.10): the action, or an error detail. */
    public record ActionParse(Rule.@Nullable Action action, @Nullable String error) {
    }

    /** Parses one action with the rule schema; {@code allowScene} = the {@code scene} action is allowed (rules, not scenes). */
    public static ActionParse parseAction(@Nullable JsonElement element, long maxCraft, boolean allowScene) {
        if (!(element instanceof JsonObject obj)) return new ActionParse(null, "step must be an object");
        try {
            var action = action(obj, maxCraft);
            if (action instanceof Rule.SceneAction && !allowScene) return new ActionParse(null, "a scene cannot run another scene");
            return new ActionParse(action, null);
        } catch (Invalid e) {
            return new ActionParse(null, e.getMessage());
        }
    }

    private static Rule.Action action(JsonObject obj, long maxCraft) {
        String type = string(obj, "type", null, "action.type");
        return switch (type) {
            case "redstone" -> {
                keys(obj, Set.of("type", "uplinkId", "side", "value"), "redstone action");
                String side = string(obj, "side", null, "side");
                if (!SIDES.contains(side)) throw new Invalid("side");
                yield new Rule.RedstoneAction(string(obj, "uplinkId", UUID_RE, "uplinkId"), side, (int) number(obj, "value", 0, 15, true));
            }
            case "widget" -> {
                keys(obj, Set.of("type", "uplinkId", "widgetId", "value"), "widget action");
                var value = obj.get("value");
                if (!(value instanceof JsonPrimitive p)) throw new Invalid("value must be a number, string or boolean");
                if (p.isString() && p.getAsString().length() > 256) throw new Invalid("value too long");
                if (p.isNumber() && !Double.isFinite(p.getAsDouble())) throw new Invalid("value");
                yield new Rule.WidgetAction(string(obj, "uplinkId", UUID_RE, "uplinkId"), string(obj, "widgetId", WIDGET_ID, "widgetId"), p);
            }
            case "craft" -> {
                keys(obj, Set.of("type", "uplinkId", "item", "amount"), "craft action");
                yield new Rule.CraftAction(string(obj, "uplinkId", UUID_RE, "uplinkId"), string(obj, "item", ITEM, "item"),
                    (long) number(obj, "amount", 1, Math.max(1, maxCraft), true));
            }
            case "alert" -> {
                keys(obj, Set.of("type", "text", "level"), "alert action");
                String level = obj.has("level") ? string(obj, "level", null, "level") : "info";
                if (!LEVELS.contains(level)) throw new Invalid("level");
                yield new Rule.AlertAction(text(obj, "text", MAX_ALERT, true), level);
            }
            case "dashboard" -> {
                keys(obj, Set.of("type", "enabled"), "dashboard action");
                yield new Rule.DashboardAction(bool(obj, "enabled"));
            }
            case "scene" -> {
                keys(obj, Set.of("type", "sceneId"), "scene action");
                yield new Rule.SceneAction(string(obj, "sceneId", ID, "sceneId"));
            }
            default -> throw new Invalid("unknown action " + type);
        };
    }

    // ---- JSON out ----------------------------------------------------------------------------------------------------

    public static JsonObject toJson(Rule rule) {
        var json = new JsonObject();
        json.addProperty("id", rule.id());
        json.addProperty("name", rule.name());
        json.addProperty("enabled", rule.enabled());
        json.addProperty("cooldown", rule.cooldown());
        json.add("when", switch (rule.when()) {
            case Rule.CheckTrigger t -> checkJson(t.check());
            case Rule.OfflineTrigger t -> obj("type", "offline", "uplinkId", t.uplinkId());
            case Rule.IntervalTrigger t -> {
                var o = obj("type", "interval");
                o.addProperty("minutes", t.minutes());
                yield o;
            }
            case Rule.TimeTrigger t -> {
                var o = obj("type", "time");
                o.addProperty("time", t.time());
                yield o;
            }
            case Rule.SensorTrigger t -> {
                var o = obj("type", "sensor", "uplinkId", t.uplinkId(), "kind", t.kind());
                if (t.player() != null) o.addProperty("player", t.player());
                yield o;
            }
            case Rule.ScheduleTrigger t -> {
                var o = obj("type", "schedule");
                var days = new JsonArray();
                for (int i = 0; i < DAYS.size(); i++) if ((t.days() & 1 << i) != 0) days.add(DAYS.get(i));
                o.add("days", days);
                o.addProperty("at", String.format(java.util.Locale.ROOT, "%02d:%02d", t.minute() / 60, t.minute() % 60));
                yield o;
            }
        });
        if (!rule.conditions().isEmpty()) {
            var conditions = new JsonArray();
            for (var c : rule.conditions()) conditions.add(checkJson(c.check()));
            json.add("if", conditions);
        }
        var actions = new JsonArray();
        for (var a : rule.actions()) actions.add(actionJson(a));
        json.add("then", actions);
        json.addProperty("notify", rule.push());
        return json;
    }

    /** One action as JSON (rules and scene steps). */
    public static JsonObject actionJson(Rule.Action a) {
        return switch (a) {
            case Rule.RedstoneAction r -> {
                var o = obj("type", "redstone", "uplinkId", r.uplinkId(), "side", r.side());
                o.addProperty("value", r.value());
                yield o;
            }
            case Rule.WidgetAction w -> {
                var o = obj("type", "widget", "uplinkId", w.uplinkId(), "widgetId", w.widgetId());
                o.add("value", w.value());
                yield o;
            }
            case Rule.CraftAction c -> {
                var o = obj("type", "craft", "uplinkId", c.uplinkId(), "item", c.item());
                o.addProperty("amount", c.amount());
                yield o;
            }
            case Rule.AlertAction al -> obj("type", "alert", "text", al.text(), "level", al.level());
            case Rule.DashboardAction d -> {
                var o = obj("type", "dashboard");
                o.addProperty("enabled", d.enabled());
                yield o;
            }
            case Rule.SceneAction sc -> obj("type", "scene", "sceneId", sc.sceneId());
        };
    }

    private static JsonObject checkJson(Rule.Check check) {
        return switch (check) {
            case Rule.WidgetCheck w -> {
                var o = obj("type", "widget", "uplinkId", w.uplinkId(), "widgetId", w.widgetId(), "op", w.cmp().symbol);
                o.addProperty("value", w.value());
                yield o;
            }
            case Rule.ItemsCheck i -> {
                var o = obj("type", "items", "uplinkId", i.uplinkId(), "item", i.item(), "op", i.cmp().symbol);
                o.addProperty("value", (long) i.value());
                yield o;
            }
            case Rule.PowerCheck p -> {
                var o = obj("type", "power", "op", p.cmp().symbol);
                o.addProperty("value", p.value());
                yield o;
            }
            case Rule.OnlineCheck on -> obj("type", "online", "uplinkId", on.uplinkId());
        };
    }

    private static JsonObject obj(String... kv) {
        var o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) o.addProperty(kv[i], kv[i + 1]);
        return o;
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private static void keys(JsonObject obj, Set<String> allowed, String where) {
        for (String key : obj.keySet()) if (!allowed.contains(key)) throw new Invalid("unknown field " + where + "." + key);
    }

    private static JsonObject object(JsonObject obj, String key) {
        if (!(obj.get(key) instanceof JsonObject o)) throw new Invalid(key + " must be an object");
        return o;
    }

    private static JsonObject asObject(JsonElement e, String what) {
        if (!(e instanceof JsonObject o)) throw new Invalid(what + " must be an object");
        return o;
    }

    private static JsonArray array(JsonObject obj, String key, int max) {
        if (!(obj.get(key) instanceof JsonArray a)) throw new Invalid(key + " must be an array");
        if (a.size() > max) throw new Invalid("at most " + max + " entries in " + key);
        return a;
    }

    private static String string(JsonObject obj, String key, @Nullable Pattern pattern, String what) {
        var e = obj.get(key);
        if (!(e instanceof JsonPrimitive p) || !p.isString()) throw new Invalid(what + " must be a string");
        String s = p.getAsString();
        if (pattern != null && !pattern.matcher(s).matches()) throw new Invalid(what);
        if (s.length() > 256) throw new Invalid(what);
        return s;
    }

    /** Plain text: control characters removed, trimmed, length-limited. */
    private static String text(JsonObject obj, String key, int max, boolean required) {
        String raw = string(obj, key, null, key);
        var sb = new StringBuilder();
        raw.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT).forEach(sb::appendCodePoint);
        String clean = sb.toString().trim();
        if (clean.length() > max) throw new Invalid(key + " longer than " + max);
        if (required && clean.isEmpty()) throw new Invalid(key + " is empty");
        return clean;
    }

    private static boolean bool(JsonObject obj, String key) {
        var e = obj.get(key);
        if (!(e instanceof JsonPrimitive p) || !p.isBoolean()) throw new Invalid(key + " must be true or false");
        return p.getAsBoolean();
    }

    private static double number(JsonObject obj, String key, double min, double max, boolean integer) {
        var e = obj.get(key);
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) throw new Invalid(key + " must be a number");
        double v = p.getAsDouble();
        if (!Double.isFinite(v) || v < min || v > max) throw new Invalid(key + " out of range");
        if (integer && v != Math.rint(v)) throw new Invalid(key + " must be a whole number");
        return v;
    }

    private static Rule.Cmp cmp(JsonObject obj) {
        var cmp = Rule.Cmp.of(string(obj, "op", null, "op"));
        if (cmp == null) throw new Invalid("op must be one of < <= > >= == !=");
        return cmp;
    }
}
