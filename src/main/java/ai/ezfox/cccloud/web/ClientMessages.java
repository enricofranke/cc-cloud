package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.layout.LayoutValidator;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Parsing and validation of everything a browser sends. Plain Java, unit tested. */
public final class ClientMessages {
    /** Max size of every message except layout_set. */
    public static final int MAX_FRAME = 8 * 1024;
    /** WebSocket frame limit: layout_set may carry a layout of up to 256 KB (plus a little envelope). */
    public static final int MAX_LAYOUT_FRAME = LayoutValidator.MAX_BYTES + 1024;
    public static final int MAX_BODY = 4 * 1024;
    private static final Pattern REQ_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public sealed interface Message permits Auth, AuthShare, Action, Query, LayoutSet, NetUnlink, RuleCommand, SceneCommand, Production, RedstoneOverride, Storage,
        CraftPreview, Terminal, RedstoneDevice, DisplayCommand, MainframeRename, CardSettings, CardDetail, CardWrite, CodeCommand, Unlock, Pong, Invalid {
    }

    /**
     * Plain-HTTP internet access ({@code internetAccess = http}): {@code {"t":"unlock","reqId","pin"}} unlocks writes
     * from an internet address for a while ({@link ai.ezfox.cccloud.web.internet.InternetGuard}) → {@code unlock_result}.
     */
    public record Unlock(String reqId, String pin) implements Message {
    }

    /**
     * Wave 4: the dashboard's card settings of a device card ({@code layout.edit}), e.g. the methods a Peripheral card
     * shows: {@code {"t":"card_settings","reqId","uplinkId","settings":{...}}} → {@code card_settings_result}. The
     * settings object is checked by the card itself; here only its shape and size.
     */
    public record CardSettings(String reqId, UUID uplinkId, JsonObject settings) implements Message {
    }

    /**
     * A card's restricted details, read on demand and never pushed: {@code {"t":"card_detail","reqId","uplinkId"}} →
     * {@code card_detail_result} with {@code detail} (a Factory Manager's program and its errors). Needs
     * {@code view.positions}; share links and API keys cannot send it.
     */
    public record CardDetail(String reqId, UUID uplinkId) implements Message {
    }

    /**
     * Writes a machine's program: {@code {"t":"card_write","reqId","uplinkId","op":"check"|"save"|"undo","text?","force?","pin?"}}
     * → {@code card_write_result}. {@code check} compiles without saving; {@code save} refuses to replace a working
     * program with a broken one unless {@code force}. Needs {@code view.positions} and {@code device.program}; saving and
     * undo also the mainframe PIN when one is set.
     */
    public record CardWrite(String reqId, UUID uplinkId, String op, @Nullable String text, boolean force, @Nullable String pin) implements Message {
    }

    /** Longest program accepted from the dashboard, in characters. */
    public static final int MAX_PROGRAM = 32 * 1024;
    /** Longest {@code card_write} message (a program with most characters escaped is refused as too large). */
    static final int MAX_CARD_WRITE_FRAME = 64 * 1024;

    /**
     * Cloud Scripts ({@code code.edit}): {@code {"t":"code","reqId","op",…}} → {@code code_result}. Fields by op: {@code id}
     * (every op on one script), {@code name} + {@code source} ({@code save}; {@code id} absent = a new script,
     * {@code baseVersion} = the version the editor started from), {@code source} ({@code check}), {@code name}
     * ({@code rename}), {@code enabled} ({@code autostart}), {@code version} ({@code version}, {@code revert}). Names and
     * sizes are checked by {@link ai.ezfox.cccloud.code.store.ScriptBook}. A malformed request keeps its {@code reqId} and
     * carries {@code error} ({@code invalid}, {@code unknown_script}, {@code too_large}), answered after the rights check.
     * {@code language} ({@code save}, {@code check}): {@code lua} (default) or {@code wasm} (the source is the stored form
     * of {@code code.wasm.WasmSource}: the module in base64 and its source text); an existing script keeps its language.
     */
    public record CodeCommand(String reqId, String op, @Nullable String id, @Nullable String name, @Nullable String source, @Nullable Integer baseVersion,
                              @Nullable Integer version, @Nullable Boolean enabled, @Nullable String error, @Nullable String language) implements Message {
        public CodeCommand(String reqId, String op, @Nullable String id, @Nullable String name, @Nullable String source, @Nullable Integer baseVersion,
                           @Nullable Integer version, @Nullable Boolean enabled, @Nullable String error) {
            this(reqId, op, id, name, source, baseVersion, version, enabled, error, null);
        }

        /** The language of a new script or a check: {@code lua} unless given. */
        public String languageOr() {
            return language == null ? "lua" : language;
        }

        /** Ops that only read (everything else changes a script or its run). */
        public static final java.util.Set<String> READ_OPS = java.util.Set.of("list", "get", "check", "versions", "version", "logs", "unwatch");

        public boolean writes() {
            return error == null && !READ_OPS.contains(op);
        }
    }

    /** Cloud Scripts ops. */
    public static final java.util.Set<String> CODE_OPS = java.util.Set.of("list", "get", "check", "save", "delete", "start", "stop", "restart", "autostart",
        "rename", "versions", "version", "revert", "logs", "unwatch");
    /** Longest {@code code} message: a source of up to 32 KB plus its escaping. */
    static final int MAX_CODE_FRAME = 64 * 1024;
    /** Longest {@code code} message with a WebAssembly script (module in base64 and its source text, see Limits). */
    static final int MAX_WASM_CODE_FRAME = MAX_LAYOUT_FRAME;
    /** Script languages the dashboard can save. */
    public static final java.util.Set<String> CODE_LANGUAGES = java.util.Set.of("lua", "wasm");
    private static final Pattern SCRIPT_ID = Pattern.compile("[a-f0-9]{8}");

    /** Card settings are small: at most this many characters of JSON. */
    static final int MAX_CARD_SETTINGS = 2048;

    /**
     * v0.10 scenes: {@code scene_set {reqId, scene}} and {@code scene_delete {reqId, id}} (both {@code rules.edit}),
     * answered with {@code scene_result}. The scene itself is validated by {@link ai.ezfox.cccloud.scene.SceneCodec}.
     * Scenes run through their button in the Scenes section (an ordinary action).
     */
    public record SceneCommand(String reqId, String op, @Nullable String id, @Nullable JsonElement scene) implements Message {
    }

    /** Production statistics operations: overview and item read, pin and unpin change the Production section. */
    public static final java.util.Set<String> PRODUCTION_OPS = java.util.Set.of("overview", "item", "pin", "unpin");
    public static final java.util.Set<String> PIN_KINDS = java.util.Set.of("item", "storage", "energy");

    /**
     * v0.10 production statistics: {@code {"t":"production","reqId","op",…}} → {@code production_result}. {@code item}
     * needs uplinkId + name, a {@code pin} its kind (item: uplinkId + name, storage: uplinkId), {@code unpin} the pin id.
     */
    public record Production(String reqId, String op, @Nullable UUID uplinkId, @Nullable String name, @Nullable String kind, @Nullable String id) implements Message {
    }

    /** Storage operations that only read (browse, craftables, fluids, item, cpus). */
    public static final java.util.Set<String> STORAGE_READ_OPS = java.util.Set.of("browse", "craftables", "fluids", "item", "cpus");
    public static final java.util.Set<String> STORAGE_OPS = java.util.Set.of("browse", "craftables", "fluids", "item", "cpus", "cancel", "retry", "favorite", "autostock");

    /**
     * Storage card v2: {@code {"t":"storage","reqId","uplinkId","id?","op",…}} → {@code storage_result}. The op's
     * own fields stay in {@code args} (validated per op by the bridge side).
     */
    public record Storage(String reqId, UUID uplinkId, @Nullable String widgetId, String op, JsonObject args) implements Message {
    }

    /**
     * v0.5 E browser terminal: {@code term_open {reqId, uplinkId, pin?}} → {@code term_result}, {@code term_input
     * {uplinkId, events:[…], reqId?}} (validated by {@link ai.ezfox.cccloud.terminal.TerminalInput} against the terminal
     * size), {@code term_close {uplinkId}}.
     */
    public record Terminal(String op, @Nullable String reqId, UUID uplinkId, @Nullable String pin, @Nullable JsonElement events) implements Message {
    }

    /** {@code {"t":"craft_preview","reqId","uplinkId","id?","name","count"}} → {@code craft_plan}. */
    public record CraftPreview(String reqId, UUID uplinkId, @Nullable String widgetId, String name, long count, boolean max) implements Message {
    }

    /**
     * v0.5 rules: {@code rule_set {reqId, rule}} (admin), {@code rule_delete {reqId, id}} (admin),
     * {@code rule_enable {reqId, id, enabled}} and {@code rule_run {reqId, id}} (operator). Answered with
     * {@code rule_result}. The rule itself is validated by {@link ai.ezfox.cccloud.rules.RuleCodec}.
     */
    public record RuleCommand(String reqId, String op, @Nullable String id, boolean enabled, @Nullable JsonElement rule) implements Message {
        /** rule_set and rule_delete edit rules (admin); rule_enable and rule_run only operate them (operator). */
        public boolean edits() {
            return op.equals("rule_set") || op.equals("rule_delete");
        }
    }

    /** {@code {"t":"auth","token","mainframeId?"}}: account tokens must name the mainframe (v0.6). */
    public record Auth(String token, @Nullable UUID mainframeId) implements Message {
        public Auth(String token) {
            this(token, null);
        }
    }

    /** {@code {"t":"auth_share","id","secret"}}: a share-link viewer (v0.6). The secret comes from the URL fragment. */
    public record AuthShare(String id, String secret) implements Message {
    }

    /** {@code {"t":"net_unlink","reqId","nodeId"}}: unlink a device from the network map (admin). */
    public record NetUnlink(String reqId, String nodeId) implements Message {
    }

    /**
     * v0.7 wireless redstone: {@code {"t":"rs_override","reqId","channel","level":0-15|null,"seconds":1-86400|null}} – an
     * operator forces a channel's level for {@code seconds} (null = until reset); {@code level:null} clears it.
     * Answered with {@code rs_result}.
     */
    public record RedstoneOverride(String reqId, String channel, @Nullable Integer level, @Nullable Integer seconds) implements Message {
    }

    /**
     * Cloud Redstone devices: {@code {"t":"rs_device","reqId","deviceId","name?","group?"}} – rename a device or move it to
     * another group (= card; "" = the automatic card). Plain text, at most 32 characters each, at least one of them.
     * Needs {@code network.link}; answered with {@code rs_result}.
     */
    public record RedstoneDevice(String reqId, UUID deviceId, @Nullable String name, @Nullable String group) implements Message {
    }

    /**
     * Cloud Displays of this mainframe: {@code {"t":"display","reqId","op":"list"}} and, for one display ({@code id} from
     * the list), {@code "op":"set"} with at least one of {@code target} ("" = nothing, a section id, {@code c:<card>},
     * {@code p:<page>}), {@code interactive}, {@code allowRestricted}, or {@code "op":"unlink"}. A request, not a
     * broadcast, so positions only go to whoever asks and may see them. Needs {@code layout.edit}; answered with
     * {@code display_result}.
     */
    public record DisplayCommand(String reqId, String op, @Nullable UUID id, @Nullable String target, @Nullable Boolean interactive,
                                 @Nullable Boolean allowRestricted) implements Message {
    }

    /**
     * {@code {"t":"mainframe_rename","reqId","name"}}: renames the mainframe, plain text of at most 32 characters, ""
     * = the default name ({@link ai.ezfox.cccloud.gui.GuiActions#mainframeName}). Needs {@code layout.edit}; answered
     * with {@code mainframe_result}, every client gets the name as a {@code state} delta.
     */
    public record MainframeRename(String reqId, String name) implements Message {
    }

    /** Only the type and request id of a message (for the hard deny of share sessions). */
    public record Peek(@Nullable String t, @Nullable String reqId) {
    }

    /**
     * @param value a scalar, or the raw object of a structured operation (then {@code op} is set).
     */
    public record Action(@Nullable String reqId, UUID uplinkId, String id, JsonElement value, @Nullable Op op, @Nullable String pin) implements Message {
        public Action(@Nullable String reqId, UUID uplinkId, String id, JsonElement value, @Nullable String pin) {
            this(reqId, uplinkId, id, value, null, pin);
        }
    }

    /** Item search on an item_search widget: {@code {"t":"query","reqId","uplinkId","id","q"}}. */
    public record Query(String reqId, UUID uplinkId, String id, String q) implements Message {
    }

    /**
     * {@code {"t":"layout_set","reqId","layout":{...}}}. Validation happens while parsing; {@code error} is
     * {@code too_large} or {@code invalid_layout} (answered with layout_result after the rights/rate checks).
     */
    public record LayoutSet(String reqId, @Nullable JsonObject layout, @Nullable String error) implements Message {
    }

    public record Pong() implements Message {
    }

    public record Invalid(String reason, @Nullable String reqId, @Nullable String detail) implements Message {
        public Invalid(String reason, @Nullable String reqId) {
            this(reason, reqId, null);
        }
    }

    public record PairRequest(String code, String name) {
    }

    /** Storage card operations that change something (cancel or retry a job, favorites, the watchlist); the others only read. */
    static final java.util.Set<String> STORAGE_WRITE_OPS = java.util.Set.of("cancel", "retry", "favorite", "autostock");

    /**
     * Whether a message changes something in the game or the mainframe's settings (controls, layout, rules, scenes,
     * access, terminal input, …). Reads (item search, storage browsing, statistics, craft plans), authentication, pong,
     * closing a terminal and the PIN unlock are not writes. Plain-HTTP internet access asks for the PIN before any write
     * from an internet address. A new message type must be classified here (the switch is exhaustive).
     */
    public static boolean isWrite(Message message) {
        return switch (message) {
            case Action a -> true;
            case LayoutSet l -> true;
            case NetUnlink n -> true;
            case RuleCommand r -> true;
            case SceneCommand s -> true;
            case RedstoneOverride r -> true;
            case RedstoneDevice r -> true;
            case DisplayCommand d -> !d.op().equals("list");
            case MainframeRename m -> true;
            case CardSettings c -> true;
            case CardDetail c -> false;
            case CardWrite c -> !c.op().equals("check");
            case CodeCommand c -> c.writes();
            case Production p -> p.op().equals("pin") || p.op().equals("unpin");
            case Storage s -> STORAGE_WRITE_OPS.contains(s.op());
            case Terminal t -> !t.op().equals("term_close");
            case Query q -> false;
            case CraftPreview c -> false;
            case Unlock u -> false;
            case Auth a -> false;
            case AuthShare a -> false;
            case Pong p -> false;
            case Invalid i -> false;
        };
    }

    /** Message types that are accepted before authentication. Everything else needs an authenticated session. */
    public static boolean isPublic(Message message) {
        return message instanceof Auth || message instanceof AuthShare || message instanceof Pong || message instanceof Invalid;
    }

    /** Reads only {@code t} and a well-formed {@code reqId} (bounded; used to answer share sessions with "forbidden"). */
    public static Peek peek(String text) {
        if (text.length() > MAX_LAYOUT_FRAME) return new Peek(null, null);
        JsonObject obj = parseObject(text);
        if (obj == null) return new Peek(null, null);
        String t = string(obj, "t");
        String reqId = string(obj, "reqId");
        if (t != null && t.length() > 32) t = null;
        if (reqId != null && !REQ_ID.matcher(reqId).matches()) reqId = null;
        return new Peek(t, reqId);
    }

    private ClientMessages() {
    }

    public static Message parse(String text) {
        if (text.length() > MAX_LAYOUT_FRAME) return new Invalid("too_large", null);
        JsonObject obj = parseObject(text);
        if (obj == null) return new Invalid(text.length() > MAX_FRAME ? "too_large" : "invalid_message", null);
        String t = string(obj, "t");
        if (t == null) return new Invalid("invalid_message", null);
        if (text.length() > MAX_FRAME && !"layout_set".equals(t) && !("card_write".equals(t) && text.length() <= MAX_CARD_WRITE_FRAME)
            && !("code".equals(t) && text.length() <= ("wasm".equals(string(obj, "language")) ? MAX_WASM_CODE_FRAME : MAX_CODE_FRAME))) {
            return new Invalid("too_large", null);
        }
        return switch (t) {
            case "auth" -> {
                String token = string(obj, "token");
                String mainframe = string(obj, "mainframeId");
                UUID mainframeId = mainframe == null ? null : parseUuid(mainframe);
                if (token == null || token.isEmpty() || token.length() > 128 || (mainframe != null && mainframeId == null)) yield new Invalid("invalid_message", null);
                yield new Auth(token, mainframeId);
            }
            case "auth_share" -> {
                String id = string(obj, "id");
                String secret = string(obj, "secret");
                yield id == null || secret == null || id.length() > 32 || secret.isEmpty() || secret.length() > 64
                    ? new Invalid("invalid_message", null) : new AuthShare(id, secret);
            }
            case "rule_set", "rule_delete", "rule_enable", "rule_run" -> parseRule(t, obj);
            case "scene_set", "scene_delete" -> parseScene(t, obj);
            case "production" -> parseProduction(obj);
            case "storage" -> parseStorage(obj);
            case "term_open", "term_input", "term_close" -> parseTerminal(t, obj);
            case "craft_preview" -> parseCraftPreview(obj);
            case "net_unlink" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                String nodeId = string(obj, "nodeId");
                yield nodeId == null || !NODE_ID.matcher(nodeId).matches() ? new Invalid("invalid_message", reqId) : new NetUnlink(reqId, nodeId);
            }
            case "rs_override" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                String channel = string(obj, "channel");
                Integer level = smallInt(obj, "level");
                Integer seconds = smallInt(obj, "seconds");
                boolean levelOk = level != null || !obj.has("level") || obj.get("level").isJsonNull();
                boolean secondsOk = seconds != null || !obj.has("seconds") || obj.get("seconds").isJsonNull();
                yield channel == null || !ai.ezfox.cccloud.redstone.RedstoneChannels.validName(channel) || !levelOk || !secondsOk
                    || (level != null && (level < 0 || level > 15)) || (seconds != null && (seconds < 1 || seconds > 86_400))
                    ? new Invalid("invalid_message", reqId) : new RedstoneOverride(reqId, channel, level, seconds);
            }
            case "rs_device" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                UUID deviceId = parseUuid(string(obj, "deviceId"));
                String name = string(obj, "name");
                String group = string(obj, "group");
                boolean extra = obj.keySet().stream().anyMatch(k -> !java.util.Set.of("t", "reqId", "deviceId", "name", "group").contains(k));
                yield deviceId == null || extra || (name == null && group == null)
                    || (name != null && !ai.ezfox.cccloud.rsdevice.RsDevice.validText(name.strip()))
                    || (group != null && !ai.ezfox.cccloud.rsdevice.RsDevice.validText(group.strip()))
                    ? new Invalid("invalid_message", reqId) : new RedstoneDevice(reqId, deviceId, name == null ? null : name.strip(), group == null ? null : group.strip());
            }
            case "display" -> parseDisplay(obj);
            case "mainframe_rename" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                String name = ai.ezfox.cccloud.gui.GuiActions.mainframeName(string(obj, "name"));
                boolean extra = obj.keySet().stream().anyMatch(k -> !java.util.Set.of("t", "reqId", "name").contains(k));
                yield name == null || extra ? new Invalid("invalid_message", reqId) : new MainframeRename(reqId, name);
            }
            case "pong", "ping" -> new Pong();
            case "action" -> parseAction(obj);
            case "query" -> parseQuery(obj);
            case "card_settings" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                UUID uplinkId = parseUuid(string(obj, "uplinkId"));
                var settings = obj.get("settings");
                boolean extra = obj.keySet().stream().anyMatch(k -> !java.util.Set.of("t", "reqId", "uplinkId", "settings").contains(k));
                yield uplinkId == null || extra || settings == null || !settings.isJsonObject() || settings.toString().length() > MAX_CARD_SETTINGS
                    ? new Invalid("invalid_message", reqId) : new CardSettings(reqId, uplinkId, settings.getAsJsonObject());
            }
            case "card_detail" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                UUID uplinkId = parseUuid(string(obj, "uplinkId"));
                yield uplinkId == null ? new Invalid("invalid_message", reqId) : new CardDetail(reqId, uplinkId);
            }
            case "card_write" -> parseCardWrite(obj);
            case "code" -> parseCode(obj);
            case "layout_set" -> parseLayoutSet(obj);
            case "unlock" -> {
                String reqId = string(obj, "reqId");
                if (reqId == null || !REQ_ID.matcher(reqId).matches()) yield new Invalid("invalid_message", null);
                String pin = string(obj, "pin");
                yield pin == null || pin.length() > 32 ? new Invalid("invalid_message", reqId) : new Unlock(reqId, pin);
            }
            default -> new Invalid("invalid_message", null);
        };
    }

    private static Message parseDisplay(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        String op = string(obj, "op");
        var invalid = new Invalid("invalid_message", reqId);
        if (op == null) return invalid;
        if (op.equals("list")) return obj.keySet().stream().allMatch(k -> k.equals("t") || k.equals("reqId") || k.equals("op")) ? new DisplayCommand(reqId, op, null, null, null, null) : invalid;
        if (!op.equals("set") && !op.equals("unlink")) return invalid;
        var allowed = op.equals("set") ? java.util.Set.of("t", "reqId", "op", "id", "target", "interactive", "allowRestricted") : java.util.Set.of("t", "reqId", "op", "id");
        if (!allowed.containsAll(obj.keySet())) return invalid;
        UUID id = parseUuid(string(obj, "id"));
        if (id == null) return invalid;
        if (op.equals("unlink")) return new DisplayCommand(reqId, op, id, null, null, null);
        if (obj.has("target") && !(obj.get("target") instanceof com.google.gson.JsonPrimitive p && p.isString())) return invalid;
        String target = string(obj, "target");
        if (target != null && !ai.ezfox.cccloud.display.DisplayCompose.validTarget(target)) return invalid;
        Boolean interactive = flag(obj, "interactive"), restricted = flag(obj, "allowRestricted");
        boolean flagsOk = (interactive != null || !obj.has("interactive")) && (restricted != null || !obj.has("allowRestricted"));
        return !flagsOk || target == null && interactive == null && restricted == null ? invalid : new DisplayCommand(reqId, op, id, target, interactive, restricted);
    }

    private static @Nullable Boolean flag(JsonObject obj, String key) {
        return obj.get(key) instanceof com.google.gson.JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : null;
    }

    private static Message parseCode(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        String op = string(obj, "op");
        String id = string(obj, "id");
        String name = string(obj, "name");
        String source = string(obj, "source");
        Integer baseVersion = smallInt(obj, "baseVersion");
        Integer version = smallInt(obj, "version");
        Boolean enabled = flag(obj, "enabled");
        String language = string(obj, "language");
        String error = null;
        if (op == null || !CODE_OPS.contains(op)) {
            op = "invalid";
            error = "invalid";
        } else if (!java.util.Set.of("t", "reqId", "op", "id", "name", "source", "baseVersion", "version", "enabled", "language").containsAll(obj.keySet())
            // a field that is there must have its type
            || (obj.has("id") && id == null) || (obj.has("name") && name == null) || (obj.has("source") && source == null)
            || (obj.has("enabled") && enabled == null) || (obj.has("version") && version == null)
            || (obj.has("baseVersion") && !obj.get("baseVersion").isJsonNull() && baseVersion == null)
            || (obj.has("language") && (language == null || !CODE_LANGUAGES.contains(language)))
            || (name != null && name.length() > 64)) {
            error = "invalid";
        } else if (id != null && !SCRIPT_ID.matcher(id).matches()) {
            error = "unknown_script";
        } else if (source != null && source.length() > ("wasm".equals(language) ? MAX_WASM_CODE_FRAME : MAX_PROGRAM)) {
            error = "too_large";
        } else if (!java.util.Set.of("list", "check", "save").contains(op) && id == null) {
            error = "invalid";
        } else {
            boolean ok = switch (op) {
                case "check" -> source != null;
                case "save" -> name != null && source != null;
                case "rename" -> name != null;
                case "autostart" -> enabled != null;
                case "version", "revert" -> version != null && version > 0;
                default -> true;
            };
            if (!ok) error = "invalid";
        }
        return new CodeCommand(reqId, op, id, name, source, baseVersion, version, enabled, error, language);
    }

    private static Message parseCardWrite(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        String op = string(obj, "op");
        String text = string(obj, "text");
        String pin = string(obj, "pin");
        var force = obj.get("force");
        boolean extra = obj.keySet().stream().anyMatch(k -> !java.util.Set.of("t", "reqId", "uplinkId", "op", "text", "force", "pin").contains(k));
        if (uplinkId == null || extra || op == null || !java.util.Set.of("check", "save", "undo").contains(op)) return new Invalid("invalid_message", reqId);
        if (op.equals("undo") ? text != null : text == null) return new Invalid("invalid_message", reqId);
        if (text != null && text.length() > MAX_PROGRAM) return new Invalid("too_large", reqId);
        if (force != null && !(force.isJsonPrimitive() && force.getAsJsonPrimitive().isBoolean())) return new Invalid("invalid_message", reqId);
        if (pin != null && !AuthStore.isValidPin(pin)) return new Invalid("wrong_pin", reqId);
        return new CardWrite(reqId, uplinkId, op, text, force != null && force.getAsBoolean(), pin);
    }

    private static Message parseAction(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId != null && !REQ_ID.matcher(reqId).matches()) reqId = null;
        String uplink = string(obj, "uplinkId");
        String id = string(obj, "id");
        UUID uplinkId = parseUuid(uplink);
        if (uplinkId == null || !Json.isValidId(id)) return new Invalid("invalid_message", reqId);
        JsonElement value = obj.get("value");
        if (value == null || value.isJsonNull()) return new Invalid("invalid_value", reqId);
        Op op = null;
        if (value.isJsonObject()) {
            op = Op.parse(value.getAsJsonObject());
            if (op == null) return new Invalid("invalid_value", reqId);
        } else {
            if (!value.isJsonPrimitive()) return new Invalid("invalid_value", reqId);
            var p = value.getAsJsonPrimitive();
            if (p.isString() && p.getAsString().length() > Limits.STRING_VALUE) return new Invalid("invalid_value", reqId);
            if (p.isNumber() && !Double.isFinite(p.getAsDouble())) return new Invalid("invalid_value", reqId);
        }
        String pin = string(obj, "pin");
        if (pin != null && !AuthStore.isValidPin(pin)) return new Invalid("wrong_pin", reqId);
        return new Action(reqId, uplinkId, id, value, op, pin);
    }

    private static final Pattern RULE_ID = Pattern.compile("[0-9a-f]{8}");

    private static Message parseStorage(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        String id = string(obj, "id");
        String op = string(obj, "op");
        if (uplinkId == null || (id != null && !Json.isValidId(id)) || op == null || !STORAGE_OPS.contains(op)) return new Invalid("invalid_message", reqId);
        var args = obj.deepCopy();
        for (String key : java.util.List.of("t", "reqId", "uplinkId", "id", "op")) args.remove(key);
        return new Storage(reqId, uplinkId, id, op, args);
    }

    private static Message parseCraftPreview(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        String id = string(obj, "id");
        String name = string(obj, "name");
        var count = obj.get("count");
        if (uplinkId == null || (id != null && !Json.isValidId(id)) || !Op.isItemName(name) || count == null || !count.isJsonPrimitive()
            || !count.getAsJsonPrimitive().isNumber() || count.getAsDouble() != Math.rint(count.getAsDouble()) || count.getAsDouble() < 1
            || count.getAsDouble() > Integer.MAX_VALUE) return new Invalid("invalid_message", reqId);
        return new CraftPreview(reqId, uplinkId, id, name, count.getAsLong(), obj.has("max") && obj.get("max").isJsonPrimitive() && obj.get("max").getAsBoolean());
    }

    private static Message parseRule(String t, JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        String id = string(obj, "id");
        if (!t.equals("rule_set") && (id == null || !RULE_ID.matcher(id).matches())) return new Invalid("invalid_message", reqId);
        boolean enabled = false;
        if (t.equals("rule_enable")) {
            var e = obj.get("enabled");
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) return new Invalid("invalid_message", reqId);
            enabled = e.getAsBoolean();
        }
        JsonElement rule = t.equals("rule_set") ? obj.get("rule") : null;
        if (t.equals("rule_set") && (rule == null || !rule.isJsonObject())) return new Invalid("invalid_message", reqId);
        return new RuleCommand(reqId, t, id, enabled, rule);
    }

    private static Message parseScene(String t, JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        if (t.equals("scene_delete")) {
            String id = string(obj, "id");
            return id == null || !RULE_ID.matcher(id).matches() ? new Invalid("invalid_message", reqId) : new SceneCommand(reqId, t, id, null);
        }
        var scene = obj.get("scene");
        return scene == null || !scene.isJsonObject() ? new Invalid("invalid_message", reqId) : new SceneCommand(reqId, t, null, scene);
    }

    private static Message parseProduction(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        String op = string(obj, "op");
        if (op == null || !PRODUCTION_OPS.contains(op)) return new Invalid("invalid_message", reqId);
        String uplink = string(obj, "uplinkId");
        UUID uplinkId = parseUuid(uplink);
        String name = string(obj, "name");
        String kind = string(obj, "kind");
        String id = string(obj, "id");
        if ((uplink != null && uplinkId == null) || (name != null && !Op.isItemName(name))) return new Invalid("invalid_message", reqId);
        boolean ok = switch (op) {
            case "item" -> uplinkId != null && name != null;
            case "pin" -> kind != null && PIN_KINDS.contains(kind) && switch (kind) {
                case "item" -> uplinkId != null && name != null;
                case "storage" -> uplinkId != null;
                default -> true;
            };
            case "unpin" -> id != null && RULE_ID.matcher(id).matches();
            default -> true;
        };
        return ok ? new Production(reqId, op, uplinkId, name, kind, id) : new Invalid("invalid_message", reqId);
    }

    private static Message parseQuery(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        String id = string(obj, "id");
        String q = string(obj, "q");
        if (uplinkId == null || !Json.isValidId(id) || q == null || q.length() > Limits.QUERY) return new Invalid("invalid_message", reqId);
        return new Query(reqId, uplinkId, id, q.strip());
    }

    private static Message parseTerminal(String t, JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId != null && !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        if (t.equals("term_open") && reqId == null) return new Invalid("invalid_message", null);
        UUID uplinkId = parseUuid(string(obj, "uplinkId"));
        if (uplinkId == null) return new Invalid("invalid_message", reqId);
        String pin = t.equals("term_open") ? string(obj, "pin") : null;
        if (pin != null && pin.length() > 32) return new Invalid("invalid_message", reqId);
        JsonElement events = t.equals("term_input") ? obj.get("events") : null;
        if (t.equals("term_input") && (events == null || !events.isJsonArray())) return new Invalid("invalid_message", reqId);
        return new Terminal(t, reqId, uplinkId, pin, events);
    }

    private static Message parseLayoutSet(JsonObject obj) {
        String reqId = string(obj, "reqId");
        if (reqId == null || !REQ_ID.matcher(reqId).matches()) return new Invalid("invalid_message", null);
        var result = LayoutValidator.validate(obj.get("layout"));
        return new LayoutSet(reqId, result.layout(), result.error());
    }

    public static @Nullable PairRequest parsePair(String body) {
        if (body.length() > MAX_BODY) return null;
        JsonObject obj = parseObject(body);
        if (obj == null) return null;
        String code = string(obj, "code");
        String name = string(obj, "name");
        if (code == null || code.length() > 32 || name == null) return null;
        String clean = AuthStore.sanitizeName(name);
        if (name.trim().isEmpty()) return null;
        return new PairRequest(code, clean);
    }

    public static @Nullable UUID parseUuid(@Nullable String s) {
        if (s == null || s.length() != 36) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable JsonObject parseObject(String text) {
        try {
            var element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** An integer field (null if missing, null or not a whole number). */
    private static @Nullable Integer smallInt(JsonObject obj, String key) {
        var e = obj.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double d = e.getAsDouble();
        if (d != Math.rint(d) || Math.abs(d) > 1_000_000) return null;
        return (int) d;
    }

    private static @Nullable String string(JsonObject obj, String key) {
        var e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}
