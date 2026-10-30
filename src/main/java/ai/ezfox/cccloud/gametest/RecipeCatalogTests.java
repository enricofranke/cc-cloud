package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.recipes.RecipeCatalog;
import ai.ezfox.cccloud.web.CloudWebServer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Live recipes ({@code GET /api/recipes}) on a real server. GameTest runs add the datapack
 * {@code testpacks/live_recipes} (never in the release jar), which does what a modpack does: it overrides the Cloud
 * Lamp recipe, removes the Cloud Sensor recipe ({@code neoforge:never}) and adds a second Cloud Core recipe under its
 * own namespace. Every other recipe the server loaded from our files must come out as shipped – so the comparison of
 * the recipe JSONs with the decoded recipes agrees for all of them – and the conditional variants follow the loaded mods.
 */
public final class RecipeCatalogTests {
    private RecipeCatalogTests() {
    }

    /** GameTest runs only (CCCloud registers it with {@code -Dcccloud.gametest=true}). */
    public static void addTestPack(AddPackFindersEvent event) {
        event.addPackFinders(Identifier.fromNamespaceAndPath(CCCloud.MODID, "testpacks/live_recipes"), PackType.SERVER_DATA,
            Component.literal("CC: Cloud GameTest recipes"), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static JsonObject entry(GameTestHelper helper, JsonObject catalog, String id) {
        for (JsonElement e : catalog.getAsJsonArray("recipes")) if (e.getAsJsonObject().get("id").getAsString().equals(id)) return e.getAsJsonObject();
        throw helper.assertionException("No live recipe " + id);
    }

    private static List<String> strings(JsonObject catalog, String key) {
        var out = new ArrayList<String>();
        catalog.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
        return out;
    }

    public static void liveRecipes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        RecipeCatalog.invalidate();
        // Built on the background executor; the test may wait for it (the build never needs the server thread).
        var snapshot = RecipeCatalog.get(server).join();
        var catalog = JsonParser.parseString(new String(snapshot.json(), StandardCharsets.UTF_8)).getAsJsonObject();

        // The test datapack: one override, one removal, one addition.
        var lamp = entry(helper, catalog, "cccloud:cloud_lamp");
        helper.assertTrue(lamp.get("status").getAsString().equals("changed"), "overridden lamp recipe is changed: " + lamp);
        helper.assertTrue(lamp.getAsJsonObject("key").toString().contains("minecraft:glowstone_dust"), "the live lamp recipe is the pack's: " + lamp);
        helper.assertTrue(strings(catalog, "removed").contains("cccloud:cloud_sensor"), "removed sensor recipe is reported: " + catalog.get("removed"));
        var added = entry(helper, catalog, "cccloud_test:extra_core");
        helper.assertTrue(added.get("status").getAsString().equals("added"), "pack recipe for our item is added: " + added);
        helper.assertTrue(added.get("result").getAsString().equals("cccloud:cloud_core"), "added recipe makes a core");
        helper.assertTrue(added.getAsJsonArray("items").toString().contains("minecraft:oak_planks|minecraft:spruce_planks"), "alternatives: " + added);

        // Everything else from our files is reported as shipped (JSON and decoded recipe compare equal for all of them).
        int shipped = 0;
        for (JsonElement e : catalog.getAsJsonArray("recipes")) {
            var r = e.getAsJsonObject();
            String id = r.get("id").getAsString();
            if (!id.startsWith(CCCloud.MODID + ":") || id.equals("cccloud:cloud_lamp")) continue;
            helper.assertTrue(r.get("status").getAsString().equals("default"), "shipped recipe reported as " + r.get("status") + ": " + r);
            shipped++;
        }
        helper.assertTrue(shipped >= 25, "most shipped recipes are live: " + shipped);
        var core = entry(helper, catalog, "cccloud:cloud_core");
        helper.assertTrue(core.get("count").getAsInt() == 2 && core.get("grid").toString().equals("[\"ABA\",\"BCB\",\"ABA\"]"), "core grid: " + core);

        // Conditional variants follow the loaded mods: inactive, never "removed".
        boolean atm = ModList.get().isLoaded("allthemodium");
        String off = atm ? "cccloud:cloud_probe_fallback" : "cccloud:cloud_probe";
        String on = atm ? "cccloud:cloud_probe" : "cccloud:cloud_probe_fallback";
        helper.assertTrue(strings(catalog, "inactive").contains(off), off + " is inactive here: " + catalog.get("inactive"));
        helper.assertTrue(!strings(catalog, "removed").contains(off), off + " is not removed");
        helper.assertTrue(entry(helper, catalog, on).get("status").getAsString().equals("default"), on + " is live");

        // Tags get a representative item of the server's tag; English names for other mods' items.
        helper.assertTrue("minecraft:gold_nugget".equals(catalog.getAsJsonObject("icons").get("#c:nuggets/gold").getAsString()), "icon for #c:nuggets/gold");
        helper.assertTrue("Glowstone Dust".equals(catalog.getAsJsonObject("names").get("minecraft:glowstone_dust").getAsString()), "server-side names");

        // Cached until the recipes change.
        helper.assertTrue(RecipeCatalog.get(server).join() == snapshot, "the catalog is cached");

        // And over HTTP (the handler asks the server thread, so the request completes while this test waits a tick).
        int port = CloudWebServer.port();
        helper.assertTrue(port > 0, "dashboard server runs");
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        CompletableFuture<HttpResponse<String>> response = http.sendAsync(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/recipes")).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        helper.succeedWhen(() -> {
            helper.assertTrue(response.isDone(), "waiting for /api/recipes");
            var r = response.join();
            helper.assertTrue(r.statusCode() == 200, "GET /api/recipes: " + r.statusCode() + " " + r.body());
            helper.assertTrue(r.body().equals(new String(snapshot.json(), StandardCharsets.UTF_8)), "same document over HTTP");
            helper.assertTrue(snapshot.etag().equals(r.headers().firstValue("ETag").orElse("")), "ETag");
        });
    }
}
