package ai.ezfox.cccloud.sensor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What an Advanced Cloud Sensor reports (docs/SPEC.md "Advanced Cloud Sensor"): a mode (everyone / players only / mobs
 * only / hostile mobs only), "ignore my team" (the owner and the members of the owner's teams) and two lists of player
 * names: the allow list ("only these players", empty = every player) and the deny list ("never these players"). The deny
 * list always wins; a name on the allow list is reported even when it belongs to the owner's team. Names are compared
 * case-insensitively. Pure Java, unit tested.
 */
public record SensorFilter(Mode mode, boolean ignoreTeam, List<String> allow, List<String> deny) {
    /** At most this many names per list. */
    public static final int MAX_NAMES = 16;
    /** Minecraft player names. */
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    public static final SensorFilter DEFAULT = new SensorFilter(Mode.ALL, true, List.of(), List.of());

    public enum Mode {
        ALL("all", "Everyone"),
        PLAYERS("players", "Players only"),
        MOBS("mobs", "Mobs only"),
        HOSTILE("hostile", "Hostile mobs only");

        public final String id;
        /** The option text of the dashboard's select. */
        public final String label;

        Mode(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public boolean accepts(SensorKind kind) {
            return switch (this) {
                case ALL -> true;
                case PLAYERS -> kind == SensorKind.PLAYER;
                case MOBS -> kind.mob();
                case HOSTILE -> kind == SensorKind.HOSTILE;
            };
        }

        public static @Nullable Mode byId(@Nullable String id) {
            for (var m : values()) if (m.id.equals(id)) return m;
            return null;
        }

        public static @Nullable Mode byLabel(@Nullable String label) {
            for (var m : values()) if (m.label.equals(label)) return m;
            return null;
        }
    }

    /**
     * One entity in the zone.
     *
     * @param name the player's name (players only)
     * @param team the player is the sensor's owner or in one of the owner's teams
     */
    public record Candidate(SensorKind kind, @Nullable String name, boolean team) {
    }

    public SensorFilter {
        allow = List.copyOf(allow);
        deny = List.copyOf(deny);
    }

    public boolean test(Candidate c) {
        if (!mode.accepts(c.kind())) return false;
        if (c.kind() != SensorKind.PLAYER) return true;
        String name = c.name() == null ? "" : c.name().toLowerCase(Locale.ROOT);
        if (contains(deny, name)) return false;
        if (!allow.isEmpty()) return contains(allow, name);
        return !(ignoreTeam && c.team());
    }

    private static boolean contains(List<String> names, String lower) {
        for (var n : names) if (n.toLowerCase(Locale.ROOT).equals(lower)) return true;
        return false;
    }

    public SensorFilter withMode(Mode value) {
        return new SensorFilter(value, ignoreTeam, allow, deny);
    }

    public SensorFilter withIgnoreTeam(boolean value) {
        return new SensorFilter(mode, value, allow, deny);
    }

    public SensorFilter withAllow(List<String> value) {
        return new SensorFilter(mode, ignoreTeam, value, deny);
    }

    public SensorFilter withDeny(List<String> value) {
        return new SensorFilter(mode, ignoreTeam, allow, value);
    }

    /**
     * Parses a list of player names as typed in the screen or on the dashboard ("Steve, Alex Notch"): separated by
     * commas, semicolons or spaces, duplicates dropped (case-insensitive). Null if a name is not a valid Minecraft name
     * or there are more than {@link #MAX_NAMES}.
     */
    public static @Nullable List<String> parseNames(@Nullable String text) {
        if (text == null) return null;
        if (text.length() > 256) return null;
        var out = new ArrayList<String>();
        var seen = new LinkedHashSet<String>();
        for (String part : text.split("[,;\\s]+")) {
            if (part.isEmpty()) continue;
            if (!NAME.matcher(part).matches()) return null;
            if (seen.add(part.toLowerCase(Locale.ROOT))) out.add(part);
        }
        return out.size() > MAX_NAMES ? null : List.copyOf(out);
    }

    /** The list as the screen and the dashboard show it ("Steve, Alex"). */
    public static String join(List<String> names) {
        return String.join(", ", names);
    }
}
