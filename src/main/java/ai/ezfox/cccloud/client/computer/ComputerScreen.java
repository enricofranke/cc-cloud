package ai.ezfox.cccloud.client.computer;

import ai.ezfox.cccloud.computer.block.ComputerMenu;
import ai.ezfox.cccloud.computer.block.ComputerScreens;
import ai.ezfox.cccloud.computer.block.HoldShortcut;
import ai.ezfox.cccloud.computer.block.TerminalMirror;
import ai.ezfox.cccloud.terminal.TerminalInput;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The in-game terminal of a Cloud Computer: the computer's screen in a bezel (grey, gold for the advanced computer) drawn
 * with CC: Cloud's own pixel font ({@link TerminalFont}), the 16 colours (grey shades on the basic computer), a blinking
 * cursor. Every key, character, paste (Ctrl+V) and, on the advanced computer, mouse click, drag, release and scroll goes
 * to the computer, batched once per tick in the browser terminal's format. Ctrl+T / Ctrl+R / Ctrl+S held for a second
 * terminate, reboot or shut down; Esc closes the screen.
 */
public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {
    private static final int BORDER = 6;
    private static final int TITLE_H = 12;
    private static final int FOOTER_H = 12;

    private final JsonArray pending = new JsonArray();
    private final Set<Integer> down = new HashSet<>();
    private final HoldShortcut hold = new HoldShortcut();
    private int termX, termY;
    private float scale = 1f;
    private int lastCellX = -1, lastCellY = -1, dragButton = -1;

    public ComputerScreen(ComputerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 51 * TerminalFont.CELL_W + 2 * BORDER, 19 * TerminalFont.CELL_H + 2 * BORDER + TITLE_H + FOOTER_H);
    }

    private int cols() {
        return menu.mirror.ready() ? menu.mirror.width() : 51;
    }

    private int rows() {
        return menu.mirror.ready() ? menu.mirror.height() : 19;
    }

    @Override
    protected void init() {
        super.init();
        layout();
    }

    /** Fits the terminal into the window: whole GUI pixels per font pixel when there is room, smaller otherwise. */
    private void layout() {
        int tw = cols() * TerminalFont.CELL_W, th = rows() * TerminalFont.CELL_H;
        float fit = Math.min((width - 2f * BORDER - 8) / tw, (height - 2f * BORDER - TITLE_H - FOOTER_H - 8) / th);
        scale = fit >= 1f ? (float) Math.floor(Math.min(fit, 2f)) : Math.max(0.25f, fit);
        int w = Math.round(tw * scale), h = Math.round(th * scale);
        termX = (width - w) / 2;
        termY = (height - h + TITLE_H - FOOTER_H) / 2;
        leftPos = termX - BORDER;
        topPos = termY - BORDER - TITLE_H;
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(g, mouseX, mouseY, partialTicks);
        layout();
        boolean advanced = menu.advanced();
        int w = Math.round(cols() * TerminalFont.CELL_W * scale), h = Math.round(rows() * TerminalFont.CELL_H * scale);
        int x0 = termX - BORDER, y0 = termY - BORDER - TITLE_H, x1 = termX + w + BORDER, y1 = termY + h + BORDER + FOOTER_H;
        int body = advanced ? 0xFFC9A93A : 0xFF8C8C8C, light = advanced ? 0xFFEFD77A : 0xFFB8B8B8, dark = advanced ? 0xFF7A5F14 : 0xFF4A4A4A;
        g.fill(x0, y0, x1, y1, 0xFF1B1B1B);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, body);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, light);
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, dark);
        g.fill(termX - 2, termY - 2, termX + w + 2, termY + h + 2, dark);
        g.fill(termX - 1, termY - 1, termX + w + 1, termY + h + 1, 0xFF0E0E0E);

        var state = menu.state;
        boolean on = state.has("on") && state.get("on").getAsBoolean();
        String titleText = state.has("title") ? state.get("title").getAsString() : title.getString();
        g.text(font, fit(titleText, w - 40), termX, y0 + 4, 0xFF1B1B1B, false);
        int led = on ? 0xFF5FD35F : 0xFF3A3F41;
        g.fill(termX + w - 6, y0 + 5, termX + w, y0 + 9, led);

        var mirror = menu.mirror;
        if (mirror.ready()) drawTerminal(g, mirror, advanced && mirror.colour());
        if (!on) {
            var text = Component.translatable(state.has("error") ? "cccloud.computer.screen.crashed" : "cccloud.computer.screen.off");
            int tw = font.width(text);
            int bx = termX + (w - tw) / 2 - 6, by = termY + h / 2 - 8;
            boolean hover = mouseX >= bx && mouseX < bx + tw + 12 && mouseY >= by && mouseY < by + 16;
            g.fill(bx, by, bx + tw + 12, by + 16, hover ? 0xFF2A7F99 : 0xFF202020);
            g.outline(bx, by, tw + 12, 16, 0xFF5EC8E5);
            g.text(font, text, bx + 6, by + 4, 0xFFFFFFFF, false);
            if (state.has("error")) g.text(font, fit(state.get("error").getAsString(), w - 8), termX + 4, by + 22, 0xFFEEA0A0, false);
        }

        long now = System.currentTimeMillis();
        float progress = hold.progress(now);
        String footer;
        if (progress >= 0 && hold.holding() != null) {
            footer = Component.translatable("cccloud.computer.screen.holding." + hold.holding()).getString();
            g.fill(termX, y1 - FOOTER_H + 1, termX + Math.round(w * progress), y1 - 3, 0xFFD34F4F);
        } else {
            footer = Component.translatable("cccloud.computer.screen.hint").getString();
        }
        g.text(font, fit(footer, w), termX, y1 - FOOTER_H + 2, 0xFF1B1B1B, false);
    }

    private void drawTerminal(GuiGraphicsExtractor g, TerminalMirror m, boolean colour) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(termX, termY);
        pose.scale(scale, scale);
        int cw = TerminalFont.CELL_W, ch = TerminalFont.CELL_H;
        for (int y = 0; y < m.height(); y++) {
            // backgrounds in runs of one colour
            int run = 0;
            while (run < m.width()) {
                int c = m.background(run, y);
                int end = run + 1;
                while (end < m.width() && m.background(end, y) == c) end++;
                g.fill(run * cw, y * ch, end * cw, (y + 1) * ch, 0xFF000000 | shade(c, colour));
                run = end;
            }
            for (int x = 0; x < m.width(); x++) {
                char c = m.charAt(x, y);
                if (c != ' ') TerminalFont.glyph(g, c, x * cw, y * ch, 0xFF000000 | shade(m.foreground(x, y), colour));
            }
        }
        boolean blinkOn = (System.currentTimeMillis() / 400) % 2 == 0;
        if (m.blink() && blinkOn && m.cursorX() >= 0 && m.cursorX() < m.width() && m.cursorY() >= 0 && m.cursorY() < m.height()) {
            TerminalFont.glyph(g, '_', m.cursorX() * cw, m.cursorY() * ch, 0xFF000000 | shade(m.foreground(m.cursorX(), m.cursorY()), colour));
        }
        pose.popMatrix();
    }

    /** The basic computer shows grey shades (its programs still name colours). */
    static int shade(int rgb, boolean colour) {
        if (colour) return rgb & 0xFFFFFF;
        int r = rgb >> 16 & 0xFF, gr = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        int v = (r * 299 + gr * 587 + b * 114) / 1000;
        return v << 16 | v << 8 | v;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // everything is drawn in extractBackground
    }

    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
    }

    // ---- input ---------------------------------------------------------------------------------------------------------

    private void queue(JsonObject event) {
        pending.add(event);
        if (pending.size() >= TerminalInput.MAX_EVENTS) flush();
    }

    private void flush() {
        if (pending.isEmpty()) return;
        ClientPacketDistributor.sendToServer(new ComputerScreens.InputPayload(menu.containerId, pending.toString()));
        while (!pending.isEmpty()) pending.remove(0);
    }

    private static JsonObject event(String type) {
        var json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    @Override
    protected void containerTick() {
        String control = hold.poll(System.currentTimeMillis());
        if (control != null) queue(event(control));
        flush();
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        hold.press(key, e.hasControlDown(), System.currentTimeMillis());
        if (e.hasControlDown() && key == GLFW.GLFW_KEY_V) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                var paste = event("paste");
                paste.addProperty("text", clip.length() > TerminalInput.MAX_PASTE ? clip.substring(0, TerminalInput.MAX_PASTE) : clip);
                queue(paste);
            }
            return true;
        }
        if (key < 1 || key > TerminalInput.MAX_KEY) return true;
        var json = event("key");
        json.addProperty("key", key);
        json.addProperty("held", !down.add(key));
        queue(json);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        int key = e.key();
        hold.release(key);
        if (down.remove(key)) {
            var json = event("key_up");
            json.addProperty("key", key);
            queue(json);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        int cp = e.codepoint();
        if (cp < 0x20 || cp > 0xFF || cp == 0x7F) return true;
        var json = event("char");
        json.addProperty("c", Character.toString(cp));
        queue(json);
        return true;
    }

    /** The terminal cell (1-based) under a screen position, clamped to the terminal; null when outside it. */
    private int[] cell(double mx, double my, boolean clamp) {
        int x = (int) Math.floor((mx - termX) / (TerminalFont.CELL_W * scale)) + 1;
        int y = (int) Math.floor((my - termY) / (TerminalFont.CELL_H * scale)) + 1;
        if (!clamp && (x < 1 || y < 1 || x > cols() || y > rows())) return null;
        return new int[]{Math.max(1, Math.min(cols(), x)), Math.max(1, Math.min(rows(), y))};
    }

    private static int button(int glfw) {
        return switch (glfw) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> 1;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> 2;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> 3;
            default -> -1;
        };
    }

    private void mouse(String type, int button, int[] cell) {
        var json = event(type);
        json.addProperty("button", button);
        json.addProperty("x", cell[0]);
        json.addProperty("y", cell[1]);
        queue(json);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        boolean on = menu.state.has("on") && menu.state.get("on").getAsBoolean();
        if (!on && e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            queue(event("turn_on"));
            return true;
        }
        if (!menu.advanced()) return true;
        var c = cell(e.x(), e.y(), false);
        int b = button(e.button());
        if (c == null || b < 0) return true;
        dragButton = b;
        lastCellX = c[0];
        lastCellY = c[1];
        mouse("mouse_click", b, c);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!menu.advanced() || dragButton < 0) return true;
        var c = cell(e.x(), e.y(), true);
        if (c[0] == lastCellX && c[1] == lastCellY) return true;
        lastCellX = c[0];
        lastCellY = c[1];
        mouse("mouse_drag", dragButton, c);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (!menu.advanced() || dragButton < 0) return true;
        mouse("mouse_up", dragButton, cell(e.x(), e.y(), true));
        dragButton = -1;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!menu.advanced() || scrollY == 0) return true;
        var c = cell(x, y, false);
        if (c == null) return true;
        var json = event("mouse_scroll");
        json.addProperty("dir", scrollY > 0 ? -1 : 1);
        json.addProperty("x", c[0]);
        json.addProperty("y", c[1]);
        queue(json);
        return true;
    }

    /** Keys still down go up, so a program never waits for a key that was released while the screen closed. */
    @Override
    public void removed() {
        for (int key : down) {
            var json = event("key_up");
            json.addProperty("key", key);
            queue(json);
        }
        down.clear();
        flush();
        super.removed();
    }
}
