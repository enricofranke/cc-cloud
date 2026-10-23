package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The central permission check of the web protocol (docs/SPEC.md v0.6 "Role checks" and "Fine-grained permissions").
 * Every WebSocket message, HTTP API call and script route maps to exactly one {@link Perm}; this class holds that
 * mapping and turns a missing permission into the protocol's error code. Pure Java, unit tested (including a walk
 * over every message type, route and share sessions).
 */
public final class AccessGate {
    public enum Kind {
        /** Item search (read-only). */
        QUERY(Perm.STORAGE_SEARCH),
        /** Layout editing. */
        LAYOUT(Perm.LAYOUT_EDIT),
        /** Unlinking from the network map. */
        NET_UNLINK(Perm.NETWORK_LINK),
        /** Rules: create, edit, delete. */
        RULE_EDIT(Perm.RULES_EDIT),
        /** Rules: enable/disable, run now. */
        RULE_OPERATE(Perm.RULES_TOGGLE),
        /** Access page (roles, teams, permissions). */
        ACCESS(Perm.ACCESS_MANAGE),
        /** Share links. */
        SHARES(Perm.SHARES_CREATE);

        public final Perm perm;

        Kind(Perm perm) {
            this.perm = perm;
        }
    }

    private AccessGate() {
    }

    /**
     * Null if allowed, else the error: {@code forbidden} for share sessions and sessions without view,
     * {@code read_only} for read-only sessions (no {@code act.buttons}), otherwise {@code not_allowed}.
     */
    public static @Nullable String check(Role role, Set<Perm> perms, Perm needed) {
        if (role == Role.SHARE || !perms.contains(Perm.VIEW)) return "forbidden";
        if (perms.contains(needed)) return null;
        return perms.contains(Perm.ACT_BUTTONS) ? "not_allowed" : "read_only";
    }

    public static @Nullable String check(Role role, Set<Perm> perms, Kind kind) {
        return check(role, perms, kind.perm);
    }

    /**
     * Which permission a dashboard action needs: crafting {@code storage.craft}, watchlist edits
     * {@code storage.autostock}, redstone sides and the auto Computer card's power buttons {@code act.redstone},
     * everything else (buttons, toggles, sliders, inputs) {@code act.buttons}.
     */
    public static Perm actionPerm(@Nullable String widgetType, @Nullable String uplinkMode, String widgetId) {
        if (widgetType == null) return Perm.ACT_BUTTONS;
        return switch (widgetType) {
            case "item_search" -> Perm.STORAGE_CRAFT;
            case "watchlist" -> Perm.STORAGE_AUTOSTOCK;
            case "redstone_sides" -> Perm.ACT_REDSTONE;
            default -> "auto".equals(uplinkMode) && (widgetId.equals("power") || widgetId.equals("turn_on") || widgetId.equals("reboot")
                || widgetId.equals("shutdown")) ? Perm.ACT_REDSTONE : Perm.ACT_BUTTONS;
        };
    }

    /** Storage card v2 operations: reading needs storage.search, cancelling storage.craft, favorites act.buttons. */
    public static Perm storagePerm(String op) {
        return switch (op) {
            case "cancel" -> Perm.STORAGE_CRAFT;
            case "favorite" -> Perm.ACT_BUTTONS;
            case "autostock" -> Perm.STORAGE_AUTOSTOCK;
            default -> Perm.STORAGE_SEARCH;
        };
    }

    /** The permission of an HTTP route that touches a mainframe (null: not mainframe-scoped). */
    public static @Nullable Perm routePerm(Router.Id route) {
        return switch (route) {
            case ACCESS_GET, ACCESS_POST -> Perm.ACCESS_MANAGE;
            case SHARES_GET, SHARES_POST, SHARE_DELETE -> Perm.SHARES_CREATE;
            case SCRIPT_LIST, SCRIPT_GET, SCRIPT_PUT -> Perm.SCRIPTS_UPLOAD;
            case SCRIPT_RUN -> Perm.SCRIPTS_RUN;
            case SCRIPT_DELETE -> Perm.SCRIPTS_DELETE;
            default -> null;
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
            case "rule_set", "rule_delete", "rule_enable", "rule_run" -> "rule_result";
            case "storage" -> "storage_result";
            case "craft_preview" -> "craft_plan";
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
