package ai.ezfox.cccloud.client.voxel;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.voxel.BlockTextureStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev only ({@code ./gradlew runClient -PtextureCheck=<dir>}, system property {@code cccloud.textureCheck}): joins
 * {@code run/saves/cccloud-bench}, reads the face textures of a few blocks with the real client path, builds the atlas
 * like the server does, writes {@code <dir>/voxel_atlas.png} and the face table to the log, and quits. Inactive without
 * the property.
 */
public final class DevTextureCheck {
    private static final List<String> BLOCKS = List.of("minecraft:stone", "minecraft:grass_block", "minecraft:oak_log", "minecraft:oak_leaves",
        "minecraft:poppy", "minecraft:glass", "minecraft:furnace", "minecraft:water", "minecraft:redstone_lamp", "computercraft:computer_advanced",
        "cccloud:cloud_mainframe");
    private static boolean done;
    private static int ticks;

    private DevTextureCheck() {
    }

    public static void init() {
        String dir = System.getProperty("cccloud.textureCheck", "");
        if (dir.isBlank()) return;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick(Path.of(dir)));
    }

    private static void tick(Path dir) {
        var mc = Minecraft.getInstance();
        if (done || mc.level == null || mc.player == null || ++ticks < 40) return;
        done = true;
        try {
            var sprites = new LinkedHashMap<String, byte[]>();
            var store = new BlockTextureStore();
            var table = new StringBuilder();
            for (String id : BLOCKS) {
                var faces = ClientBlockTextures.read(id, sprites);
                for (var s : sprites.entrySet()) if (!store.hasSprite(s.getKey())) store.putSprite(s.getKey(), s.getValue());
                var array = new String[6];
                for (int i = 0; i < faces.faces().size(); i++) array[i] = faces.faces().get(i).isEmpty() ? null : faces.faces().get(i);
                boolean ok = faces.faces().size() == 6 && store.putBlock(id, "dev", array, faces.tint());
                table.append('\n').append(id).append(" → ").append(faces.faces()).append(" tint=").append(Integer.toBinaryString(faces.tint()))
                    .append(ok ? "" : " (not stored)");
            }
            Files.createDirectories(dir);
            Files.write(dir.resolve("voxel_atlas.png"), store.atlas());
            CCCloud.LOGGER.info("[cccloud] Texture check: {} sprites, atlas {} bytes in {}{}", sprites.size(), store.atlas().length, dir, table);
        } catch (Exception e) {
            CCCloud.LOGGER.error("[cccloud] Texture check failed", e);
        }
        mc.stop();
    }
}
