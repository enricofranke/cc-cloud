package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.gui.GuiNetwork;
import ai.ezfox.cccloud.gui.UplinkMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Cloud Uplink screen (also used for the ME/RS Cloud Bridges): status, linked mainframe (position, distance,
 * range), title, widget count, mode, and "unlink" for the owner/trusted players.
 */
public class UplinkScreen extends CloudScreen<UplinkMenu> {
    public UplinkScreen(UplinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected Identifier texture() {
        return CloudGui.UPLINK;
    }

    @Override
    protected int accent() {
        return bool(view, "advanced") ? CloudGui.GOLD : CloudGui.CYAN;
    }

    @Override
    protected Component rightTitle() {
        return Component.empty();
    }

    @Override
    protected void buildRows() {
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
        }));

        if (view.has("network")) {
            String network = str(view, "network");
            kv("cccloud.gui.network", Component.translatable("cccloud.gui.status." + network).getString());
        }

        heading("cccloud.gui.link");
        if (!bool(view, "linked")) {
            textRow(Component.translatable("cccloud.gui.not_linked"), CloudGui.TEXT_DIM);
        } else {
            String name = str(view, "mainframe");
            kv("cccloud.gui.mainframe", (name.isEmpty() ? Component.translatable("cccloud.gui.mainframe_unloaded").getString() : name)
                + " @ " + str(view, "mainframePos"));
            long distance = num(view, "distance");
            long range = num(view, "range");
            String rangeText = distance < 0 ? Component.translatable("cccloud.gui.other_dimension").getString()
                : distance + " / " + (range < 0 ? "∞" : String.valueOf(range));
            kv("cccloud.gui.distance", rangeText);
            textRow(Component.translatable(bool(view, "inRange") ? "cccloud.gui.in_range" : "cccloud.gui.out_of_range"),
                bool(view, "inRange") ? CloudGui.GREEN : CloudGui.RED);
            if (view.has("routeState")) textRow(Component.translatable("cccloud.gui.route_state." + str(view, "routeState")), CloudGui.YELLOW);
            if (view.has("hops")) kv("cccloud.gui.hops", String.valueOf(num(view, "hops")));
            if (bool(view, "canUnlink")) {
                rows.add(new Row(20, (c, x, y, w) -> {
                    var unlink = Component.translatable("cccloud.gui.unlink");
                    c.button(x + w - c.buttonWidth(unlink) - 3, y + 3, unlink, CloudGui.ButtonStyle.DANGER, true,
                        () -> ClientPacketDistributor.sendToServer(new GuiNetwork.UplinkActionPayload(menu.containerId, "unlink")));
                }));
            }
        }

        heading("cccloud.gui.section");
        String title = str(view, "title");
        kv("cccloud.gui.title", title.isEmpty() ? "-" : title);
        kv("cccloud.gui.mode", Component.translatable("cccloud.gui.mode." + (str(view, "mode").isEmpty() ? "lua" : str(view, "mode"))).getString());
        kv("cccloud.gui.widgets", String.valueOf(num(view, "widgets")));
        if (view.has("computers")) kv("cccloud.gui.computers", String.valueOf(num(view, "computers")));
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
