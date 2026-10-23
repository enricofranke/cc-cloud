package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.ingame.InGamePayloads;
import ai.ezfox.cccloud.ingame.LabelText;
import ai.ezfox.cccloud.tablet.TabletMenu;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The Cloud Tablet screen: the dashboard drawn with the mod's CC-style widgets. Same sections and widgets as the web
 * (from the hub's snapshot and deltas), the layout's hidden cards/widgets and card order are respected. Actions are
 * browser-format messages ({@code {"t":"action",...}}) – the server validates them exactly like a browser's. Buttons
 * with {@code confirm} need a second click within 3 s; PIN widgets ask for the mainframe PIN first.
 */
public class TabletScreen extends CloudScreen<TabletMenu> {
    private static final int TAB_CARDS = 0;
    private static final int TAB_ALERTS = 1;
    /** Minimum space between a caption and its value. */
    private static final int LABEL_GAP = 8;

    private final Map<String, EditBox> inputs = new HashMap<>();
    private final EditBox pinBox;
    /** An action waiting for its PIN (shown as a row at the top). */
    private @Nullable JsonObject pendingPin;
    private String armedKey = "";
    private long armedUntil;
    private final List<String> myRequests = new ArrayList<>();
    private String lastError = "";
    private long lastErrorUntil;
    private int reqSeq;
    /** Latest search per item_search widget: reqId. */
    private final Map<String, String> searches = new HashMap<>();

    public TabletScreen(TabletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        pinBox = new EditBox(font, 0, 0, 60, 10, Component.translatable("cccloud.gui.pin"));
        pinBox.setMaxLength(8);
        pinBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        editBoxes.add(pinBox);
    }

    @Override
    protected Identifier texture() {
        return CloudGui.UPLINK;
    }

    @Override
    protected int accent() {
        return menu.isAdvanced() ? CloudGui.GOLD : CloudGui.CYAN;
    }

    @Override
    protected Component rightTitle() {
        return Component.literal(menu.mainframeName());
    }

    @Override
    protected List<Component> tabs() {
        int alerts = view.has("alerts") ? view.getAsJsonArray("alerts").size() : 0;
        return List.of(Component.translatable("cccloud.tablet.tab.cards"),
            Component.translatable("cccloud.tablet.tab.alerts").append(alerts > 0 ? " (" + alerts + ")" : ""));
    }

    private boolean canControl() {
        return view.has("canControl") ? bool(view, "canControl") : menu.canControl();
    }

    // ---- rows ------------------------------------------------------------------------------------------------------

    @Override
    protected void buildRows() {
        checkResults();
        if (!bool(view, "ready")) {
            textRow(Component.translatable("cccloud.tablet.connecting"), CloudGui.TEXT_DIM);
            return;
        }
        var mainframe = view.getAsJsonObject("mainframe");
        if (!bool(mainframe, "online") || !bool(mainframe, "enabled")) {
            String reason = str(mainframe, "reason");
            warningRow(Component.translatable(reason.isEmpty() ? "cccloud.tablet.mainframe_offline" : "cccloud.tablet.reason." + reason));
        }
        if (System.currentTimeMillis() < lastErrorUntil) warningRow(Component.translatableWithFallback("cccloud.tablet.result." + lastError, lastError));
        if (pendingPin != null) pinRow();
        if (tab == TAB_ALERTS) {
            alerts();
            return;
        }
        if (!canControl()) textRow(Component.translatable("cccloud.tablet.read_only"), CloudGui.TEXT_DIM);
        var layout = view.get("layout") != null && view.get("layout").isJsonObject() ? view.getAsJsonObject("layout") : new JsonObject();
        var cards = layout.has("cards") ? layout.getAsJsonObject("cards") : new JsonObject();
        var widgetsLayout = layout.has("widgets") ? layout.getAsJsonObject("widgets") : new JsonObject();
        var uplinks = new ArrayList<JsonObject>();
        for (var u : view.getAsJsonArray("uplinks")) uplinks.add(u.getAsJsonObject());
        uplinks.sort(Comparator.comparingInt(u -> cards.has(str(u, "uplinkId")) ? (int) num(cards.getAsJsonObject(str(u, "uplinkId")), "order") : 5000));
        if (uplinks.isEmpty()) textRow(Component.translatable("cccloud.tablet.empty"), CloudGui.TEXT_DIM);
        for (var uplink : uplinks) {
            String uplinkId = str(uplink, "uplinkId");
            var cardLayout = cards.has(uplinkId) ? cards.getAsJsonObject(uplinkId) : new JsonObject();
            if (bool(cardLayout, "hidden")) continue;
            String title = cardLayout.has("title") ? str(cardLayout, "title") : str(uplink, "title");
            // Cached copy of an unloaded section: shown greyed with the time it was last seen, controls disabled.
            boolean loaded = !uplink.has("loaded") || bool(uplink, "loaded");
            boolean online = loaded && bool(uplink, "online");
            rows.add(new Row(16, (c, x, y, w) -> {
                CloudGui.led(c.g(), texture(), x + 2, y + 4, online ? 2 : 0);
                c.text(title.isEmpty() ? Component.translatable("cccloud.tablet.untitled").getString() : title, x + 14, y + 4,
                    loaded ? accent() : CloudGui.TEXT_DIM, w - 16);
            }));
            if (!loaded) {
                long seen = num(uplink, "lastSeen");
                textRow(Component.translatable("cccloud.tablet.cached", seen > 0 ? ago(Math.max(0, (System.currentTimeMillis() - seen) / 1000)) : "?"),
                    CloudGui.TEXT_DIM);
            }
            for (var element : uplink.getAsJsonArray("widgets")) {
                var widget = element.getAsJsonObject();
                var wl = widgetsLayout.get(uplinkId + "/" + str(widget, "id"));
                if (wl != null && wl.isJsonObject() && bool(wl.getAsJsonObject(), "hidden")) continue;
                widget(uplinkId, widget, online);
            }
        }
    }

    private void alerts() {
        var alerts = view.getAsJsonArray("alerts");
        if (alerts.isEmpty()) textRow(Component.translatable("cccloud.tablet.no_alerts"), CloudGui.TEXT_DIM);
        for (var a : alerts) {
            var alert = a.getAsJsonObject();
            String level = str(alert, "level");
            int color = level.equals("crit") ? CloudGui.RED : level.equals("warn") ? CloudGui.YELLOW : CloudGui.TEXT;
            textRow(Component.literal(str(alert, "text")), color);
        }
    }

    private void pinRow() {
        rows.add(new Row(20, (c, x, y, w) -> {
            c.text(Component.translatable("cccloud.gui.pin"), x + 2, y + 6, CloudGui.YELLOW);
            c.edit(pinBox, x + 40, y + 2, 64);
            int bx = c.button(x + 110, y + 2, Component.translatable("cccloud.tablet.ok"), CloudGui.ButtonStyle.ACCENT, !pinBox.getValue().isEmpty(), () -> {
                var action = pendingPin;
                pendingPin = null;
                if (action != null) {
                    action.addProperty("pin", pinBox.getValue());
                    send(action);
                }
                pinBox.setValue("");
                rebuild();
            });
            c.button(x + 114 + bx, y + 2, Component.translatable("cccloud.tablet.cancel"), CloudGui.ButtonStyle.NORMAL, true, () -> {
                pendingPin = null;
                pinBox.setValue("");
                rebuild();
            });
        }));
    }

    private void widget(String uplinkId, JsonObject widget, boolean online) {
        String type = str(widget, "type");
        String id = str(widget, "id");
        String label = str(widget, "label");
        var opts = widget.has("opts") && widget.get("opts").isJsonObject() ? widget.getAsJsonObject("opts") : new JsonObject();
        var value = widget.get("value");
        boolean enabled = online && canControl();
        switch (type) {
            case "label" -> label(label, text(value));
            case "gauge", "progress" -> rows.add(new Row(ROW, (c, x, y, w) -> bar(c, x, y, w, label, value, opts)));
            case "indicator" -> rows.add(new Row(ROW, (c, x, y, w) -> {
                boolean on = value != null && value.isJsonPrimitive() && value.getAsBoolean();
                int color = on ? color(str(opts, "onColor"), CloudGui.GREEN) : color(str(opts, "offColor"), 0xFF505050);
                c.g().fill(x + 4, y + 5, x + 12, y + 13, color);
                c.text(label, x + 16, y + 5, CloudGui.TEXT, w - 20);
            }));
            case "chart" -> rows.add(new Row(48, (c, x, y, w) -> chart(c, x, y, w, label, widget, opts)));
            case "button" -> rows.add(new Row(ROW, (c, x, y, w) -> {
                String key = uplinkId + "/" + id;
                boolean armed = key.equals(armedKey) && System.currentTimeMillis() < armedUntil;
                var text = armed ? Component.translatable("cccloud.tablet.confirm") : Component.literal(label);
                var style = armed || "red".equals(str(opts, "color")) ? CloudGui.ButtonStyle.DANGER : CloudGui.ButtonStyle.NORMAL;
                c.button(x + 4, y + 2, text, style, enabled, () -> act(uplinkId, id, new JsonPrimitive(true), opts));
            }));
            case "toggle" -> rows.add(new Row(ROW, (c, x, y, w) -> {
                boolean on = value != null && value.isJsonPrimitive() && value.getAsBoolean();
                c.toggle(x + 4, y + 3, on, enabled, () -> act(uplinkId, id, new JsonPrimitive(!on), opts));
                c.text(label + (opts.has("pin") ? " 🔒" : ""), x + 32, y + 5, CloudGui.TEXT, w - 36);
            }));
            case "slider" -> rows.add(new Row(ROW, (c, x, y, w) -> {
                double v = number(value), step = opts.has("step") ? opts.get("step").getAsDouble() : 1;
                double min = opts.has("min") ? opts.get("min").getAsDouble() : 0, max = opts.has("max") ? opts.get("max").getAsDouble() : 100;
                c.text(label, x + 4, y + 5, CloudGui.TEXT_DIM, w / 2 - 8);
                int bx = x + w / 2;
                c.button(bx, y + 2, Component.literal("-"), CloudGui.ButtonStyle.NORMAL, enabled && v > min,
                    () -> act(uplinkId, id, Json.number(Math.max(min, v - step)), opts));
                c.text(fmt(v) + " " + str(opts, "unit"), bx + 34, y + 5, CloudGui.TEXT, 60);
                c.button(bx + 96, y + 2, Component.literal("+"), CloudGui.ButtonStyle.NORMAL, enabled && v < max,
                    () -> act(uplinkId, id, Json.number(Math.min(max, v + step)), opts));
            }));
            case "select", "buttons" -> {
                var options = opts.has("options") ? opts.getAsJsonArray("options") : new JsonArray();
                rows.add(new Row(13, (c, x, y, w) -> c.text(label, x + 4, y + 3, CloudGui.TEXT_DIM, w - 8)));
                rows.add(new Row(ROW, (c, x, y, w) -> {
                    int bx = x + 4;
                    for (var o : options) {
                        String option = o.getAsString();
                        boolean current = type.equals("select") && value != null && value.isJsonPrimitive() && option.equals(value.getAsString());
                        var text = Component.literal(fit(option, 80));
                        if (bx + c.buttonWidth(text) > x + w) break;
                        bx += c.button(bx, y + 2, text, current ? CloudGui.ButtonStyle.ACCENT : CloudGui.ButtonStyle.NORMAL, enabled && !current,
                            () -> act(uplinkId, id, new JsonPrimitive(option), opts)) + 3;
                    }
                }));
            }
            case "number", "text" -> {
                var box = input(uplinkId + "/" + id, type.equals("text") ? (opts.has("maxLength") ? opts.get("maxLength").getAsInt() : 64) : 24);
                rows.add(new Row(ROW + 2, (c, x, y, w) -> {
                    c.text(label, x + 4, y + 6, CloudGui.TEXT_DIM, w / 3);
                    c.edit(box, x + w / 3, y + 2, w / 3);
                    if (!box.isFocused() && box.getValue().isEmpty() && value != null && !value.isJsonNull()) box.setValue(text(value));
                    var set = Component.translatable("cccloud.tablet.set");
                    c.button(x + 2 * w / 3 + 4, y + 2, set, CloudGui.ButtonStyle.NORMAL, enabled, () -> {
                        JsonElement v = new JsonPrimitive(box.getValue());
                        if (type.equals("number")) {
                            try {
                                v = Json.number(Double.parseDouble(box.getValue().trim().replace(',', '.')));
                            } catch (NumberFormatException e) {
                                showError("invalid_value");
                                return;
                            }
                        }
                        act(uplinkId, id, v, opts);
                    });
                }));
            }
            case "item_search" -> search(uplinkId, id, label, online);
            case "watchlist", "jobs" -> {
                rows.add(new Row(13, (c, x, y, w) -> c.text(label, x + 4, y + 3, CloudGui.TEXT_DIM, w - 8)));
                var list = value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
                if (list.isEmpty()) rows.add(new Row(12, (c, x, y, w) -> c.text("–", x + 8, y + 2, CloudGui.TEXT_DIM, w)));
                for (var r : list) {
                    var row = r.getAsJsonObject();
                    String name = row.has("displayName") ? str(row, "displayName") : str(row, "name");
                    String right = type.equals("jobs") ? str(row, "status") + " " + Math.round(numberOf(row, "progress") * 100) + "%"
                        : num(row, "count") + " / " + num(row, "min");
                    boolean low = type.equals("watchlist") && num(row, "count") < num(row, "min");
                    rows.add(new Row(12, (c, x, y, w) -> {
                        c.text(name, x + 8, y + 2, low ? CloudGui.YELLOW : CloudGui.TEXT, w - 90);
                        c.text(right, x + w - 80, y + 2, CloudGui.TEXT_DIM, 78);
                    }));
                }
            }
            case "redstone_sides" -> redstone(uplinkId, id, label, value, opts, enabled);
            default -> textRow(Component.literal(label + " (" + type + ")"), CloudGui.TEXT_DIM);
        }
    }

    /**
     * A label widget with the web's rule: long or multi-entry values (e.g. tick time per dimension) get the caption on
     * its own row and the value below (one line per entry, with the TPS a tick time allows); otherwise caption and value
     * share a row and the caption, not the value, is cut with an ellipsis, keeping a minimum gap.
     */
    private void label(String label, String value) {
        if (!LabelText.stacked(label, value)) {
            rows.add(new Row(ROW, (c, x, y, w) -> {
                if (label.isEmpty()) {
                    c.text(value, x + 4, y + 5, CloudGui.TEXT, w - 8);
                    return;
                }
                int inner = w - 8;
                int vw = Math.min(c.font().width(value), inner * 2 / 3);
                int lw = Math.max(0, inner - vw - LABEL_GAP);
                c.text(label, x + 4, y + 5, CloudGui.TEXT_DIM, lw);
                c.text(value, x + 4 + inner - vw, y + 5, CloudGui.TEXT, vw);
            }));
            return;
        }
        rows.add(new Row(12, (c, x, y, w) -> c.text(label, x + 4, y + 3, CloudGui.TEXT_DIM, w - 8)));
        var entries = LabelText.split(value);
        if (entries.size() > 1) {
            for (var e : entries) {
                String right = e.value() + (e.tps() != null ? " · " + e.tps() + " TPS" : "");
                rows.add(new Row(11, (c, x, y, w) -> {
                    int inner = w - 12;
                    int vw = Math.min(c.font().width(right), inner * 2 / 3);
                    c.text(e.name(), x + 10, y + 1, CloudGui.TEXT, Math.max(0, inner - vw - LABEL_GAP));
                    c.text(right, x + 10 + inner - vw, y + 1, CloudGui.TEXT, vw);
                }));
            }
            return;
        }
        var lines = font.split(Component.literal(value), Math.max(40, listW - 30));
        rows.add(new Row(Math.max(1, lines.size()) * 10 + 2, (c, x, y, w) -> {
            int ly = y + 1;
            for (var line : lines) {
                c.g().text(font, line, x + 10, ly, CloudGui.TEXT, false);
                ly += 10;
            }
        }));
    }

    private void bar(Ctx c, int x, int y, int w, String label, @Nullable JsonElement value, JsonObject opts) {
        double min = opts.has("min") ? opts.get("min").getAsDouble() : 0, max = opts.has("max") ? opts.get("max").getAsDouble() : 100;
        double v = number(value);
        int labelW = Math.min(w / 3, 90);
        c.text(label, x + 4, y + 5, CloudGui.TEXT_DIM, labelW - 4);
        int bx = x + labelW, bw = w - labelW - 70;
        c.g().fill(bx, y + 5, bx + bw, y + 13, 0xFF262626);
        double f = max > min ? Math.max(0, Math.min(1, (v - min) / (max - min))) : 0;
        int color = color(str(opts, "color"), CloudGui.CYAN);
        if (opts.has("crit") && v >= opts.get("crit").getAsDouble()) color = CloudGui.RED;
        else if (opts.has("warn") && v >= opts.get("warn").getAsDouble()) color = CloudGui.YELLOW;
        c.g().fill(bx, y + 5, bx + (int) (bw * f), y + 13, color);
        c.text(value == null || value.isJsonNull() ? "–" : fmt(v) + " " + str(opts, "unit"), bx + bw + 4, y + 5, CloudGui.TEXT, 64);
    }

    private void chart(Ctx c, int x, int y, int w, String label, JsonObject widget, JsonObject opts) {
        c.text(label, x + 4, y + 2, CloudGui.TEXT_DIM, w - 70);
        var history = widget.has("history") && widget.get("history").isJsonArray() ? widget.getAsJsonArray("history") : new JsonArray();
        double last = history.isEmpty() ? Double.NaN : history.get(history.size() - 1).getAsDouble();
        c.text(Double.isNaN(last) ? "–" : fmt(last) + " " + str(opts, "unit"), x + w - 66, y + 2, CloudGui.TEXT, 64);
        int cx = x + 4, cy = y + 13, cw = w - 8, ch = 32;
        c.g().fill(cx, cy, cx + cw, cy + ch, 0xFF181818);
        if (history.isEmpty()) return;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (var p : history) {
            min = Math.min(min, p.getAsDouble());
            max = Math.max(max, p.getAsDouble());
        }
        if (opts.has("min")) min = opts.get("min").getAsDouble();
        if (opts.has("max")) max = opts.get("max").getAsDouble();
        if (max <= min) max = min + 1;
        int n = history.size();
        for (int i = 0; i < cw; i++) {
            int index = n == 1 ? 0 : (int) ((long) i * (n - 1) / Math.max(1, cw - 1));
            double f = (history.get(index).getAsDouble() - min) / (max - min);
            int h = (int) Math.round(Math.max(0, Math.min(1, f)) * (ch - 1));
            c.g().fill(cx + i, cy + ch - 1 - h, cx + i + 1, cy + ch, color(str(opts, "color"), CloudGui.CYAN) & 0x80FFFFFF | 0x40000000);
            c.g().fill(cx + i, cy + ch - 1 - h, cx + i + 1, cy + ch - h, color(str(opts, "color"), CloudGui.CYAN));
        }
    }

    private void search(String uplinkId, String id, String label, boolean online) {
        var box = input(uplinkId + "/" + id, 64);
        rows.add(new Row(ROW + 2, (c, x, y, w) -> {
            c.text(label, x + 4, y + 6, CloudGui.TEXT_DIM, w / 3);
            c.edit(box, x + w / 3, y + 2, w / 3);
            c.button(x + 2 * w / 3 + 4, y + 2, Component.translatable("cccloud.tablet.search"), CloudGui.ButtonStyle.NORMAL, online && !box.getValue().isBlank(), () -> {
                var json = new JsonObject();
                json.addProperty("t", "query");
                String reqId = nextReqId();
                json.addProperty("reqId", reqId);
                json.addProperty("uplinkId", uplinkId);
                json.addProperty("id", id);
                json.addProperty("q", box.getValue());
                searches.put(uplinkId + "/" + id, reqId);
                ClientPacketDistributor.sendToServer(new InGamePayloads.TabletRequest(menu.containerId, Json.GSON.toJson(json)));
            });
        }));
        String reqId = searches.get(uplinkId + "/" + id);
        var results = view.has("results") ? view.getAsJsonObject("results") : new JsonObject();
        if (reqId != null && results.has(reqId)) {
            var result = results.getAsJsonObject(reqId);
            if (result.has("error")) {
                textRow(Component.translatableWithFallback("cccloud.tablet.result." + str(result, "error"), str(result, "error")), CloudGui.YELLOW);
            } else {
                for (var r : result.getAsJsonArray("rows")) {
                    var row = r.getAsJsonObject();
                    String name = str(row, "displayName"), count = String.format(Locale.ROOT, "%,d", num(row, "count"));
                    rows.add(new Row(12, (c, x, y, w) -> {
                        c.text(name, x + 8, y + 2, CloudGui.TEXT, w - 80);
                        c.text(count, x + w - 70, y + 2, CloudGui.TEXT_DIM, 68);
                    }));
                }
            }
        }
    }

    private void redstone(String uplinkId, String id, String label, @Nullable JsonElement value, JsonObject opts, boolean enabled) {
        rows.add(new Row(13, (c, x, y, w) -> c.text(label, x + 4, y + 3, CloudGui.TEXT_DIM, w - 8)));
        var sides = value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
        for (var s : sides) {
            var side = s.getAsJsonObject();
            String name = str(side, "side");
            long out = num(side, "out"), in = num(side, "in");
            rows.add(new Row(ROW, (c, x, y, w) -> {
                c.text(Component.translatableWithFallback("cccloud.tablet.side." + name, name).getString(), x + 8, y + 5, CloudGui.TEXT, 60);
                c.toggle(x + 70, y + 3, out > 0, enabled, () -> {
                    var op = new JsonObject();
                    op.addProperty("op", "set_output");
                    op.addProperty("side", name);
                    op.addProperty("value", out > 0 ? 0 : 15);
                    act(uplinkId, id, op, opts);
                });
                c.text("out " + out, x + 100, y + 5, CloudGui.TEXT_DIM, 40);
                c.g().fill(x + 146, y + 6, x + 152, y + 12, in > 0 ? CloudGui.RED : 0xFF404040);
                c.text("in " + in, x + 156, y + 5, CloudGui.TEXT_DIM, 40);
            }));
        }
    }

    // ---- actions ---------------------------------------------------------------------------------------------------

    private void act(String uplinkId, String id, JsonElement value, JsonObject opts) {
        String key = uplinkId + "/" + id;
        if (bool(opts, "confirm") && !(key.equals(armedKey) && System.currentTimeMillis() < armedUntil)) {
            armedKey = key;
            armedUntil = System.currentTimeMillis() + 3000;
            showError("confirm_again");
            return;
        }
        armedKey = "";
        var json = new JsonObject();
        json.addProperty("t", "action");
        json.addProperty("reqId", nextReqId());
        json.addProperty("uplinkId", uplinkId);
        json.addProperty("id", id);
        json.add("value", value);
        if (bool(opts, "pin")) {
            pendingPin = json;
            rebuild();
            return;
        }
        send(json);
    }

    private void send(JsonObject json) {
        myRequests.add(json.get("reqId").getAsString());
        if (myRequests.size() > 32) myRequests.removeFirst();
        ClientPacketDistributor.sendToServer(new InGamePayloads.TabletRequest(menu.containerId, Json.GSON.toJson(json)));
    }

    private String nextReqId() {
        return "tab-" + (++reqSeq);
    }

    /** Shows the error of the latest finished request of this screen. */
    private void checkResults() {
        var results = view.has("results") ? view.getAsJsonObject("results") : new JsonObject();
        for (var reqId : List.copyOf(myRequests)) {
            if (!results.has(reqId)) continue;
            myRequests.remove(reqId);
            var result = results.getAsJsonObject(reqId);
            if (result.has("error")) showError(str(result, "error"));
        }
    }

    private void showError(String code) {
        lastError = code;
        lastErrorUntil = System.currentTimeMillis() + 4000;
        rebuild();
    }

    private void rebuild() {
        int current = tab;
        rows.clear();
        tab = current;
        buildRows();
    }

    private EditBox input(String key, int maxLength) {
        return inputs.computeIfAbsent(key, k -> {
            var box = new EditBox(font, 0, 0, 60, 10, Component.literal(k));
            box.setMaxLength(maxLength);
            editBoxes.add(box);
            addRenderableWidget(box);
            return box;
        });
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static double number(@Nullable JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
    }

    private static double numberOf(JsonObject o, String key) {
        return number(o.get(key));
    }

    private static String text(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return "–";
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return fmt(e.getAsDouble());
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static int color(String name, int fallback) {
        return switch (name) {
            case "green" -> CloudGui.GREEN;
            case "yellow" -> CloudGui.YELLOW;
            case "red" -> CloudGui.RED;
            case "cyan" -> CloudGui.CYAN;
            case "gray" -> 0xFF8C8C8C;
            default -> fallback;
        };
    }
}
