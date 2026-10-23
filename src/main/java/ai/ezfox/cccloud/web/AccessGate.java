package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * The central role check of the web protocol (docs/SPEC.md v0.6 "Role checks in the protocol"). Every WebSocket
 * message and HTTP API call of an authenticated client is checked here before any handler runs. Pure Java, unit
 * tested, including a walk over every message type with a share session.
 */
public final class AccessGate {
    public enum Kind {
        /** Buttons, toggles, sliders, crafting, watchlist edits: operator. */
        ACTION,
        /** Item search (read-only): viewer. */
        QUERY,
        /** Layout editing: admin. */
        LAYOUT,
        /** Unlinking from the network map: admin. */
        NET_UNLINK,
        /** Access, shares and devices of a mainframe (HTTP): admin. */
        MANAGE
    }

    private AccessGate() {
    }

    /** Null if the role may do this, else the error code for the result message. */
    public static @Nullable String check(Role role, Kind kind) {
        if (role == Role.SHARE) return "forbidden";
        return switch (kind) {
            case QUERY -> role.atLeast(Role.VIEWER) ? null : "forbidden";
            case ACTION -> role.canControl() ? null : "read_only";
            case LAYOUT, NET_UNLINK -> !role.canControl() ? "read_only" : role.atLeast(Role.ADMIN) ? null : "not_allowed";
            case MANAGE -> role.atLeast(Role.ADMIN) ? null : "not_allowed";
        };
    }

    /**
     * Hard deny for share sessions: every message except {@code pong} is refused. Requests with a reqId are answered
     * with their own result type and {@code "error":"forbidden"}; everything else with {@code {"t":"error"}}.
     *
     * @return the answer, or null for pong (nothing to send)
     */
    public static @Nullable String shareDeny(String text) {
        var peek = ClientMessages.peek(text);
        String t = peek.t();
        if ("pong".equals(t) || "ping".equals(t)) return null;
        String resultType = t == null ? null : switch (t) {
            case "action" -> "action_result";
            case "query" -> "query_result";
            case "layout_set" -> "layout_result";
            case "net_unlink" -> "net_result";
            default -> null;
        };
        var json = new JsonObject();
        if (resultType != null && peek.reqId() != null) {
            json.addProperty("t", resultType);
            json.addProperty("reqId", peek.reqId());
            json.addProperty("ok", false);
            json.addProperty("error", "forbidden");
            if (resultType.equals("query_result")) {
                json.add("rows", new JsonArray());
                json.addProperty("truncated", false);
            }
        } else {
            json.addProperty("t", "error");
            json.addProperty("error", "forbidden");
        }
        return Json.GSON.toJson(json);
    }
}
