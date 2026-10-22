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
        return 18;
    }

    @Override
    protected void drawHeader(Ctx c, int x, int y, int w) {
        var g = c.g();
        String status = str(view, "status");
        int led = switch (status) {
            case "online" -> 2;
            case "connecting" -> 1;
            case "killed" -> 3;
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
    }

    @Override
    protected List<Component> tabs() {
        if (!manage()) return List.of();
        return List.of(Component.translatable("cccloud.gui.tab.overview"), Component.translatable("cccloud.gui.tab.devices"),
            Component.translatable("cccloud.gui.tab.trusted"), Component.translatable("cccloud.gui.tab.security"));
    }

    // ---- rows --------------------------------------------------------------------------------------------------------

    @Override
    protected void buildRows() {
        if (!manage()) tab = 0;
        switch (tab) {
            case 1 -> devices();
            case 2 -> trusted();
            case 3 -> security();
            default -> overview();
        }
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
