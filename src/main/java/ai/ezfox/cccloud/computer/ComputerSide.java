package ai.ezfox.cccloud.computer;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * A side of a Cloud Computer as programs name it ({@code redstone.getInput("left")}), relative to the block's facing:
 * {@code front} is the side the screen looks out of. The block entity maps these to world directions.
 */
public enum ComputerSide {
    BOTTOM, TOP, BACK, FRONT, RIGHT, LEFT;

    private static final ComputerSide[] VALUES = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The side for a program's side name, or null. */
    public static @Nullable ComputerSide byId(@Nullable String id) {
        if (id == null) return null;
        for (var side : VALUES) if (side.id().equals(id)) return side;
        return null;
    }

    public static ComputerSide[] all() {
        return VALUES.clone();
    }
}
