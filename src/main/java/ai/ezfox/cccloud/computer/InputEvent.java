package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.terminal.TerminalInput;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Input for a Cloud Computer from the in-game screen or the browser terminal. {@link CloudComputer#input} turns the
 * control inputs ({@code terminate}, {@code shutdown}, {@code reboot}, {@code turn_on}) into actions and queues the
 * others as events with the same name. Arguments are plain Java values (Integer, Boolean, String).
 *
 * <ul>
 *   <li>{@code key (code, held)}, {@code key_up (code)}: key codes as in the {@code keys} API (GLFW numbers)</li>
 *   <li>{@code char (text)}: one printable character; {@code paste (text)}: one line</li>
 *   <li>{@code mouse_click / mouse_up / mouse_drag (button, x, y)}, {@code mouse_scroll (dir, x, y)}: 1-based cells</li>
 * </ul>
 */
public record InputEvent(String name, Object[] args) {
    /** Names a client may send; the first four are controls, not events. */
    public static final Set<String> CONTROLS = Set.of("terminate", "shutdown", "reboot", "turn_on");
    public static final Set<String> EVENTS = Set.of("key", "key_up", "char", "paste", "mouse_click", "mouse_drag", "mouse_up", "mouse_scroll");

    public InputEvent {
        if (!CONTROLS.contains(name) && !EVENTS.contains(name)) throw new IllegalArgumentException("unknown input " + name);
        args = args.clone();
    }

    public boolean control() {
        return CONTROLS.contains(name);
    }

    @Override
    public Object[] args() {
        return args.clone();
    }

    public static InputEvent key(int code, boolean held) {
        return new InputEvent("key", new Object[]{code, held});
    }

    public static InputEvent keyUp(int code) {
        return new InputEvent("key_up", new Object[]{code});
    }

    public static InputEvent character(char c) {
        return new InputEvent("char", new Object[]{String.valueOf(c)});
    }

    public static InputEvent paste(String text) {
        return new InputEvent("paste", new Object[]{text});
    }

    public static InputEvent mouse(String name, int button, int x, int y) {
        return new InputEvent(name, new Object[]{button, x, y});
    }

    public static InputEvent scroll(int dir, int x, int y) {
        return new InputEvent("mouse_scroll", new Object[]{dir, x, y});
    }

    public static InputEvent control(String name) {
        if (!CONTROLS.contains(name)) throw new IllegalArgumentException("not a control: " + name);
        return new InputEvent(name, new Object[0]);
    }

    /** Longest paste in characters. */
    public static final int MAX_PASTE = 512;

    /** A paste as one line: up to the first line break, without control characters, at most {@link #MAX_PASTE}. */
    public static String cleanPaste(String text) {
        int nl = text.indexOf('\n');
        if (nl >= 0) text = text.substring(0, nl);
        var out = new StringBuilder(Math.min(text.length(), MAX_PASTE));
        for (int i = 0; i < text.length() && out.length() < MAX_PASTE; i++) {
            char c = text.charAt(i);
            if (c >= 0x20 && c != 0x7f && c <= 0xff) out.append(c);
        }
        return out.toString();
    }

    /** From an already validated browser event ({@link TerminalInput#parse}); null for a name we do not take. */
    public static @Nullable InputEvent of(TerminalInput.Event event) {
        if (!CONTROLS.contains(event.name()) && !EVENTS.contains(event.name())) return null;
        return new InputEvent(event.name(), event.args());
    }
}
