package ai.ezfox.cccloud.rsdevice;

import org.jspecify.annotations.Nullable;

/**
 * The actions of a Cloud Redstone device's screen ({@code cccloud:rs_device_action {containerId, action, arg}}),
 * parsed and validated before anything touches the device. Settings need the right to link devices on the
 * device's mainframe ({@code network.link}, like the uplink screen's wireless-redstone settings); operating the device
 * (on/off, level, press) needs nothing more than standing at it, like flipping it by hand. Pure Java, unit tested.
 */
public final class RsScreenActions {
    public sealed interface Action permits Rename, Regroup, Mode, Level, Power, Press, Pulse, Follow, Pin, Confirm, Unlink, Sensor {
    }

    /**
     * Advanced Cloud Sensor settings ({@code s_<key>}: armed, team, output, detect, notify, place, allow, deny, zone); the
     * argument is checked by {@code SensorActions.screen}. Arming needs {@code act.redstone} on its mainframe, the rest
     * {@code network.link}.
     */
    public record Sensor(String key, String arg) implements Action {
        public static final java.util.Set<String> KEYS = java.util.Set.of("armed", "team", "output", "detect", "notify", "place", "allow", "deny", "zone");
    }

    public record Rename(String name) implements Action {
    }

    public record Regroup(String group) implements Action {
    }

    /** Switch: analog (0–15) or on/off. */
    public record Mode(boolean analog) implements Action {
    }

    /** Analog switch: output level · lamp: brightness (1–15). */
    public record Level(int level) implements Action {
    }

    /** Switch or lamp on/off. */
    public record Power(boolean on) implements Action {
    }

    public record Press() implements Action {
    }

    public record Pulse(int ticks) implements Action {
    }

    public record Follow(boolean on) implements Action {
    }

    public record Pin(boolean on) implements Action {
    }

    public record Confirm(boolean on) implements Action {
    }

    public record Unlink() implements Action {
    }

    /** Longest argument of a sensor action (16 player names with separators). */
    public static final int MAX_SENSOR_ARG = 300;

    private RsScreenActions() {
    }

    /** The action, or null if the action name or its argument is invalid. */
    public static @Nullable Action parse(String action, String arg) {
        return switch (action) {
            case "name" -> text(arg) == null ? null : new Rename(text(arg));
            case "group" -> text(arg) == null ? null : new Regroup(text(arg));
            case "mode" -> arg.equals("analog") ? new Mode(true) : arg.equals("toggle") ? new Mode(false) : null;
            case "level" -> {
                Integer v = integer(arg);
                yield v == null || v < 0 || v > 15 ? null : new Level(v);
            }
            case "power" -> bool(arg) == null ? null : new Power(bool(arg));
            case "press" -> arg.isEmpty() ? new Press() : null;
            case "pulse" -> {
                Integer v = integer(arg);
                yield v == null || v < RsDevice.PULSE_MIN || v > RsDevice.PULSE_MAX ? null : new Pulse(v);
            }
            case "follow" -> bool(arg) == null ? null : new Follow(bool(arg));
            case "pin" -> bool(arg) == null ? null : new Pin(bool(arg));
            case "confirm" -> bool(arg) == null ? null : new Confirm(bool(arg));
            case "unlink" -> arg.isEmpty() ? new Unlink() : null;
            default -> {
                if (!action.startsWith("s_") || !Sensor.KEYS.contains(action.substring(2))) yield null;
                if (arg.length() > MAX_SENSOR_ARG || arg.chars().anyMatch(Character::isISOControl)) yield null;
                yield new Sensor(action.substring(2), arg);
            }
        };
    }

    /** Settings (everything except operating the device) need {@code network.link} on its mainframe. */
    public static boolean needsManage(Action action) {
        return !(action instanceof Level || action instanceof Power || action instanceof Press || action instanceof Sensor s && s.key().equals("armed"));
    }

    /** Arming or disarming an Advanced Cloud Sensor: {@code act.redstone} on its mainframe (an intruder cannot just disarm it). */
    public static boolean needsOperate(Action action) {
        return action instanceof Sensor s && s.key().equals("armed");
    }

    /** Whether the action makes sense for this kind of device. */
    public static boolean fits(Action action, RsKind kind) {
        return switch (action) {
            case Mode m -> kind == RsKind.SWITCH;
            case Level l -> kind == RsKind.SWITCH || kind == RsKind.LAMP && l.level() > 0;
            case Power p -> kind.switchable();
            case Press p -> kind == RsKind.BUTTON;
            case Pulse p -> kind == RsKind.BUTTON;
            case Follow f -> kind == RsKind.LAMP;
            case Pin p -> kind != RsKind.SENSOR;
            case Confirm c -> kind != RsKind.SENSOR;
            case Sensor s -> kind == RsKind.ADVANCED_SENSOR;
            case Rename r -> true;
            case Regroup r -> true;
            case Unlink u -> true;
        };
    }

    private static @Nullable String text(String arg) {
        String s = arg.strip();
        return RsDevice.validText(s) ? s : null;
    }

    private static @Nullable Integer integer(String arg) {
        if (!arg.matches("-?\\d{1,3}")) return null;
        return Integer.parseInt(arg);
    }

    private static @Nullable Boolean bool(String arg) {
        return arg.equals("true") ? Boolean.TRUE : arg.equals("false") ? Boolean.FALSE : null;
    }
}
