package ai.ezfox.cccloud.access;

import java.util.UUID;

/**
 * Server-wide permissions of a player (bans and the NeoForge PermissionAPI nodes, so LuckPerms / FTB Ranks control
 * them). An interface so the {@link AccessResolver} can be unit tested; the game implementation is
 * {@link CloudPermissions}. Works for offline players too (dashboards are used while the player is not in the game).
 */
public interface ServerPermissions {
    enum Node {
        /** May place/use the blocks and link browsers (default true). */
        USE("use"),
        /** View any mainframe's dashboard as viewer (default op level 2). */
        ADMIN_VIEW("admin.view"),
        /** Operate any mainframe (default op level 2). */
        ADMIN_OPERATE("admin.operate"),
        /** Manage devices/roles of any mainframe (default op level 2). */
        ADMIN_MANAGE("admin.manage"),
        COMMAND_ICONS("command.icons"),
        COMMAND_RELOAD("command.reload"),
        /** Ignore per-player limits (default op level 4). */
        BYPASS_LIMITS("bypass.limits"),
        /** Feature switch: script import (and later the terminal) on this server (default true). */
        FEATURE_SCRIPTS("feature.scripts"),
        /** v0.10 public API: may create API keys, and the player's keys work (default true; false = keys dead). */
        API_KEYS("api.keys");

        public final String path;

        Node(String path) {
            this.path = path;
        }

        public String fullName() {
            return "cccloud." + path;
        }
    }

    boolean banned(UUID player);

    boolean has(UUID player, Node node);

    /** {@code cccloud.limit.mainframes} (integer node, default 3). */
    int mainframeLimit(UUID player);
}
