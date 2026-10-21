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
        /** Needs a valid device token (Authorization: Bearer) whose player still has rights on the mainframe. */
        DEVICE
    }

    public enum Id { PAIR, PAIR_STATUS, MAINFRAMES, WEBSOCKET, STATIC }

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
