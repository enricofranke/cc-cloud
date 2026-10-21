package ai.ezfox.cccloud.uplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** JSON helpers shared by the uplink and the web server. */
public final class Json {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern WIDGET_ID = Pattern.compile("[a-z0-9_]{1,32}");

    private Json() {
    }

    public static boolean isValidId(@Nullable String id) {
        return id != null && WIDGET_ID.matcher(id).matches();
    }

    /** A number as JSON, written as an integer when it has no fractional part. */
    public static JsonPrimitive number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 9.007199254740992E15) return new JsonPrimitive((long) value);
        return new JsonPrimitive(value);
    }

    /** Converts a JSON scalar into a value CC can pass to Lua (Boolean, Double, String or null). */
    public static @Nullable Object toLua(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        var p = element.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return p.getAsDouble();
        return p.getAsString();
    }

    /** Converts a Lua scalar into JSON. Tables are not allowed as widget values. */
    public static @Nullable JsonElement fromLua(@Nullable Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return number(n.doubleValue());
        if (value instanceof String s) return new JsonPrimitive(s);
        return null;
    }
}
