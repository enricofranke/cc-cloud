package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.gui.CardPreview;
import ai.ezfox.cccloud.gui.GuiNetwork;
import ai.ezfox.cccloud.gui.UplinkMenu;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The screen of a Cloud Uplink, a Cloud Bridge and a Cloud Relay, answering in plain words first: what the block is
 * connected to and whether it is OK (header), the card it puts on the dashboard with a live preview, the connection to
 * the mainframe (signal, relays, a one-line fix for every problem), the actions (rename the card, open it on the
 * dashboard, unlink), and the technical details under a collapsed "Advanced" part (wireless redstone, ids, numbers).
 */
public class UplinkScreen extends CloudScreen<UplinkMenu> {
    private static final String PREF_ADVANCED = "uplinkAdvanced";
    private static final long CONFIRM_MS = 4000;
    /** Below this signal (15 %) a radio link is weak (NetworkGraph.WEAK). */
    private static final double WEAK = 0.15;

    private final EditBox channel;
    private final EditBox titleBox;
    private boolean channelLoaded;
    private boolean renaming;
    private long unlinkArmedUntil;
    private boolean advancedOpen;

    public UplinkScreen(UplinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        channel = new EditBox(font, 0, 0, 80, 10, Component.translatable("cccloud.gui.redstone.channel"));
        channel.setMaxLength(32);
        channel.setBordered(false);
        channel.setFilter(s -> s.matches("[A-Za-z0-9_.-]*"));
        editBoxes.add(channel);
        titleBox = new EditBox(font, 0, 0, 80, 10, Component.translatable("cccloud.gui.card.rename"));
        titleBox.setMaxLength(48);
        titleBox.setBordered(false);
        titleBox.setFilter(s -> s.chars().noneMatch(Character::isISOControl));
        editBoxes.add(titleBox);
        advancedOpen = ScreenPrefs.flag(PREF_ADVANCED, false);
    }

    /** Opens or closes the "Advanced" part (remembered per player on this client). */
    public void setAdvancedOpen(boolean open) {
        advancedOpen = open;
        ScreenPrefs.setFlag(PREF_ADVANCED, open);
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        buildRows();
    }

    private void send(String action, String arg) {
        ClientPacketDistributor.sendToServer(new GuiNetwork.UplinkActionPayload(menu.containerId, action, arg));
    }

    private void sendRedstone(String mode) {
        send("redstone", mode + ":" + channel.getValue());
    }

    private String kind() {
        String kind = str(view, "kind");
        return kind.isEmpty() ? "uplink" : kind;
    }

    @Override
    protected void onEnter(EditBox box) {
        if (box == channel) {
            var rs = view.has("redstone") ? view.getAsJsonObject("redstone") : new JsonObject();
            String mode = str(rs, "mode");
            sendRedstone(mode.isEmpty() || mode.equals("off") ? "receive" : mode);
        } else if (box == titleBox) {
            saveTitle();
        }
    }

    private void saveTitle() {
        send("rename", titleBox.getValue().strip());
        renaming = false;
        setFocused(null);
        rebuild();
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

    // ---- header: what it is, is it OK -----------------------------------------------------------------------------

    @Override
    protected int headerHeight() {
        return 30;
    }

    private static net.minecraft.world.level.block.Block block(String id) {
        var key = Identifier.tryParse(id);
        return key == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getValue(key);
    }

    private static String modName(String namespace) {
        if (namespace.equals("minecraft")) return "";
        return net.neoforged.fml.ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    private JsonObject attached() {
        return view.get("attached") instanceof JsonObject a ? a : new JsonObject();
    }

    /** The icon in the header: the attached machine/computer, else the block itself. */
    private ItemStack icon() {
        String id = kind().equals("uplink") && attached().has("block") ? str(attached(), "block") : str(view, "block");
        var b = block(id);
        if (b == Blocks.AIR || b.asItem() == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(b.asItem());
    }

    /** "Connected to:" / "Reads:" and the name (with the mod in brackets). */
    private Component[] what() {
        switch (kind()) {
            case "bridge" -> {
                String storage = str(view, "storage");
                String mod = modName(storage.equals("rs") ? "refinedstorage" : "ae2");
                var name = Component.translatable("cccloud.gui.what.storage." + (storage.equals("rs") ? "rs" : "me")).getString();
                return new Component[]{Component.translatable("cccloud.gui.what.reads"), Component.literal(withMod(name, mod))};
            }
            case "relay" -> {
                return new Component[]{Component.empty(), Component.translatable("cccloud.gui.what.relay")};
            }
            default -> {
                var a = attached();
                boolean probe = bool(view, "probe");
                if (!a.has("block")) {
                    return new Component[]{Component.empty(), Component.translatable(probe ? "cccloud.gui.what.probe_none" : "cccloud.gui.what.nothing")};
                }
                var b = block(str(a, "block"));
                String mod = modName(Identifier.tryParse(str(a, "block")) instanceof Identifier key ? key.getNamespace() : "");
                String name;
                if (a.has("computer")) {
                    // "Computer #7" says it all: no mod name
                    name = a.has("label") ? Component.translatable("cccloud.gui.what.computer_label", num(a, "computer"), str(a, "label")).getString()
                        : Component.translatable("cccloud.gui.what.computer", num(a, "computer")).getString();
                    mod = "";
                } else {
                    name = b.getName().getString();
                }
                return new Component[]{Component.translatable(probe ? "cccloud.gui.what.reads_remote" : "cccloud.gui.what.connected"),
                    Component.literal(withMod(name, mod))};
            }
        }
    }

    private static String withMod(String name, String mod) {
        return mod.isEmpty() || mod.equalsIgnoreCase(name) ? name : name + " (" + mod + ")";
    }

    /** LED (0 off, 1 connecting, 2 online, 3 killed), colour and the one-line state. */
    private record State(int led, int color, Component text) {
    }

    private State state() {
        if (kind().equals("relay")) {
            if (!bool(view, "powered")) return new State(3, CloudGui.RED, Component.translatable("cccloud.gui.state.relay_no_power"));
            long networks = view.get("networks") instanceof JsonArray a ? a.size() + num(view, "otherNetworks") : num(view, "otherNetworks");
            if (networks > 0) return new State(2, CloudGui.GREEN, Component.translatable("cccloud.gui.state.relay_working", networks));
            return new State(1, CloudGui.YELLOW, Component.translatable("cccloud.gui.state.relay_idle"));
        }
        if (!bool(view, "linked")) return new State(0, CloudGui.TEXT_DIM, Component.translatable("cccloud.gui.state.not_linked"));
        if (view.has("claim")) return new State(0, CloudGui.RED, Component.translatable("cccloud.gui.state.claim_blocked"));
        String route = str(view, "routeState");
        if (route.equals("over_capacity")) return new State(1, CloudGui.YELLOW, Component.translatable("cccloud.gui.state.over_capacity"));
        if (route.equals("no_route")) return new State(0, CloudGui.RED, Component.translatable("cccloud.gui.state.no_route"));
        String mainframe = str(view, "mainframeState");
        if (!mainframe.isEmpty() && !mainframe.equals("ok")) {
            return new State(mainframe.equals("killed") || mainframe.equals("no_power") ? 3 : 0, CloudGui.RED,
                Component.translatable("cccloud.gui.state.mainframe." + mainframe));
        }
        boolean weak = view.has("signal") && !view.has("cable") && view.get("signal").getAsDouble() < WEAK;
        return switch (str(view, "status")) {
            case "online" -> weak ? new State(1, CloudGui.YELLOW, Component.translatable("cccloud.gui.state.weak"))
                : new State(2, CloudGui.GREEN, Component.translatable("cccloud.gui.state.online"));
            case "connecting" -> new State(1, CloudGui.YELLOW, Component.translatable("cccloud.gui.state.connecting"));
            default -> new State(0, CloudGui.RED, Component.translatable("cccloud.gui.state.offline"));
        };
    }

    @Override
    protected void drawHeader(Ctx c, int x, int y, int w) {
        var g = c.g();
        CloudGui.slot(g, texture(), x, y + 4, 0);
        var icon = icon();
        if (!icon.isEmpty()) g.fakeItem(icon, x + 1, y + 5);
        int tx = x + 22, tw = w - 22;
        var what = what();
        int prefixW = what[0].getString().isEmpty() ? 0 : font.width(what[0]) + 4;
        if (prefixW > 0) g.text(font, what[0], tx, y + 2, 0xFF404040, false);
        c.text(what[1].getString(), tx + prefixW, y + 2, CloudGui.TEXT_DARK, tw - prefixW);
        var s = state();
        String text = fit(s.text().getString(), tw - 22);
        int bw = Math.min(tw, 22 + font.width(text));
        CloudGui.badge(g, texture(), tx, y + 13, bw);
        CloudGui.led(g, texture(), tx + 4, y + 16, s.led());
        g.text(font, text, tx + 16, y + 16, s.color(), false);
    }

    // ---- rows --------------------------------------------------------------------------------------------------------

    @Override
    protected void buildRows() {
        if (kind().equals("relay")) {
            relayRows();
        } else {
            // A problem and its fix come first.
            if (view.get("claim") instanceof JsonObject claim) {
                // claim guard (docs/SECURITY.md "Claims"): the team's name only with view.positions
                String team = str(claim, "team");
                warningRow(team.isEmpty() ? Component.translatable("cccloud.gui.claim.blocked_other") : Component.translatable("cccloud.gui.claim.blocked", team));
                textRow(Component.translatable("cccloud.gui.claim.hint"), CloudGui.TEXT_DIM);
            }
            var hint = hint();
            if (hint != null) warningRow(hint);
            if (view.has("exposedElsewhere")) {
                warningRow(Component.translatable("cccloud.gui.hint.side_exposes_nothing", face(str(view, "exposedElsewhere"))));
            }
            if (bool(view, "probe")) probeRows();
            cardRows();
            connectionRows();
            actionRows();
        }
        advancedRows();
    }

    /** Cloud Probe: the machine it reads (distance, face) or how to pick one, and a button to forget it. */
    private void probeRows() {
        heading("cccloud.gui.probe.heading");
        var remote = view.get("remote") instanceof JsonObject r ? r : new JsonObject();
        if (!remote.has("distance")) {
            textRow(Component.translatable("cccloud.gui.probe.none", num(remote, "range")), CloudGui.TEXT_DIM);
            return;
        }
        String name = remote.has("block") ? block(str(remote, "block")).getName().getString() : Component.translatable("cccloud.gui.probe.unloaded").getString();
        textRow(Component.translatable("cccloud.gui.probe.reads", name, num(remote, "distance"), face(str(remote, "side"))), CloudGui.TEXT);
        if (remote.has("pos")) textRow(Component.translatable("cccloud.gui.probe.at", str(remote, "pos")), CloudGui.TEXT_DIM);
        boolean can = bool(view, "canClearTarget");
        rows.add(new Row(20, (c, x, y, w) ->
            c.button(x + 2, y + 3, Component.translatable("cccloud.gui.probe.clear"), CloudGui.ButtonStyle.NORMAL, can, () -> send("clear_target", ""))));
    }

    /** A block face in words ("top", "north", …). */
    private static Component face(String side) {
        return Component.translatableWithFallback("cccloud.gui.face." + side, side);
    }

    /** "On the dashboard": what kind of card, what it shows and controls, and the live preview. */
    private void cardRows() {
        heading(bool(view, "linked") ? "cccloud.gui.card.heading" : "cccloud.gui.card.heading_unlinked");
        var preview = view.get("preview") instanceof JsonObject p ? p : new JsonObject();
        textRow(Component.translatable(cardKey()), CloudGui.TEXT);
        // What it shows is in the preview below; what a player can do with it is said in words.
        var summary = CardPreview.summary(preview, 4);
        if (!summary.controls().isEmpty()) textRow(list("cccloud.gui.card.controls", summary.controls(), summary.moreControls()), CloudGui.TEXT_DIM);
        if (kind().equals("bridge") && !"online".equals(str(view, "network"))) {
            warningRow(Component.translatable("cccloud.gui.card.no_storage_network." + ("rs".equals(str(view, "storage")) ? "rs" : "me")));
        }
        if (!"none".equals(str(view, "cardKind"))) {
            String title = str(view, "title");
            int h = CardPainter.height(preview) + 4;
            rows.add(new Row(h, (c, x, y, w) -> CardPainter.draw(c.g(), font, x + 2, y + 1, Math.min(w - 4, 260), title, CloudGui.GOLD, preview)));
            if (bool(view, "customTitle")) {
                textRow(Component.translatable("cccloud.gui.card.custom_title", str(view, "defaultTitle")), CloudGui.TEXT_DIM);
            }
        }
    }

    private String cardKey() {
        return switch (str(view, "cardKind")) {
            case "device" -> "cccloud.gui.card.kind.device";
            case "storage" -> "cccloud.gui.card.kind.storage";
            case "auto" -> "cccloud.gui.card.kind.auto";
            case "lua" -> attached().has("computer") ? "cccloud.gui.card.kind.lua_here" : "cccloud.gui.card.kind.lua";
            default -> "cccloud.gui.card.kind.none";
        };
    }

    private static Component list(String key, java.util.List<String> items, int more) {
        String joined = String.join(", ", items);
        if (more > 0) joined += " " + Component.translatable("cccloud.gui.card.and_more", more).getString();
        return Component.translatable(key, joined);
    }

    /** "Connection": the mainframe in plain words and a signal bar (the fix for a problem is shown on top). */
    private void connectionRows() {
        if (!bool(view, "linked")) return;
        heading("cccloud.gui.conn.heading");
        String name = str(view, "mainframe");
        textRow(name.isEmpty() ? Component.translatable("cccloud.gui.conn.linked_unloaded") : Component.translatable("cccloud.gui.conn.linked_to", name), CloudGui.TEXT);
        if (view.has("signal") || view.has("cable")) signalRow();
    }

    private void signalRow() {
        double signal = view.has("signal") ? view.get("signal").getAsDouble() : 1;
        String cable = str(view, "cable");
        long relays = num(view, "relays");
        String how = relays > 0 ? Component.translatable(relays == 1 ? "cccloud.gui.conn.via_relay" : "cccloud.gui.conn.via_relays", relays).getString()
            : Component.translatable("cccloud.gui.conn.direct").getString();
        rows.add(new Row(13, (c, x, y, w) -> {
            var g = c.g();
            var label = Component.translatable("cccloud.gui.conn.signal");
            c.text(label, x + 2, y + 2, CloudGui.TEXT_DIM);
            int bx = x + 6 + font.width(label);
            String text;
            if (!cable.isEmpty()) {
                int color = cable.equals("fiber") ? 0xFF4FE3F0 : 0xFFA0A8AC;
                g.fill(bx, y + 4, bx + 14, y + 6, color);
                g.fill(bx - 2, y + 2, bx, y + 8, color);
                g.fill(bx + 14, y + 2, bx + 16, y + 8, color);
                text = Component.translatable("cccloud.gui.conn." + cable).getString() + " · " + how;
            } else {
                int bars = signal >= 0.75 ? 4 : signal >= 0.5 ? 3 : signal >= WEAK ? 2 : signal > 0 ? 1 : 0;
                int color = signal < WEAK ? CloudGui.YELLOW : CloudGui.CYAN;
                for (int i = 0; i < 4; i++) {
                    int bh = 2 + i * 2;
                    g.fill(bx + i * 4, y + 10 - bh, bx + i * 4 + 3, y + 10, i < bars ? color : 0xFF333333);
                }
                text = Math.round(signal * 100) + " % · " + how;
            }
            c.text(text, bx + 20, y + 2, signal < WEAK && cable.isEmpty() ? CloudGui.YELLOW : CloudGui.TEXT, w - (bx + 20 - x) - 2);
        }));
    }

    /** One line that says what is wrong and how to fix it, or null. */
    private Component hint() {
        if (!bool(view, "linked")) return Component.translatable(kind().equals("bridge") ? "cccloud.gui.conn.not_linked_bridge" : "cccloud.gui.not_linked");
        String route = str(view, "routeState");
        if (route.equals("no_route")) {
            boolean advancedHelps = kind().equals("uplink") && !bool(view, "advanced");
            return Component.translatable(advancedHelps ? "cccloud.gui.hint.no_route_advanced" : "cccloud.gui.hint.no_route");
        }
        if (route.equals("over_capacity")) return Component.translatable("cccloud.gui.hint.over_capacity");
        if (route.equals("legacy_out_of_range")) return Component.translatable("cccloud.gui.legacy");
        String mainframe = str(view, "mainframeState");
        if (!mainframe.isEmpty() && !mainframe.equals("ok")) return Component.translatable("cccloud.gui.hint.mainframe." + mainframe);
        if (view.has("signal") && !view.has("cable") && view.get("signal").getAsDouble() < WEAK) return Component.translatable("cccloud.gui.hint.weak");
        return null;
    }

    /** Rename the card, open it on the dashboard, unlink (asks once more). */
    private void actionRows() {
        if (!bool(view, "linked")) return;
        boolean canRename = bool(view, "canRename"), canDashboard = bool(view, "canDashboard"), canUnlink = bool(view, "canUnlink");
        if (renaming) {
            rows.add(new Row(20, (c, x, y, w) -> {
                var save = Component.translatable("cccloud.gui.card.save");
                var cancel = Component.translatable("cccloud.gui.card.cancel");
                int sw = c.buttonWidth(save), cw = c.buttonWidth(cancel);
                c.edit(titleBox, x + 2, y + 3, Math.max(40, w - sw - cw - 10));
                c.button(x + w - sw - cw - 6, y + 3, save, CloudGui.ButtonStyle.ACCENT, true, this::saveTitle);
                c.button(x + w - cw - 3, y + 3, cancel, CloudGui.ButtonStyle.NORMAL, true, () -> {
                    renaming = false;
                    setFocused(null);
                    rebuild();
                });
            }));
            if (bool(view, "customTitle")) {
                rows.add(new Row(18, (c, x, y, w) -> {
                    var reset = Component.translatable("cccloud.gui.card.reset_title");
                    c.button(x + 2, y + 2, reset, CloudGui.ButtonStyle.NORMAL, true, () -> {
                        titleBox.setValue("");
                        saveTitle();
                    });
                }));
            }
            textRow(Component.translatable("cccloud.gui.card.rename_hint"), CloudGui.TEXT_DIM);
        }
        rows.add(new Row(20, (c, x, y, w) -> {
            var rename = Component.translatable("cccloud.gui.card.rename");
            var open = Component.translatable("cccloud.gui.card.show");
            boolean armed = System.currentTimeMillis() < unlinkArmedUntil;
            var unlink = Component.translatable(armed ? "cccloud.gui.unlink_confirm" : "cccloud.gui.unlink");
            int bx = x + 2;
            if (!renaming) {
                bx += c.button(bx, y + 3, rename, CloudGui.ButtonStyle.NORMAL, canRename, () -> {
                    renaming = true;
                    titleBox.setValue(str(view, "title"));
                    titleBox.moveCursorToEnd(false);
                    rebuild();
                    setFocused(titleBox);
                    titleBox.setFocused(true);
                }) + 3;
            }
            bx += c.button(bx, y + 3, open, CloudGui.ButtonStyle.NORMAL, canDashboard, () -> send("dashboard", "")) + 3;
            int uw = c.buttonWidth(unlink);
            int ux = Math.max(bx, x + w - uw - 3);
            c.button(ux, y + 3, unlink, CloudGui.ButtonStyle.DANGER, canUnlink, () -> {
                if (System.currentTimeMillis() < unlinkArmedUntil) {
                    unlinkArmedUntil = 0;
                    send("unlink", "");
                } else {
                    unlinkArmedUntil = System.currentTimeMillis() + CONFIRM_MS;
                }
            });
        }));
        if (!canRename && !canUnlink) textRow(Component.translatable("cccloud.gui.card.no_rights"), CloudGui.TEXT_DIM);
    }

    // ---- relay -------------------------------------------------------------------------------------------------------

    private void relayRows() {
        heading("cccloud.gui.relay.heading");
        boolean ender = "ender".equals(str(view, "relayType"));
        textRow(ender ? Component.translatable("cccloud.gui.relay.explain_ender", num(view, "deviceRange"))
            : Component.translatable("cccloud.gui.relay.explain", num(view, "nodeRange"), num(view, "deviceRange")), CloudGui.TEXT);
        if (ender) textRow(Component.translatable(bool(view, "enderLinked") ? "cccloud.relay.ender_group" : "cccloud.relay.ender_unlinked"), CloudGui.TEXT_DIM);

        heading("cccloud.gui.relay.networks");
        var networks = view.get("networks") instanceof JsonArray a ? a : new JsonArray();
        for (var e : networks) {
            var n = e.getAsJsonObject();
            String name = str(n, "name");
            long devices = num(n, "devices");
            double signal = n.has("signal") ? n.get("signal").getAsDouble() : -1;
            long relays = num(n, "relays");
            String cable = str(n, "cable");
            rows.add(new Row(24, (c, x, y, w) -> {
                var g = c.g();
                CloudGui.row(g, x, y, w, 24, false);
                var count = Component.translatable(devices == 1 ? "cccloud.gui.relay.device" : "cccloud.gui.relay.devices", devices);
                int cw = font.width(count);
                c.text(name, x + 4, y + 3, CloudGui.TEXT, w - cw - 14);
                g.text(font, count, x + w - cw - 4, y + 3, devices > 0 ? CloudGui.CYAN : CloudGui.TEXT_DIM, false);
                String how = relays > 0 ? Component.translatable(relays == 1 ? "cccloud.gui.conn.via_relay" : "cccloud.gui.conn.via_relays", relays).getString()
                    : Component.translatable("cccloud.gui.conn.direct").getString();
                String line = !cable.isEmpty() ? Component.translatable("cccloud.gui.conn." + cable).getString() + " · " + how
                    : signal >= 0 ? Component.translatable("cccloud.gui.conn.signal").getString() + " " + Math.round(signal * 100) + " % · " + how : how;
                c.text(line, x + 4, y + 13, signal >= 0 && signal < WEAK && cable.isEmpty() ? CloudGui.YELLOW : CloudGui.TEXT_DIM, w - 8);
            }));
        }
        long others = num(view, "otherNetworks");
        if (others > 0) textRow(Component.translatable("cccloud.gui.relay.other_networks", others), CloudGui.TEXT_DIM);
        if (networks.isEmpty() && others == 0) {
            warningRow(bool(view, "powered") ? Component.translatable(ender ? "cccloud.gui.relay.idle_ender" : "cccloud.gui.relay.idle", num(view, "nodeRange"))
                : Component.translatable("cccloud.gui.relay.no_power_hint", num(view, "usage")));
        }

        heading("cccloud.gui.relay.power");
        long stored = num(view, "energy"), capacity = Math.max(1, num(view, "capacity")), usage = num(view, "usage");
        rows.add(new Row(14, (c, x, y, w) -> {
            var g = c.g();
            int bw = w - 4;
            g.fill(x + 2, y + 2, x + 2 + bw, y + 11, 0xFF1A1A1A);
            int filled = (int) Math.min(bw - 2, (bw - 2) * stored / capacity);
            int color = !bool(view, "powered") ? CloudGui.RED : stored * 5 < capacity ? CloudGui.YELLOW : CloudGui.GREEN;
            g.fill(x + 3, y + 3, x + 3 + filled, y + 10, color);
            String text = String.format("%,d / %,d FE · %,d FE/t", stored, capacity, usage);
            g.pose().pushMatrix();
            g.pose().translate(x + 5, y + 4);
            g.pose().scale(0.6f, 0.6f);
            g.text(font, text, 0, 0, CloudGui.TEXT, true);
            g.pose().popMatrix();
        }));
        if (!bool(view, "powered") && !networks.isEmpty()) warningRow(Component.translatable("cccloud.gui.relay.no_power_hint", usage));
    }

    // ---- advanced ----------------------------------------------------------------------------------------------------

    /** A clickable "Advanced" heading; open: wireless redstone and the technical numbers. */
    private void advancedRows() {
        rows.add(new Row(15, (c, x, y, w) -> {
            var g = c.g();
            var label = Component.translatable("cccloud.gui.advanced");
            boolean hover = c.hover(x, y, w, 15);
            int color = hover ? CloudGui.TEXT : accent();
            // a small pixel triangle: right = closed, down = open
            int ax = x + 3, ay = y + 4;
            if (advancedOpen) {
                for (int i = 0; i < 4; i++) g.fill(ax + i, ay + 1 + i, ax + 7 - i, ay + 2 + i, color);
            } else {
                for (int i = 0; i < 4; i++) g.fill(ax + i, ay + i, ax + i + 1, ay + 7 - i, color);
            }
            g.text(font, label, x + 13, y + 4, color, false);
            if (hover) g.fill(x + 13, y + 13, x + 13 + font.width(label), y + 14, color);
            c.hitArea(x, y, w, 15, () -> setAdvancedOpen(!advancedOpen));
        }));
        if (!advancedOpen) return;
        if (kind().equals("uplink")) redstoneRows();
        heading("cccloud.gui.adv.details");
        switch (kind()) {
            case "relay" -> {
                kv("cccloud.gui.adv.type", str(view, "relayType"));
                kv("cccloud.gui.adv.ranges", Component.translatable("cccloud.gui.adv.relay_ranges", num(view, "nodeRange"), num(view, "deviceRange")).getString());
                kv("cccloud.gui.adv.id", shortId(str(view, "relayId")));
                kv("cccloud.gui.adv.position", str(view, "pos"));
            }
            default -> {
                String cardKind = str(view, "cardKind");
                kv("cccloud.gui.adv.card", cardKind + (view.has("template") ? " · " + str(view, "template") : "") + " · " + str(view, "mode"));
                kv("cccloud.gui.widgets", String.valueOf(num(view, "widgets")));
                if (view.has("computers")) kv("cccloud.gui.computers", String.valueOf(num(view, "computers")));
                kv("cccloud.gui.adv.id", shortId(str(view, "sectionId")));
                kv("cccloud.gui.adv.position", str(view, "pos"));
                if (bool(view, "linked")) {
                    String where = str(view, "mainframePos");
                    kv("cccloud.gui.adv.mainframe_at", where.isEmpty() ? "-" : where + " · " + str(view, "mainframeDim"));
                    long distance = num(view, "distance");
                    kv("cccloud.gui.distance", distance < 0 ? Component.translatable("cccloud.gui.other_dimension").getString()
                        : Component.translatable("cccloud.gui.adv.blocks", distance).getString());
                    kv("cccloud.gui.adv.reach", Component.translatable("cccloud.gui.adv.reach_value", num(view, "range"),
                        view.has("mainframeRange") ? String.valueOf(num(view, "mainframeRange")) : "-").getString());
                    if (view.has("hops")) {
                        kv("cccloud.gui.hops", num(view, "hops") + (view.has("signal") ? " · " + Math.round(view.get("signal").getAsDouble() * 100) + " %" : ""));
                    }
                    if (view.has("routeState")) kv("cccloud.gui.adv.route", str(view, "routeState"));
                }
            }
        }
    }

    private static String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) + "…" : id.isEmpty() ? "-" : id;
    }

    /** v0.7 wireless redstone: mode buttons, channel field (network.link on the mainframe), current level. */
    private void redstoneRows() {
        if (!view.has("redstone")) return;
        var rs = view.getAsJsonObject("redstone");
        heading("cccloud.gui.redstone");
        if (!channelLoaded) {
            channel.setValue(str(rs, "channel"));
            channelLoaded = true;
        }
        String mode = str(rs, "mode");
        boolean canEdit = bool(rs, "canEdit");
        rows.add(new Row(20, (c, x, y, w) -> {
            int bx = x + 2;
            for (var m : new String[]{"off", "transmit", "receive"}) {
                var label = Component.translatable("cccloud.gui.redstone.mode." + m);
                bx += c.button(bx, y + 3, label, m.equals(mode) ? CloudGui.ButtonStyle.ACCENT : CloudGui.ButtonStyle.NORMAL, canEdit, () -> sendRedstone(m)) + 3;
            }
        }));
        if (!"off".equals(mode)) {
            rows.add(new Row(20, (c, x, y, w) -> {
                var label = Component.translatable("cccloud.gui.redstone.channel");
                int lw = font.width(label) + 6;
                c.text(label, x + 2, y + 6, CloudGui.TEXT_DIM);
                var set = Component.translatable("cccloud.gui.redstone.set");
                int setW = c.buttonWidth(set);
                if (canEdit) {
                    c.edit(channel, x + 2 + lw, y + 3, Math.max(40, w - lw - setW - 10));
                    c.button(x + w - setW - 3, y + 3, set, CloudGui.ButtonStyle.ACCENT, true, () -> sendRedstone(mode));
                } else {
                    c.text(str(rs, "channel"), x + 2 + lw, y + 6, CloudGui.TEXT, w - lw - 4);
                }
            }));
            kv(mode.equals("transmit") ? "cccloud.gui.redstone.sending" : "cccloud.gui.redstone.receiving", String.valueOf(num(rs, "level")));
        }
        if (!canEdit) textRow(Component.translatable("cccloud.gui.redstone.no_rights"), CloudGui.TEXT_DIM);
    }

    private void kv(String key, String value) {
        rows.add(new Row(12, (c, x, y, w) -> {
            var label = Component.translatable(key);
            int lw = font.width(label) + 6;
            c.text(label, x + 2, y + 2, CloudGui.TEXT_DIM);
            c.text(value, x + 2 + lw, y + 2, CloudGui.TEXT, w - lw - 4);
        }));
    }
}
