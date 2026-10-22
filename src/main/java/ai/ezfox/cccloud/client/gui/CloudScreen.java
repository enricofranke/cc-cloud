package ai.ezfox.cccloud.client.gui;

import ai.ezfox.cccloud.gui.CloudMenu;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * Base of the CC-style screens. The panel adapts to the window (works from ~200×120 GUI pixels, i.e. GUI scale 4–5
 * on small windows): a title bar, an optional header and tabs, and a scrollable list of rows on a terminal inset.
 * Rows are drawn in immediate mode; buttons register hit boxes while drawing, so only what is visible is clickable.
 */
public abstract class CloudScreen<M extends CloudMenu> extends AbstractContainerScreen<M> {
    protected static final int MAX_W = 320;
    protected static final int MAX_H = 232;
    protected static final int ROW = 18;

    /** A list row: a height and a draw function (y is the row's top, already scrolled). */
    protected record Row(int height, RowDrawer drawer) {
    }

    @FunctionalInterface
    protected interface RowDrawer {
        void draw(Ctx c, int x, int y, int w);
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    /** Drawing context of one frame. */
    protected static final class Ctx {
        final CloudScreen<?> screen;
        final Font font;
        final List<Hit> hits;
        final GuiGraphicsExtractor g;
        final int mouseX, mouseY;
        final int clipTop, clipBottom;

        Ctx(CloudScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY, int clipTop, int clipBottom) {
            this.screen = screen;
            this.font = screen.font;
            this.hits = screen.hits;
            this.g = g;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.clipTop = clipTop;
            this.clipBottom = clipBottom;
        }

        public GuiGraphicsExtractor g() {
            return g;
        }

        public boolean hover(int x, int y, int w, int h) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY >= clipTop && mouseY < clipBottom;
        }

        /** Draws a button and makes it clickable; returns its width. */
        public int button(int x, int y, Component label, CloudGui.ButtonStyle style, boolean enabled, Runnable action) {
            int w = Math.max(30, font.width(label) + 10);
            boolean hover = enabled && hover(x, y, w, 14);
            CloudGui.button(g, x, y, w, style, hover, enabled);
            g.centeredText(font, label, x + w / 2, y + 3, CloudGui.buttonText(style, enabled));
            if (enabled) hits.add(new Hit(x, Math.max(y, clipTop), w, Math.min(y + 14, clipBottom) - Math.max(y, clipTop), action));
            return w;
        }

        public int buttonWidth(Component label) {
            return Math.max(30, font.width(label) + 10);
        }

        /** A 14×14 icon button (0 = copy). */
        public void iconButton(int x, int y, int icon, Runnable action) {
            boolean hover = hover(x, y, 14, 14);
            CloudGui.button(g, x, y, 14, CloudGui.ButtonStyle.NORMAL, hover, true);
            CloudGui.icon(g, x + 3, y + 3, icon);
            hits.add(new Hit(x, Math.max(y, clipTop), 14, Math.min(y + 14, clipBottom) - Math.max(y, clipTop), action));
        }

        public void toggle(int x, int y, boolean on, boolean enabled, Runnable action) {
            CloudGui.toggle(g, x, y, on);
            if (enabled) hits.add(new Hit(x, y, 24, 12, action));
        }

        /** Text cut to fit {@code maxW} (with an ellipsis). */
        public void text(String s, int x, int y, int color, int maxW) {
            g.text(font, screen.fit(s, maxW), x, y, color, false);
        }

        public void text(Component s, int x, int y, int color) {
            g.text(font, s, x, y, color, false);
        }

        /** Places an edit box in the list (only visible if fully inside the viewport). */
        public void edit(EditBox box, int x, int y, int w) {
            CloudGui.textField(g, x, y, w, box.isFocused());
            box.setX(x + 4);
            box.setY(y + 3);
            box.setWidth(w - 8);
            box.setVisible(y >= clipTop && y + 14 <= clipBottom);
        }

        public Font font() {
            return font;
        }
    }

    protected final List<Row> rows = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    protected final List<EditBox> editBoxes = new ArrayList<>();
    protected JsonObject view = new JsonObject();
    private int seenVersion = -1;
    private double scroll;
    private int contentHeight;
    protected int panelX, panelY, panelW, panelH;
    protected int listX, listY, listW, listH;
    protected int tab;
    private String notice = "";
    private long noticeUntil;
    /** Client time when the current view arrived (for countdowns). */
    protected long viewTime;

    protected CloudScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    protected abstract Identifier texture();

    protected abstract int accent();

    /** Height of the header area between the title bar and the tabs/list. */
    protected int headerHeight() {
        return 0;
    }

    protected void drawHeader(Ctx c, int x, int y, int w) {
    }

    protected List<Component> tabs() {
        return List.of();
    }

    /** Rebuilds {@link #rows} from {@link #view} for the current tab. */
    protected abstract void buildRows();

    protected abstract Component rightTitle();

    @Override
    protected void init() {
        CloudGui.refresh();
        panelW = Math.min(MAX_W, width - 12);
        panelH = Math.min(MAX_H, height - 12);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        // AbstractContainerScreen positions slots/labels with leftPos/topPos; we have none, but keep them consistent.
        leftPos = panelX;
        topPos = panelY;
        super.init();
        for (var box : editBoxes) addRenderableWidget(box);
        refreshView(true);
    }

    private void layoutList() {
        int y = panelY + 22 + headerHeight() + (tabs().isEmpty() ? 0 : 14);
        listX = panelX + 7;
        listY = y;
        listW = panelW - 14;
        listH = panelY + panelH - 7 - y;
    }

    private void refreshView(boolean force) {
        if (!force && menu.viewVersion() == seenVersion) return;
        seenVersion = menu.viewVersion();
        try {
            view = JsonParser.parseString(menu.view()).getAsJsonObject();
        } catch (RuntimeException e) {
            view = new JsonObject();
        }
        viewTime = System.currentTimeMillis();
        layoutList();
        if (view.has("notice")) {
            notice = view.get("notice").getAsString();
            noticeUntil = viewTime + 4000;
        }
        rows.clear();
        buildRows();
    }

    @Override
    protected void containerTick() {
        refreshView(false);
    }

    protected void setTab(int tab) {
        if (this.tab == tab) return;
        this.tab = tab;
        scroll = 0;
        for (var box : editBoxes) {
            box.setVisible(false);
            box.setFocused(false);
        }
        setFocused(null);
        rows.clear();
        buildRows();
    }

    protected void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ---- rendering -------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(g, mouseX, mouseY, partialTicks);
        hits.clear();
        var tex = texture();
        CloudGui.panel(g, tex, panelX, panelY, panelW, panelH, accent());
        CloudGui.titleBar(g, tex, panelX + 4, panelY + 4, panelW - 8, accent());
        var right = rightTitle();
        int rightW = font.width(right);
        g.text(font, fit(title.getString(), panelW - 24 - rightW), panelX + 9, panelY + 8, CloudGui.TEXT_DARK, false);
        g.text(font, right, panelX + panelW - 9 - rightW, panelY + 8, CloudGui.TEXT_DARK, false);

        var top = new Ctx(this, g, mouseX, mouseY, 0, height);
        if (headerHeight() > 0) drawHeader(top, panelX + 7, panelY + 22, panelW - 14);
        var tabs = tabs();
        if (!tabs.isEmpty()) {
            int ty = panelY + 22 + headerHeight();
            int x = panelX + 7;
            int each = Math.max(24, (panelW - 14) / tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                int w = i == tabs.size() - 1 ? panelX + panelW - 7 - x : each - 1;
                CloudGui.tab(g, tex, x, ty, w, i == tab, accent());
                boolean hover = top.hover(x, ty, w, 14);
                g.centeredText(font, Component.literal(fit(tabs.get(i).getString(), w - 6)), x + w / 2, ty + 3,
                    i == tab ? CloudGui.TEXT : hover ? CloudGui.TEXT_DARK : 0xFF404040);
                int index = i;
                hits.add(new Hit(x, ty, w, 14, () -> setTab(index)));
                x += w + 1;
            }
        }

        CloudGui.inset(g, tex, listX, listY, listW, listH);
        int innerX = listX + 3, innerY = listY + 3, innerW = listW - 6 - 8, innerH = listH - 6;
        contentHeight = 0;
        for (var row : rows) contentHeight += row.height();
        int maxScroll = Math.max(0, contentHeight - innerH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (var box : editBoxes) box.setVisible(false);
        g.enableScissor(innerX, innerY, innerX + innerW, innerY + innerH);
        var c = new Ctx(this, g, mouseX, mouseY, innerY, innerY + innerH);
        int y = innerY - (int) scroll;
        for (var row : rows) {
            if (y + row.height() > innerY && y < innerY + innerH) row.drawer().draw(c, innerX, y, innerW);
            y += row.height();
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackX = listX + listW - 3 - 7;
            int thumbH = Math.max(10, innerH * innerH / contentHeight);
            int thumbY = innerY + (int) ((innerH - thumbH) * (scroll / maxScroll));
            CloudGui.scrollbar(g, trackX, innerY, innerH, thumbY, thumbH);
        }

        if (!notice.isEmpty() && System.currentTimeMillis() < noticeUntil) {
            var text = Component.translatableWithFallback("cccloud.gui.notice." + notice, notice);
            int w = Math.min(listW - 8, font.width(text) + 10);
            int nx = listX + (listW - w) / 2, ny = listY + listH - 16;
            g.fill(nx, ny, nx + w, ny + 13, 0xE0202020);
            g.outline(nx, ny, w, 13, accent());
            g.centeredText(font, Component.literal(fit(text.getString(), w - 6)), nx + w / 2, ny + 3, CloudGui.TEXT);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Everything is drawn in extractBackground.
    }

    // ---- input -----------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (var hit : List.copyOf(hits)) {
                if (hit.h() > 0 && event.x() >= hit.x() && event.x() < hit.x() + hit.w() && event.y() >= hit.y() && event.y() < hit.y() + hit.h()) {
                    playClick();
                    hit.action().run();
                    return true;
                }
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled) setFocused(null);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        if (x >= listX && x < listX + listW && y >= listY && y < listY + listH) {
            scroll -= deltaY * 14;
            return true;
        }
        return super.mouseScrolled(x, y, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // While typing, keys must not close the screen (inventory key) or trigger anything else.
        if (getFocused() instanceof EditBox box && box.isVisible() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                onEnter(box);
                return true;
            }
            box.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    /** Enter pressed in an edit box. */
    protected void onEnter(EditBox box) {
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    protected String fit(String s, int maxW) {
        if (maxW <= 0) return "";
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
    }

    protected static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    protected static boolean bool(JsonObject o, String key) {
        var e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    protected static long num(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? 0 : e.getAsLong();
    }

    protected void heading(String key) {
        rows.add(new Row(13, (c, x, y, w) -> c.text(Component.translatable(key), x + 1, y + 3, accent())));
    }

    protected void textRow(Component text, int color) {
        // Wrapped text: as many lines as needed.
        var lines = font.split(text, Math.max(40, listW - 24));
        int h = Math.max(1, lines.size()) * 10 + 3;
        rows.add(new Row(h, (c, x, y, w) -> {
            int ly = y + 2;
            for (var line : lines) {
                c.g().text(font, line, x + 2, ly, color, false);
                ly += 10;
            }
        }));
    }

    protected void warningRow(Component text) {
        var lines = font.split(text, Math.max(40, listW - 34));
        int h = Math.max(1, lines.size()) * 10 + 3;
        rows.add(new Row(h, (c, x, y, w) -> {
            CloudGui.icon(c.g(), x + 2, y + 2, 1);
            int ly = y + 2;
            for (var line : lines) {
                c.g().text(font, line, x + 14, ly, CloudGui.YELLOW, false);
                ly += 10;
            }
        }));
    }

    /** "12 s" / "5 min" / "3 h" / "2 d". */
    protected static String ago(long seconds) {
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return seconds / 60 + " min";
        if (seconds < 86400) return seconds / 3600 + " h";
        return seconds / 86400 + " d";
    }
}
