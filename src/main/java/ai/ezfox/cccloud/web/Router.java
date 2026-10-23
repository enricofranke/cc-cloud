package ai.ezfox.cccloud.web;

import io.netty.handler.codec.http.HttpMethod;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The single list of HTTP routes. Every route declares its access level; {@link HttpHandler} enforces it centrally
 * before any route code runs (deny by default: a route is only reachable without a device token if it is explicitly
 * {@link Access#PUBLIC}).
 */
public final class Router {
    public enum Access {
        /** Reachable without a token. */
        PUBLIC,
        /**
         * Needs a valid device token (Authorization: Bearer) of a player who is not banned. The role on the mainframe a
         * request touches is checked by the route ({@link AccessApi} via the AccessResolver and {@link AccessGate}).
         */
        DEVICE
    }

    public enum Id {
        PAIR, PAIR_STATUS, MAINFRAMES, DEVICES, DEVICE_DELETE, ACCESS_GET, ACCESS_POST, SHARES_GET, SHARES_POST, SHARE_DELETE, ICON, WEBSOCKET, STATIC;

        /** The v0.6 account/role APIs (rate limited per IP, answered by {@link AccessApi}). */
        public boolean isApi() {
            return this == MAINFRAMES || this == DEVICES || this == DEVICE_DELETE || this == ACCESS_GET || this == ACCESS_POST
                || this == SHARES_GET || this == SHARES_POST || this == SHARE_DELETE;
        }
    }

    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    public record Route(Id id, HttpMethod method, Pattern path, Access access, String example) {
        boolean matches(HttpMethod m, String p) {
            if (!path.matcher(p).matches()) return false;
            return method.equals(m) || (id == Id.STATIC && m.equals(HttpMethod.HEAD));
        }
    }

    public static final Pattern PAIRING_ID = Pattern.compile("[A-Za-z0-9_-]{24}");

    public static final List<Route> ROUTES = List.of(
        new Route(Id.PAIR, HttpMethod.POST, Pattern.compile("/api/pair"), Access.PUBLIC, "/api/pair"),
        new Route(Id.PAIR_STATUS, HttpMethod.GET, Pattern.compile("/api/pair/[A-Za-z0-9_-]{24}"), Access.PUBLIC, "/api/pair/AAAAAAAAAAAAAAAAAAAAAAAA"),
        new Route(Id.MAINFRAMES, HttpMethod.GET, Pattern.compile("/api/mainframes"), Access.DEVICE, "/api/mainframes"),
        new Route(Id.DEVICES, HttpMethod.GET, Pattern.compile("/api/devices"), Access.DEVICE, "/api/devices"),
        new Route(Id.DEVICE_DELETE, HttpMethod.DELETE, Pattern.compile("/api/devices/[0-9a-f]{16}"), Access.DEVICE, "/api/devices/0123456789abcdef"),
        new Route(Id.ACCESS_GET, HttpMethod.GET, Pattern.compile("/api/mainframes/" + UUID_RE + "/access"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/access"),
        new Route(Id.ACCESS_POST, HttpMethod.POST, Pattern.compile("/api/mainframes/" + UUID_RE + "/access"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/access"),
        new Route(Id.SHARES_GET, HttpMethod.GET, Pattern.compile("/api/mainframes/" + UUID_RE + "/shares"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/shares"),
        new Route(Id.SHARES_POST, HttpMethod.POST, Pattern.compile("/api/mainframes/" + UUID_RE + "/shares"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/shares"),
        new Route(Id.SHARE_DELETE, HttpMethod.DELETE, Pattern.compile("/api/mainframes/" + UUID_RE + "/shares/[0-9a-f]{16}"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/shares/0123456789abcdef"),
        // Item icons are public (explicitly allow-listed): no secrets, rate limited per IP, still LAN-only by default.
        new Route(Id.ICON, HttpMethod.GET, Pattern.compile("/api/icon/[a-z0-9_.-]{1,64}/[a-z0-9_./-]{1,512}\\.png"), Access.PUBLIC, "/api/icon/minecraft/iron_ingot.png"),
        // The upgrade itself is public; every WebSocket message except auth/pong is denied until the auth message succeeded.
        new Route(Id.WEBSOCKET, HttpMethod.GET, Pattern.compile("/ws/client"), Access.PUBLIC, "/ws/client"),
        // Static files: everything that is not under /api/ or /ws/.
        new Route(Id.STATIC, HttpMethod.GET, Pattern.compile("/(?!api/|api$|ws/|ws$).*"), Access.PUBLIC, "/")
    );

    private Router() {
    }

    public static @Nullable Route match(HttpMethod method, String path) {
        for (var route : ROUTES) if (route.matches(method, path)) return route;
        return null;
    }

    /** True if some route exists for the path with another method (→ 405 instead of 404). */
    public static boolean pathExists(String path) {
        for (var route : ROUTES) if (route.path().matcher(path).matches()) return true;
        return false;
    }
}
