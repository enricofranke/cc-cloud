package ai.ezfox.cccloud.gui;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import java.util.Set;
import ai.ezfox.cccloud.web.AuthStore;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Server-side checks for every action packet of the in-game screens. Never trusts the client: the player must have the
 * matching screen open (and still be in range), must have the rights the action needs, and every argument is
 * validated before anything is executed. Pure Java, unit tested; the game side plugs in through {@link MainframeOps}
 * and {@link UplinkOps}.
 */
public final class GuiActions {
    public static final int MAX_ARG = 64;
    private static final Pattern PENDING_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern DEVICE_ID = Pattern.compile("[0-9a-f]{" + AuthStore.DEVICE_ID_LENGTH + "}");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /**
     * Mainframe screen actions. All of them need the admin role, except {@link #LINK_ACCOUNT} ("Link this browser", any
     * player who may use the mod).
     */
    public enum Mainframe {
        TOGGLE_DASHBOARD(Perm.POWER_TOGGLE), NEW_CODE(Perm.DEVICES_MANAGE), APPROVE(Perm.DEVICES_MANAGE), DENY(Perm.DEVICES_MANAGE),
        REVOKE(Perm.DEVICES_MANAGE), REVOKE_ALL(Perm.DEVICES_MANAGE), TRUST(Perm.ACCESS_MANAGE), UNTRUST(Perm.ACCESS_MANAGE),
        SET_ROLE(Perm.ACCESS_MANAGE), PIN_SET(Perm.PIN_MANAGE), PIN_CLEAR(Perm.PIN_MANAGE), LINK_ACCOUNT(null);

        /** The permission on the mainframe this action needs (null: any player who may use the mod). */
        public final @Nullable Perm perm;

        Mainframe(@Nullable Perm perm) {
            this.perm = perm;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Mainframe byId(String id) {
            for (var a : values()) if (a.id().equals(id)) return a;
            return null;
        }
    }

    /**
     * Uplink screen actions (also the bridge screen's), each with the permission it needs on the linked mainframe:
     * unlinking and {@code redstone} (wireless redstone, arg {@code <off|transmit|receive>:<channel>}) need
     * {@code network.link}, {@code rename} (the card's dashboard title, arg = the title, empty = the default) needs
     * {@code layout.edit}, {@code dashboard} (a chat link to the card on the dashboard) needs {@code view}.
     */
    public enum Uplink {
        UNLINK(Perm.NETWORK_LINK), REDSTONE(Perm.NETWORK_LINK), RENAME(Perm.LAYOUT_EDIT), DASHBOARD(Perm.VIEW);

        public final Perm perm;

        Uplink(Perm perm) {
            this.perm = perm;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Uplink byId(String id) {
            for (var a : values()) if (a.id().equals(id)) return a;
            return null;
        }
    }

    /** Executes mainframe actions (server thread). Each returns null on success or an error code. */
    public interface MainframeOps {
        @Nullable String toggleDashboard();

        @Nullable String newCode();

        @Nullable String approve(String pendingId);

        @Nullable String deny(String pendingId);

        @Nullable String revoke(String deviceId);

        @Nullable String revokeAll();

        @Nullable String trust(String playerName);

        @Nullable String untrust(UUID player);

        /** Direct role of a player (viewer, operator, admin). */
        @Nullable String setRole(UUID player, Role role);

        /** Shows an account code for "Link this browser". */
        @Nullable String linkAccount();

        @Nullable String setPin(String pin);

        @Nullable String clearPin();
    }

    public interface UplinkOps {
        @Nullable String unlink();

        /** Wireless redstone mode and channel (validated); null on success or {@code not_supported}. */
        default @Nullable String redstone(ai.ezfox.cccloud.redstone.RedstoneMode mode, String channel) {
            return "not_supported";
        }

        /** The card's title on the dashboard (validated plain text, empty = back to the default). */
        default @Nullable String rename(String title) {
            return "not_supported";
        }

        /** Sends the player a chat link that opens this card on the dashboard. */
        default @Nullable String dashboardLink() {
            return "not_supported";
        }
    }

    private GuiActions() {
    }

    /**
     * @param screenOpen the player has this mainframe's screen open (matching container id) and is still in range
     * @param perms      the player's permissions on the mainframe, resolved live on the server (AccessResolver)
     * @return null on success, else an error code
     */
    public static @Nullable String mainframe(String action, String arg, boolean screenOpen, Set<Perm> perms, MainframeOps ops) {
        var a = Mainframe.byId(action);
        if (a == null) return "unknown_action";
        if (!screenOpen) return "no_screen";
        if (a == Mainframe.LINK_ACCOUNT) return arg.isEmpty() ? ops.linkAccount() : "invalid_argument";
        if (a.perm != null && !perms.contains(a.perm)) return "not_owner";
        if (arg.length() > MAX_ARG) return "invalid_argument";
        return switch (a) {
            case TOGGLE_DASHBOARD -> arg.isEmpty() ? ops.toggleDashboard() : "invalid_argument";
            case NEW_CODE -> arg.isEmpty() ? ops.newCode() : "invalid_argument";
            case APPROVE -> PENDING_ID.matcher(arg).matches() ? ops.approve(arg) : "invalid_argument";
            case DENY -> PENDING_ID.matcher(arg).matches() ? ops.deny(arg) : "invalid_argument";
            case REVOKE -> DEVICE_ID.matcher(arg).matches() ? ops.revoke(arg) : "invalid_argument";
            case REVOKE_ALL -> arg.isEmpty() ? ops.revokeAll() : "invalid_argument";
            case TRUST -> PLAYER_NAME.matcher(arg).matches() ? ops.trust(arg) : "invalid_argument";
            case UNTRUST -> {
                UUID uuid = parseUuid(arg);
                yield uuid == null ? "invalid_argument" : ops.untrust(uuid);
            }
            case SET_ROLE -> {
                int colon = arg.indexOf(':');
                UUID uuid = colon < 0 ? null : parseUuid(arg.substring(0, colon));
                Role role = colon < 0 ? null : Role.parse(arg.substring(colon + 1));
                yield uuid == null || role == null || role == Role.OWNER ? "invalid_argument" : ops.setRole(uuid, role);
            }
            case PIN_SET -> AuthStore.isValidPin(arg) ? ops.setPin(arg) : "invalid_pin";
            case PIN_CLEAR -> arg.isEmpty() ? ops.clearPin() : "invalid_argument";
            case LINK_ACCOUNT -> "invalid_argument"; // handled above
        };
    }

    /**
     * @param canUnlink the uplink is linked and the player has rights on its mainframe
     */
    public static @Nullable String uplink(String action, String arg, boolean screenOpen, boolean canUnlink, UplinkOps ops) {
        return uplink(action, arg, screenOpen, canUnlink ? Set.of(Perm.NETWORK_LINK) : Set.of(), ops);
    }

    /**
     * @param perms the player's permissions on the mainframe the uplink is linked to (empty when it is not linked)
     */
    public static @Nullable String uplink(String action, String arg, boolean screenOpen, Set<Perm> perms, UplinkOps ops) {
        var a = Uplink.byId(action);
        if (a == null) return "unknown_action";
        if (!screenOpen) return "no_screen";
        if (a == Uplink.UNLINK || a == Uplink.DASHBOARD) {
            if (!arg.isEmpty()) return "invalid_argument";
            if (!perms.contains(a.perm)) return a == Uplink.UNLINK ? "not_owner" : "not_allowed";
            return a == Uplink.UNLINK ? ops.unlink() : ops.dashboardLink();
        }
        if (a == Uplink.RENAME) {
            String title = cardTitle(arg);
            if (title == null) return "invalid_argument";
            if (!perms.contains(a.perm)) return "not_allowed";
            return ops.rename(title);
        }
        boolean canUnlink = perms.contains(Perm.NETWORK_LINK);
        int colon = arg.indexOf(':');
        var mode = colon < 0 ? null : ai.ezfox.cccloud.redstone.RedstoneMode.byId(arg.substring(0, colon));
        String channel = colon < 0 ? "" : arg.substring(colon + 1);
        if (mode == null || arg.length() > MAX_ARG) return "invalid_argument";
        if (!(mode == ai.ezfox.cccloud.redstone.RedstoneMode.OFF && channel.isEmpty()) && !ai.ezfox.cccloud.redstone.RedstoneChannels.validName(channel)) {
            return "invalid_channel";
        }
        if (!canUnlink) return "not_owner";
        return ops.redstone(mode, channel);
    }

    /** A card title from the screen: plain text without control characters, trimmed, at most 48 characters; null if invalid. */
    static @Nullable String cardTitle(String arg) {
        if (arg.length() > ai.ezfox.cccloud.uplink.Limits.TITLE) return null;
        for (int i = 0; i < arg.length(); i++) {
            if (Character.isISOControl(arg.charAt(i))) return null;
        }
        return arg.strip();
    }

    private static @Nullable UUID parseUuid(String s) {
        if (s.length() != 36) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
