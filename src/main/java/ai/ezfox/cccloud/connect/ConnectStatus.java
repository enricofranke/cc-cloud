package ai.ezfox.cccloud.connect;

import org.jspecify.annotations.Nullable;

/**
 * The state of the Connect tunnel, for the command, the mainframe screen and the server log.
 *
 * @param state       what the tunnel is doing
 * @param name        the dashboard name ("" = none set)
 * @param url         {@code https://<name>.cccloud.dev} (set once a name is configured)
 * @param error       the last error code from the relay or the network ({@code name_taken}, {@code revoked},
 *                    {@code unreachable}, …), or null
 * @param bundleKnown the relay has this mod version's dashboard (false: browsers see "update CC: Cloud")
 * @param browsers    open dashboard WebSockets through the tunnel
 * @param since       when the state last changed (epoch ms)
 * @param retryAt     when the next connection attempt is due (epoch ms, 0 = none)
 */
public record ConnectStatus(State state, String name, String url, @Nullable String error, boolean bundleKnown, int browsers, long since, long retryAt) {
    public enum State {
        /** {@code internetAccess} is not {@code connect}. */
        OFF,
        /** Connect is on but no name is set yet. */
        NO_NAME,
        /** Connecting or proving the key. */
        CONNECTING,
        /** Online: browsers can use the address. */
        CONNECTED,
        /** Lost or not reached; tries again by itself. */
        RETRYING,
        /** The relay refused the name (taken, reserved, revoked, limits): needs an admin. */
        FAILED;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final ConnectStatus OFF = new ConnectStatus(State.OFF, "", "", null, true, 0, 0, 0);

    public ConnectStatus with(State state, @Nullable String error) {
        return new ConnectStatus(state, name, url, error, bundleKnown, browsers, System.currentTimeMillis(), 0);
    }

    public boolean online() {
        return state == State.CONNECTED;
    }
}
