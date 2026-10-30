package ai.ezfox.cccloud.sensor;

import org.jspecify.annotations.Nullable;

/**
 * How an Advanced Cloud Sensor reports one entity: its kind, its entity type id ({@code minecraft:zombie}) and its
 * name: a player's name, or the type's (or item's) English name ("Zombie", "Diamond"). A player's name is null when
 * names are switched off on the server ({@code advancedSensorPlayerNames}); it is then always reported as "a player".
 */
public record SensorEntry(SensorKind kind, String type, @Nullable String name) {
    public static final String A_PLAYER = "a player";

    /** Whether this is a player whose name is known (and may be shown to operators). */
    public boolean named() {
        return kind == SensorKind.PLAYER && name != null && !name.isEmpty();
    }

    /** The name as operators see it ("Steve", "Zombie", or "a player" when names are off). */
    public String label() {
        if (kind == SensorKind.PLAYER) return named() ? name : A_PLAYER;
        return name == null || name.isEmpty() ? type : name;
    }

    /** The name as viewers, share links and in-world displays see it: players are always "a player". */
    public String publicLabel() {
        return kind == SensorKind.PLAYER ? A_PLAYER : label();
    }

    /** The same entity as viewers see it (no player name). */
    public SensorEntry anonymous() {
        return kind == SensorKind.PLAYER && name != null ? new SensorEntry(kind, type, null) : this;
    }
}
