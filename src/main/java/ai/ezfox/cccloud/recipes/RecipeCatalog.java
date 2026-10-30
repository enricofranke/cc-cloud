package ai.ezfox.cccloud.recipes;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/**
 * The live recipes of every CC: Cloud item, for the dashboard's recipes page ({@code GET /api/recipes}). Modpacks change
 * recipes with datapacks, KubeJS or CraftTweaker; JEI and the in-game handbook show what the server really loaded, and
 * so does the dashboard with this: every recipe whose id is ours or whose result is one of our items, read from the
 * server's recipe manager after all of that ran, and compared with the mod's own recipe files.
 * <p>
 * Status of each recipe: {@code default} (as shipped), {@code changed} (same id, other content), {@code added} (a
 * recipe of the pack for one of our items), plus the ids of default recipes the server {@code removed} and of
 * conditional ones that are {@code inactive} here by design (the AllTheModium variant without AllTheModium, the
 * {@code *_fallback} variant with it, the bridges without AE2 / Refined Storage).
 * <p>
 * Built off the server thread on the first request and cached until the recipe manager changes ({@code /reload}, a
 * KubeJS reload, another world); bounded to {@link #MAX_RECIPES} recipes.
 */
public final class RecipeCatalog {
    /** At most this many live recipes are listed (the mod ships about 60). */
    public static final int MAX_RECIPES = 256;
    /** Item names for at most this many ids. */
    static final int MAX_NAMES = 512;
    private static final String RECIPE_DIR = "recipe";
    private static final Predicate<String> MOD_LOADED = modId -> net.neoforged.fml.ModList.get().isLoaded(modId);

    /** The serialized catalog and its ETag. */
    public record Snapshot(byte[] json, String etag, int recipes) {
    }

    /** One of the mod's recipe files: its shape (null if it cannot be compared) and whether its conditions hold here. */
    public record Default(Identifier id, @Nullable RecipeShape shape, boolean active) {
    }

    private record Cached(MinecraftServer server, RecipeMap recipes, CompletableFuture<Snapshot> snapshot) {
    }

    private static volatile @Nullable Cached cache;

    private RecipeCatalog() {
    }

    public static void init() {
        // A reload builds a new recipe manager (the cache would notice too); drop the old one right away.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) cache = null;
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> cache = null);
    }

    /**
     * The current catalog of a server, built when the recipes changed since the last call. Call on the server thread:
     * it only takes the references there; the catalog is built on Minecraft's background executor (recipes, packs and
     * registries are read-only once loaded; a reload in between replaces them and the next request builds again), so
     * the first request after a reload costs the tick nothing.
     */
    public static CompletableFuture<Snapshot> get(MinecraftServer server) {
        var manager = server.getRecipeManager();
        var recipes = manager.recipeMap();
        var c = cache;
        if (c != null && c.server == server && c.recipes == recipes && !c.snapshot.isCompletedExceptionally()) return c.snapshot;
        var resources = server.getResourceManager();
        var registries = server.registryAccess();
        var context = SlotDisplayContext.fromLevel(server.overworld());
        var future = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            var defaults = loadDefaults(resources, registries);
            Function<RecipeHolder<?>, List<RecipeDisplay>> displays = holder -> {
                var out = new ArrayList<RecipeDisplay>(1);
                manager.listDisplaysForRecipe(holder.id(), entry -> out.add(entry.display()));
                return out;
            };
            var snapshot = snapshot(build(recipes.values(), defaults, displays, context, registries));
            CloudLog.LOGGER.debug("[cccloud] Recipe catalog: {} recipes, {} bytes in {} ms", snapshot.recipes(), snapshot.json().length,
                (System.nanoTime() - start) / 1_000_000);
            return snapshot;
        }, Util.backgroundExecutor());
        cache = new Cached(server, recipes, future);
        return future;
    }

    static Snapshot snapshot(JsonObject json) {
        byte[] bytes = Json.GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        var crc = new java.util.zip.CRC32();
        crc.update(bytes);
        return new Snapshot(bytes, "\"r" + Long.toHexString(crc.getValue()) + "\"", json.getAsJsonArray("recipes").size());
    }

    // ---- the catalog --------------------------------------------------------------------------------------------------

    /**
     * Builds the catalog (pure; unit tested with hand-made recipes).
     *
     * @param live     every recipe the server loaded
     * @param defaults the mod's own recipe files by recipe id
     * @param displays the displays of a recipe (the recipe manager caches them; tests call {@code Recipe#display})
     * @param context  for displays that resolve to items (tags, fuel)
     * @param registries for the tags' representative items; null in tests without tags
     */
    public static JsonObject build(Collection<RecipeHolder<?>> live, Map<Identifier, Default> defaults, Function<RecipeHolder<?>, List<RecipeDisplay>> displays,
                                   ContextMap context, HolderLookup.@Nullable Provider registries) {
        var picked = new TreeMap<String, JsonObject>();
        var shapes = new ArrayList<RecipeShape>();
        boolean truncated = false;
        var liveIds = new java.util.HashSet<Identifier>();
        for (var holder : live) {
            Identifier id = holder.id().identifier();
            boolean ours = id.getNamespace().equals(CCCloud.MODID);
            if (ours) liveIds.add(id);
            List<RecipeDisplay> shown;
            try {
                shown = displays.apply(holder);
            } catch (RuntimeException e) {
                shown = List.of();
            }
            // Cheap filter first: only recipes with our id or with one of our items as the result.
            if (!ours && !hasOurResult(shown, context)) continue;
            String type = typeOf(holder);
            RecipeShape shape;
            try {
                shape = RecipeShape.fromDisplay(type, shown, context);
            } catch (RuntimeException e) {
                shape = null;
            }
            var def = defaults.get(id);
            String status;
            if (def == null) status = "added";
            else if (def.shape() == null) status = "default"; // a file we cannot compare is never reported as changed
            else status = shape != null && def.shape().sameAs(shape) ? "default" : "changed";
            if (picked.size() >= MAX_RECIPES) {
                truncated = true;
                continue;
            }
            var entry = new JsonObject();
            entry.addProperty("id", id.toString());
            if (ours) entry.addProperty("file", id.getPath());
            entry.addProperty("status", status);
            if (shape != null) {
                shape.writeTo(entry);
                shapes.add(shape);
            } else {
                // No display (a machine recipe of another mod under our id): the type only.
                entry.addProperty("kind", "other");
                entry.addProperty("type", type);
            }
            picked.put(id.toString(), entry);
        }

        var recipes = new JsonArray();
        picked.values().forEach(recipes::add);
        var removed = new JsonArray();
        var inactive = new JsonArray();
        defaults.values().stream().sorted(Comparator.comparing(d -> d.id().toString())).forEach(d -> {
            if (liveIds.contains(d.id())) return;
            (d.active() ? removed : inactive).add(d.id().toString());
        });

        var out = new JsonObject();
        out.addProperty("v", 1);
        out.add("recipes", recipes);
        out.add("removed", removed);
        out.add("inactive", inactive);
        out.add("icons", icons(shapes, registries));
        out.add("names", names(shapes));
        out.addProperty("truncated", truncated);
        return out;
    }

    private static boolean hasOurResult(List<RecipeDisplay> displays, ContextMap context) {
        for (var display : displays) {
            String id = RecipeShape.resultId(display.result(), context);
            if (id != null && id.startsWith(CCCloud.MODID + ":")) return true;
        }
        return false;
    }

    private static String typeOf(RecipeHolder<?> holder) {
        try {
            var key = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
            if (key != null) return key.toString();
        } catch (RuntimeException ignored) {
            // a recipe without a registered serializer
        }
        var type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        return type == null ? "unknown" : type.toString();
    }

    /** A representative item per tag (the server's tag contents; a vanilla item when there is one). */
    private static JsonObject icons(List<RecipeShape> shapes, HolderLookup.@Nullable Provider registries) {
        var out = new JsonObject();
        if (registries == null) return out;
        var items = registries.lookup(Registries.ITEM).orElse(null);
        if (items == null) return out;
        for (var shape : shapes) {
            for (String ingredient : shape.ingredientIds()) {
                for (String part : ingredient.split("\\|")) {
                    if (!part.startsWith("#") || out.has(part)) continue;
                    var tagId = Identifier.tryParse(part.substring(1));
                    if (tagId == null) continue;
                    var tag = items.get(TagKey.create(Registries.ITEM, tagId));
                    if (tag.isEmpty()) continue;
                    Holder<Item> pick = null;
                    for (var holder : tag.get()) {
                        if (pick == null) pick = holder;
                        if (holder.unwrapKey().map(k -> k.identifier().getNamespace().equals("minecraft")).orElse(false)) {
                            pick = holder;
                            break;
                        }
                    }
                    if (pick != null) pick.unwrapKey().ifPresent(k -> out.addProperty(part, k.identifier().toString()));
                }
            }
        }
        return out;
    }

    /** English names (server language) of the items the recipes name, for items the dashboard has no name for. */
    private static JsonObject names(List<RecipeShape> shapes) {
        var out = new JsonObject();
        for (var shape : shapes) {
            for (String ingredient : shape.ingredientIds()) {
                for (String part : ingredient.split("\\|")) {
                    if (part.startsWith("#") || out.has(part) || out.size() >= MAX_NAMES) continue;
                    var item = ItemNames.byName(part);
                    if (item == null) continue;
                    String name = ItemNames.displayName(item);
                    // Untranslated keys (no language loaded) are no help to the reader.
                    if (!name.isBlank() && !name.equals(part) && !name.equals(item.getDescriptionId()) && name.length() <= 64) out.addProperty(part, name);
                }
            }
        }
        return out;
    }

    // ---- the mod's own recipe files -----------------------------------------------------------------------------------

    /**
     * The mod's recipe files: every {@code data/cccloud/recipe/*.json} of our own mod pack ({@code mod/cccloud}), read
     * straight from that pack (listing every pack's recipe folder would walk tens of thousands of files in a big modpack).
     * Conditions ({@code neoforge:conditions}) are evaluated here.
     */
    static Map<Identifier, Default> loadDefaults(ResourceManager resources, HolderLookup.Provider registries) {
        var out = new TreeMap<Identifier, Default>();
        var context = conditionContext(registries);
        resources.listPacks().filter(pack -> isOurPack(pack.packId())).forEach(pack ->
            pack.listResources(PackType.SERVER_DATA, CCCloud.MODID, RECIPE_DIR, (file, supplier) -> {
                String path = file.getPath();
                if (!path.endsWith(".json")) return;
                var id = Identifier.fromNamespaceAndPath(CCCloud.MODID, path.substring(RECIPE_DIR.length() + 1, path.length() - ".json".length()));
                try (Reader reader = new java.io.InputStreamReader(supplier.get(), StandardCharsets.UTF_8)) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    out.put(id, new Default(id, RecipeShape.fromJson(json), conditionsHold(json, context, MOD_LOADED)));
                } catch (Exception e) {
                    CloudLog.LOGGER.warn("[cccloud] Could not read the default recipe {}", id, e);
                }
            }));
        return out;
    }

    /** The pack of our own jar: {@code mod/cccloud} (NeoForge names mod packs after the mod ids of the file). */
    static boolean isOurPack(String packId) {
        if (!packId.startsWith("mod/")) return false;
        for (String mod : packId.substring(4).split(",")) if (mod.equals(CCCloud.MODID)) return true;
        return false;
    }

    /**
     * True if the file has no {@code neoforge:conditions} or all of them hold. NeoForge's own types (always, never, not,
     * and, or, mod_loaded) are evaluated here; any other type goes through NeoForge's condition codec, and a condition
     * that cannot be read counts as true (the recipe is then expected, so its absence shows as removed).
     */
    static boolean conditionsHold(JsonObject json, ICondition.IContext context, Predicate<String> modLoaded) {
        var conditions = json.get("neoforge:conditions");
        if (conditions == null || !conditions.isJsonArray()) return true;
        for (var condition : conditions.getAsJsonArray()) {
            if (!holds(condition, context, modLoaded)) return false;
        }
        return true;
    }

    private static boolean holds(JsonElement condition, ICondition.IContext context, Predicate<String> modLoaded) {
        Boolean known = evaluate(condition, modLoaded);
        if (known != null) return known;
        var parsed = ICondition.CODEC.parse(JsonOps.INSTANCE, condition).result().orElse(null);
        if (parsed == null) return true;
        try {
            return parsed.test(context);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** NeoForge's built-in condition types, or null for anything else. */
    static @Nullable Boolean evaluate(JsonElement condition, Predicate<String> modLoaded) {
        if (!condition.isJsonObject() || !condition.getAsJsonObject().has("type")) return null;
        var o = condition.getAsJsonObject();
        try {
            return switch (o.get("type").getAsString()) {
                case "neoforge:always" -> true;
                case "neoforge:never" -> false;
                case "neoforge:mod_loaded" -> modLoaded.test(o.get("modid").getAsString());
                case "neoforge:not" -> {
                    Boolean inner = evaluate(o.get("value"), modLoaded);
                    yield inner == null ? null : !inner;
                }
                case "neoforge:and", "neoforge:or" -> {
                    boolean and = o.get("type").getAsString().equals("neoforge:and");
                    for (var value : o.getAsJsonArray("values")) {
                        Boolean v = evaluate(value, modLoaded);
                        if (v == null) yield null;
                        if (and && !v) yield false;
                        if (!and && v) yield true;
                    }
                    yield and;
                }
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ICondition.IContext conditionContext(HolderLookup.Provider registries) {
        return new ICondition.IContext() {
            @Override
            public <T> boolean isTagLoaded(TagKey<T> key) {
                return registries.lookup(key.registry()).flatMap(r -> r.get(key)).isPresent();
            }
        };
    }

    /** Forgets the cached catalog (the next request builds it again). */
    public static void invalidate() {
        cache = null;
    }

    /** Defaults from recipe files given directly (unit tests read ours from the classpath). */
    static Map<Identifier, Default> defaultsOf(Map<Identifier, JsonObject> files, Predicate<String> modLoaded) {
        var out = new HashMap<Identifier, Default>();
        files.forEach((id, json) -> out.put(id, new Default(id, RecipeShape.fromJson(json), conditionsHold(json, ICondition.IContext.EMPTY, modLoaded))));
        return out;
    }
}
