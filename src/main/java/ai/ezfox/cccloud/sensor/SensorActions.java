package ai.ezfox.cccloud.sensor;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

/**
 * Changes of an Advanced Cloud Sensor's settings from its dashboard card (a widget action, validated against the
 * widget already) and from its in-game screen ({@code cccloud:rs_device_action} with a text argument). Pure Java,
 * unit tested; the zone check (claims, spawn protection) happens when the result is applied.
 */
public final class SensorActions {
    /** The new settings, or the error ({@code invalid_value}, {@code not_interactive}, {@code unknown_widget}). */
    public record Result(@Nullable SensorSettings settings, @Nullable String error) {
        static Result ok(SensorSettings s) {
            return new Result(s, null);
        }

        static Result fail(String error) {
            return new Result(null, error);
        }
    }

    private SensorActions() {
    }

    /** A dashboard action on a card widget. */
    public static Result apply(SensorSettings s, String widgetId, JsonElement value) {
        if (!(value instanceof JsonPrimitive p)) return Result.fail("invalid_value");
        var f = s.filter();
        return switch (widgetId) {
            case SensorCards.ARMED -> p.isBoolean() ? Result.ok(s.withArmed(p.getAsBoolean())) : Result.fail("invalid_value");
            case SensorCards.TEAM -> p.isBoolean() ? Result.ok(s.withFilter(f.withIgnoreTeam(p.getAsBoolean()))) : Result.fail("invalid_value");
            case SensorCards.OUTPUT -> p.isBoolean() ? Result.ok(s.withRedstone(p.getAsBoolean())) : Result.fail("invalid_value");
            case SensorCards.DETECT -> {
                var mode = p.isString() ? SensorFilter.Mode.byLabel(p.getAsString()) : null;
                yield mode == null ? Result.fail("invalid_value") : Result.ok(s.withFilter(f.withMode(mode)));
            }
            case SensorCards.NOTIFY -> {
                var alert = p.isString() ? SensorSettings.Alert.byLabel(p.getAsString()) : null;
                yield alert == null ? Result.fail("invalid_value") : Result.ok(s.withAlert(alert));
            }
            case SensorCards.PLACE -> {
                var place = p.isString() ? SensorSettings.Placement.byLabel(p.getAsString()) : null;
                yield place == null ? Result.fail("invalid_value") : Result.ok(s.withZone(s.sizeX(), s.sizeY(), s.sizeZ(), place));
            }
            case SensorCards.ALLOW, SensorCards.DENY -> {
                var names = p.isString() ? SensorFilter.parseNames(p.getAsString()) : null;
                if (names == null) yield Result.fail("invalid_value");
                yield Result.ok(s.withFilter(widgetId.equals(SensorCards.ALLOW) ? f.withAllow(names) : f.withDeny(names)));
            }
            case SensorCards.ZONE_X, SensorCards.ZONE_Y, SensorCards.ZONE_Z -> {
                if (!p.isNumber()) yield Result.fail("invalid_value");
                int v = (int) Math.round(p.getAsDouble());
                if (v < 1) yield Result.fail("invalid_value");
                yield Result.ok(switch (widgetId) {
                    case SensorCards.ZONE_X -> s.withZone(v, s.sizeY(), s.sizeZ(), s.placement());
                    case SensorCards.ZONE_Y -> s.withZone(s.sizeX(), v, s.sizeZ(), s.placement());
                    default -> s.withZone(s.sizeX(), s.sizeY(), v, s.placement());
                });
            }
            case SensorCards.ACTIVITY, SensorCards.OCCUPIED -> Result.fail("not_interactive");
            default -> Result.fail("unknown_widget");
        };
    }

    /**
     * A screen action ({@code sensor_<key>} with a text argument): {@code armed|team|output} true/false, {@code detect}
     * a mode id, {@code notify} an alert id, {@code place} front/around, {@code allow|deny} names, {@code zone} "x,y,z".
     * The zone is limited to {@code max*} here, as on the card.
     */
    public static Result screen(SensorSettings s, String key, String arg, int maxX, int maxY, int maxZ) {
        var f = s.filter();
        return switch (key) {
            case "armed", "team", "output" -> {
                Boolean b = arg.equals("true") ? Boolean.TRUE : arg.equals("false") ? Boolean.FALSE : null;
                if (b == null) yield Result.fail("invalid_value");
                yield Result.ok(switch (key) {
                    case "armed" -> s.withArmed(b);
                    case "team" -> s.withFilter(f.withIgnoreTeam(b));
                    default -> s.withRedstone(b);
                });
            }
            case "detect" -> {
                var mode = SensorFilter.Mode.byId(arg);
                yield mode == null ? Result.fail("invalid_value") : Result.ok(s.withFilter(f.withMode(mode)));
            }
            case "notify" -> {
                var alert = SensorSettings.Alert.byId(arg);
                yield alert == null ? Result.fail("invalid_value") : Result.ok(s.withAlert(alert));
            }
            case "place" -> {
                var place = SensorSettings.Placement.byId(arg);
                yield place == null ? Result.fail("invalid_value") : Result.ok(s.withZone(s.sizeX(), s.sizeY(), s.sizeZ(), place));
            }
            case "allow", "deny" -> {
                var names = SensorFilter.parseNames(arg);
                if (names == null) yield Result.fail("invalid_value");
                yield Result.ok(s.withFilter(key.equals("allow") ? f.withAllow(names) : f.withDeny(names)));
            }
            case "zone" -> {
                var parts = arg.split(",");
                if (parts.length != 3) yield Result.fail("invalid_value");
                int[] v = new int[3];
                int[] max = {maxX, maxY, maxZ};
                for (int i = 0; i < 3; i++) {
                    if (!parts[i].matches("\\d{1,2}")) yield Result.fail("invalid_value");
                    v[i] = Integer.parseInt(parts[i]);
                    if (v[i] < 1 || v[i] > max[i]) yield Result.fail("invalid_value");
                }
                yield Result.ok(s.withZone(v[0], v[1], v[2], s.placement()));
            }
            default -> Result.fail("invalid_value");
        };
    }
}
