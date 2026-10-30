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
        DEVICE,
        /**
         * v0.10 public API: needs a valid API key (Authorization: Bearer cck_…, never in the URL); device tokens are not
         * accepted. Scopes and the key owner's live rights are checked by {@link ai.ezfox.cccloud.publicapi.ApiV1}. The
         * route does not exist (404) while {@code apiEnabled=false}.
         */
        API_KEY
    }

    public enum Id {
        PAIR, PAIR_STATUS, CONFIG, VOXEL_ATLAS, MAINFRAMES, DEVICES, DEVICE_DELETE, ACCESS_GET, ACCESS_POST, SHARES_GET, SHARES_POST, SHARE_DELETE, PUSH_GET, PUSH_POST,
        SCRIPT_LIST, SCRIPT_GET, SCRIPT_PUT, SCRIPT_DELETE, SCRIPT_RUN, SCRIPT_MOVE, HISTORY, MAP_TILE, VOXELS, ICON, WEBSOCKET, APIKEYS_GET, APIKEYS_POST,
        API_OPENAPI, API_PREFLIGHT, API_ME, API_SECTIONS, API_SECTION, API_WIDGET, API_ITEMS, API_PRODUCTION, API_SCENES, API_SCENE_RUN, API_EVENTS, STATIC;

        /** The v0.10 public API ({@code /api/v1/…}): only exists while {@code apiEnabled=true}. */
        public boolean isPublicApi() {
            return name().startsWith("API_");
        }

        /** Script import routes that change the computer (PIN if set, upload rate limit). */
        public boolean isScriptWrite() {
            return this == SCRIPT_PUT || this == SCRIPT_DELETE || this == SCRIPT_RUN || this == SCRIPT_MOVE;
        }

        public boolean isScript() {
            return isScriptWrite() || this == SCRIPT_LIST || this == SCRIPT_GET;
        }

        public boolean isMap() {
            return this == MAP_TILE || this == VOXELS;
        }

        /** The v0.6 account/role APIs (rate limited per IP, answered by {@link AccessApi}). */
        public boolean isApi() {
            return this == MAINFRAMES || this == DEVICES || this == DEVICE_DELETE || this == ACCESS_GET || this == ACCESS_POST
                || this == SHARES_GET || this == SHARES_POST || this == SHARE_DELETE || this == PUSH_GET || this == PUSH_POST || this == HISTORY
                || this == APIKEYS_GET || this == APIKEYS_POST || isScript() || isMap();
        }
    }

    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String COMPUTER = "/api/mainframes/" + UUID_RE + "/computers/" + UUID_RE;
    /** Script import / editor file path in the URL: up to 8 levels (validated again by ScriptFiles). */
    private static final String FILE = "/files/[A-Za-z0-9_.-]{1,64}(/[A-Za-z0-9_.-]{1,64}){0,7}";
    private static final String EXAMPLE_COMPUTER = "/api/mainframes/00000000-0000-0000-0000-000000000000/computers/00000000-0000-0000-0000-000000000001";
    private static final String V1 = "/api/v1/mainframes/" + UUID_RE;
    private static final String EXAMPLE_V1 = "/api/v1/mainframes/00000000-0000-0000-0000-000000000000";
    private static final String EXAMPLE_SECTION = "00000000-0000-0000-0000-000000000001";

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
        new Route(Id.PUSH_GET, HttpMethod.GET, Pattern.compile("/api/mainframes/" + UUID_RE + "/push"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/push"),
        new Route(Id.PUSH_POST, HttpMethod.POST, Pattern.compile("/api/mainframes/" + UUID_RE + "/push"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/push"),
        new Route(Id.SCRIPT_LIST, HttpMethod.GET, Pattern.compile(COMPUTER + "/files"), Access.DEVICE, EXAMPLE_COMPUTER + "/files"),
        new Route(Id.SCRIPT_GET, HttpMethod.GET, Pattern.compile(COMPUTER + FILE), Access.DEVICE, EXAMPLE_COMPUTER + "/files/startup.lua"),
        new Route(Id.SCRIPT_PUT, HttpMethod.PUT, Pattern.compile(COMPUTER + FILE), Access.DEVICE, EXAMPLE_COMPUTER + "/files/startup.lua"),
        new Route(Id.SCRIPT_DELETE, HttpMethod.DELETE, Pattern.compile(COMPUTER + FILE), Access.DEVICE, EXAMPLE_COMPUTER + "/files/startup.lua"),
        new Route(Id.SCRIPT_RUN, HttpMethod.POST, Pattern.compile(COMPUTER + "/run"), Access.DEVICE, EXAMPLE_COMPUTER + "/run"),
        new Route(Id.SCRIPT_MOVE, HttpMethod.POST, Pattern.compile(COMPUTER + "/move"), Access.DEVICE, EXAMPLE_COMPUTER + "/move"),
        new Route(Id.HISTORY, HttpMethod.GET, ai.ezfox.cccloud.history.HistoryApi.PATH, Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/history/24h/json/w/00000000-0000-0000-0000-000000000001/temp"),
        new Route(Id.MAP_TILE, HttpMethod.GET, Pattern.compile("/api/mainframes/" + UUID_RE
            + "/map/[a-z0-9_.-]{1,64}:[a-z0-9_.-]{1,64}/(surface|slice/-?[0-9]{1,4})/-?[0-9]{1,6}/-?[0-9]{1,6}\\.png"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/map/minecraft:overworld/surface/0/0.png"),
        new Route(Id.VOXELS, HttpMethod.GET, Pattern.compile("/api/mainframes/" + UUID_RE
            + "/voxels/[a-z0-9_.-]{1,64}:[a-z0-9_.-]{1,64}/-?[0-9]{1,7}/-?[0-9]{1,3}/-?[0-9]{1,7}"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/voxels/minecraft:overworld/0/4/0"),
        new Route(Id.SHARE_DELETE, HttpMethod.DELETE, Pattern.compile("/api/mainframes/" + UUID_RE + "/shares/[0-9a-f]{16}"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/shares/0123456789abcdef"),
        new Route(Id.APIKEYS_GET, HttpMethod.GET, Pattern.compile("/api/mainframes/" + UUID_RE + "/apikeys"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/apikeys"),
        new Route(Id.APIKEYS_POST, HttpMethod.POST, Pattern.compile("/api/mainframes/" + UUID_RE + "/apikeys"), Access.DEVICE,
            "/api/mainframes/00000000-0000-0000-0000-000000000000/apikeys"),
        // v0.10 public API (off by default: 404 then). The description is public while the API is enabled.
        new Route(Id.API_OPENAPI, HttpMethod.GET, Pattern.compile("/api/v1/openapi\\.json"), Access.PUBLIC, "/api/v1/openapi.json"),
        new Route(Id.API_PREFLIGHT, HttpMethod.OPTIONS, Pattern.compile("/api/v1/[A-Za-z0-9_./-]{1,256}"), Access.PUBLIC, "/api/v1/me"),
        new Route(Id.API_ME, HttpMethod.GET, Pattern.compile("/api/v1/me"), Access.API_KEY, "/api/v1/me"),
        new Route(Id.API_SECTIONS, HttpMethod.GET, Pattern.compile(V1 + "/sections"), Access.API_KEY, EXAMPLE_V1 + "/sections"),
        new Route(Id.API_SECTION, HttpMethod.GET, Pattern.compile(V1 + "/sections/" + UUID_RE), Access.API_KEY, EXAMPLE_V1 + "/sections/" + EXAMPLE_SECTION),
        new Route(Id.API_WIDGET, HttpMethod.POST, Pattern.compile(V1 + "/sections/" + UUID_RE + "/widgets/[A-Za-z0-9_.:-]{1,64}"), Access.API_KEY,
            EXAMPLE_V1 + "/sections/" + EXAMPLE_SECTION + "/widgets/lamp"),
        new Route(Id.API_ITEMS, HttpMethod.GET, Pattern.compile(V1 + "/storage/" + UUID_RE + "/items"), Access.API_KEY, EXAMPLE_V1 + "/storage/" + EXAMPLE_SECTION + "/items"),
        new Route(Id.API_PRODUCTION, HttpMethod.GET, Pattern.compile(V1 + "/production"), Access.API_KEY, EXAMPLE_V1 + "/production"),
        new Route(Id.API_SCENES, HttpMethod.GET, Pattern.compile(V1 + "/scenes"), Access.API_KEY, EXAMPLE_V1 + "/scenes"),
        new Route(Id.API_SCENE_RUN, HttpMethod.POST, Pattern.compile(V1 + "/scenes/[0-9a-f]{8}/run"), Access.API_KEY, EXAMPLE_V1 + "/scenes/0a1b2c3d/run"),
        new Route(Id.API_EVENTS, HttpMethod.GET, Pattern.compile(V1 + "/events"), Access.API_KEY, EXAMPLE_V1 + "/events"),
        // Item icons are public (explicitly allow-listed): no secrets, rate limited per IP, still LAN-only by default.
        // Public: version, Script Store URL and feature switches (no secrets).
        new Route(Id.CONFIG, HttpMethod.GET, Pattern.compile("/api/config"), Access.PUBLIC, "/api/config"),
        // Voxel view stage 2: the block texture atlas (game textures only, like item icons).
        new Route(Id.VOXEL_ATLAS, HttpMethod.GET, Pattern.compile("/api/voxel-atlas/[0-9]{1,9}\\.png"), Access.PUBLIC, "/api/voxel-atlas/1.png"),
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
