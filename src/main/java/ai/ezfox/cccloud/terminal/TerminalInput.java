package ai.ezfox.cccloud.terminal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Validates browser input for a terminal (docs/SPEC.md v0.5 E) and turns it into CC events. Strict: unknown types or
 * fields reject the whole message. Pure Java, unit tested.
 */
public final class TerminalInput {
    public static final int MAX_EVENTS = 32;
    public static final int MAX_PASTE = 512;
    /** GLFW key codes CC uses (keys API), -1/0 excluded. */
    public static final int MAX_KEY = 348;

    /** One CC event: {@code name} and its arguments. */
    public record Event(String name, Object[] args) {
    }

    public record Parsed(@Nullable List<Event> events, @Nullable String error) {
    }

    private TerminalInput() {
    }

    /**
     * @param advanced mouse events only on advanced computers
     * @param width    terminal size for mouse coordinates (1-based like CC)
     */
    public static Parsed parse(@Nullable JsonElement element, boolean advanced, int width, int height) {
        if (!(element instanceof JsonArray array) || array.isEmpty() || array.size() > MAX_EVENTS) return new Parsed(null, "invalid_input");
        var events = new ArrayList<Event>();
        for (var e : array) {
            if (!(e instanceof JsonObject obj)) return new Parsed(null, "invalid_input");
            String type = str(obj.get("type"));
            if (type == null) return new Parsed(null, "invalid_input");
            Event event = switch (type) {
                case "char" -> keys(obj, Set.of("type", "c")) && str(obj.get("c")) instanceof String c && c.length() == 1 && c.charAt(0) >= 0x20
                    && c.charAt(0) <= 0xff && c.charAt(0) != 0x7f ? new Event("char", new Object[]{c}) : null;
                case "key" -> keys(obj, Set.of("type", "key", "held")) && key(obj) != null
                    ? new Event("key", new Object[]{key(obj), bool(obj.get("held"))}) : null;
                case "key_up" -> keys(obj, Set.of("type", "key")) && key(obj) != null ? new Event("key_up", new Object[]{key(obj)}) : null;
                case "paste" -> {
                    String text = keys(obj, Set.of("type", "text")) ? str(obj.get("text")) : null;
                    if (text == null || text.isEmpty() || text.length() > MAX_PASTE) yield null;
                    // CC pastes a single line: like the in-game client, stop at the first line break, drop control characters
                    int nl = text.indexOf('\n');
                    if (nl >= 0) text = text.substring(0, nl);
                    String clean = TerminalFrame.clean(text).replace("\r", "");
                    yield clean.isEmpty() ? null : new Event("paste", new Object[]{clean});
                }
                case "terminate", "reboot", "shutdown", "turn_on" -> keys(obj, Set.of("type")) ? new Event(type, new Object[0]) : null;
                case "mouse_click", "mouse_up", "mouse_drag" -> {
                    if (!advanced || !keys(obj, Set.of("type", "button", "x", "y"))) yield null;
                    Integer button = integer(obj.get("button"), 1, 3), x = integer(obj.get("x"), 1, width), y = integer(obj.get("y"), 1, height);
                    yield button == null || x == null || y == null ? null : new Event(type, new Object[]{button, x, y});
                }
                case "mouse_scroll" -> {
                    if (!advanced || !keys(obj, Set.of("type", "dir", "x", "y"))) yield null;
                    Integer dir = integer(obj.get("dir"), -1, 1), x = integer(obj.get("x"), 1, width), y = integer(obj.get("y"), 1, height);
                    yield dir == null || dir == 0 || x == null || y == null ? null : new Event(type, new Object[]{dir, x, y});
                }
                default -> null;
            };
            if (event == null) return new Parsed(null, "invalid_input");
            events.add(event);
        }
        return new Parsed(events, null);
    }

    private static @Nullable Integer key(JsonObject obj) {
        return integer(obj.get("key"), 1, MAX_KEY);
    }

    private static boolean keys(JsonObject obj, Set<String> allowed) {
        for (var k : obj.keySet()) if (!allowed.contains(k)) return false;
        return true;
    }

    private static @Nullable String str(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    private static boolean bool(@Nullable JsonElement e) {
        return e instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    private static @Nullable Integer integer(@Nullable JsonElement e, int min, int max) {
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) return null;
        double d = p.getAsDouble();
        if (d != Math.rint(d) || d < min || d > max) return null;
        return (int) d;
    }
}
