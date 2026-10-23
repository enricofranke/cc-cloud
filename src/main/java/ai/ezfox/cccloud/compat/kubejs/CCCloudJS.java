package ai.ezfox.cccloud.compat.kubejs;

import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.api.ScriptSections;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.network.CloudNetwork;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The {@code CCCloud} binding for KubeJS server scripts: list mainframes, check a player's permissions and publish
 * dashboard sections with widgets (no computer, no Lua). Everything goes through {@link ScriptSections}, i.e. the same
 * widget/value validation as Lua; dashboard actions on these widgets need the same per-widget permission as any other
 * section ({@code act.buttons}). Invalid input throws with a message for the script author.
 *
 * <pre>{@code
 * const id = CCCloud.mainframes()[0].id
 * CCCloud.section(id, "reactor").title("Reactor")
 *   .gauge("temp", "Temperature", {max: 1200, unit: "K"})
 *   .toggle("pumps", "Coolant pumps")
 *   .onAction(ctx => { if (ctx.widget == "pumps") server.runCommandSilent(...) })
 *   .publish()
 * CCCloud.set(id, "reactor", "temp", 812)
 * }</pre>
 */
public final class CCCloudJS {
    public static final CCCloudJS INSTANCE = new CCCloudJS();

    private CCCloudJS() {
    }

    /** A mainframe as scripts see it. */
    public record MainframeInfo(String id, String name, String owner, String dimension, int x, int y, int z, boolean online) {
    }

    /** All loaded mainframes. */
    public List<MainframeInfo> mainframes() {
        var list = new ArrayList<MainframeInfo>();
        for (var mf : List.copyOf(CloudNetwork.mainframes())) {
            if (mf.isRemoved() || mf.getLevel() == null) continue;
            var pos = mf.getBlockPos();
            list.add(new MainframeInfo(mf.getMainframeId().toString(), mf.getDisplayName(), mf.getOwnerName(),
                mf.getLevel().dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), mf.status() == CloudStatus.ONLINE));
        }
        return list;
    }

    /** The loaded mainframes whose dashboard the player may see ({@code view}). */
    public List<MainframeInfo> mainframesOf(ServerPlayer player) {
        var list = new ArrayList<MainframeInfo>();
        for (var info : mainframes()) if (Access.has(player, UUID.fromString(info.id()), Perm.VIEW)) list.add(info);
        return list;
    }

    /** Whether the player has a permission ({@code "view"}, {@code "act.buttons"}, {@code "layout.edit"}, …) on a mainframe. */
    public boolean has(ServerPlayer player, String mainframeId, String permission) {
        var perm = Perm.byId(permission);
        if (perm == null) throw new ScriptSections.ScriptException("unknown permission " + permission);
        return Access.has(player, uuid(mainframeId), perm);
    }

    /** Starts a section definition; nothing changes until {@code publish()}. */
    public SectionBuilder section(String mainframeId, String key) {
        return new SectionBuilder(uuid(mainframeId), key);
    }

    /** Sets a widget value (validated like Lua's {@code cloud.set}). */
    public void set(String mainframeId, String key, String widgetId, @Nullable Object value) {
        ScriptSections.set(uuid(mainframeId), key, widgetId, number(value));
    }

    /** The current value of a widget (e.g. a toggle the dashboard switched), or null. */
    public @Nullable Object get(String mainframeId, String key, String widgetId) {
        return plain(ScriptSections.value(uuid(mainframeId), key, widgetId));
    }

    /** Sends an alert on a script section: level info, warn or crit. */
    public void alert(String mainframeId, String key, String text, String level) {
        ScriptSections.alert(uuid(mainframeId), key, text, level);
    }

    /** Removes a script section from the dashboard. */
    public boolean remove(String mainframeId, String key) {
        return ScriptSections.remove(uuid(mainframeId), key);
    }

    /** The keys of the script sections on a mainframe. */
    public List<String> sections(String mainframeId) {
        return ScriptSections.keys(uuid(mainframeId));
    }

    static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ScriptSections.ScriptException("not a mainframe id: " + id);
        }
    }

    /** JS numbers may arrive as Integer/Long; widget validation expects doubles like Lua. */
    static @Nullable Object number(@Nullable Object value) {
        return value instanceof Number n && !(value instanceof Double) ? n.doubleValue() : value;
    }

    /** A JSON value as a plain Java value for scripts. */
    static @Nullable Object plain(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return null;
        var p = value.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return p.getAsDouble();
        return p.getAsString();
    }

    /** What an action handler gets ({@code ctx.widget}, {@code ctx.value}, {@code ctx.user}); {@code ctx.reject("busy")} refuses it. */
    public static final class ActionContext {
        private final ScriptSections.ActionContext context;
        @Nullable String rejected;

        ActionContext(ScriptSections.ActionContext context) {
            this.context = context;
        }

        public String getMainframeId() {
            return context.mainframeId().toString();
        }

        public String getSection() {
            return context.section();
        }

        public String getWidget() {
            return context.widgetId();
        }

        public @Nullable Object getValue() {
            return plain(context.value());
        }

        /** The paired device's name (who pressed it). */
        public String getUser() {
            return context.user();
        }

        /** Refuses the action; the browser shows the code (letters, digits, underscore, at most 32). */
        public void reject(String code) {
            rejected = code.matches("[a-z0-9_]{1,32}") ? code : "rejected";
        }
    }

    /** A JS function {@code ctx => ...}. */
    @FunctionalInterface
    public interface ActionCallback {
        void onAction(ActionContext context);
    }

    /** Fluent section definition. */
    public static final class SectionBuilder {
        private final UUID mainframeId;
        private final String key;
        private String title;
        private boolean restricted;
        private @Nullable ServerPlayer as;
        private @Nullable ActionCallback callback;
        private final List<ScriptSections.WidgetSpec> widgets = new ArrayList<>();

        SectionBuilder(UUID mainframeId, String key) {
            this.mainframeId = mainframeId;
            this.key = key;
            this.title = key;
        }

        public SectionBuilder title(String title) {
            this.title = title;
            return this;
        }

        /** Only devices that may see restricted sections get it (like the Server card). */
        public SectionBuilder restricted(boolean restricted) {
            this.restricted = restricted;
            return this;
        }

        /** Publish on behalf of a player: refused unless the player has {@code layout.edit} on the mainframe. */
        public SectionBuilder as(ServerPlayer player) {
            this.as = player;
            return this;
        }

        public SectionBuilder onAction(ActionCallback callback) {
            this.callback = callback;
            return this;
        }

        /** Any widget type a script may use: button, toggle, slider, gauge, label, chart, progress, indicator, select, number, text, buttons. */
        public SectionBuilder widget(String type, String id, @Nullable String label, @Nullable Map<?, ?> opts) {
            widgets.add(new ScriptSections.WidgetSpec(type, id, label, normalize(opts)));
            return this;
        }

        public SectionBuilder button(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("button", id, label, opts);
        }

        public SectionBuilder button(String id, String label) {
            return button(id, label, null);
        }

        public SectionBuilder toggle(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("toggle", id, label, opts);
        }

        public SectionBuilder toggle(String id, String label) {
            return toggle(id, label, null);
        }

        public SectionBuilder slider(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("slider", id, label, opts);
        }

        public SectionBuilder gauge(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("gauge", id, label, opts);
        }

        public SectionBuilder gauge(String id, String label) {
            return gauge(id, label, null);
        }

        public SectionBuilder progress(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("progress", id, label, opts);
        }

        public SectionBuilder chart(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("chart", id, label, opts);
        }

        public SectionBuilder indicator(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("indicator", id, label, opts);
        }

        public SectionBuilder indicator(String id, String label) {
            return indicator(id, label, null);
        }

        /** A text value with a caption. */
        public SectionBuilder label(String id, String caption, @Nullable Map<?, ?> opts) {
            return widget("label", id, caption, opts);
        }

        public SectionBuilder label(String id, String caption) {
            return label(id, caption, null);
        }

        public SectionBuilder select(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("select", id, label, opts);
        }

        public SectionBuilder buttons(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("buttons", id, label, opts);
        }

        public SectionBuilder number(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("number", id, label, opts);
        }

        public SectionBuilder text(String id, String label, @Nullable Map<?, ?> opts) {
            return widget("text", id, label, opts);
        }

        /** Validates and shows the section (replacing an earlier one with the same key). Returns the section id. */
        public String publish() {
            if (as != null && !Access.has(as, mainframeId, Perm.LAYOUT_EDIT)) {
                throw new ScriptSections.ScriptException(as.getGameProfile().name() + " may not change this dashboard (layout.edit)");
            }
            var cb = callback;
            ScriptSections.ActionHandler handler = cb == null ? null : ctx -> {
                var context = new ActionContext(ctx);
                cb.onAction(context);
                return context.rejected;
            };
            return ScriptSections.publish(mainframeId, key, title, widgets, restricted, handler).toString();
        }

        /** JS objects arrive as maps with JS numbers; arrays (options) as lists. Numbers become doubles like Lua's. */
        private static @Nullable Map<?, ?> normalize(@Nullable Map<?, ?> opts) {
            if (opts == null) return null;
            var copy = new java.util.LinkedHashMap<Object, Object>();
            for (var e : opts.entrySet()) {
                Object v = e.getValue();
                if (v instanceof List<?> list) {
                    // Lua lists arrive as 1-based maps ({1.0="a", 2.0="b"}); WidgetFactory expects that shape.
                    var table = new java.util.LinkedHashMap<Object, Object>();
                    for (int i = 0; i < list.size(); i++) table.put((double) (i + 1), CCCloudJS.number(list.get(i)));
                    v = table;
                } else {
                    v = CCCloudJS.number(v);
                }
                copy.put(String.valueOf(e.getKey()), v);
            }
            return copy;
        }
    }
}
