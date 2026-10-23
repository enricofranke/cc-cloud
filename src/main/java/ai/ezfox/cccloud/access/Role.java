package ai.ezfox.cccloud.access;

import org.jspecify.annotations.Nullable;

/**
 * Roles per mainframe (docs/SPEC.md v0.6), ordered by rank. {@link #SHARE} is the separate read-only role of share-link
 * viewers: it ranks like {@link #NONE} for every check and is hard-denied everything except receiving the filtered view.
 */
public enum Role {
    NONE("none", 0),
    SHARE("share", 0),
    VIEWER("viewer", 1),
    OPERATOR("operator", 2),
    ADMIN("admin", 3),
    OWNER("owner", 4);

    public final String id;
    public final int rank;

    Role(String id, int rank) {
        this.id = id;
        this.rank = rank;
    }

    /** True if this role has at least the rights of {@code other}. SHARE and NONE never satisfy a check. */
    public boolean atLeast(Role other) {
        return rank > 0 && rank >= other.rank;
    }

    /** Buttons, toggles, sliders, crafting: operator and above. */
    public boolean canControl() {
        return atLeast(OPERATOR);
    }

    public static Role max(Role a, Role b) {
        return b.rank > a.rank ? b : a;
    }

    public static Role min(Role a, Role b) {
        return b.rank < a.rank ? b : a;
    }

    /** Parses {@code viewer|operator|admin|owner|none}; SHARE is never parsed from input. */
    public static @Nullable Role parse(@Nullable String id) {
        if (id == null) return null;
        return switch (id) {
            case "none" -> NONE;
            case "viewer" -> VIEWER;
            case "operator" -> OPERATOR;
            case "admin" -> ADMIN;
            case "owner" -> OWNER;
            default -> null;
        };
    }
}
