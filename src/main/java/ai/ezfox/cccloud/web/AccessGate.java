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
        /** Wave 4: a device card's settings (the Peripheral card's methods): part of arranging the dashboard. */
        CARD_SETTINGS(Perm.LAYOUT_EDIT),
        /** Unlinking from the network map. */
        NET_UNLINK(Perm.NETWORK_LINK),
        /** Rules: create, edit, delete. */
        RULE_EDIT(Perm.RULES_EDIT),
        /** Rules: enable/disable, run now. */
        RULE_OPERATE(Perm.RULES_TOGGLE),
        /** Access page (roles, teams, permissions). */
        ACCESS(Perm.ACCESS_MANAGE),
        /** Share links. */
        SHARES(Perm.SHARES_CREATE),
        /** v0.5 E browser terminal. */
        TERMINAL(Perm.TERMINAL),
        /** v0.7 wireless redstone channel overrides. */
        REDSTONE(Perm.ACT_REDSTONE),
        /** v0.10 scenes: create, edit, delete (running is a button action). */
        SCENE_EDIT(Perm.RULES_EDIT),
        /** Cloud Redstone devices: rename / regroup from the dashboard (like linking them). */
        RS_DEVICE(Perm.NETWORK_LINK);

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
     * {@code storage.autostock}, redstone sides, the auto Computer card's power buttons and device cards' redstone modes
     * ({@link #isRedstoneControl}) {@code act.redstone},
     * everything else (buttons, toggles, sliders, inputs) {@code act.buttons}.
     */
    public static Perm actionPerm(@Nullable String widgetType, @Nullable String uplinkMode, String widgetId) {
        return actionPerm(widgetType, uplinkMode, widgetId, null);
    }

    /**
     * As above, with the section's {@code template}: every control of a Cloud Redstone card ({@code redstone}: switches,
     * buttons, lamps, "All on/off") needs {@code act.redstone}; on an Advanced Cloud Sensor's card ({@code sensor})
     * arming needs {@code act.redstone} and every setting (filter, notifications, lists, zone) {@code network.link}.
     */
    public static Perm actionPerm(@Nullable String widgetType, @Nullable String uplinkMode, String widgetId, @Nullable String template) {
        if (widgetType == null) return Perm.ACT_BUTTONS;
        if ("redstone".equals(template) && "direct".equals(uplinkMode)) return Perm.ACT_REDSTONE;
        // Advanced Cloud Sensor card: arming is operating it (like a redstone control); what it watches is a setting
        if ("sensor".equals(template) && "direct".equals(uplinkMode)) return "armed".equals(widgetId) ? Perm.ACT_REDSTONE : Perm.NETWORK_LINK;
        return switch (widgetType) {
            case "item_search" -> Perm.STORAGE_CRAFT;
            case "watchlist" -> Perm.STORAGE_AUTOSTOCK;
            case "redstone_sides" -> Perm.ACT_REDSTONE;
            default -> "auto".equals(uplinkMode) && (widgetId.equals("power") || widgetId.equals("turn_on") || widgetId.equals("reboot")
                || widgetId.equals("shutdown")) || "direct".equals(uplinkMode) && isRedstoneControl(widgetId) ? Perm.ACT_REDSTONE : Perm.ACT_BUTTONS;
        };
    }

    /**
     * Device cards (direct mode) name a machine's own redstone control {@code redstone} or {@code <something>_redstone}
     * (Energy card redstone mode, quarry/spawner/router redstone modes, ...); those need {@code act.redstone}.
     */
    static boolean isRedstoneControl(String widgetId) {
        return widgetId.equals("redstone") || widgetId.endsWith("_redstone");
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

    /**
     * v0.10 production statistics: the overview needs view (each storage in it also storage.search), item details
     * storage.search, pins layout.edit (item and storage pins also storage.search, checked by the game side).
     */
    public static Perm productionPerm(String op) {
        return switch (op) {
            case "item" -> Perm.STORAGE_SEARCH;
            case "pin", "unpin" -> Perm.LAYOUT_EDIT;
            default -> Perm.VIEW;
        };
    }

    /** The permission of an HTTP route that touches a mainframe (null: not mainframe-scoped). */
    public static @Nullable Perm routePerm(Router.Id route) {
        return switch (route) {
            case ACCESS_GET, ACCESS_POST -> Perm.ACCESS_MANAGE;
            case SHARES_GET, SHARES_POST, SHARE_DELETE -> Perm.SHARES_CREATE;
            case PUSH_GET, PUSH_POST -> Perm.PUSH_MANAGE;
            case APIKEYS_GET, APIKEYS_POST -> Perm.API_KEYS;
            case SCRIPT_LIST, SCRIPT_GET, SCRIPT_PUT, SCRIPT_MOVE -> Perm.SCRIPTS_UPLOAD;
            case SCRIPT_RUN -> Perm.SCRIPTS_RUN;
            case SCRIPT_DELETE -> Perm.SCRIPTS_DELETE;
            case MAP_TILE, VOXELS, VOXEL_BATCH -> Perm.VIEW_POSITIONS;
            case HISTORY -> Perm.VIEW;
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
        return deny(text, "forbidden");
    }

    /**
     * Refuses a client message with {@code error}: requests with a reqId get their own result type, everything else
     * {@code {"t":"error"}} (share sessions: {@code forbidden}; plain-HTTP internet access: {@code remote_locked}).
     *
     * @return the answer, or null for pong (nothing to send)
     */
    public static @Nullable String deny(String text, String error) {
        var peek = ClientMessages.peek(text);
        String t = peek.t();
        if ("pong".equals(t) || "ping".equals(t)) return null;
        String resultType = t == null ? null : switch (t) {
            case "action" -> "action_result";
            case "query" -> "query_result";
            case "layout_set" -> "layout_result";
            case "card_settings" -> "card_settings_result";
            case "net_unlink" -> "net_result";
            case "rs_override", "rs_device" -> "rs_result";
            case "rule_set", "rule_delete", "rule_enable", "rule_run" -> "rule_result";
            case "scene_set", "scene_delete" -> "scene_result";
            case "production" -> "production_result";
            case "storage" -> "storage_result";
            case "craft_preview" -> "craft_plan";
            case "term_open", "term_input" -> "term_result";
            case "unlock" -> "unlock_result";
            default -> null;
        };
        var json = new JsonObject();
        if (resultType != null && peek.reqId() != null) {
            json.addProperty("t", resultType);
            json.addProperty("reqId", peek.reqId());
            json.addProperty("ok", false);
            json.addProperty("error", error);
            if (resultType.equals("query_result")) {
                json.add("rows", new JsonArray());
                json.addProperty("truncated", false);
            }
        } else {
            json.addProperty("t", "error");
            json.addProperty("error", error);
        }
        return Json.GSON.toJson(json);
    }
}
