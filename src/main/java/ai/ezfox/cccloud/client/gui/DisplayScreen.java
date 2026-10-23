package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.display.DisplayMenu;
import ai.ezfox.cccloud.ingame.InGamePayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Cloud Display screen: size, linked mainframe and shown section; for admins the section picker (restricted
 * sections are not offered), the "in-world clicks" switch and unlink.
 */
public class DisplayScreen extends CloudScreen<DisplayMenu> {
    public DisplayScreen(DisplayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected Identifier texture() {
        return CloudGui.UPLINK;
    }

    @Override
    protected int accent() {
        return CloudGui.GOLD;
    }

    @Override
    protected Component rightTitle() {
        return Component.literal(str(view, "size"));
    }

    private void send(String action, String arg) {
        ClientPacketDistributor.sendToServer(new InGamePayloads.DisplayAction(menu.containerId, action, arg));
    }

    @Override
    protected void buildRows() {
        if (!bool(view, "linked")) {
            textRow(Component.translatable("cccloud.display.not_linked"), CloudGui.TEXT_DIM);
            return;
        }
        heading("cccloud.gui.link");
        textRow(Component.translatable("cccloud.display.mainframe", str(view, "mainframe")), CloudGui.TEXT);
        String status = str(view, "status");
        String title = str(view, "title");
        textRow(status.equals("ok") ? Component.translatable("cccloud.display.showing", title)
            : Component.translatable("cccloud.display.status." + status), status.equals("ok") ? CloudGui.GREEN : CloudGui.YELLOW);
        if (!bool(view, "canConfigure")) {
            textRow(Component.translatable("cccloud.display.admin_only"), CloudGui.TEXT_DIM);
            return;
        }
        boolean interactive = bool(view, "interactive");
        rows.add(new Row(ROW, (c, x, y, w) -> {
            c.toggle(x + 4, y + 3, interactive, true, () -> send("interactive", String.valueOf(!interactive)));
            c.text(Component.translatable("cccloud.display.interactive"), x + 32, y + 5, CloudGui.TEXT);
        }));
        heading("cccloud.display.sections");
        String current = str(view, "section");
        var sections = view.has("sections") ? view.getAsJsonArray("sections") : new com.google.gson.JsonArray();
        if (sections.isEmpty()) textRow(Component.translatable("cccloud.display.no_sections"), CloudGui.TEXT_DIM);
        for (var s : sections) {
            var section = s.getAsJsonObject();
            String id = str(section, "uplinkId");
            String name = str(section, "title");
            boolean selected = id.equals(current);
            rows.add(new Row(ROW, (c, x, y, w) -> {
                CloudGui.row(c.g(), x, y, w, ROW, false);
                c.text(name.isEmpty() ? Component.translatable("cccloud.tablet.untitled").getString() : name, x + 6, y + 5,
                    selected ? CloudGui.GREEN : CloudGui.TEXT, w - 80);
                var show = Component.translatable(selected ? "cccloud.display.shown" : "cccloud.display.show");
                c.button(x + w - c.buttonWidth(show) - 3, y + 2, show, CloudGui.ButtonStyle.NORMAL, !selected, () -> send("select", id));
            }));
        }
        rows.add(new Row(22, (c, x, y, w) -> {
            var unlink = Component.translatable("cccloud.gui.unlink");
            c.button(x + w - c.buttonWidth(unlink) - 3, y + 4, unlink, CloudGui.ButtonStyle.DANGER, true, () -> send("unlink", ""));
        }));
    }
}
