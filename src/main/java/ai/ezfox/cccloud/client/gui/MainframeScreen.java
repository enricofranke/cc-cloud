package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.gui.GuiNetwork;
import ai.ezfox.cccloud.gui.MainframeMenu;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Cloud Mainframe screen: status + dashboard switch in the header; tabs Overview (URLs, pairing code, approvals,
 * LAN hint), Devices, Trusted and Security (PIN). Players without rights get the read-only overview only.
 */
public class MainframeScreen extends CloudScreen<MainframeMenu> {
    private final EditBox trustName;
    private final EditBox pin;

    public MainframeScreen(MainframeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        trustName = new EditBox(font, 0, 0, 80, 10, Component.translatable("cccloud.gui.player_name"));
        trustName.setBordered(false);
        trustName.setMaxLength(16);
        trustName.setFilter(s -> s.matches("[A-Za-z0-9_]*"));
        trustName.setHint(Component.translatable("cccloud.gui.player_name").withColor(CloudGui.TEXT_DIM));
        pin = new EditBox(font, 0, 0, 80, 10, Component.translatable("cccloud.gui.pin"));
        pin.setBordered(false);
        pin.setMaxLength(8);
        pin.setFilter(s -> s.matches("[0-9]*"));
        pin.addFormatter((text, offset) -> FormattedCharSequence.forward("•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        pin.setHint(Component.translatable("cccloud.gui.pin_hint").withColor(CloudGui.TEXT_DIM));
        editBoxes.add(trustName);
        editBoxes.add(pin);
    }

    @Override
    protected Identifier texture() {
        return CloudGui.MAINFRAME;
    }

    @Override
    protected int accent() {
        return CloudGui.GOLD;
    }

    @Override
    protected Component rightTitle() {
        return Component.literal("@ " + str(view, "pos"));
    }

    private boolean manage() {
        return bool(view, "canManage");
    }

    private void send(String action, String arg) {
        ClientPacketDistributor.sendToServer(new GuiNetwork.MainframeActionPayload(menu.containerId, action, arg));
    }

    // ---- header ------------------------------------------------------------------------------------------------------

    @Override
    protected int headerHeight() {
        return bool(view, "requirePower") ? 26 : 18;
    }

    @Override
    protected void drawHeader(Ctx c, int x, int y, int w) {
        var g = c.g();
        String status = str(view, "status");
        int led = switch (status) {
            case "online" -> 2;
            case "connecting" -> 1;
            case "killed", "no_power" -> 3;
            default -> 0;
        };
        var statusText = Component.translatable("cccloud.gui.status." + (status.isEmpty() ? "offline" : status));
        long clients = num(view, "clients");
        var users = Component.translatable("cccloud.gui.users", clients);
        int badgeW = Math.min(w / 2 + 20, 18 + font.width(statusText) + 6 + font.width(users) + 8);
        CloudGui.badge(g, texture(), x, y, badgeW);
        CloudGui.led(g, texture(), x + 4, y + 3, led);
        int statusColor = led == 2 ? CloudGui.GREEN : led == 1 ? CloudGui.YELLOW : CloudGui.RED;
        g.text(font, statusText, x + 16, y + 3, statusColor, false);
        int ux = x + 16 + font.width(statusText) + 5;
        c.text(users.getString(), ux, y + 3, CloudGui.TEXT_DIM, badgeW - (ux - x) - 4);

        boolean on = bool(view, "enabled");
        var onText = Component.translatable(on ? "cccloud.gui.on" : "cccloud.gui.off");
        int rightX = x + w;
        int onW = font.width(onText);
        g.text(font, onText, rightX - onW, y + 3, on ? CloudGui.CYAN : CloudGui.TEXT_DIM, false);
        int toggleX = rightX - onW - 28;
        c.toggle(toggleX, y + 1, on, manage(), () -> send("toggle_dashboard", ""));
        var label = Component.translatable("cccloud.gui.dashboard");
        int labelX = toggleX - 4 - font.width(label);
        if (labelX > x + badgeW + 4) g.text(font, label, labelX, y + 3, 0xFF303030, false);

        if (bool(view, "requirePower")) {
            // FE buffer bar under the badge: fill, "stored / capacity FE  (usage FE/t)".
            long stored = num(view, "energy"), capacity = Math.max(1, num(view, "energyCapacity")), usage = num(view, "energyUsage");
            int by = y + 17, bw = w;
            g.fill(x, by, x + bw, by + 7, 0xFF1A1A1A);
            int filled = (int) Math.min(bw - 2, (bw - 2) * stored / capacity);
            int color = "no_power".equals(status) ? CloudGui.RED : stored * 5 < capacity ? CloudGui.YELLOW : CloudGui.GREEN;
            g.fill(x + 1, by + 1, x + 1 + filled, by + 6, color);
            String text = String.format("%,d / %,d FE  ·  %,d FE/t", stored, capacity, usage);
            g.pose().pushMatrix();
            g.pose().translate(x + 3, by + 1);
            g.pose().scale(0.6f, 0.6f);
            g.text(font, text, 0, 0, CloudGui.TEXT, true);
            g.pose().popMatrix();
        }
    }

    @Override
    protected List<Component> tabs() {
        if (!manage()) return List.of(Component.translatable("cccloud.gui.tab.overview"), Component.translatable("cccloud.gui.tab.network"));
        return List.of(Component.translatable("cccloud.gui.tab.overview"), Component.translatable("cccloud.gui.tab.devices"),
            Component.translatable("cccloud.gui.tab.trusted"), Component.translatable("cccloud.gui.tab.security"),
            Component.translatable("cccloud.gui.tab.network"));
    }

    // ---- rows --------------------------------------------------------------------------------------------------------

    @Override
    protected void buildRows() {
        if (!manage()) {
            if (tab > 1) tab = 0;
            if (tab == 1) network();
            else overview();
            return;
        }
        switch (tab) {
            case 1 -> devices();
            case 2 -> trusted();
            case 3 -> security();
            case 4 -> network();
            default -> overview();
        }
    }

    // ---- network tab (v0.4) ------------------------------------------------------------------------------------------

    private static int statusColor(String status) {
        return switch (status) {
            case "online" -> CloudGui.GREEN;
            case "connecting" -> CloudGui.YELLOW;
            case "unloaded" -> CloudGui.TEXT_DIM;
            default -> CloudGui.RED;
        };
    }

    private void network() {
        // Power breakdown
        if (view.has("powerBreakdown")) {
            heading("cccloud.gui.power_breakdown");
            var power = view.getAsJsonObject("powerBreakdown");
            for (var key : List.of("base", "uplinks", "advanced_uplinks", "bridges", "clients", "hops", "total")) {
                long value = num(power, key);
                if (value == 0 && !key.equals("total") && !key.equals("base")) continue;
                rows.add(new Row(11, (c, x, y, w) -> {
                    var label = Component.translatable("cccloud.gui.power." + key);
                    c.text(label, x + 4, y + 1, key.equals("total") ? CloudGui.TEXT : CloudGui.TEXT_DIM);
                    String v = String.format("%,d FE/t", value);
                    c.g().text(font, v, x + w - 4 - font.width(v), y + 1, key.equals("total") ? CloudGui.GOLD : CloudGui.TEXT, false);
                }));
            }
        }
        heading("cccloud.gui.network");
        if (!view.has("network")) {
            textRow(Component.translatable("cccloud.gui.network_empty"), CloudGui.TEXT_DIM);
            return;
        }
        var network = view.getAsJsonObject("network");
        var nodes = network.getAsJsonArray("nodes");
        if (nodes.size() <= 1) textRow(Component.translatable("cccloud.gui.network_empty"), CloudGui.TEXT_DIM);
        int noRoute = 0;
        for (var e : nodes) if ("no_route".equals(str(e.getAsJsonObject(), "status"))) noRoute++;
        if (noRoute > 0) warningRow(Component.translatable("cccloud.gui.no_route_hint", noRoute));

        // Mini map (top-down x/z of the mainframe's dimension)
        rows.add(new Row(104, (c, x, y, w) -> miniMap(c, x, y, w, 100, network)));

        // Tree list: children by parent, depth-first from the mainframe.
        var byParent = new java.util.LinkedHashMap<String, List<com.google.gson.JsonObject>>();
        com.google.gson.JsonObject root = null;
        var signals = new java.util.HashMap<String, Double>();
        for (var e : network.getAsJsonArray("links")) {
            var l = e.getAsJsonObject();
            signals.put(str(l, "a") + ">" + str(l, "b"), l.get("signal").getAsDouble());
            signals.put(str(l, "b") + ">" + str(l, "a"), l.get("signal").getAsDouble());
        }
        var orphans = new java.util.ArrayList<com.google.gson.JsonObject>();
        for (var e : nodes) {
            var n = e.getAsJsonObject();
            if ("mainframe".equals(str(n, "type"))) root = n;
            else if (n.has("parent")) byParent.computeIfAbsent(str(n, "parent"), k -> new java.util.ArrayList<>()).add(n);
            else orphans.add(n);
        }
        if (root != null) treeRows(root, 0, byParent, signals);
        for (var n : orphans) treeRow(n, 1, signals);
    }

    private void treeRows(com.google.gson.JsonObject node, int depth, java.util.Map<String, List<com.google.gson.JsonObject>> byParent,
                          java.util.Map<String, Double> signals) {
        if (depth > 9) return;
        treeRow(node, depth, signals);
        for (var child : byParent.getOrDefault(str(node, "id"), List.of())) treeRows(child, depth + 1, byParent, signals);
    }

    private void treeRow(com.google.gson.JsonObject node, int depth, java.util.Map<String, Double> signals) {
        String status = str(node, "status");
        boolean legacy = node.has("flags") && node.get("flags").toString().contains("legacy_out_of_range");
        Double signal = node.has("parent") ? signals.get(str(node, "parent") + ">" + str(node, "id")) : null;
        String type = str(node, "type");
        String name = str(node, "name").isEmpty() ? Component.translatableWithFallback("cccloud.network.node." + type, type).getString() : str(node, "name");
        String distance = "";
        rows.add(new Row(14, (c, x, y, w) -> {
            int ix = x + 2 + depth * 8;
            var g = c.g();
            g.fill(ix, y + 4, ix + 5, y + 9, legacy ? CloudGui.YELLOW : statusColor(status));
            int right = x + w - 2;
            if (signal != null) {
                // Signal bars (4)
                int bars = signal >= 0.75 ? 4 : signal >= 0.5 ? 3 : signal >= 0.15 ? 2 : 1;
                int color = signal < 0.15 ? CloudGui.YELLOW : CloudGui.CYAN;
                for (int i = 0; i < 4; i++) {
                    int bh = 2 + i * 2;
                    g.fill(right - 15 + i * 4, y + 11 - bh, right - 12 + i * 4, y + 11, i < bars ? color : 0xFF333333);
                }
                right -= 19;
            }
            String label = name + (legacy ? " ⚠" : "");
            c.text(label, ix + 8, y + 3, CloudGui.TEXT, right - ix - 10);
        }));
        if (legacy) textRow(Component.translatable("cccloud.gui.legacy"), CloudGui.YELLOW);
    }

    private void miniMap(Ctx c, int x, int y, int w, int h, com.google.gson.JsonObject network) {
        var g = c.g();
        g.fill(x, y, x + w, y + h, 0xFF0A0F12);
        // Grid
        for (int gx = x; gx < x + w; gx += 10) g.fill(gx, y, gx + 1, y + h, 0xFF151D22);
        for (int gy = y; gy < y + h; gy += 10) g.fill(x, gy, x + w, gy + 1, 0xFF151D22);
        String dim = null;
        var points = new java.util.HashMap<String, int[]>();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        var nodes = network.getAsJsonArray("nodes");
        for (var e : nodes) {
            var n = e.getAsJsonObject();
            if ("mainframe".equals(str(n, "type"))) dim = str(n, "dim");
        }
        for (var e : nodes) {
            var n = e.getAsJsonObject();
            if (!n.has("pos") || !str(n, "dim").equals(dim)) continue;
            var p = n.getAsJsonObject("pos");
            minX = Math.min(minX, p.get("x").getAsDouble());
            maxX = Math.max(maxX, p.get("x").getAsDouble());
            minZ = Math.min(minZ, p.get("z").getAsDouble());
            maxZ = Math.max(maxZ, p.get("z").getAsDouble());
        }
        if (minX == Double.MAX_VALUE) return;
        double span = Math.max(16, Math.max(maxX - minX, maxZ - minZ));
        double scale = Math.min(w - 12, h - 12) / span;
        double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        for (var e : nodes) {
            var n = e.getAsJsonObject();
            if (!n.has("pos") || !str(n, "dim").equals(dim)) continue;
            var p = n.getAsJsonObject("pos");
            int px = x + w / 2 + (int) Math.round((p.get("x").getAsDouble() - cx) * scale);
            int pz = y + h / 2 + (int) Math.round((p.get("z").getAsDouble() - cz) * scale);
            points.put(str(n, "id"), new int[]{px, pz, statusColor(str(n, "status")), "mainframe".equals(str(n, "type")) ? 1 : 0});
        }
        for (var e : network.getAsJsonArray("links")) {
            var l = e.getAsJsonObject();
            var a = points.get(str(l, "a"));
            var b = points.get(str(l, "b"));
            if (a == null || b == null) continue;
            int color = "ender".equals(str(l, "kind")) ? 0xFF9C5BD6 : l.get("signal").getAsDouble() < 0.15 ? 0xFF8A7A20 : 0xFF2C6E7A;
            int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int i = 0; i <= steps; i += 2) {
                int px = a[0] + (b[0] - a[0]) * i / Math.max(1, steps);
                int pz = a[1] + (b[1] - a[1]) * i / Math.max(1, steps);
                g.fill(px, pz, px + 1, pz + 1, color);
            }
        }
        for (var p : points.values()) {
            int r = p[3] == 1 ? 2 : 1;
            g.fill(p[0] - r, p[1] - r, p[0] + r + 1, p[1] + r + 1, p[3] == 1 ? CloudGui.GOLD : p[2]);
        }
        String scaleText = Math.round(span) + " m";
        g.text(font, scaleText, x + w - 3 - font.width(scaleText), y + h - 10, CloudGui.TEXT_DIM, false);
    }

    private void overview() {
        heading("cccloud.gui.dashboard_urls");
        var urls = view.has("urls") ? view.getAsJsonArray("urls") : new com.google.gson.JsonArray();
        if (!bool(view, "webRunning") || urls.isEmpty()) {
            warningRow(Component.translatable("cccloud.gui.web_not_running"));
        }
        for (var e : urls) {
            String url = e.getAsString();
            rows.add(new Row(ROW, (c, x, y, w) -> {
                CloudGui.row(c.g(), x, y, w, ROW, c.hover(x, y, w, ROW));
                c.text(url, x + 4, y + 5, CloudGui.TEXT, w - 26);
                c.iconButton(x + w - 16, y + 2, 0, () -> {
                    minecraft.keyboardHandler.setClipboard(url);
                    showLocalNotice("copied");
                });
            }));
        }
        if (!bool(view, "cloudAllowed")) warningRow(Component.translatable("cccloud.gui.cloud_disabled"));
        if (!bool(view, "internet")) warningRow(Component.translatable("cccloud.gui.lan_only"));
        else if (!bool(view, "tls")) warningRow(Component.translatable("cccloud.gui.no_tls"));

        if (view.has("iconsTotal")) {
            long done = num(view, "iconsDone"), total = num(view, "iconsTotal");
            textRow(Component.translatable("cccloud.gui.icons", String.format("%,d", done), String.format("%,d", total)),
                done >= total ? CloudGui.TEXT_DIM : CloudGui.CYAN);
        }
        if (!manage()) {
            textRow(Component.translatable("cccloud.gui.readonly_hint"), CloudGui.TEXT_DIM);
            return;
        }
        heading("cccloud.gui.pairing_code");
        String code = str(view, "code");
        long codeSeconds = num(view, "codeSeconds");
        rows.add(new Row(24, (c, x, y, w) -> {
            CloudGui.row(c.g(), x, y, w, 24, false);
            var g = c.g();
            var newCode = Component.translatable("cccloud.gui.new_code");
            int bw = c.buttonWidth(newCode);
            int codeW = font.width(code) * 2;
            boolean big = codeW + 50 + bw < w;
            if (big) {
                g.pose().pushMatrix();
                g.pose().translate(x + 5, y + 4);
                g.pose().scale(2, 2);
                g.text(font, code, 0, 0, CloudGui.CYAN, false);
                g.pose().popMatrix();
            } else {
                g.text(font, code, x + 5, y + 8, CloudGui.CYAN, false);
            }
            long left = Math.max(0, codeSeconds - (System.currentTimeMillis() - viewTime) / 1000);
            String time = String.format("%d:%02d", left / 60, left % 60);
            int tx = x + 5 + (big ? codeW : font.width(code)) + 8;
            if (tx + font.width(time) < x + w - bw - 6) g.text(font, time, tx, y + 8, CloudGui.TEXT_DIM, false);
            c.button(x + w - bw - 3, y + 5, newCode, CloudGui.ButtonStyle.NORMAL, true, () -> send("new_code", ""));
        }));
        textRow(Component.translatable("cccloud.gui.pairing_hint"), CloudGui.TEXT_DIM);

        heading("cccloud.gui.pending");
        var pending = view.has("pending") ? view.getAsJsonArray("pending") : new com.google.gson.JsonArray();
        if (pending.isEmpty()) textRow(Component.translatable("cccloud.gui.no_pending"), CloudGui.TEXT_DIM);
        for (var e : pending) {
            var p = e.getAsJsonObject();
            String id = str(p, "id");
            rows.add(new Row(ROW, (c, x, y, w) -> {
                CloudGui.row(c.g(), x, y, w, ROW, false);
                var allow = Component.translatable("cccloud.gui.allow");
                var deny = Component.translatable("cccloud.gui.deny");
                int buttons = c.buttonWidth(allow) + c.buttonWidth(deny) + 6;
                String name = str(p, "name");
                c.text(name, x + 4, y + 5, CloudGui.TEXT, w - buttons - 10);
                int nameW = Math.min(font.width(name), w - buttons - 10);
                c.text(str(p, "ip"), x + 8 + nameW, y + 5, CloudGui.TEXT_DIM, w - buttons - 14 - nameW);
                int bx = x + w - buttons;
                bx += c.button(bx, y + 2, allow, CloudGui.ButtonStyle.ACCENT, true, () -> send("approve", id)) + 3;
                c.button(bx, y + 2, deny, CloudGui.ButtonStyle.DANGER, true, () -> send("deny", id));
            }));
        }
    }

    private void devices() {
        var devices = view.has("devices") ? view.getAsJsonArray("devices") : new com.google.gson.JsonArray();
        heading("cccloud.gui.devices");
        if (devices.isEmpty()) textRow(Component.translatable("cccloud.gui.no_devices"), CloudGui.TEXT_DIM);
        for (var e : devices) {
            var d = e.getAsJsonObject();
            String id = str(d, "id");
            String detail = str(d, "player") + " · " + Component.translatable("cccloud.gui.last_seen", ago(num(d, "lastUsedAgo"))).getString()
                + (bool(d, "control") ? "" : " · " + Component.translatable("cccloud.gui.read_only").getString());
            rows.add(new Row(24, (c, x, y, w) -> {
                CloudGui.row(c.g(), x, y, w, 24, c.hover(x, y, w, 24));
                var revoke = Component.translatable("cccloud.gui.revoke");
                int bw = c.buttonWidth(revoke);
                c.text(str(d, "name"), x + 4, y + 3, CloudGui.TEXT, w - bw - 12);
                c.text(detail, x + 4, y + 13, CloudGui.TEXT_DIM, w - bw - 12);
                c.button(x + w - bw - 3, y + 5, revoke, CloudGui.ButtonStyle.DANGER, true, () -> send("revoke", id));
            }));
        }
        if (!devices.isEmpty()) {
            rows.add(new Row(20, (c, x, y, w) -> {
                var all = Component.translatable("cccloud.gui.revoke_all");
                c.button(x + w - c.buttonWidth(all) - 3, y + 3, all, CloudGui.ButtonStyle.DANGER, true, () -> send("revoke_all", ""));
            }));
        }
    }

    private void trusted() {
        String owner = str(view, "owner");
        rows.add(new Row(14, (c, x, y, w) -> {
            var label = Component.translatable("cccloud.gui.owner");
            c.text(label, x + 2, y + 3, CloudGui.GOLD);
            c.text(owner.isEmpty() ? "-" : owner, x + 6 + font.width(label), y + 3, CloudGui.TEXT, w - 10 - font.width(label));
        }));
        heading("cccloud.gui.trusted_players");
        var trusted = view.has("trusted") ? view.getAsJsonArray("trusted") : new com.google.gson.JsonArray();
        if (trusted.isEmpty()) textRow(Component.translatable("cccloud.gui.no_trusted"), CloudGui.TEXT_DIM);
        for (var e : trusted) {
            var t = e.getAsJsonObject();
            String uuid = str(t, "uuid");
            rows.add(new Row(ROW, (c, x, y, w) -> {
                CloudGui.row(c.g(), x, y, w, ROW, c.hover(x, y, w, ROW));
                var remove = Component.translatable("cccloud.gui.remove");
                int bw = c.buttonWidth(remove);
                c.text(str(t, "name"), x + 4, y + 5, CloudGui.TEXT, w - bw - 12);
                c.button(x + w - bw - 3, y + 2, remove, CloudGui.ButtonStyle.DANGER, true, () -> send("untrust", uuid));
            }));
        }
        rows.add(new Row(20, (c, x, y, w) -> {
            var add = Component.translatable("cccloud.gui.add");
            int bw = c.buttonWidth(add);
            c.edit(trustName, x + 2, y + 3, w - bw - 8);
            c.button(x + w - bw - 3, y + 3, add, CloudGui.ButtonStyle.ACCENT, !trustName.getValue().isEmpty(), this::addTrusted);
        }));
    }

    private void addTrusted() {
        if (trustName.getValue().isEmpty()) return;
        send("trust", trustName.getValue());
        trustName.setValue("");
    }

    private void security() {
        heading("cccloud.gui.pin_title");
        boolean pinSet = bool(view, "pinSet");
        textRow(Component.translatable(pinSet ? "cccloud.gui.pin_set" : "cccloud.gui.pin_not_set"), pinSet ? CloudGui.GREEN : CloudGui.TEXT_DIM);
        rows.add(new Row(20, (c, x, y, w) -> {
            var set = Component.translatable(pinSet ? "cccloud.gui.pin_change" : "cccloud.gui.pin_set_button");
            int bw = c.buttonWidth(set);
            c.edit(pin, x + 2, y + 3, w - bw - 8);
            boolean valid = pin.getValue().length() >= 4;
            c.button(x + w - bw - 3, y + 3, set, CloudGui.ButtonStyle.ACCENT, valid, this::setPin);
        }));
        if (pinSet) {
            rows.add(new Row(20, (c, x, y, w) -> {
                var clear = Component.translatable("cccloud.gui.pin_clear");
                c.button(x + w - c.buttonWidth(clear) - 3, y + 3, clear, CloudGui.ButtonStyle.DANGER, true, () -> send("pin_clear", ""));
            }));
        }
        textRow(Component.translatable("cccloud.gui.pin_explain"), CloudGui.TEXT_DIM);
        heading("cccloud.gui.access");
        if (!bool(view, "internet")) warningRow(Component.translatable("cccloud.gui.lan_only"));
        else textRow(Component.translatable("cccloud.gui.internet_allowed"), CloudGui.TEXT);
        textRow(Component.translatable(bool(view, "tls") ? "cccloud.gui.tls_on" : "cccloud.gui.tls_off"), bool(view, "tls") ? CloudGui.GREEN : CloudGui.TEXT_DIM);
    }

    private void setPin() {
        if (pin.getValue().length() < 4) return;
        send("pin_set", pin.getValue());
        pin.setValue("");
    }

    @Override
    protected void onEnter(EditBox box) {
        if (box == trustName) addTrusted();
        else if (box == pin) setPin();
    }

    private void showLocalNotice(String code) {
        var json = view.deepCopy();
        json.addProperty("notice", code);
        menu.setView(json.toString());
    }
}
