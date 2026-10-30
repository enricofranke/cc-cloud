package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The server-side icon renderer on hand-made models and textures (no Mojang or mod assets): layer compositing, tints,
 * animation frames, the isometric cube against golden images, and what it leaves for game clients.
 */
class ServerIconRendererTest {
    private final Map<String, byte[]> files = new HashMap<>();

    private ServerIconRenderer renderer() {
        return new ServerIconRenderer(files::get);
    }

    private void file(String path, String json) {
        files.put(path, json.replace('\'', '"').getBytes(StandardCharsets.UTF_8));
    }

    private void texture(String id, int w, int h, java.util.function.IntBinaryOperator pixel) {
        var px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = pixel.applyAsInt(x, y);
        var split = IconFallback.split(id);
        files.put("assets/" + split[0] + "/textures/" + split[1] + ".png", PngCodec.encode(w, h, px));
    }

    /** A 16×16 test face: a colour gradient, a dark 4×4 marker in the top-left corner and a light one top-right. */
    private void face(String id, int base) {
        texture(id, 16, 16, (x, y) -> {
            if (x < 4 && y < 4) return 0xFF101010;
            if (x >= 12 && y < 2) return 0xFFF0F0F0;
            int k = 160 + x * 4 + y * 2;
            return 0xFF000000 | Math.min(255, ((base >> 16) & 0xFF) * k / 255) << 16 | Math.min(255, ((base >> 8) & 0xFF) * k / 255) << 8
                | Math.min(255, (base & 0xFF) * k / 255);
        });
    }

    private static int at(ServerIconRenderer.Result r, int x, int y) {
        return r.argb()[y * 32 + x];
    }

    // ---- flat items --------------------------------------------------------------------------------------------------

    @Test
    void composesLayersWithTintsAtTwiceTheSize() {
        texture("test:item/body", 16, 16, (x, y) -> x >= 2 && x < 14 && y >= 2 && y < 14 ? 0xFFCC2222 : 0);
        texture("test:item/stripe", 16, 16, (x, y) -> y == 8 ? 0xFFFFFFFF : 0);
        file("assets/test/items/thing.json", "{'model':{'type':'minecraft:model','model':'test:item/thing','tints':[{'type':'minecraft:constant','value':-1},"
            + "{'type':'minecraft:dye','default':" + 0xFF3366FF + "}]}}");
        file("assets/test/models/item/thing.json", "{'parent':'minecraft:item/generated','textures':{'layer0':'test:item/body','layer1':'test:item/stripe'}}");

        var r = renderer().render("test", "thing");
        assertEquals(ServerIconRenderer.Kind.FLAT, r.kind(), r.detail());
        assertEquals(0, at(r, 0, 0) >>> 24, "transparent border");
        assertEquals(0xFFCC2222, at(r, 4, 4), "layer 0, each texel 2×2");
        assertEquals(0xFFCC2222, at(r, 27, 27));
        assertEquals(0, at(r, 28, 28) >>> 24);
        assertEquals(0xFF3366FF, at(r, 10, 16), "layer 1 on top, tinted with the dye default");
        assertEquals(0xFF3366FF, at(r, 10, 17));
        assertTrue(PngCodec.isValid(r.png(), 32, 32), "a valid 32×32 RGBA icon, like client uploads");
    }

    @Test
    void handheldAndOldStyleModelsAreFlatToo() {
        texture("test:item/sword", 16, 16, (x, y) -> x == y ? 0xFF888888 : 0);
        file("assets/test/items/sword.json", "{'model':{'type':'minecraft:model','model':'test:item/sword'}}");
        file("assets/test/models/item/sword.json", "{'parent':'minecraft:item/handheld','textures':{'layer0':'test:item/sword'}}");
        // No item definition at all: the classic item/<path> model.
        texture("test:item/old", 16, 16, (x, y) -> 0xFF00AA00);
        file("assets/test/models/item/old.json", "{'parent':'item/generated','textures':{'layer0':'test:item/old'}}");

        var sword = renderer().render("test", "sword");
        assertEquals(ServerIconRenderer.Kind.FLAT, sword.kind(), sword.detail());
        assertEquals(0xFF888888, at(sword, 31, 31));
        var old = renderer().render("test", "old");
        assertEquals(ServerIconRenderer.Kind.FLAT, old.kind(), old.detail());
        assertEquals(0xFF00AA00, at(old, 0, 0));
    }

    @Test
    void animatedTexturesShowTheirFirstFrame() {
        // 4 frames of 16×16 stacked: red, green, blue, white.
        int[] frames = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};
        texture("test:item/anim", 16, 64, (x, y) -> frames[y / 16]);
        file("assets/test/textures/item/anim.png.mcmeta", "{'animation':{'frametime':4}}");
        file("assets/test/models/item/anim.json", "{'parent':'minecraft:item/generated','textures':{'layer0':'test:item/anim'}}");
        assertEquals(0xFFFF0000, at(renderer().render("test", "anim"), 16, 31), "frame 0 only");

        file("assets/test/textures/item/anim.png.mcmeta", "{'animation':{'frames':[2,1,0]}}");
        assertEquals(0xFF0000FF, at(renderer().render("test", "anim"), 16, 16), "the first frame listed");
    }

    @Test
    void largeTexturesAreSampledDown() {
        texture("test:item/hd", 64, 64, (x, y) -> x < 32 ? 0xFF112233 : 0xFF445566);
        file("assets/test/models/item/hd.json", "{'parent':'minecraft:item/generated','textures':{'layer0':'test:item/hd'}}");
        var r = renderer().render("test", "hd");
        assertEquals(0xFF112233, at(r, 15, 5));
        assertEquals(0xFF445566, at(r, 16, 5));
    }

    // ---- cubes -------------------------------------------------------------------------------------------------------

    private void orientable() {
        face("test:block/top", 0xFF5080FF);
        face("test:block/side", 0xFF60C060);
        face("test:block/front", 0xFFFF9020);
        file("assets/test/items/machine.json", "{'model':{'type':'minecraft:model','model':'test:block/machine'}}");
        file("assets/test/models/block/machine.json", "{'parent':'minecraft:block/orientable','textures':{'top':'test:block/top','side':'test:block/side',"
            + "'front':'test:block/front'}}");
    }

    @Test
    void cubeShowsTopLeftEastAndRightNorthWithInventoryShading() {
        orientable();
        var r = renderer().render("test", "machine");
        assertEquals(ServerIconRenderer.Kind.CUBE, r.kind(), r.detail());
        // Silhouette like the game's: a hexagon from x 2..29, y 0..31 (checked against client renders).
        assertEquals(0, at(r, 1, 16) >>> 24);
        assertEquals(0, at(r, 30, 16) >>> 24);
        assertEquals(0, at(r, 2, 6) >>> 24);
        assertEquals(255, at(r, 2, 8) >>> 24);
        assertEquals(255, at(r, 15, 30) >>> 24);
        // Top: full light; left: the side texture (east) at 65 %; right: the front (north, orientable's front) at 40 %.
        int top = at(r, 16, 7), left = at(r, 6, 20), right = at(r, 25, 20);
        assertTrue(((top >> 8) & 0xFF) < ((top) & 0xFF), "top is blue: " + Integer.toHexString(top));
        assertTrue(((left >> 8) & 0xFF) > ((left >> 16) & 0xFF), "left is the green side: " + Integer.toHexString(left));
        assertTrue(((right >> 16) & 0xFF) > ((right) & 0xFF) * 2, "right is the orange front: " + Integer.toHexString(right));
        assertTrue(brightness(top) > brightness(left) * 1.2, "top brighter than left");
    }

    private static int brightness(int argb) {
        return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
    }

    @Test
    void cubeMatchesGoldenImage() throws IOException {
        orientable();
        assertGolden("cube_orientable", renderer().render("test", "machine"));
    }

    @Test
    void overlayElementsAndTintsMatchGoldenImage() throws IOException {
        // A grass-block-like model: a full cube plus a tinted overlay cube on the sides, tinted top.
        face("test:block/dirt", 0xFF8B5A2B);
        texture("test:block/top", 16, 16, (x, y) -> 0xFF000000 | (150 + (x + y) % 3 * 20) * 0x010101);
        texture("test:block/overlay", 16, 16, (x, y) -> y < 4 + (x % 3) ? 0xFFC0C0C0 : 0);
        file("assets/test/items/turf.json", "{'model':{'type':'minecraft:model','model':'test:block/turf','tints':[{'type':'minecraft:constant','value':"
            + 0xFF7CBD6B + "}]}}");
        file("assets/test/models/block/turf.json", "{'parent':'minecraft:block/block','textures':{'side':'test:block/dirt','top':'test:block/top',"
            + "'overlay':'test:block/overlay'},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{'up':{'texture':'#top','tintindex':0},"
            + "'north':{'texture':'#side'},'east':{'texture':'#side'},'south':{'texture':'#side'},'west':{'texture':'#side'}}},"
            + "{'from':[0,0,0],'to':[16,16,16],'faces':{'north':{'texture':'#overlay','tintindex':0},'east':{'texture':'#overlay','tintindex':0}}}]}");
        var r = renderer().render("test", "turf");
        assertEquals(ServerIconRenderer.Kind.CUBE, r.kind(), r.detail());
        assertGolden("cube_overlay_tint", r);
    }

    @Test
    void vanillaCubeParentsWorkWithoutVanillaFiles() {
        face("test:block/a", 0xFFAA4444);
        face("test:block/b", 0xFF44AA44);
        for (var parent : new String[]{"cube_all", "cube_column", "cube_bottom_top", "cube_top", "cube_bottom_top_inner_faces", "cube_all_inner_faces",
            "orientable_with_bottom", "orientable_vertical", "cube_column_horizontal", "cube_directional", "leaves", "template_glazed_terracotta"}) {
            file("assets/test/models/block/m.json", "{'parent':'minecraft:block/" + parent + "','textures':{'all':'test:block/a','side':'test:block/a',"
                + "'end':'test:block/b','top':'test:block/b','bottom':'test:block/b','front':'test:block/b','pattern':'test:block/a','up':'test:block/a',"
                + "'down':'test:block/a','north':'test:block/a','south':'test:block/a','east':'test:block/a','west':'test:block/a'}}");
            file("assets/test/items/m.json", "{'model':{'type':'minecraft:model','model':'test:block/m'}}");
            var r = renderer().render("test", "m");
            assertEquals(ServerIconRenderer.Kind.CUBE, r.kind(), parent + ": " + r.detail());
        }
    }

    @Test
    void realVanillaModelsWinOverTheStandIns() {
        face("test:block/a", 0xFFAA4444);
        // A "vanilla" cube_all from a client jar that would draw nothing but the north face.
        file("assets/minecraft/models/block/cube_all.json", "{'parent':'block/block','elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{"
            + "'north':{'texture':'#all'}}}]}");
        file("assets/test/models/block/m.json", "{'parent':'minecraft:block/cube_all','textures':{'all':'test:block/a'}}");
        file("assets/test/items/m.json", "{'model':{'type':'minecraft:model','model':'test:block/m'}}");
        var r = renderer().render("test", "m");
        assertEquals(ServerIconRenderer.Kind.CUBE, r.kind(), r.detail());
        assertEquals(0, at(r, 6, 20) >>> 24, "no east face: the real model was used");
        assertEquals(255, at(r, 25, 20) >>> 24);
    }

    // ---- item definitions --------------------------------------------------------------------------------------------

    @Test
    void picksTheInventoryCaseOfItemDefinitions() {
        texture("test:item/gui", 16, 16, (x, y) -> 0xFF0000AA);
        texture("test:item/hand", 16, 16, (x, y) -> 0xFFAA0000);
        file("assets/test/models/item/gui.json", "{'parent':'minecraft:item/generated','textures':{'layer0':'test:item/gui'}}");
        file("assets/test/models/item/hand.json", "{'parent':'minecraft:item/generated','textures':{'layer0':'test:item/hand'}}");
        String gui = "{'type':'minecraft:model','model':'test:item/gui'}", hand = "{'type':'minecraft:model','model':'test:item/hand'}";

        file("assets/test/items/a.json", "{'model':{'type':'minecraft:select','property':'minecraft:display_context','cases':[{'when':['gui','fixed'],'model':"
            + gui + "}],'fallback':" + hand + "}}");
        assertEquals(0xFF0000AA, at(renderer().render("test", "a"), 5, 5), "display_context: the gui case");
        file("assets/test/items/b.json", "{'model':{'type':'minecraft:condition','property':'minecraft:using_item','on_false':" + gui + ",'on_true':" + hand + "}}");
        assertEquals(0xFF0000AA, at(renderer().render("test", "b"), 5, 5), "condition: on_false");
        file("assets/test/items/c.json", "{'model':{'type':'minecraft:range_dispatch','property':'minecraft:use_duration','entries':[{'threshold':0.65,'model':"
            + hand + "}],'fallback':" + gui + "}}");
        assertEquals(0xFF0000AA, at(renderer().render("test", "c"), 5, 5), "range_dispatch at rest: the fallback");
        file("assets/test/items/d.json", "{'model':{'type':'minecraft:range_dispatch','property':'minecraft:compass','entries':[{'threshold':0.0,'model':"
            + gui + "},{'threshold':0.5,'model':" + hand + "}]}}");
        assertEquals(0xFF0000AA, at(renderer().render("test", "d"), 5, 5), "range_dispatch: the entry for 0");
    }

    // ---- left for game clients ---------------------------------------------------------------------------------------

    @Test
    void skipsWhatOnlyAGameClientCanDraw() {
        face("test:block/a", 0xFFAA4444);
        texture("test:item/x", 16, 16, (x, y) -> 0xFF777777);
        String cube = "'textures':{'all':'test:block/a'}";
        Map<String, String> cases = new HashMap<>();
        cases.put("special", "{'model':{'type':'minecraft:special','base':'test:item/x','model':{'type':'minecraft:chest'}}}");
        cases.put("composite", "{'model':{'type':'minecraft:composite','models':[{'type':'minecraft:model','model':'test:block/full'}]}}");
        cases.put("model type", "{'model':{'type':'othermod:fancy','model':'test:block/full'}}");
        for (var e : cases.entrySet()) {
            file("assets/test/items/i.json", e.getValue());
            assertEquals(ServerIconRenderer.Kind.SKIPPED, renderer().render("test", "i").kind(), e.getKey());
        }
        Map<String, String> models = new HashMap<>();
        models.put("loader", "{'loader':'neoforge:obj','model':'test:models/x.obj'}");
        models.put("slab", "{'parent':'minecraft:block/block'," + cube + ",'elements':[{'from':[0,0,0],'to':[16,8,16],'faces':{'up':{'texture':'#all'}}}]}");
        models.put("rotated", "{'parent':'minecraft:block/block'," + cube + ",'elements':[{'from':[0,0,0],'to':[16,16,16],'rotation':{'angle':22.5,'axis':'y',"
            + "'origin':[8,8,8]},'faces':{'up':{'texture':'#all'}}}]}");
        models.put("entity", "{'parent':'minecraft:builtin/entity'}");
        models.put("vanilla shape", "{'parent':'minecraft:block/slab'," + cube + "}");
        models.put("missing texture", "{'parent':'minecraft:block/cube_all','textures':{'all':'test:block/nope'}}");
        models.put("missing vanilla texture", "{'parent':'minecraft:block/cube_all','textures':{'all':'minecraft:block/stone'}}");
        models.put("no layer0", "{'parent':'minecraft:item/generated','textures':{'layer1':'test:item/x'}}");
        file("assets/test/items/i.json", "{'model':{'type':'minecraft:model','model':'test:block/i'}}");
        for (var e : models.entrySet()) {
            file("assets/test/models/block/i.json", e.getValue());
            var r = renderer().render("test", "i");
            assertEquals(ServerIconRenderer.Kind.SKIPPED, r.kind(), e.getKey());
            assertNull(r.png());
            assertFalse(r.detail().isEmpty(), e.getKey() + " says why");
        }
        files.put("assets/test/models/block/i.json", "not json {".getBytes(StandardCharsets.UTF_8));
        assertEquals(ServerIconRenderer.Kind.SKIPPED, renderer().render("test", "i").kind(), "broken JSON");
        files.put("assets/test/textures/block/a.png", new byte[]{1, 2, 3});
        file("assets/test/models/block/i.json", "{'parent':'minecraft:block/cube_all'," + cube + "}");
        assertEquals(ServerIconRenderer.Kind.SKIPPED, renderer().render("test", "i").kind(), "broken PNG");
    }

    // ---- golden images -----------------------------------------------------------------------------------------------

    /**
     * Compares with {@code src/test/resources/icons/golden/<name>.png} (made from the test textures above): every
     * channel within 4, at most 8 pixels further off. On a mismatch the render is written to build/test-goldens.
     */
    private static void assertGolden(String name, ServerIconRenderer.Result r) throws IOException {
        assertNotNull(r.argb(), r.detail());
        byte[] actual = r.png();
        try (var in = ServerIconRendererTest.class.getResourceAsStream("/icons/golden/" + name + ".png")) {
            int[] expected = in == null ? null : PngReader.read(in.readAllBytes()).argb();
            int off = 0;
            if (expected != null) {
                for (int i = 0; i < expected.length; i++) {
                    for (int s = 0; s < 32; s += 8) {
                        if (Math.abs(((expected[i] >>> s) & 0xFF) - ((r.argb()[i] >>> s) & 0xFF)) > 4) {
                            off++;
                            break;
                        }
                    }
                }
            }
            if (expected == null || off > 8) {
                var dir = Path.of(System.getProperty("cccloud.goldenOut", "test-goldens"));
                Files.createDirectories(dir);
                Files.write(dir.resolve(name + ".png"), actual);
                fail(expected == null ? "no golden image " + name + ".png; the render is in " + dir.toAbsolutePath()
                    : off + " pixels differ from " + name + ".png; the render is in " + dir.toAbsolutePath());
            }
        }
    }
}
