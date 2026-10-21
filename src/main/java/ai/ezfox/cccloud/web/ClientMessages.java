package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Parsing and validation of everything a browser sends. Plain Java, unit tested. */
public final class ClientMessages {
    public static final int MAX_FRAME = 8 * 1024;
    public static final int MAX_BODY = 4 * 1024;
    private static final Pattern REQ_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    public sealed interface Message permits Auth, Action, Pong, Invalid {
    }

    public record Auth(String token) implements Message {
    }

    public record Action(@Nullable String reqId, UUID uplinkId, String id, JsonElement value, @Nullable String pin) implements Message {
    }

    public record Pong() implements Message {
    }

    public record Invalid(String reason, @Nullable String reqId) implements Message {
    }

    public record PairRequest(String code, String name) {
    }

    /** Message types that are accepted before authentication. Everything else needs an authenticated session. */
    public static boolean isPublic(Message message) {
        return message instanceof Auth || message instanceof Pong || message instanceof Invalid;
    }

    private ClientMessages() {
    }

    public static Message parse(String text) {
        if (text.length() > MAX_FRAME) return new Invalid("too_large", null);
        JsonObject obj = parseObject(text);
        if (obj == null) return new Invalid("invalid_message", null);
        String t = string(obj, "t");
        if (t == null) return new Invalid("invalid_message", null);
        return switch (t) {
            case "auth" -> {
                String token = string(obj, "token");
                yield token == null || token.isEmpty() || token.length() > 128 ? new Invalid("invalid_message", null) : new Auth(token);
            }
            case "pong", "ping" -> new Pong();
            case "action" -> parseAction(obj);
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
        if (!value.isJsonPrimitive()) return new Invalid("invalid_value", reqId);
        var p = value.getAsJsonPrimitive();
        if (p.isString() && p.getAsString().length() > Limits.STRING_VALUE) return new Invalid("invalid_value", reqId);
        if (p.isNumber() && !Double.isFinite(p.getAsDouble())) return new Invalid("invalid_value", reqId);
        String pin = string(obj, "pin");
        if (pin != null && !AuthStore.isValidPin(pin)) return new Invalid("wrong_pin", reqId);
        return new Action(reqId, uplinkId, id, value, pin);
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
