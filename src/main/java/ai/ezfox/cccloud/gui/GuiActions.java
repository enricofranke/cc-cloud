package ai.ezfox.cccloud.gui;

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

    /** Mainframe screen actions. All of them need owner, trusted or operator rights. */
    public enum Mainframe {
        TOGGLE_DASHBOARD, NEW_CODE, APPROVE, DENY, REVOKE, REVOKE_ALL, TRUST, UNTRUST, PIN_SET, PIN_CLEAR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Mainframe byId(String id) {
            for (var a : values()) if (a.id().equals(id)) return a;
            return null;
        }
    }

    /** Uplink screen actions. Unlinking needs rights on the linked mainframe. */
    public enum Uplink {
        UNLINK;

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

        @Nullable String setPin(String pin);

        @Nullable String clearPin();
    }

    public interface UplinkOps {
        @Nullable String unlink();
    }

    private GuiActions() {
    }

    /**
     * @param screenOpen the player has this mainframe's screen open (matching container id) and is still in range
     * @param canManage  owner, trusted or operator, checked live on the server
     * @return null on success, else an error code
     */
    public static @Nullable String mainframe(String action, String arg, boolean screenOpen, boolean canManage, MainframeOps ops) {
        var a = Mainframe.byId(action);
        if (a == null) return "unknown_action";
        if (!screenOpen) return "no_screen";
        if (!canManage) return "not_owner";
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
            case PIN_SET -> AuthStore.isValidPin(arg) ? ops.setPin(arg) : "invalid_pin";
            case PIN_CLEAR -> arg.isEmpty() ? ops.clearPin() : "invalid_argument";
        };
    }

    /**
     * @param canUnlink the uplink is linked and the player has rights on its mainframe
     */
    public static @Nullable String uplink(String action, String arg, boolean screenOpen, boolean canUnlink, UplinkOps ops) {
        var a = Uplink.byId(action);
        if (a == null) return "unknown_action";
        if (!screenOpen) return "no_screen";
        if (!arg.isEmpty()) return "invalid_argument";
        if (!canUnlink) return "not_owner";
        return ops.unlink();
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
