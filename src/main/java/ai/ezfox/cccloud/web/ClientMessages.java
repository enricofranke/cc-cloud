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

    public sealed interface Message permits Auth, AuthShare, Action, Query, LayoutSet, NetUnlink, Pong, Invalid {
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
            case "net_unlink" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                String nodeId = string(obj, "nodeId");
                yield nodeId == null || !NODE_ID.matcher(nodeId).matches() ? new Invalid("invalid_message", reqId) : new NetUnlink(reqId, nodeId);
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

    private static Message parseQuery(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        String id = string(obj, "id");
        String q = string(obj, "q");
        if (uplinkId == null || !Json.isValidId(id) || q == null || q.length() > Limits.QUERY) return new Invalid("invalid_message", reqId);
        return new Query(reqId, uplinkId, id, q.strip());
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

    private static @Nullable String string(JsonObject obj, String key) {
        var e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}
