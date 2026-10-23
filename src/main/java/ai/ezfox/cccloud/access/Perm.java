package ai.ezfox.cccloud.access;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Fine-grained permissions on a mainframe (docs/SPEC.md "Fine-grained permissions per mainframe"). Roles are presets
 * of these; a grant is a preset plus {@code allow}/{@code deny} lists (deny wins). Every check site asks for one
 * permission.
 */
public enum Perm {
    VIEW("view", "view"),
    VIEW_POSITIONS("view.positions", "view"),
    ACT_BUTTONS("act.buttons", "act"),
    ACT_REDSTONE("act.redstone", "act"),
    STORAGE_SEARCH("storage.search", "storage"),
    STORAGE_CRAFT("storage.craft", "storage"),
    STORAGE_AUTOSTOCK("storage.autostock", "storage"),
    SCRIPTS_UPLOAD("scripts.upload", "scripts"),
    SCRIPTS_RUN("scripts.run", "scripts"),
    SCRIPTS_DELETE("scripts.delete", "scripts"),
    TERMINAL("terminal", "scripts"),
    LAYOUT_EDIT("layout.edit", "manage"),
    RULES_EDIT("rules.edit", "rules"),
    RULES_TOGGLE("rules.toggle", "rules"),
    PUSH_MANAGE("push.manage", "manage"),
    DEVICES_MANAGE("devices.manage", "manage"),
    ACCESS_MANAGE("access.manage", "manage"),
    SHARES_CREATE("shares.create", "manage"),
    PIN_MANAGE("pin.manage", "manage"),
    POWER_TOGGLE("power.toggle", "manage"),
    NETWORK_LINK("network.link", "manage");

    public final String id;
    public final String group;

    Perm(String id, String group) {
        this.id = id;
        this.group = group;
    }

    public static @Nullable Perm byId(@Nullable String id) {
        for (var p : values()) if (p.id.equals(id)) return p;
        return null;
    }

    private static final Set<Perm> VIEWER = Collections.unmodifiableSet(EnumSet.of(VIEW, STORAGE_SEARCH));
    private static final Set<Perm> OPERATOR;
    private static final Set<Perm> ALL = Collections.unmodifiableSet(EnumSet.allOf(Perm.class));

    static {
        var operator = EnumSet.copyOf(VIEWER);
        operator.addAll(EnumSet.of(VIEW_POSITIONS, ACT_BUTTONS, ACT_REDSTONE, STORAGE_CRAFT, RULES_TOGGLE));
        OPERATOR = Collections.unmodifiableSet(operator);
    }

    /** The preset of a role: viewer, operator (+ positions, actions, crafting, rule toggles), admin and owner (all). */
    public static Set<Perm> preset(Role role) {
        return switch (role) {
            case NONE, SHARE -> Set.of();
            case VIEWER -> VIEWER;
            case OPERATOR -> OPERATOR;
            case ADMIN, OWNER -> ALL;
        };
    }

    public static Set<Perm> all() {
        return ALL;
    }

    /** {@code preset ∪ allow − deny} (deny wins). */
    public static EnumSet<Perm> effective(Role role, Set<Perm> allow, Set<Perm> deny) {
        var set = EnumSet.noneOf(Perm.class);
        set.addAll(preset(role));
        if (role != Role.NONE && role != Role.SHARE) set.addAll(allow);
        set.removeAll(deny);
        return set;
    }
}
