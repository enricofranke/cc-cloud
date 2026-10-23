package ai.ezfox.cccloud.client.voxel;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.icons.PngCodec;
import ai.ezfox.cccloud.voxel.BlockTextureService;
import ai.ezfox.cccloud.voxel.BlockTextureStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Client part of the voxel view's block textures (stage 2): for each requested block, the sprite of every face of its
 * default state's model (first quad per side; models without side quads use their first inner quad, e.g. flowers),
 * scaled to 16×16 (first animation frame), as PNG. Runs on the client thread when the server asks; same opt-in as the
 * item icons (client config {@code shareIconRenders}).
 */
public final class ClientBlockTextures {
    private static final Direction[] ORDER = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private ClientBlockTextures() {
    }

    public static void init() {
        BlockTextureService.clientRequestHandler = payload -> Minecraft.getInstance().execute(() -> handle(payload.ids()));
    }

    private static void handle(List<String> ids) {
        if (!ai.ezfox.cccloud.client.CloudClientConfig.shareIconRenders()) return;
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(BlockTextureService.UploadPayload.TYPE)) return;
        var blocks = new ArrayList<BlockTextureService.BlockFaces>();
        var sprites = new LinkedHashMap<String, byte[]>();
        for (String id : ids) {
            try {
                blocks.add(read(id, sprites));
            } catch (RuntimeException | LinkageError e) {
                CCCloud.LOGGER.debug("[cccloud] Cannot read the textures of {}", id, e);
                blocks.add(new BlockTextureService.BlockFaces(id, List.of(), 0));
            }
            if (sprites.size() > BlockTextureService.MAX_SPRITES_PER_UPLOAD - 6) {
                send(blocks, sprites);
                blocks.clear();
                sprites.clear();
            }
        }
        if (!blocks.isEmpty()) send(blocks, sprites);
    }

    private static void send(List<BlockTextureService.BlockFaces> blocks, Map<String, byte[]> sprites) {
        var list = new ArrayList<BlockTextureService.Sprite>();
        sprites.forEach((name, png) -> list.add(new BlockTextureService.Sprite(name, png)));
        ClientPacketDistributor.sendToServer(new BlockTextureService.UploadPayload(List.copyOf(blocks), list));
    }

    static BlockTextureService.BlockFaces read(String id, Map<String, byte[]> sprites) {
        var key = Identifier.tryParse(id);
        var block = key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
        if (block == null) return new BlockTextureService.BlockFaces(id, List.of(), 0);
        var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(block.defaultBlockState());
        var parts = new ArrayList<BlockStateModelPart>();
        model.collectParts(RandomSource.create(42L), parts);
        BakedQuad inner = null;
        for (var part : parts) {
            var q = part.getQuads(null);
            if (!q.isEmpty()) {
                inner = q.getFirst();
                break;
            }
        }
        var faces = new ArrayList<String>(6);
        int tint = 0;
        for (int i = 0; i < 6; i++) {
            BakedQuad quad = null;
            for (var part : parts) {
                var q = part.getQuads(ORDER[i]);
                if (!q.isEmpty()) {
                    quad = q.getFirst();
                    break;
                }
            }
            if (quad == null) quad = inner;
            String name = quad == null ? null : sprite(quad.materialInfo().sprite().contents(), sprites);
            faces.add(name == null ? "" : name);
            if (name != null && quad.materialInfo().isTinted()) tint |= 1 << i;
        }
        return new BlockTextureService.BlockFaces(id, faces, tint);
    }

    /** The sprite's name, its 16×16 PNG added to {@code sprites}; null for the missing texture or unreadable images. */
    private static @Nullable String sprite(SpriteContents contents, Map<String, byte[]> sprites) {
        String name = contents.name().toString();
        if (name.equals("minecraft:missingno") || !BlockTextureStore.ID.matcher(name).matches()) return null;
        if (sprites.containsKey(name)) return name;
        var image = contents.getOriginalImage();
        int w = contents.width(), h = contents.height(); // first frame
        if (image == null || w <= 0 || h <= 0 || w > image.getWidth() || h > image.getHeight()) return null;
        int t = BlockTextureStore.TILE;
        var argb = new int[t * t];
        for (int y = 0; y < t; y++) {
            for (int x = 0; x < t; x++) {
                // box average when larger than 16 (resource packs), nearest when smaller
                int x0 = x * w / t, x1 = Math.max(x0 + 1, (x + 1) * w / t), y0 = y * h / t, y1 = Math.max(y0 + 1, (y + 1) * h / t);
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        int p = image.getPixel(xx, yy);
                        a += p >>> 24;
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                argb[y * t + x] = (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        byte[] png = PngCodec.encode(t, t, argb);
        if (png.length > BlockTextureStore.MAX_PNG) return null;
        sprites.put(name, png);
        return name;
    }
}
