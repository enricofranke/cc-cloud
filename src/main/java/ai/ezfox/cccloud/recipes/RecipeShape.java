package ai.ezfox.cccloud.recipes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import org.jspecify.annotations.Nullable;

/**
 * A recipe reduced to what the docs' crafting grid shows: the kind, the result and its count, and the ingredients as
 * strings – an item id ({@code minecraft:stone}), a tag ({@code #c:ingots/gold}) or alternatives
 * ({@code minecraft:oak_planks|minecraft:spruce_planks}). Built either from a live recipe's display (what JEI and the
 * recipe book show) or from one of our recipe JSONs, so a server's recipe can be compared with the mod's default without
 * the recipe codec. Shaped grids are trimmed like vanilla does (empty outer rows and columns removed).
 */
public record RecipeShape(Kind kind, String type, String result, int count, int width, int height, List<@Nullable String> cells, List<String> items,
                          @Nullable String station) {
    public enum Kind {
        SHAPED, SHAPELESS, OTHER
    }

    /** At most this many alternatives of one ingredient are listed. */
    static final int MAX_ALTERNATIVES = 8;
    /** Ingredients of a non-crafting recipe (or of a grid larger than 3×3) that are listed. */
    static final int MAX_ITEMS = 16;
    private static final String LETTERS = "ABCDEFGHI";

    /** Same recipe for a player: kind, result, count and ingredients (a shapeless recipe in any order). */
    public boolean sameAs(RecipeShape o) {
        if (kind != o.kind || !result.equals(o.result) || count != o.count) return false;
        return switch (kind) {
            case SHAPED -> width == o.width && height == o.height && cells.equals(o.cells);
            case SHAPELESS -> sorted(items).equals(sorted(o.items));
            case OTHER -> type.equals(o.type) && items.equals(o.items);
        };
    }

    private static List<String> sorted(List<String> list) {
        var copy = new ArrayList<>(list);
        Collections.sort(copy);
        return copy;
    }

    /**
     * Writes the fields the docs' recipe view reads: {@code result}, {@code count} (when more than one), and either
     * {@code grid} + {@code key} (shaped, at most 3×3), {@code shapeless} + {@code items}, or {@code items} +
     * {@code station} for other recipe types (and grids larger than 3×3, flagged {@code large}).
     */
    void writeTo(JsonObject out) {
        out.addProperty("kind", kind.name().toLowerCase(java.util.Locale.ROOT));
        out.addProperty("type", type);
        out.addProperty("result", result);
        if (count > 1) out.addProperty("count", count);
        if (station != null && kind == Kind.OTHER) out.addProperty("station", station);
        if (kind == Kind.SHAPED && width <= 3 && height <= 3) {
            Map<String, Character> letters = new LinkedHashMap<>();
            var grid = new JsonArray();
            for (int y = 0; y < height; y++) {
                var row = new StringBuilder();
                for (int x = 0; x < width; x++) {
                    String cell = cells.get(y * width + x);
                    if (cell == null) {
                        row.append(' ');
                        continue;
                    }
                    Character letter = letters.get(cell);
                    if (letter == null) {
                        letter = LETTERS.charAt(letters.size());
                        letters.put(cell, letter);
                    }
                    row.append(letter);
                }
                grid.add(row.toString());
            }
            var key = new JsonObject();
            letters.forEach((cell, letter) -> key.addProperty(String.valueOf(letter), cell));
            out.add("grid", grid);
            out.add("key", key);
            return;
        }
        var list = new JsonArray();
        if (kind == Kind.SHAPED) {
            // A grid wider or taller than 3×3 (extended crafting tables): the distinct ingredients.
            out.addProperty("large", true);
            new LinkedHashSet<>(cells).stream().filter(c -> c != null).limit(MAX_ITEMS).forEach(list::add);
        } else {
            items.stream().limit(kind == Kind.SHAPELESS ? 9 : MAX_ITEMS).forEach(list::add);
        }
        if (kind == Kind.SHAPELESS) out.addProperty("shapeless", true);
        out.add("items", list);
    }

    /** Every item id and tag the recipe names (for icons and names). */
    List<String> ingredientIds() {
        var out = new ArrayList<String>();
        out.add(result);
        for (String c : cells) if (c != null) out.add(c);
        out.addAll(items);
        if (station != null) out.add(station);
        return out;
    }

    // ---- live recipes (display) ---------------------------------------------------------------------------------------

    /** The shape of a live recipe from its first display, or null if it has none or no item result. */
    static @Nullable RecipeShape fromDisplay(String type, List<RecipeDisplay> displays, ContextMap context) {
        if (displays.isEmpty()) return null;
        var display = displays.getFirst();
        var result = result(display.result(), context);
        if (result == null) return null;
        String station = ingredient(display.craftingStation(), context);
        return switch (display) {
            case ShapedCraftingRecipeDisplay s -> {
                var cells = new ArrayList<@Nullable String>(s.ingredients().size());
                for (var slot : s.ingredients()) cells.add(ingredient(slot, context));
                yield new RecipeShape(Kind.SHAPED, type, result.id, result.count, s.width(), s.height(), cells, List.of(), station);
            }
            case ShapelessCraftingRecipeDisplay s -> new RecipeShape(Kind.SHAPELESS, type, result.id, result.count, 0, 0, List.of(),
                present(s.ingredients(), context), station);
            case FurnaceRecipeDisplay f -> other(type, result, List.of(f.ingredient()), station, context);
            case StonecutterRecipeDisplay s -> other(type, result, List.of(s.input()), station, context);
            case SmithingRecipeDisplay s -> other(type, result, List.of(s.template(), s.base(), s.addition()), station, context);
            default -> other(type, result, List.of(), station, context);
        };
    }

    private static RecipeShape other(String type, Result result, List<SlotDisplay> inputs, @Nullable String station, ContextMap context) {
        return new RecipeShape(Kind.OTHER, type, result.id, result.count, 0, 0, List.of(), present(inputs, context), station);
    }

    private static List<String> present(List<SlotDisplay> slots, ContextMap context) {
        var out = new ArrayList<String>(slots.size());
        for (var slot : slots) {
            String s = ingredient(slot, context);
            if (s != null) out.add(s);
        }
        return out;
    }

    private record Result(String id, int count) {
    }

    /** The item id a result display makes, or null. */
    static @Nullable String resultId(SlotDisplay display, ContextMap context) {
        var result = result(display, context);
        return result == null ? null : result.id;
    }

    private static @Nullable Result result(SlotDisplay display, ContextMap context) {
        return switch (display) {
            case SlotDisplay.ItemStackSlotDisplay s -> new Result(key(s.stack().item()), s.stack().count());
            case SlotDisplay.ItemSlotDisplay i -> new Result(key(i.item()), 1);
            default -> {
                ItemStack stack;
                try {
                    stack = display.resolveForFirstStack(context);
                } catch (RuntimeException e) {
                    stack = ItemStack.EMPTY;
                }
                yield stack.isEmpty() ? null : new Result(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
            }
        };
    }

    /** One slot as an ingredient string, or null for an empty slot. */
    static @Nullable String ingredient(SlotDisplay display, ContextMap context) {
        return switch (display) {
            case SlotDisplay.Empty e -> null;
            case SlotDisplay.TagSlotDisplay t -> "#" + t.tag().location();
            case SlotDisplay.ItemSlotDisplay i -> key(i.item());
            case SlotDisplay.ItemStackSlotDisplay s -> key(s.stack().item());
            case SlotDisplay.WithRemainder w -> ingredient(w.input(), context);
            case SlotDisplay.OnlyWithComponent o -> ingredient(o.source(), context);
            case SlotDisplay.Composite c -> {
                var parts = new LinkedHashSet<String>();
                for (var part : c.contents()) {
                    String s = ingredient(part, context);
                    if (s != null) parts.addAll(List.of(s.split("\\|")));
                }
                yield alternatives(parts);
            }
            default -> {
                // Other displays (mods, any fuel, demos): the items they resolve to.
                var parts = new LinkedHashSet<String>();
                try {
                    for (var stack : display.resolveForStacks(context)) {
                        if (!stack.isEmpty()) parts.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                        if (parts.size() > MAX_ALTERNATIVES) break;
                    }
                } catch (RuntimeException ignored) {
                    // a display that needs context we do not have
                }
                yield alternatives(parts);
            }
        };
    }

    private static @Nullable String alternatives(java.util.Collection<String> parts) {
        if (parts.isEmpty()) return null;
        return String.join("|", parts.stream().limit(MAX_ALTERNATIVES).toList());
    }

    private static String key(Holder<Item> item) {
        return item.unwrapKey().map(k -> k.identifier().toString()).orElseGet(() -> BuiltInRegistries.ITEM.getKey(item.value()).toString());
    }

    // ---- default recipes (our JSON files) -----------------------------------------------------------------------------

    /**
     * The shape of one of our recipe JSONs (shaped or shapeless crafting with string or list ingredients), or null if
     * the file uses something else (then it cannot be compared and is never reported as changed).
     */
    public static @Nullable RecipeShape fromJson(JsonObject json) {
        try {
            String type = Identifier.parse(json.get("type").getAsString()).toString();
            var resultJson = json.get("result");
            String result;
            int count = 1;
            if (resultJson.isJsonPrimitive()) {
                result = Identifier.parse(resultJson.getAsString()).toString();
            } else {
                var r = resultJson.getAsJsonObject();
                result = Identifier.parse(r.get("id").getAsString()).toString();
                if (r.has("count")) count = r.get("count").getAsInt();
            }
            if (type.equals("minecraft:crafting_shaped")) {
                var pattern = new ArrayList<String>();
                for (var row : json.getAsJsonArray("pattern")) pattern.add(row.getAsString());
                var key = json.getAsJsonObject("key");
                var trimmed = trim(pattern);
                if (trimmed.isEmpty()) return null;
                int width = trimmed.getFirst().length();
                var cells = new ArrayList<@Nullable String>();
                for (String row : trimmed) {
                    for (int x = 0; x < width; x++) {
                        char ch = x < row.length() ? row.charAt(x) : ' ';
                        if (ch == ' ') {
                            cells.add(null);
                            continue;
                        }
                        var ingredient = key.get(String.valueOf(ch));
                        if (ingredient == null) return null;
                        String s = jsonIngredient(ingredient);
                        if (s == null) return null;
                        cells.add(s);
                    }
                }
                return new RecipeShape(Kind.SHAPED, type, result, count, width, trimmed.size(), cells, List.of(), "minecraft:crafting_table");
            }
            if (type.equals("minecraft:crafting_shapeless")) {
                var items = new ArrayList<String>();
                for (var ingredient : json.getAsJsonArray("ingredients")) {
                    String s = jsonIngredient(ingredient);
                    if (s == null) return null;
                    items.add(s);
                }
                return new RecipeShape(Kind.SHAPELESS, type, result, count, 0, 0, List.of(), items, "minecraft:crafting_table");
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@code "ns:item"}, {@code "#ns:tag"} or a list of item ids; null for anything else (custom ingredients). */
    private static @Nullable String jsonIngredient(JsonElement e) {
        if (e.isJsonPrimitive()) {
            String s = e.getAsString();
            return s.startsWith("#") ? "#" + Identifier.parse(s.substring(1)) : Identifier.parse(s).toString();
        }
        if (e.isJsonArray()) {
            var parts = new LinkedHashSet<String>();
            for (var part : e.getAsJsonArray()) {
                if (!part.isJsonPrimitive() || part.getAsString().startsWith("#")) return null;
                parts.add(Identifier.parse(part.getAsString()).toString());
            }
            return alternatives(parts);
        }
        return null;
    }

    /** Removes empty outer rows and columns, like vanilla's {@code ShapedRecipePattern.shrink}. */
    static List<String> trim(List<String> pattern) {
        int left = Integer.MAX_VALUE, right = -1, top = -1, bottom = -1;
        for (int y = 0; y < pattern.size(); y++) {
            String row = pattern.get(y);
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == ' ') continue;
                left = Math.min(left, x);
                right = Math.max(right, x);
                if (top < 0) top = y;
                bottom = y;
            }
        }
        if (top < 0) return List.of();
        var out = new ArrayList<String>();
        for (int y = top; y <= bottom; y++) {
            String row = pattern.get(y);
            var sb = new StringBuilder();
            for (int x = left; x <= right; x++) sb.append(x < row.length() ? row.charAt(x) : ' ');
            out.add(sb.toString());
        }
        return out;
    }
}
