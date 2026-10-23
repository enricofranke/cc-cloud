package ai.ezfox.cccloud.display;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Where each widget of a section goes on a Cloud Display, in texture pixels ({@link #PX_PER_BLOCK} per block). Pure
 * and deterministic (no font metrics), so the client renderer and the server's click hit-test agree. Unit tested.
 * <p>
 * Layout: a title line, then the widgets in order in a grid of columns at least 80 px wide; charts, lists (watchlist, jobs)
 * and the redstone sides take a full row. Widgets that do not fit are counted in {@link Result#hidden()}.
 */
public final class DisplayLayout {
    public static final int PX_PER_BLOCK = 64;
    /** Border of the screen inside the frame (closed edges of the model are 3/16 of a block). */
    public static final int BORDER = 12;
    static final int COLUMN = 80;
    static final int TITLE = 12;
    static final int GAP = 3;

    /** One widget's box. {@code clickable}: a button or toggle without PIN and confirmation (in-world clicks). */
    public record Box(String id, String type, int x, int y, int w, int h, boolean clickable) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public record Result(int width, int height, List<Box> boxes, int hidden) {
        public @Nullable Box hit(double px, double py) {
            for (var box : boxes) if (box.contains(px, py)) return box;
            return null;
        }
    }

    private DisplayLayout() {
    }

    /** Layout of a section on a display of {@code blocksW × blocksH} blocks. */
    public static Result layout(@Nullable JsonObject section, int blocksW, int blocksH) {
        int w = blocksW * PX_PER_BLOCK, h = blocksH * PX_PER_BLOCK;
        var boxes = new ArrayList<Box>();
        if (section == null || !section.has("widgets") || !section.get("widgets").isJsonArray()) return new Result(w, h, boxes, 0);
        int left = BORDER, top = BORDER + TITLE + GAP, right = w - BORDER, bottom = h - BORDER;
        int innerW = right - left;
        int cols = Math.max(1, (innerW + GAP) / (COLUMN + GAP));
        int colW = (innerW - (cols - 1) * GAP) / cols;
        int x = 0, y = top, rowH = 0, hidden = 0;
        for (var element : section.getAsJsonArray("widgets")) {
            if (!element.isJsonObject()) continue;
            var widget = element.getAsJsonObject();
            String type = str(widget, "type");
            String id = str(widget, "id");
            boolean full = isFullWidth(type);
            int height = height(type, widget);
            if (full && x > 0 || x >= cols) {
                y += rowH + GAP;
                x = 0;
                rowH = 0;
            }
            int bx = left + x * (colW + GAP);
            int bw = full ? innerW : colW;
            if (y + height > bottom) {
                hidden++;
                continue;
            }
            boxes.add(new Box(id, type, bx, y, bw, height, clickable(type, widget)));
            rowH = Math.max(rowH, height);
            x = full ? cols : x + 1;
        }
        return new Result(w, h, boxes, hidden);
    }

    static boolean isFullWidth(String type) {
        return switch (type) {
            case "chart", "watchlist", "jobs", "redstone_sides" -> true;
            default -> false;
        };
    }

    static int height(String type, JsonObject widget) {
        return switch (type) {
            case "chart" -> 44;
            case "watchlist", "jobs" -> {
                int rows = widget.has("value") && widget.get("value").isJsonArray() ? widget.getAsJsonArray("value").size() : 0;
                yield 12 + Math.max(1, Math.min(5, rows)) * 10;
            }
            default -> 24;
        };
    }

    static boolean clickable(String type, JsonObject widget) {
        if (!type.equals("button") && !type.equals("toggle")) return false;
        var opts = widget.has("opts") && widget.get("opts").isJsonObject() ? widget.getAsJsonObject("opts") : new JsonObject();
        return !bool(opts, "pin") && !bool(opts, "confirm");
    }

    /**
     * A click on the display's front face in rectangle coordinates (texture pixels, origin top-left). {@code hitX/Y/Z}
     * are the hit position inside the clicked block (0..1); same mapping as CC: Tweaked's monitors.
     */
    public static double[] toPixels(float hitX, float hitY, float hitZ, Direction facing, Direction orientation, int xIndex, int yIndex, int height) {
        float[] xy = blockXY(hitX, hitY, hitZ, facing, orientation);
        // The row index grows upwards (the origin is the bottom-left block), the pair's y is measured from the top.
        return new double[]{(xy[0] + xIndex) * PX_PER_BLOCK, (xy[1] + (height - yIndex - 1)) * PX_PER_BLOCK};
    }

    /** CC's XYPair: x from the left, y from the top, both 0..1 inside one block. */
    static float[] blockXY(float x, float y, float z, Direction facing, Direction orientation) {
        return switch (orientation) {
            case NORTH -> switch (facing) {
                case NORTH -> new float[]{1 - x, 1 - y};
                case SOUTH -> new float[]{x, 1 - y};
                case WEST -> new float[]{z, 1 - y};
                default -> new float[]{1 - z, 1 - y};
            };
            case DOWN -> switch (facing) {
                case NORTH -> new float[]{1 - x, z};
                case SOUTH -> new float[]{x, 1 - z};
                case WEST -> new float[]{z, x};
                default -> new float[]{1 - z, 1 - x};
            };
            case UP -> switch (facing) {
                case NORTH -> new float[]{1 - x, 1 - z};
                case SOUTH -> new float[]{x, z};
                case WEST -> new float[]{z, 1 - x};
                default -> new float[]{1 - z, x};
            };
            default -> new float[]{x, z};
        };
    }

    static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    static boolean bool(JsonObject o, String key) {
        var e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }
}
