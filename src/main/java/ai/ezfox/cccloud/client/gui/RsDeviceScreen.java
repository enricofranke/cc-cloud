package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.rsdevice.RsDeviceMenu;
import ai.ezfox.cccloud.rsdevice.RsScreens;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The screen of a Cloud Switch / Button / Lamp / Sensor, in plain words: what the device is and does, its state with
 * the controls a hand on the block would have, how it shows on the dashboard (name, group = card, PIN/confirm) and how
 * it is connected. Settings are greyed out for players who may not link devices on its mainframe.
 */
public class RsDeviceScreen extends CloudScreen<RsDeviceMenu> {
    private final EditBox name;
    private final EditBox group;
    private boolean loaded;

    public RsDeviceScreen(RsDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        var font = Minecraft.getInstance().font;
        name = new EditBox(font, 0, 0, 80, 10, Component.translatable("cccloud.gui.rsdevice.name"));
        group = new EditBox(font, 0, 0, 80, 10, Component.translatable("cccloud.gui.rsdevice.group"));
        for (var box : new EditBox[]{name, group}) {
            box.setMaxLength(32);
            box.setBordered(false);
            box.setFilter(s -> s.chars().noneMatch(Character::isISOControl));
            editBoxes.add(box);
        }
    }

    private void send(String action, String arg) {
        ClientPacketDistributor.sendToServer(new RsScreens.ActionPayload(menu.containerId, action, arg));
    }

    @Override
    protected void onEnter(EditBox box) {
        if (box == name) send("name", name.getValue());
        if (box == group) send("group", group.getValue());
    }

    @Override
    protected Identifier texture() {
        return CloudGui.UPLINK;
    }

    @Override
    protected int accent() {
        return CloudGui.CYAN;
    }

    @Override
    protected Component rightTitle() {
        return Component.literal(str(view, "displayName"));
    }

    @Override
    protected void buildRows() {
        if (!loaded && view.has("kind")) {
            name.setValue(str(view, "name"));
            group.setValue(str(view, "group"));
            loaded = true;
        }
        String kind = str(view, "kind");
        statusRow();
        textRow(Component.translatable("cccloud.gui.rsdevice.what." + kind), CloudGui.TEXT_DIM);

        heading("cccloud.gui.rsdevice.control");
        switch (kind) {
            case "switch" -> switchRows();
            case "button" -> buttonRows();
            case "lamp" -> lampRows();
            default -> sensorRows();
        }

        heading("cccloud.gui.rsdevice.dashboard");
        dashboardRows(kind);

        heading("cccloud.gui.link");
        linkRows();
    }

    // ---- status --------------------------------------------------------------------------------------------------------

    private void statusRow() {
        String status = str(view, "status");
        int led = switch (status) {
            case "online" -> 2;
            case "connecting" -> 1;
            default -> 0;
        };
        rows.add(new Row(ROW, (c, x, y, w) -> {
            CloudGui.row(c.g(), x, y, w, ROW, false);
            CloudGui.led(c.g(), texture(), x + 4, y + 5, led);
            var text = Component.translatable("cccloud.gui.status." + (status.isEmpty() ? "offline" : status));
            c.text(text, x + 16, y + 5, led == 2 ? CloudGui.GREEN : led == 1 ? CloudGui.YELLOW : CloudGui.RED);
            if (!bool(view, "linked")) {
                var hint = Component.translatable("cccloud.gui.not_linked");
                c.text(hint.getString(), x + 22 + font.width(text), y + 5, CloudGui.TEXT_DIM, w - 26 - font.width(text));
            }
        }));
    }

    // ---- controls ------------------------------------------------------------------------------------------------------

    /** "Label: value" with buttons on the right. */
    private void valueRow(Component label, String value, int color, String[] buttons, Runnable[] actions, boolean[] enabled) {
        rows.add(new Row(20, (c, x, y, w) -> {
            int bx = x + w - 3;
            for (int i = buttons.length - 1; i >= 0; i--) {
                var b = Component.translatable(buttons[i]);
                bx -= c.buttonWidth(b);
                c.button(bx, y + 3, b, CloudGui.ButtonStyle.NORMAL, enabled[i], actions[i]);
                bx -= 3;
            }
            int lw = font.width(label) + 6;
            c.text(label, x + 2, y + 6, CloudGui.TEXT_DIM);
            c.text(value, x + 2 + lw, y + 6, color, bx - x - lw - 4);
        }));
    }

    private void toggleRow(String key, boolean on, boolean enabled, Runnable action) {
        rows.add(new Row(16, (c, x, y, w) -> {
            c.text(Component.translatable(key), x + 2, y + 4, enabled ? CloudGui.TEXT : CloudGui.TEXT_DIM);
            c.toggle(x + w - 28, y + 2, on, enabled, action);
        }));
    }

    private String onOff(long level) {
        return Component.translatable(level > 0 ? "cccloud.gui.rsdevice.on" : "cccloud.gui.rsdevice.off").getString() + " (" + level + ")";
    }

    private void switchRows() {
        long level = num(view, "level");
        boolean analog = bool(view, "analog");
        boolean manage = bool(view, "canManage");
        int color = level > 0 ? CloudGui.GREEN : CloudGui.TEXT;
        if (analog) {
            valueRow(Component.translatable("cccloud.gui.rsdevice.output"), onOff(level), color,
                new String[]{"cccloud.gui.rsdevice.minus", "cccloud.gui.rsdevice.plus", "cccloud.gui.rsdevice.turn_off", "cccloud.gui.rsdevice.turn_on"},
                new Runnable[]{() -> send("level", String.valueOf(Math.max(0, level - 1))), () -> send("level", String.valueOf(Math.min(15, level + 1))),
                    () -> send("power", "false"), () -> send("power", "true")},
                new boolean[]{level > 0, level < 15, level > 0, level == 0});
        } else {
            valueRow(Component.translatable("cccloud.gui.rsdevice.output"), onOff(level), color,
                new String[]{"cccloud.gui.rsdevice.turn_off", "cccloud.gui.rsdevice.turn_on"},
                new Runnable[]{() -> send("power", "false"), () -> send("power", "true")}, new boolean[]{level > 0, level == 0});
        }
        rows.add(new Row(20, (c, x, y, w) -> {
            var label = Component.translatable("cccloud.gui.rsdevice.mode");
            c.text(label, x + 2, y + 6, CloudGui.TEXT_DIM);
            int bx = x + 8 + font.width(label);
            bx += c.button(bx, y + 3, Component.translatable("cccloud.gui.rsdevice.mode.toggle"), analog ? CloudGui.ButtonStyle.NORMAL : CloudGui.ButtonStyle.ACCENT,
                manage, () -> send("mode", "toggle")) + 3;
            c.button(bx, y + 3, Component.translatable("cccloud.gui.rsdevice.mode.analog"), analog ? CloudGui.ButtonStyle.ACCENT : CloudGui.ButtonStyle.NORMAL,
                manage, () -> send("mode", "analog"));
        }));
        textRow(Component.translatable(analog ? "cccloud.gui.rsdevice.mode.analog_hint" : "cccloud.gui.rsdevice.mode.toggle_hint"), CloudGui.TEXT_DIM);
    }

    private void buttonRows() {
        long level = num(view, "level");
        long pulse = num(view, "pulse");
        boolean manage = bool(view, "canManage");
        valueRow(Component.translatable("cccloud.gui.rsdevice.output"), onOff(level), level > 0 ? CloudGui.GREEN : CloudGui.TEXT,
            new String[]{"cccloud.gui.rsdevice.press"}, new Runnable[]{() -> send("press", "")}, new boolean[]{level == 0});
        valueRow(Component.translatable("cccloud.gui.rsdevice.pulse"),
            Component.translatable("cccloud.gui.rsdevice.pulse_value", pulse, String.format(java.util.Locale.ROOT, "%.2f", pulse / 20.0)).getString(), CloudGui.TEXT,
            new String[]{"cccloud.gui.rsdevice.minus", "cccloud.gui.rsdevice.plus"},
            new Runnable[]{() -> send("pulse", String.valueOf(Math.max(2, pulse - 2))), () -> send("pulse", String.valueOf(Math.min(40, pulse + 2)))},
            new boolean[]{manage && pulse > 2, manage && pulse < 40});
    }

    private void lampRows() {
        long level = num(view, "level");
        boolean on = bool(view, "on");
        long preset = num(view, "preset");
        boolean manage = bool(view, "canManage");
        valueRow(Component.translatable("cccloud.gui.rsdevice.light"), onOff(level), level > 0 ? CloudGui.YELLOW : CloudGui.TEXT,
            new String[]{"cccloud.gui.rsdevice.turn_off", "cccloud.gui.rsdevice.turn_on"},
            new Runnable[]{() -> send("power", "false"), () -> send("power", "true")}, new boolean[]{on, !on});
        valueRow(Component.translatable("cccloud.gui.rsdevice.brightness"), String.valueOf(preset), CloudGui.TEXT,
            new String[]{"cccloud.gui.rsdevice.minus", "cccloud.gui.rsdevice.plus"},
            new Runnable[]{() -> send("level", String.valueOf(Math.max(1, preset - 1))), () -> send("level", String.valueOf(Math.min(15, preset + 1)))},
            new boolean[]{preset > 1, preset < 15});
        toggleRow("cccloud.gui.rsdevice.follow", bool(view, "follow"), manage, () -> send("follow", String.valueOf(!bool(view, "follow"))));
        if (bool(view, "follow")) kv("cccloud.gui.rsdevice.input", String.valueOf(num(view, "input")));
    }

    private void sensorRows() {
        long level = num(view, "level");
        rows.add(new Row(20, (c, x, y, w) -> {
            var label = Component.translatable("cccloud.gui.rsdevice.signal");
            c.text(label, x + 2, y + 6, CloudGui.TEXT_DIM);
            c.text(onOff(level), x + 8 + font.width(label), y + 6, level > 0 ? CloudGui.GREEN : CloudGui.TEXT, w - font.width(label) - 12);
        }));
        textRow(Component.translatable("cccloud.gui.rsdevice.sensor_hint"), CloudGui.TEXT_DIM);
    }

    // ---- dashboard -----------------------------------------------------------------------------------------------------

    private void dashboardRows(String kind) {
        boolean manage = bool(view, "canManage");
        editRow("cccloud.gui.rsdevice.name", name, manage, () -> send("name", name.getValue()));
        editRow("cccloud.gui.rsdevice.group", group, manage, () -> send("group", group.getValue()));
        var groups = view.has("groups") ? view.getAsJsonArray("groups") : new JsonArray();
        if (manage && !groups.isEmpty()) {
            rows.add(new Row(20, (c, x, y, w) -> {
                int bx = x + 2;
                for (var g : groups) {
                    var label = Component.literal(g.getAsString());
                    if (bx + c.buttonWidth(label) > x + w) break;
                    String value = g.getAsString();
                    bx += c.button(bx, y + 3, label, CloudGui.ButtonStyle.NORMAL, true, () -> {
                        group.setValue(value);
                        send("group", value);
                    }) + 3;
                }
            }));
        }
        String card = str(view, "card");
        textRow(card.isEmpty() ? Component.translatable("cccloud.gui.rsdevice.no_card")
            : Component.translatable("cccloud.gui.rsdevice.card", card), card.isEmpty() ? CloudGui.TEXT_DIM : CloudGui.TEXT);
        if (!kind.equals("sensor")) {
            toggleRow("cccloud.gui.rsdevice.pin", bool(view, "pin"), manage, () -> send("pin", String.valueOf(!bool(view, "pin"))));
            if (!(kind.equals("switch") && bool(view, "analog"))) {
                toggleRow("cccloud.gui.rsdevice.confirm", bool(view, "confirm"), manage, () -> send("confirm", String.valueOf(!bool(view, "confirm"))));
            }
        }
        if (!manage) textRow(Component.translatable("cccloud.gui.rsdevice.no_rights"), CloudGui.TEXT_DIM);
    }

    private void editRow(String key, EditBox box, boolean enabled, Runnable save) {
        rows.add(new Row(20, (c, x, y, w) -> {
            var label = Component.translatable(key);
            int lw = Math.max(font.width(label) + 6, 44);
            c.text(label, x + 2, y + 6, CloudGui.TEXT_DIM);
            if (enabled) {
                var set = Component.translatable("cccloud.gui.redstone.set");
                int setW = c.buttonWidth(set);
                c.edit(box, x + 2 + lw, y + 3, Math.max(40, w - lw - setW - 10));
                c.button(x + w - setW - 3, y + 3, set, CloudGui.ButtonStyle.ACCENT, true, save);
            } else {
                c.text(box.getValue().isEmpty() ? "-" : box.getValue(), x + 2 + lw, y + 6, CloudGui.TEXT, w - lw - 4);
            }
        }));
    }

    // ---- connection ----------------------------------------------------------------------------------------------------

    private void linkRows() {
        if (!bool(view, "linked")) {
            textRow(Component.translatable("cccloud.gui.rsdevice.how_to_link"), CloudGui.TEXT_DIM);
            return;
        }
        String mainframe = str(view, "mainframe");
        String where = str(view, "mainframePos");
        kv("cccloud.gui.mainframe", (mainframe.isEmpty() ? Component.translatable("cccloud.gui.mainframe_unloaded").getString() : mainframe)
            + (where.isEmpty() ? "" : " @ " + where));
        long distance = num(view, "distance");
        long range = num(view, "range");
        kv("cccloud.gui.distance", distance < 0 ? Component.translatable("cccloud.gui.other_dimension").getString() : distance + " / " + range);
        textRow(Component.translatable(bool(view, "inRange") ? "cccloud.gui.in_range" : "cccloud.gui.out_of_range"),
            bool(view, "inRange") ? CloudGui.GREEN : CloudGui.RED);
        if (view.has("routeState")) textRow(Component.translatable("cccloud.gui.route_state." + str(view, "routeState")), CloudGui.YELLOW);
        if (view.has("hops")) kv("cccloud.gui.hops", String.valueOf(num(view, "hops")));
        if (bool(view, "canUnlink")) {
            rows.add(new Row(20, (c, x, y, w) -> {
                var unlink = Component.translatable("cccloud.gui.unlink");
                c.button(x + w - c.buttonWidth(unlink) - 3, y + 3, unlink, CloudGui.ButtonStyle.DANGER, true, () -> send("unlink", ""));
            }));
        }
    }

    private void kv(String key, String value) {
        rows.add(new Row(13, (c, x, y, w) -> {
            var label = Component.translatable(key);
            int lw = font.width(label) + 6;
            c.text(label, x + 2, y + 2, CloudGui.TEXT_DIM);
            c.text(value, x + 2 + lw, y + 2, CloudGui.TEXT, w - lw - 4);
        }));
    }
}
