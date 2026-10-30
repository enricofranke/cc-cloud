package ai.ezfox.cccloud.icons;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Item definition → model → parent chain → texture, as on a dedicated server without a client. */
class IconFallbackTest {
    final Map<String, byte[]> files = new HashMap<>();
    final byte[] png = PngCodec.encode(16, 16, new int[16 * 16]);

    void put(String path, String json) {
        files.put(path, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void followsItemDefinitionAndParents() {
        put("assets/mod/items/gear.json", "{\"model\":{\"type\":\"minecraft:condition\",\"on_true\":{\"type\":\"minecraft:model\",\"model\":\"mod:item/gear_on\"},"
            + "\"on_false\":{\"type\":\"minecraft:model\",\"model\":\"mod:item/gear\"}}}");
        put("assets/mod/models/item/gear.json", "{\"parent\":\"mod:item/base\",\"textures\":{\"layer0\":\"#main\"}}");
        put("assets/mod/models/item/base.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"main\":\"mod:item/gear_texture\"}}");
        files.put("assets/mod/textures/item/gear_texture.png", png);
        assertArrayEquals(png, IconFallback.resolve("mod", "gear", files::get));
        assertEquals("mod:item/gear", IconFallback.itemModel("mod", "gear", files::get), "the fallback branch is preferred");
    }

    @Test
    void blockItemsUseFaceTextures() {
        put("assets/mod/items/machine.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mod:block/machine\"}}");
        put("assets/mod/models/block/machine.json", "{\"parent\":\"minecraft:block/orientable\",\"textures\":{\"front\":\"mod:block/machine_front\",\"side\":\"mod:block/casing\"}}");
        files.put("assets/mod/textures/block/machine_front.png", png);
        assertArrayEquals(png, IconFallback.resolve("mod", "machine", files::get));
    }

    @Test
    void spriteObjectsAsTextures() {
        // 26.1 models may write a texture as {"sprite": …, "force_translucent": true} (vanilla glass does)
        put("assets/mod/items/pane.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mod:block/pane\"}}");
        put("assets/mod/models/block/pane.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":{\"force_translucent\":true,\"sprite\":\"mod:block/pane\"}}}");
        files.put("assets/mod/textures/block/pane.png", png);
        assertArrayEquals(png, IconFallback.resolve("mod", "pane", files::get));
    }

    @Test
    void missingOrBrokenData() {
        assertNull(IconFallback.resolve("mod", "nothing", files::get));
        put("assets/mod/items/bad.json", "{not json");
        assertNull(IconFallback.resolve("mod", "bad", files::get));
        put("assets/mod/models/item/loop.json", "{\"parent\":\"mod:item/loop\",\"textures\":{\"layer0\":\"#layer0\"}}");
        assertNull(IconFallback.texture("mod:item/loop", files::get), "self references and parent loops end");
        assertArrayEquals(new String[]{"minecraft", "item/stick"}, IconFallback.split("item/stick"));
    }
}
