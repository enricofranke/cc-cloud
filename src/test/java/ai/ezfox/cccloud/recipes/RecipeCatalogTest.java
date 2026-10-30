package ai.ezfox.cccloud.recipes;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/recipes}: the live recipes of our items compared with the mod's recipe files. The "server" here is a
 * hand-made recipe list that a datapack changed: one recipe overridden, one removed, one added by the pack, one
 * conditional recipe inactive, the rest as shipped (also a shapeless one in another order and a tag ingredient).
 */
class RecipeCatalogTest {
    private static final Recipe.CommonInfo COMMON = new Recipe.CommonInfo(true);
    private static final CraftingRecipe.CraftingBookInfo BOOK = new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "");

    /** Our recipe files (src/main/resources/data/cccloud/recipe, passed in by build.gradle). */
    static Map<Identifier, JsonObject> ourFiles() throws IOException {
        String dir = System.getProperty("cccloud.recipeDir");
        assertNotNull(dir, "build.gradle passes -Dcccloud.recipeDir");
        var files = new LinkedHashMap<Identifier, JsonObject>();
        try (var list = Files.list(Path.of(dir))) {
            for (var file : list.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json")) continue;
                files.put(Identifier.fromNamespaceAndPath("cccloud", name.substring(0, name.length() - 5)),
                    JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
        }
        return files;
    }

    private static Item item(String id) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        assertNotSame(Items.AIR, item, id + " is registered");
        return item;
    }

    private static Ingredient tag(String id) {
        return Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM, TagKey.create(Registries.ITEM, Identifier.parse(id))));
    }

    private static RecipeHolder<?> holder(String id, Recipe<?> recipe) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)), recipe);
    }

    private static JsonObject build(List<RecipeHolder<?>> live, Map<Identifier, RecipeCatalog.Default> defaults) {
        return RecipeCatalog.build(live, defaults, h -> h.value().display(), ContextMap.EMPTY, null);
    }

    private static JsonObject entry(JsonObject catalog, String id) {
        for (JsonElement e : catalog.getAsJsonArray("recipes")) if (e.getAsJsonObject().get("id").getAsString().equals(id)) return e.getAsJsonObject();
        return fail("no live entry " + id + " in " + catalog);
    }

    private static List<String> strings(JsonArray array) {
        var out = new ArrayList<String>();
        array.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    @Test
    void everyRecipeFileOfTheModCanBeCompared() throws Exception {
        var files = ourFiles();
        assertTrue(files.size() >= 50, "the mod ships its recipes");
        for (var e : files.entrySet()) {
            var shape = RecipeShape.fromJson(e.getValue());
            assertNotNull(shape, e.getKey() + " normalizes (shaped/shapeless with string or list ingredients)");
            assertTrue(shape.width() <= 3 && shape.height() <= 3, e.getKey() + " fits a 3x3 grid");
        }
        // Conditions: the AllTheModium variants load with that mod, their fallbacks without it.
        var probe = files.get(Identifier.parse("cccloud:cloud_probe"));
        var fallback = files.get(Identifier.parse("cccloud:cloud_probe_fallback"));
        java.util.function.Predicate<String> withAtm = "allthemodium"::equals;
        java.util.function.Predicate<String> without = id -> false;
        assertTrue(RecipeCatalog.conditionsHold(probe, ICondition.IContext.EMPTY, withAtm));
        assertFalse(RecipeCatalog.conditionsHold(probe, ICondition.IContext.EMPTY, without));
        assertFalse(RecipeCatalog.conditionsHold(fallback, ICondition.IContext.EMPTY, withAtm));
        assertTrue(RecipeCatalog.conditionsHold(fallback, ICondition.IContext.EMPTY, without));
        assertTrue(RecipeCatalog.conditionsHold(files.get(Identifier.parse("cccloud:cloud_core")), ICondition.IContext.EMPTY, without), "no conditions");
        assertFalse(RecipeCatalog.conditionsHold(json("{\"neoforge:conditions\":[{\"type\":\"neoforge:never\"}]}"), ICondition.IContext.EMPTY, without));
        assertTrue(RecipeCatalog.conditionsHold(json("{\"neoforge:conditions\":[{\"type\":\"neoforge:or\",\"values\":[{\"type\":\"neoforge:never\"},"
            + "{\"type\":\"neoforge:not\",\"value\":{\"type\":\"neoforge:never\"}}]}]}"), ICondition.IContext.EMPTY, without));
        assertFalse(RecipeCatalog.conditionsHold(json("{\"neoforge:conditions\":[{\"type\":\"neoforge:and\",\"values\":[{\"type\":\"neoforge:always\"},"
            + "{\"type\":\"neoforge:mod_loaded\",\"modid\":\"ae2\"}]}]}"), ICondition.IContext.EMPTY, without));
        // A condition type nobody knows: the recipe counts as expected.
        assertTrue(RecipeCatalog.conditionsHold(json("{\"neoforge:conditions\":[{\"type\":\"somemod:moon_phase\"}]}"), ICondition.IContext.EMPTY, without));
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void trimsGridsLikeVanilla() {
        assertEquals(List.of("A", "B"), RecipeShape.trim(List.of("A  ", "B  ", "   ")));
        assertEquals(List.of(" B ", "SPS"), RecipeShape.trim(List.of(" B ", "SPS")));
        assertEquals(List.of(), RecipeShape.trim(List.of("   ")));
    }

    @Test
    void datapackOverridesShowUpAsChangedRemovedAndAdded() throws Exception {
        var files = ourFiles();
        // A "server" without AllTheModium: the AllTheModium probe is not loaded by design (its condition).
        var defaults = RecipeCatalog.defaultsOf(files, id -> false);
        assertFalse(defaults.get(Identifier.parse("cccloud:cloud_probe")).active());
        assertTrue(defaults.get(Identifier.parse("cccloud:cloud_sensor")).active());

        var core = item("cccloud:cloud_core");
        var live = new ArrayList<RecipeHolder<?>>();
        // As shipped: nPn / PEP / nPn with tags (gold nuggets, ender pearls) and prismarine crystals, two cores.
        live.add(holder("cccloud:cloud_core", new ShapedRecipe(COMMON, BOOK, ShapedRecipePattern.of(
            Map.of('n', tag("c:nuggets/gold"), 'P', Ingredient.of(Items.PRISMARINE_CRYSTALS), 'E', tag("c:ender_pearls")),
            "nPn", "PEP", "nPn"), new ItemStackTemplate(core, 2))));
        // As shipped, other key letters and another ingredient order (shapeless): still the default.
        live.add(holder("cccloud:cloud_link_card", new ShapelessRecipe(COMMON, BOOK, new ItemStackTemplate(item("cccloud:cloud_link_card")),
            List.of(Ingredient.of(Items.PRISMARINE_CRYSTALS), tag("c:dusts/redstone"), tag("c:nuggets/gold"), Ingredient.of(Items.PAPER)))));
        // Overridden by a datapack (same id): glowstone instead of prismarine crystals.
        live.add(holder("cccloud:cloud_lamp", new ShapedRecipe(COMMON, BOOK, ShapedRecipePattern.of(
            Map.of('G', Ingredient.of(Items.GLOWSTONE_DUST), 'N', tag("c:nuggets/gold"), 'L', Ingredient.of(Items.REDSTONE_LAMP)),
            " G ", "NLN"), new ItemStackTemplate(item("cccloud:cloud_lamp")))));
        // Added by the pack (KubeJS id) for one of our items, with alternatives.
        live.add(holder("kubejs:cheap_core", new ShapelessRecipe(COMMON, BOOK, new ItemStackTemplate(core),
            List.of(Ingredient.of(Items.ENDER_PEARL), Ingredient.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS)))));
        // Not ours at all: never listed.
        live.add(holder("minecraft:stone_bricks", new ShapedRecipe(COMMON, BOOK, ShapedRecipePattern.of(
            Map.of('#', Ingredient.of(Items.STONE)), "##", "##"), new ItemStackTemplate(Items.STONE_BRICKS, 4))));

        var catalog = build(live, defaults);
        assertEquals(1, catalog.get("v").getAsInt());
        assertFalse(catalog.get("truncated").getAsBoolean());

        var coreEntry = entry(catalog, "cccloud:cloud_core");
        assertEquals("default", coreEntry.get("status").getAsString(), coreEntry.toString());
        assertEquals("cloud_core", coreEntry.get("file").getAsString());
        assertEquals(2, coreEntry.get("count").getAsInt());
        assertEquals("[\"ABA\",\"BCB\",\"ABA\"]", coreEntry.get("grid").toString());
        assertEquals("#c:nuggets/gold", coreEntry.getAsJsonObject("key").get("A").getAsString());
        assertEquals("minecraft:prismarine_crystals", coreEntry.getAsJsonObject("key").get("B").getAsString());

        assertEquals("default", entry(catalog, "cccloud:cloud_link_card").get("status").getAsString());
        assertTrue(entry(catalog, "cccloud:cloud_link_card").get("shapeless").getAsBoolean());

        var lamp = entry(catalog, "cccloud:cloud_lamp");
        assertEquals("changed", lamp.get("status").getAsString());
        assertEquals("[\" A \",\"BCB\"]", lamp.get("grid").toString());
        assertEquals("minecraft:glowstone_dust", lamp.getAsJsonObject("key").get("A").getAsString());

        var added = entry(catalog, "kubejs:cheap_core");
        assertEquals("added", added.get("status").getAsString());
        assertFalse(added.has("file"), "only our ids map to a docs recipe");
        assertEquals("cccloud:cloud_core", added.get("result").getAsString());
        assertEquals("[\"minecraft:ender_pearl\",\"minecraft:oak_planks|minecraft:spruce_planks\"]", added.get("items").toString());

        assertThrows(org.opentest4j.AssertionFailedError.class, () -> entry(catalog, "minecraft:stone_bricks"));
        var removed = strings(catalog.getAsJsonArray("removed"));
        assertTrue(removed.contains("cccloud:cloud_sensor"), "the pack removed the sensor recipe: " + removed);
        assertFalse(removed.contains("cccloud:cloud_core"));
        var inactive = strings(catalog.getAsJsonArray("inactive"));
        assertTrue(inactive.contains("cccloud:cloud_probe"), "a recipe whose condition is off is inactive, not removed");
        assertFalse(removed.contains("cccloud:cloud_probe"));
        // Names of other mods' items in the server language, never untranslated keys.
        var names = catalog.getAsJsonObject("names");
        if (names.has("minecraft:glowstone_dust")) assertEquals("Glowstone Dust", names.get("minecraft:glowstone_dust").getAsString());
    }

    @Test
    void sizeIsBounded() {
        var core = item("cccloud:cloud_core");
        var live = new ArrayList<RecipeHolder<?>>();
        for (int i = 0; i < RecipeCatalog.MAX_RECIPES + 10; i++) {
            live.add(holder("kubejs:core_" + i, new ShapelessRecipe(COMMON, BOOK, new ItemStackTemplate(core), List.of(Ingredient.of(Items.DIRT)))));
        }
        var catalog = build(live, Map.of());
        assertEquals(RecipeCatalog.MAX_RECIPES, catalog.getAsJsonArray("recipes").size());
        assertTrue(catalog.get("truncated").getAsBoolean());
        var snapshot = RecipeCatalog.snapshot(catalog);
        assertTrue(snapshot.etag().startsWith("\"") && snapshot.etag().endsWith("\""));
        assertEquals(snapshot.etag(), RecipeCatalog.snapshot(build(live, Map.of())).etag(), "same recipes, same ETag");
    }

    @Test
    void onlyOurModPackCountsAsDefault() {
        assertTrue(RecipeCatalog.isOurPack("mod/cccloud"));
        assertTrue(RecipeCatalog.isOurPack("mod/othermod,cccloud"));
        assertFalse(RecipeCatalog.isOurPack("mod/cccloud:testpacks/live_recipes"), "the GameTest datapack overrides like any datapack");
        assertFalse(RecipeCatalog.isOurPack("file/my_overrides.zip"));
        assertFalse(RecipeCatalog.isOurPack("mod/kubejs"));
    }
}
