package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.layout.LayoutValidator;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Parsing and validation of everything a browser sends. Plain Java, unit tested. */
public final class ClientMessages {
    /** Max size of every message except layout_set. */
    public static final int MAX_FRAME = 8 * 1024;
    /** WebSocket frame limit: layout_set may carry a layout of up to 64 KB (plus a little envelope). */
    public static final int MAX_LAYOUT_FRAME = LayoutValidator.MAX_BYTES + 1024;
    public static final int MAX_BODY = 4 * 1024;
    private static final Pattern REQ_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public sealed interface Message permits Auth, AuthShare, Action, Query, LayoutSet, NetUnlink, RuleCommand, SceneCommand, Production, RedstoneOverride, Storage,
        CraftPreview, Terminal, RedstoneDevice, Pong, Invalid {
    }

    /**
     * v0.10 scenes: {@code scene_set {reqId, scene}} and {@code scene_delete {reqId, id}} (both {@code rules.edit}),
     * answered with {@code scene_result}. The scene itself is validated by {@link ai.ezfox.cccloud.scene.SceneCodec}.
     * Scenes run through their button in the Scenes section (an ordinary action).
     */
    public record SceneCommand(String reqId, String op, @Nullable String id, @Nullable JsonElement scene) implements Message {
    }

    /** Production statistics operations: overview and item read, pin and unpin change the Production section. */
    public static final java.util.Set<String> PRODUCTION_OPS = java.util.Set.of("overview", "item", "pin", "unpin");
    public static final java.util.Set<String> PIN_KINDS = java.util.Set.of("item", "storage", "energy");

    /**
     * v0.10 production statistics: {@code {"t":"production","reqId","op",…}} → {@code production_result}. {@code item}
     * needs uplinkId + name, a {@code pin} its kind (item: uplinkId + name, storage: uplinkId), {@code unpin} the pin id.
     */
    public record Production(String reqId, String op, @Nullable UUID uplinkId, @Nullable String name, @Nullable String kind, @Nullable String id) implements Message {
    }

    /** Storage operations that only read (browse, craftables, fluids, item, cpus). */
    public static final java.util.Set<String> STORAGE_READ_OPS = java.util.Set.of("browse", "craftables", "fluids", "item", "cpus");
    public static final java.util.Set<String> STORAGE_OPS = java.util.Set.of("browse", "craftables", "fluids", "item", "cpus", "cancel", "favorite", "autostock");

    /**
     * Storage card v2: {@code {"t":"storage","reqId","uplinkId","id?","op",…}} → {@code storage_result}. The op's
     * own fields stay in {@code args} (validated per op by the bridge side).
     */
    public record Storage(String reqId, UUID uplinkId, @Nullable String widgetId, String op, JsonObject args) implements Message {
    }

    /**
     * v0.5 E browser terminal: {@code term_open {reqId, uplinkId, pin?}} → {@code term_result}, {@code term_input
     * {uplinkId, events:[…], reqId?}} (validated by {@link ai.ezfox.cccloud.terminal.TerminalInput} against the terminal
     * size), {@code term_close {uplinkId}}.
     */
    public record Terminal(String op, @Nullable String reqId, UUID uplinkId, @Nullable String pin, @Nullable JsonElement events) implements Message {
    }

    /** {@code {"t":"craft_preview","reqId","uplinkId","id?","name","count"}} → {@code craft_plan}. */
    public record CraftPreview(String reqId, UUID uplinkId, @Nullable String widgetId, String name, long count) implements Message {
    }

    /**
     * v0.5 rules: {@code rule_set {reqId, rule}} (admin), {@code rule_delete {reqId, id}} (admin),
     * {@code rule_enable {reqId, id, enabled}} and {@code rule_run {reqId, id}} (operator). Answered with
     * {@code rule_result}. The rule itself is validated by {@link ai.ezfox.cccloud.rules.RuleCodec}.
     */
    public record RuleCommand(String reqId, String op, @Nullable String id, boolean enabled, @Nullable JsonElement rule) implements Message {
        /** rule_set and rule_delete edit rules (admin); rule_enable and rule_run only operate them (operator). */
        public boolean edits() {
            return op.equals("rule_set") || op.equals("rule_delete");
        }
    }

    /** {@code {"t":"auth","token","mainframeId?"}}: account tokens must name the mainframe (v0.6). */
    public record Auth(String token, @Nullable UUID mainframeId) implements Message {
        public Auth(String token) {
            this(token, null);
        }
    }

    /** {@code {"t":"auth_share","id","secret"}}: a share-link viewer (v0.6). The secret comes from the URL fragment. */
    public record AuthShare(String id, String secret) implements Message {
    }

    /** {@code {"t":"net_unlink","reqId","nodeId"}}: unlink a device from the network map (admin). */
    public record NetUnlink(String reqId, String nodeId) implements Message {
    }

    /**
     * v0.7 wireless redstone: {@code {"t":"rs_override","reqId","channel","level":0-15|null,"seconds":1-86400|null}} – an
     * operator forces a channel's level for {@code seconds} (null = until reset); {@code level:null} clears it.
     * Answered with {@code rs_result}.
     */
    public record RedstoneOverride(String reqId, String channel, @Nullable Integer level, @Nullable Integer seconds) implements Message {
    }

    /**
     * Cloud Redstone devices: {@code {"t":"rs_device","reqId","deviceId","name?","group?"}} – rename a device or move it to
     * another group (= card; "" = the automatic card). Plain text, at most 32 characters each, at least one of them.
     * Needs {@code network.link}; answered with {@code rs_result}.
     */
    public record RedstoneDevice(String reqId, UUID deviceId, @Nullable String name, @Nullable String group) implements Message {
    }

    /** Only the type and request id of a message (for the hard deny of share sessions). */
    public record Peek(@Nullable String t, @Nullable String reqId) {
    }

    /**
     * @param value a scalar, or the raw object of a structured operation (then {@code op} is set).
     */
    public record Action(@Nullable String reqId, UUID uplinkId, String id, JsonElement value, @Nullable Op op, @Nullable String pin) implements Message {
        public Action(@Nullable String reqId, UUID uplinkId, String id, JsonElement value, @Nullable String pin) {
            this(reqId, uplinkId, id, value, null, pin);
        }
    }

    /** Item search on an item_search widget: {@code {"t":"query","reqId","uplinkId","id","q"}}. */
    public record Query(String reqId, UUID uplinkId, String id, String q) implements Message {
    }

    /**
     * {@code {"t":"layout_set","reqId","layout":{...}}}. Validation happens while parsing; {@code error} is
     * {@code too_large} or {@code invalid_layout} (answered with layout_result after the rights/rate checks).
     */
    public record LayoutSet(String reqId, @Nullable JsonObject layout, @Nullable String error) implements Message {
    }

    public record Pong() implements Message {
    }

    public record Invalid(String reason, @Nullable String reqId, @Nullable String detail) implements Message {
        public Invalid(String reason, @Nullable String reqId) {
            this(reason, reqId, null);
        }
    }

    public record PairRequest(String code, String name) {
    }

    /** Message types that are accepted before authentication. Everything else needs an authenticated session. */
    public static boolean isPublic(Message message) {
        return message instanceof Auth || message instanceof AuthShare || message instanceof Pong || message instanceof Invalid;
    }

    /** Reads only {@code t} and a well-formed {@code reqId} (bounded; used to answer share sessions with "forbidden"). */
    public static Peek peek(String text) {
        if (text.length() > MAX_LAYOUT_FRAME) return new Peek(null, null);
        JsonObject obj = parseObject(text);
        if (obj == null) return new Peek(null, null);
        String t = string(obj, "t");
        String reqId = string(obj, "reqId");
        if (t != null && t.length() > 32) t = null;
        if (reqId != null && !REQ_ID.matcher(reqId).matches()) reqId = null;
        return new Peek(t, reqId);
    }

    private ClientMessages() {
    }

    public static Message parse(String text) {
        if (text.length() > MAX_LAYOUT_FRAME) return new Invalid("too_large", null);
        JsonObject obj = parseObject(text);
        if (obj == null) return new Invalid(text.length() > MAX_FRAME ? "too_large" : "invalid_message", null);
        String t = string(obj, "t");
        if (t == null) return new Invalid("invalid_message", null);
        if (text.length() > MAX_FRAME && !"layout_set".equals(t)) return new Invalid("too_large", null);
        return switch (t) {
            case "auth" -> {
                String token = string(obj, "token");
                String mainframe = string(obj, "mainframeId");
                UUID mainframeId = mainframe == null ? null : parseUuid(mainframe);
                if (token == null || token.isEmpty() || token.length() > 128 || (mainframe != null && mainframeId == null)) yield new Invalid("invalid_message", null);
                yield new Auth(token, mainframeId);
            }
            case "auth_share" -> {
                String id = string(obj, "id");
                String secret = string(obj, "secret");
                yield id == null || secret == null || id.length() > 32 || secret.isEmpty() || secret.length() > 64
                    ? new Invalid("invalid_message", null) : new AuthShare(id, secret);
            }
            case "rule_set", "rule_delete", "rule_enable", "rule_run" -> parseRule(t, obj);
            case "scene_set", "scene_delete" -> parseScene(t, obj);
            case "production" -> parseProduction(obj);
            case "storage" -> parseStorage(obj);
            case "term_open", "term_input", "term_close" -> parseTerminal(t, obj);
            case "craft_preview" -> parseCraftPreview(obj);
            case "net_unlink" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                String nodeId = string(obj, "nodeId");
                yield nodeId == null || !NODE_ID.matcher(nodeId).matches() ? new Invalid("invalid_message", reqId) : new NetUnlink(reqId, nodeId);
            }
            case "rs_override" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                String channel = string(obj, "channel");
                Integer level = smallInt(obj, "level");
                Integer seconds = smallInt(obj, "seconds");
                boolean levelOk = level != null || !obj.has("level") || obj.get("level").isJsonNull();
                boolean secondsOk = seconds != null || !obj.has("seconds") || obj.get("seconds").isJsonNull();
                yield channel == null || !ai.ezfox.cccloud.redstone.RedstoneChannels.validName(channel) || !levelOk || !secondsOk
                    || (level != null && (level < 0 || level > 15)) || (seconds != null && (seconds < 1 || seconds > 86_400))
                    ? new Invalid("invalid_message", reqId) : new RedstoneOverride(reqId, channel, level, seconds);
            }
            case "rs_device" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                UUID deviceId = parseUuid(string(obj, "deviceId"));
                String name = string(obj, "name");
                String group = string(obj, "group");
                boolean extra = obj.keySet().stream().anyMatch(k -> !java.util.Set.of("t", "reqId", "deviceId", "name", "group").contains(k));
                yield deviceId == null || extra || (name == null && group == null)
                    || (name != null && !ai.ezfox.cccloud.rsdevice.RsDevice.validText(name.strip()))
                    || (group != null && !ai.ezfox.cccloud.rsdevice.RsDevice.validText(group.strip()))
                    ? new Invalid("invalid_message", reqId) : new RedstoneDevice(reqId, deviceId, name == null ? null : name.strip(), group == null ? null : group.strip());
            }
            case "pong", "ping" -> new Pong();
            case "action" -> parseAction(obj);
            case "query" -> parseQuery(obj);
            case "layout_set" -> parseLayoutSet(obj);
            default -> new Invalid("invalid_message", null);
        };
    }

    private static Message parseAction(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId != null && !REQ_ID.matcher(reqId).matches()) reqId = null;
        String uplink = string(obj, "uplinkId");
        String id = string(obj, "id");
        UUID uplinkId = parseUuid(uplink);
        if (uplinkId == null || !Json.isValidId(id)) return new Invalid("invalid_message", reqId);
        JsonElement value = obj.get("value");
        if (value == null || value.isJsonNull()) return new Invalid("invalid_value", reqId);
        Op op = null;
        if (value.isJsonObject()) {
            op = Op.parse(value.getAsJsonObject());
            if (op == null) return new Invalid("invalid_value", reqId);
        } else {
            if (!value.isJsonPrimitive()) return new Invalid("invalid_value", reqId);
            var p = value.getAsJsonPrimitive();
            if (p.isString() && p.getAsString().length() > Limits.STRING_VALUE) return new Invalid("invalid_value", reqId);
            if (p.isNumber() && !Double.isFinite(p.getAsDouble())) return new Invalid("invalid_value", reqId);
        }
        String pin = string(obj, "pin");
        if (pin != null && !AuthStore.isValidPin(pin)) return new Invalid("wrong_pin", reqId);
        return new Action(reqId, uplinkId, id, value, op, pin);
    }

    private static final Pattern RULE_ID = Pattern.compile("[0-9a-f]{8}");

    private static Message parseStorage(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        String id = string(obj, "id");
        String op = string(obj, "op");
        if (uplinkId == null || (id != null && !Json.isValidId(id)) || op == null || !STORAGE_OPS.contains(op)) return new Invalid("invalid_message", reqId);
        var args = obj.deepCopy();
        for (String key : java.util.List.of("t", "reqId", "uplinkId", "id", "op")) args.remove(key);
        return new Storage(reqId, uplinkId, id, op, args);
    }

    private static Message parseCraftPreview(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        String id = string(obj, "id");
        String name = string(obj, "name");
        var count = obj.get("count");
        if (uplinkId == null || (id != null && !Json.isValidId(id)) || !Op.isItemName(name) || count == null || !count.isJsonPrimitive()
            || !count.getAsJsonPrimitive().isNumber() || count.getAsDouble() != Math.rint(count.getAsDouble()) || count.getAsDouble() < 1
            || count.getAsDouble() > Integer.MAX_VALUE) return new Invalid("invalid_message", reqId);
        return new CraftPreview(reqId, uplinkId, id, name, count.getAsLong());
    }

    private static Message parseRule(String t, JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        String id = string(obj, "id");
        if (!t.equals("rule_set") && (id == null || !RULE_ID.matcher(id).matches())) return new Invalid("invalid_message", reqId);
        boolean enabled = false;
        if (t.equals("rule_enable")) {
            var e = obj.get("enabled");
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) return new Invalid("invalid_message", reqId);
            enabled = e.getAsBoolean();
        }
        JsonElement rule = t.equals("rule_set") ? obj.get("rule") : null;
        if (t.equals("rule_set") && (rule == null || !rule.isJsonObject())) return new Invalid("invalid_message", reqId);
        return new RuleCommand(reqId, t, id, enabled, rule);
    }

    private static Message parseScene(String t, JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        if (t.equals("scene_delete")) {
            String id = string(obj, "id");
            return id == null || !RULE_ID.matcher(id).matches() ? new Invalid("invalid_message", reqId) : new SceneCommand(reqId, t, id, null);
        }
        var scene = obj.get("scene");
        return scene == null || !scene.isJsonObject() ? new Invalid("invalid_message", reqId) : new SceneCommand(reqId, t, null, scene);
    }

    private static Message parseProduction(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        String op = string(obj, "op");
        if (op == null || !PRODUCTION_OPS.contains(op)) return new Invalid("invalid_message", reqId);
        String uplink = string(obj, "uplinkId");
        UUID uplinkId = parseUuid(uplink);
        String name = string(obj, "name");
        String kind = string(obj, "kind");
        String id = string(obj, "id");
        if ((uplink != null && uplinkId == null) || (name != null && !Op.isItemName(name))) return new Invalid("invalid_message", reqId);
        boolean ok = switch (op) {
            case "item" -> uplinkId != null && name != null;
            case "pin" -> kind != null && PIN_KINDS.contains(kind) && switch (kind) {
                case "item" -> uplinkId != null && name != null;
                case "storage" -> uplinkId != null;
                default -> true;
            };
            case "unpin" -> id != null && RULE_ID.matcher(id).matches();
            default -> true;
        };
        return ok ? new Production(reqId, op, uplinkId, name, kind, id) : new Invalid("invalid_message", reqId);
    }

    private static Message parseQuery(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        String id = string(obj, "id");
        String q = string(obj, "q");
        if (uplinkId == null || !Json.isValidId(id) || q == null || q.length() > Limits.QUERY) return new Invalid("invalid_message", reqId);
        return new Query(reqId, uplinkId, id, q.strip());
    }

    private static Message parseTerminal(String t, JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId != null && !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        if (t.equals("term_open") && reqId == null) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        if (uplinkId == null) return new Invalid("invalid_message", reqId);
        String pin = t.equals("term_open") ? string(obj, "pin") : null;
        if (pin != null && pin.length() > 32) return new Invalid("invalid_message", reqId);
        JsonElement events = t.equals("term_input") ? obj.get("events") : null;
        if (t.equals("term_input") && (events == null || !events.isJsonArray())) return new Invalid("invalid_message", reqId);
        return new Terminal(t, reqId, uplinkId, pin, events);
    }

    private static Message parseLayoutSet(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        var result = LayoutValidator.validate(obj.get("layout"));
        return new LayoutSet(reqId, result.layout(), result.error());
    }

    public static @Nullable PairRequest parsePair(String body) {
        if (body.length() > MAX_BODY) return null;
        JsonObject obj = parseObject(body);
        if (obj == null) return null;
        String code = string(obj, "code");
        String name = string(obj, "name");
        if (code == null || code.length() > 32 || name == null) return null;
        String clean = AuthStore.sanitizeName(name);
        if (name.trim().isEmpty()) return null;
        return new PairRequest(code, clean);
    }

    public static @Nullable UUID parseUuid(@Nullable String s) {
        if (s == null || s.length() != 36) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable JsonObject parseObject(String text) {
        try {
            var element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** An integer field (null if missing, null or not a whole number). */
    private static @Nullable Integer smallInt(JsonObject obj, String key) {
        var e = obj.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double d = e.getAsDouble();
        if (d != Math.rint(d) || Math.abs(d) > 1_000_000) return null;
        return (int) d;
    }

    private static @Nullable String string(JsonObject obj, String key) {
        var e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}
