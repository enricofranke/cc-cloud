package ai.ezfox.cccloud.sensor;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * What an Advanced Cloud Sensor saw (docs/SPEC.md "Advanced Cloud Sensor"): a player, a hostile mob (anything that is
 * an {@code Enemy}), a passive mob (every other mob: animals, villagers, golems, fish), a dropped item, or something
 * else that moves (a minecart, a boat, an armor stand). Projectiles, experience orbs, markers and display entities are
 * never reported. {@code id} is the saved/protocol name.
 */
public enum SensorKind {
    PLAYER("player"),
    HOSTILE("hostile"),
    PASSIVE("passive"),
    ITEM("item"),
    OTHER("other");

    public final String id;

    SensorKind(String id) {
        this.id = id;
    }

    public boolean mob() {
        return this == HOSTILE || this == PASSIVE;
    }

    public static @Nullable SensorKind byId(@Nullable String id) {
        if (id == null) return null;
        for (var kind : values()) if (kind.id.equals(id.toLowerCase(Locale.ROOT))) return kind;
        return null;
    }
}
