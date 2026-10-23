package ai.ezfox.cccloud.client.display;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.display.CloudDisplayBlockEntity;
import ai.ezfox.cccloud.display.DisplayLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One dynamic texture per visible display (origin block). Repainted when the content, size or click mode changes, at
 * most 4 times per second; textures of displays that were not drawn for 30 s are freed. Render thread only.
 */
public final class DisplayTextures {
    private static final long MIN_INTERVAL_MS = 250;
    private static final long IDLE_MS = 30_000;

    private static final class Entry {
        final DynamicTexture texture;
        final Identifier id;
        final int width, height;
        int version = -1;
        boolean interactive;
        long painted;
        long used;

        Entry(BlockPos pos, int width, int height) {
            this.width = width;
            this.height = height;
            this.texture = new DynamicTexture(() -> "Cloud Display " + pos.toShortString(), width * DisplayLayout.PX_PER_BLOCK,
                height * DisplayLayout.PX_PER_BLOCK, true);
            this.id = Identifier.fromNamespaceAndPath(CCCloud.MODID, "display/" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + System.nanoTime());
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();

    private DisplayTextures() {
    }

    /** The texture of an origin display, repainted if needed (and allowed); null if there is nothing to draw. */
    public static @Nullable Identifier get(CloudDisplayBlockEntity display) {
        long now = System.currentTimeMillis();
        var pos = display.getBlockPos().immutable();
        var entry = ENTRIES.get(pos);
        if (entry != null && (entry.width != display.width() || entry.height != display.height())) {
            entry.close();
            ENTRIES.remove(pos);
            entry = null;
        }
        if (entry == null) {
            entry = new Entry(pos, display.width(), display.height());
            ENTRIES.put(pos, entry);
        }
        entry.used = now;
        boolean stale = entry.version != display.contentVersion() || entry.interactive != display.interactive();
        if (stale && (entry.version == -1 || now - entry.painted >= MIN_INTERVAL_MS)) {
            GlyphCanvas.loadFont();
            var pixels = entry.texture.getPixels();
            if (pixels != null) {
                DisplayPainter.paint(new GlyphCanvas(pixels), parse(display.content()), display.width(), display.height(), display.interactive());
                entry.texture.upload();
            }
            entry.version = display.contentVersion();
            entry.interactive = display.interactive();
            entry.painted = now;
        }
        return entry.id;
    }

    static @Nullable JsonObject parse(String json) {
        if (json.isEmpty()) return null;
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Frees textures of displays not drawn for a while (called once per client tick). */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        for (Iterator<Entry> it = ENTRIES.values().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (now - entry.used > IDLE_MS) {
                entry.close();
                it.remove();
            }
        }
    }

    /** Frees everything (world unload, resource reload). */
    public static void clear() {
        ENTRIES.values().forEach(Entry::close);
        ENTRIES.clear();
        GlyphCanvas.reset();
    }

    /** Test/debug: textures alive. */
    public static int size() {
        return ENTRIES.size();
    }

    static JsonObject empty() {
        return new JsonObject();
    }
}
