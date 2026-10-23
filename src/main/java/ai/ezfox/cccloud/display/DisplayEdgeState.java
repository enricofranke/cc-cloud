package ai.ezfox.cccloud.display;

import net.minecraft.util.StringRepresentable;

/**
 * Which neighbours a display block is joined with, seen from the front (same names and flags as CC: Tweaked's
 * monitors, which the blockstate file and models follow).
 */
public enum DisplayEdgeState implements StringRepresentable {
    NONE("none", 0),
    L("l", Flags.LEFT),
    R("r", Flags.RIGHT),
    LR("lr", Flags.LEFT | Flags.RIGHT),
    U("u", Flags.UP),
    D("d", Flags.DOWN),
    UD("ud", Flags.UP | Flags.DOWN),
    RD("rd", Flags.RIGHT | Flags.DOWN),
    LD("ld", Flags.LEFT | Flags.DOWN),
    RU("ru", Flags.RIGHT | Flags.UP),
    LU("lu", Flags.LEFT | Flags.UP),
    LRD("lrd", Flags.LEFT | Flags.RIGHT | Flags.DOWN),
    RUD("rud", Flags.RIGHT | Flags.UP | Flags.DOWN),
    LUD("lud", Flags.LEFT | Flags.UP | Flags.DOWN),
    LRU("lru", Flags.LEFT | Flags.RIGHT | Flags.UP),
    LRUD("lrud", Flags.LEFT | Flags.RIGHT | Flags.UP | Flags.DOWN);

    private static final DisplayEdgeState[] BY_FLAG = new DisplayEdgeState[16];

    static {
        for (var state : values()) BY_FLAG[state.flags] = state;
    }

    private final String name;
    private final int flags;

    DisplayEdgeState(String name, int flags) {
        this.name = name;
        this.flags = flags;
    }

    public static DisplayEdgeState of(boolean up, boolean down, boolean left, boolean right) {
        return BY_FLAG[(up ? Flags.UP : 0) | (down ? Flags.DOWN : 0) | (left ? Flags.LEFT : 0) | (right ? Flags.RIGHT : 0)];
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    private static final class Flags {
        static final int UP = 1;
        static final int DOWN = 2;
        static final int LEFT = 4;
        static final int RIGHT = 8;
    }
}
