package ai.ezfox.cccloud.api;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.network.DashboardSection;
import ai.ezfox.cccloud.uplink.ActionValidator;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.QueryResult;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.core.GlobalPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Dashboard sections published by server-side scripts (the KubeJS bindings) instead of a computer: a section with
 * widgets on a mainframe's dashboard, no Lua and no block needed. Widgets and values go through exactly the validation a
 * Lua script gets ({@link WidgetFactory}); dashboard actions go through the bridge's per-widget permission check and
 * {@link ActionValidator} before the script's handler sees them. Sections live in memory: scripts publish them again
 * when they load (server start, {@code /reload}). They follow their mainframe (offline while it is, gone while it is
 * unloaded). Server thread only.
 */
public final class ScriptSections {
    /** Script section keys: like widget ids but shorter. */
    public static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,32}");
    public static final int MAX_PER_MAINFRAME = 16;
    public static final int MAX_WIDGETS = 64;
    /** Widget types a script may use (the others need a bridge or a computer). */
    public static final Set<WidgetType> TYPES = Set.of(WidgetType.BUTTON, WidgetType.TOGGLE, WidgetType.SLIDER, WidgetType.GAUGE, WidgetType.LABEL,
        WidgetType.CHART, WidgetType.PROGRESS, WidgetType.INDICATOR, WidgetType.SELECT, WidgetType.NUMBER, WidgetType.TEXT, WidgetType.BUTTONS);

    /** One widget as a script describes it ({@code opts} like the Lua option table). */
    public record WidgetSpec(String type, String id, @Nullable String label, @Nullable Map<?, ?> opts) {
    }

    /** What a script's action handler gets: the validated value and who sent it. */
    public record ActionContext(UUID mainframeId, String section, String widgetId, JsonElement value, String user) {
    }

    /** A script's action handler: null = accepted, else an error code for the browser (e.g. "busy"). */
    @FunctionalInterface
    public interface ActionHandler {
        @Nullable String handle(ActionContext context);
    }

    /** Thrown for invalid input; the message is meant for the script author. */
    public static final class ScriptException extends RuntimeException {
        public ScriptException(String message) {
            super(message);
        }
    }

    private static final Map<UUID, Map<String, Section>> SECTIONS = new HashMap<>();
    private static boolean listening;

    private ScriptSections() {
    }

    /** Stable section id (layouts survive restarts and republishing). */
    public static UUID sectionId(UUID mainframeId, String key) {
        return UUID.nameUUIDFromBytes(("cccloud:script:" + key + ":" + mainframeId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Publishes (or replaces) the section {@code key} on a mainframe. Values of widgets that keep their id and type are
     * kept. {@code restricted}: only devices allowed to see restricted sections get it (like the Server card).
     */
    public static UUID publish(UUID mainframeId, String key, String title, List<WidgetSpec> specs, boolean restricted, @Nullable ActionHandler handler) {
        listen();
        if (!KEY.matcher(key).matches()) throw new ScriptException("section key must match [a-z0-9_]{1,32}: " + key);
        if (CloudNetwork.mainframe(mainframeId) == null) throw new ScriptException("no loaded mainframe with id " + mainframeId);
        if (specs.size() > MAX_WIDGETS) throw new ScriptException("too many widgets (max " + MAX_WIDGETS + ")");
        String checkedTitle;
        try {
            checkedTitle = WidgetFactory.checkText("title", title, Limits.TITLE);
        } catch (WidgetFactory.WidgetException e) {
            throw new ScriptException(e.getMessage());
        }
        var widgets = new LinkedHashMap<String, Widget>();
        for (var spec : specs) {
            var widget = widget(spec);
            if (widgets.putIfAbsent(widget.id(), widget) != null) throw new ScriptException("duplicate widget id " + widget.id());
        }
        var map = SECTIONS.computeIfAbsent(mainframeId, id -> new LinkedHashMap<>());
        var old = map.get(key);
        if (old == null && map.size() >= MAX_PER_MAINFRAME) throw new ScriptException("too many script sections on this mainframe (max " + MAX_PER_MAINFRAME + ")");
        if (old != null) {
            for (var widget : widgets.values()) {
                var previous = old.widgets.get(widget.id());
                if (previous != null && previous.type() == widget.type()) widget.setValue(previous.value());
            }
            CloudNetwork.unregisterSection(old);
        }
        var section = new Section(mainframeId, key, checkedTitle, widgets, restricted, handler);
        map.put(key, section);
        section.attach();
        return section.sectionId;
    }

    static Widget widget(WidgetSpec spec) {
        var type = WidgetType.byId(spec.type());
        if (type == null || !TYPES.contains(type)) throw new ScriptException("unsupported widget type " + spec.type() + " (allowed: " + TYPES + ")");
        try {
            String id = WidgetFactory.checkId(spec.id());
            var widget = WidgetFactory.create(type, id, spec.label(), spec.opts());
            if (type == WidgetType.LABEL && spec.label() != null && !spec.label().isEmpty()) {
                // A caption for the label (like the ready-made cards); the text is the value.
                String caption = WidgetFactory.checkText("label", spec.label(), Limits.LABEL);
                widget = new Widget(widget.id(), WidgetType.LABEL, caption, widget.opts(), widget.value());
            }
            return widget;
        } catch (WidgetFactory.WidgetException e) {
            throw new ScriptException(spec.id() + ": " + e.getMessage());
        }
    }

    /** Sets a widget value (validated like {@code cloud.set}). */
    public static void set(UUID mainframeId, String key, String widgetId, @Nullable Object value) {
        var section = require(mainframeId, key);
        var widget = section.widgets.get(widgetId);
        if (widget == null) throw new ScriptException("unknown widget " + widgetId + " in section " + key);
        JsonElement json;
        try {
            json = WidgetFactory.valueFor(widget, value);
        } catch (WidgetFactory.WidgetException e) {
            throw new ScriptException(e.getMessage());
        }
        if (widget.type() == WidgetType.CHART) {
            widget.setValue(json);
            return; // sampled by the tick
        }
        if (widget.value().equals(json)) return;
        widget.setValue(json);
        section.dirty.add(widgetId);
    }

    /** Sends an alert on the section ({@code info|warn|crit}). */
    public static void alert(UUID mainframeId, String key, String text, String level) {
        var section = require(mainframeId, key);
        if (!WidgetFactory.ALERT_LEVELS.contains(level)) throw new ScriptException("alert level must be one of " + WidgetFactory.ALERT_LEVELS);
        String clipped = text.length() > Limits.ALERT_TEXT ? text.substring(0, Limits.ALERT_TEXT) : text;
        if (section.alerts.size() < 16) section.alerts.add(new String[]{ActionValidator.stripControl(clipped), level});
    }

    /** Removes a section from the dashboard. */
    public static boolean remove(UUID mainframeId, String key) {
        var map = SECTIONS.get(mainframeId);
        var section = map == null ? null : map.remove(key);
        if (section == null) return false;
        CloudNetwork.unregisterSection(section);
        WebHub.INSTANCE.publishUplinkRemoved(mainframeId, section.sectionId.toString());
        return true;
    }

    /** Current value of a widget (scripts read toggles etc.), or null. */
    public static @Nullable JsonElement value(UUID mainframeId, String key, String widgetId) {
        var map = SECTIONS.get(mainframeId);
        var section = map == null ? null : map.get(key);
        var widget = section == null ? null : section.widgets.get(widgetId);
        return widget == null ? null : widget.value();
    }

    /** Keys of the script sections on a mainframe. */
    public static List<String> keys(UUID mainframeId) {
        var map = SECTIONS.get(mainframeId);
        return map == null ? List.of() : List.copyOf(map.keySet());
    }

    /** The routed section (tests). */
    public static @Nullable DashboardSection section(UUID mainframeId, String key) {
        var map = SECTIONS.get(mainframeId);
        return map == null ? null : map.get(key);
    }

    private static Section require(UUID mainframeId, String key) {
        var map = SECTIONS.get(mainframeId);
        var section = map == null ? null : map.get(key);
        if (section == null) throw new ScriptException("no script section " + key + " on mainframe " + mainframeId + " (publish it first)");
        return section;
    }

    private static synchronized void listen() {
        if (listening) return;
        listening = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer().overworld().getGameTime()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SECTIONS.clear());
    }

    static void tick(long gameTime) {
        for (var map : SECTIONS.values()) {
            for (var section : map.values()) {
                try {
                    section.tick(gameTime);
                } catch (RuntimeException e) {
                    CloudLog.LOGGER.warn("[cccloud] Script section {} failed: {}", section.key, e.toString());
                }
            }
        }
    }

    private static final class Section implements DashboardSection {
        final UUID mainframeId;
        final String key;
        final String title;
        final UUID sectionId;
        final LinkedHashMap<String, Widget> widgets;
        final boolean restricted;
        final @Nullable ActionHandler handler;
        final Set<String> dirty = new LinkedHashSet<>();
        final List<String[]> alerts = new ArrayList<>();
        @Nullable CloudMainframeBlockEntity mainframe;
        @Nullable MainframeLink link;
        boolean structureDirty = true;
        boolean online;

        Section(UUID mainframeId, String key, String title, LinkedHashMap<String, Widget> widgets, boolean restricted, @Nullable ActionHandler handler) {
            this.mainframeId = mainframeId;
            this.key = key;
            this.title = title;
            this.sectionId = ScriptSections.sectionId(mainframeId, key);
            this.widgets = widgets;
            this.restricted = restricted;
            this.handler = handler;
        }

        /** Routes the section while its mainframe is loaded. */
        void attach() {
            var current = CloudNetwork.mainframe(mainframeId);
            if (current == mainframe && current != null) return;
            CloudNetwork.unregisterSection(this);
            mainframe = current;
            link = null;
            if (current == null || current.getLevel() == null) return;
            link = new MainframeLink(GlobalPos.of(current.getLevel().dimension(), current.getBlockPos()), mainframeId);
            CloudNetwork.registerSection(this);
            structureDirty = true;
        }

        void tick(long gameTime) {
            attach();
            var mf = mainframe;
            if (mf == null || mf.isRemoved()) return;
            boolean nowOnline = mf.status() == CloudStatus.ONLINE;
            if (nowOnline != online) {
                online = nowOnline;
                structureDirty = true;
            }
            for (var widget : widgets.values()) {
                if (widget.type() == WidgetType.CHART && widget.sample(gameTime)) dirty.add(widget.id());
            }
            String id = sectionId.toString();
            if (structureDirty) {
                structureDirty = false;
                dirty.clear();
                WebHub.INSTANCE.publishUplink(mainframeId, snapshot());
            } else if (!dirty.isEmpty()) {
                for (var widgetId : dirty) {
                    var widget = widgets.get(widgetId);
                    if (widget != null) WebHub.INSTANCE.publishValue(mainframeId, id, widgetId, widget.value());
                }
                dirty.clear();
            }
            if (online) for (var alert : alerts) WebHub.INSTANCE.publishAlert(mainframeId, id, alert[0], alert[1]);
            alerts.clear();
        }

        JsonObject snapshot() {
            var json = new JsonObject();
            json.addProperty("uplinkId", sectionId.toString());
            json.addProperty("title", title);
            json.addProperty("online", online);
            var array = new JsonArray();
            for (var widget : widgets.values()) array.add(widget.toJson());
            json.add("widgets", array);
            json.addProperty("mode", "direct"); // no computer behind it (no script editor, no power buttons)
            json.addProperty("template", "script");
            json.addProperty("source", "script");
            if (restricted) json.addProperty("restricted", true);
            return json;
        }

        @Override
        public UUID sectionId() {
            return sectionId;
        }

        @Override
        public @Nullable MainframeLink getLink() {
            return link;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified) {
            if (!online) return ActionResult.done("uplink_offline");
            var widget = widgets.get(widgetId);
            var result = ActionValidator.validate(widget, value, op, pinVerified);
            if (!result.ok()) return ActionResult.done(result.error());
            if (result.op() != null || result.value() == null) return ActionResult.done("invalid_value");
            String error;
            try {
                error = handler == null ? null : handler.handle(new ActionContext(mainframeId, key, widgetId, result.value(), user));
            } catch (RuntimeException e) {
                CloudLog.LOGGER.warn("[cccloud] Action handler of script section {} failed: {}", key, e.toString());
                error = "script_error";
            }
            if (error != null) return ActionResult.done(error.length() > 32 ? "script_error" : error);
            // Stateful widgets keep the accepted value (toggles, sliders, selects, inputs).
            if (widget.type() != WidgetType.BUTTON && widget.type() != WidgetType.BUTTONS && !widget.value().equals(result.value())) {
                widget.setValue(result.value());
                dirty.add(widgetId);
            }
            return ActionResult.done(null);
        }

        @Override
        public QueryResult handleQuery(String widgetId, String query) {
            return QueryResult.fail("unknown_widget");
        }
    }
}
