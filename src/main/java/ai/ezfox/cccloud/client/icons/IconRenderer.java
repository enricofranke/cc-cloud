package ai.ezfox.cccloud.client.icons;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.icons.IconService;
import ai.ezfox.cccloud.icons.PngCodec;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Renders inventory icons of items into an offscreen atlas (like vanilla's {@code GuiItemAtlas}: the same item render
 * state, lighting and orthographic projection, so icons look exactly like in the inventory), reads the atlas back
 * asynchronously and encodes each slot as a 32×32 PNG. Render thread only; work is limited to a time budget per frame.
 */
public final class IconRenderer {
    public static final int SLOT = PngCodec.ICON_SIZE;
    private static final int ATLAS = 512;
    private static final int SLOTS_PER_ROW = ATLAS / SLOT;
    private static final int SLOTS = SLOTS_PER_ROW * SLOTS_PER_ROW;

    /** One finished icon; png is empty if the item could not be rendered. */
    public record Result(String id, byte[] png) {
    }

    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final List<String> slots = new ArrayList<>(SLOTS);
    private final Consumer<List<Result>> output;
    private @Nullable GpuTexture texture;
    private @Nullable GpuTextureView textureView;
    private @Nullable GpuTexture depth;
    private @Nullable GpuTextureView depthView;
    private final PoseStack poseStack = new PoseStack();
    private final Projection projection = new Projection();
    private @Nullable ProjectionMatrixBuffer projectionBuffer;
    private boolean readbackPending;
    private long renderNanos;
    private int rendered;

    public IconRenderer(Consumer<List<Result>> output) {
        this.output = output;
    }

    public void enqueue(List<String> ids) {
        queue.addAll(ids);
    }

    public boolean busy() {
        return !queue.isEmpty() || !slots.isEmpty() || readbackPending;
    }

    public int queued() {
        return queue.size() + slots.size();
    }

    /** Total GPU-submission time spent rendering icons (for the benchmark). */
    public long renderNanos() {
        return renderNanos;
    }

    public int rendered() {
        return rendered;
    }

    /**
     * Renders queued icons for up to {@code budgetNanos} (render thread, end of frame). When the atlas is full or the
     * queue is empty, the atlas is read back and the PNGs are handed to the output (on the render thread).
     */
    public void renderFrame(long budgetNanos) {
        if (readbackPending || (queue.isEmpty() && slots.isEmpty())) return;
        ensureTextures();
        long start = System.nanoTime();
        var failed = new ArrayList<Result>();
        while (!queue.isEmpty() && slots.size() < SLOTS && System.nanoTime() - start < budgetNanos) {
            String id = queue.pollFirst();
            if (!drawToSlot(slots.size(), id)) {
                failed.add(new Result(id, new byte[0]));
                continue;
            }
            slots.add(id);
            rendered++;
        }
        renderNanos += System.nanoTime() - start;
        if (!failed.isEmpty()) output.accept(failed);
        if (!slots.isEmpty() && (slots.size() >= SLOTS || queue.isEmpty())) readBack();
    }

    private void ensureTextures() {
        if (texture != null) return;
        var device = RenderSystem.getDevice();
        texture = device.createTexture("cccloud icon atlas",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.RGBA8, ATLAS, ATLAS, 1, 1);
        textureView = device.createTextureView(texture);
        depth = device.createTexture("cccloud icon atlas depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.DEPTH32, ATLAS, ATLAS, 1, 1);
        depthView = device.createTextureView(depth);
        projectionBuffer = new ProjectionMatrixBuffer("cccloud icons");
        device.createCommandEncoder().clearColorAndDepthTextures(texture, 0, depth, 1.0);
    }

    /** Same steps as vanilla's GuiItemAtlas#drawToSlot. */
    private boolean drawToSlot(int index, String id) {
        var mc = Minecraft.getInstance();
        var key = Identifier.tryParse(id);
        var item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        if (item == null || item == Items.AIR) return false;
        int left = (index % SLOTS_PER_ROW) * SLOT;
        int top = (index / SLOTS_PER_ROW) * SLOT;
        int bottom = top + SLOT;
        try {
            var state = new TrackingItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, new ItemStack(item), ItemDisplayContext.GUI, mc.level, null, 0);
            if (state.isEmpty()) return false;
            poseStack.pushPose();
            poseStack.translate(left + SLOT / 2.0F, top + SLOT / 2.0F, 0.0F);
            poseStack.scale(SLOT, -SLOT, SLOT);
            RenderSystem.outputColorTextureOverride = textureView;
            RenderSystem.outputDepthTextureOverride = depthView;
            projection.setupOrtho(-1000.0F, 1000.0F, ATLAS, ATLAS, true);
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
            RenderSystem.enableScissorForRenderTypeDraws(left, ATLAS - bottom, SLOT, SLOT);
            mc.gameRenderer.getLighting().setupFor(state.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
            state.submit(poseStack, mc.gameRenderer.getSubmitNodeStorage(), 15728880, OverlayTexture.NO_OVERLAY, 0);
            mc.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
            mc.renderBuffers().bufferSource().endBatch();
            return true;
        } catch (RuntimeException | LinkageError e) {
            CCCloud.LOGGER.debug("[cccloud] Could not render the icon of {}", id, e);
            return false;
        } finally {
            RenderSystem.disableScissorForRenderTypeDraws();
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            while (!poseStack.isEmpty()) poseStack.popPose();
        }
    }

    private void readBack() {
        var device = RenderSystem.getDevice();
        var ids = List.copyOf(slots);
        slots.clear();
        readbackPending = true;
        var buffer = device.createBuffer(() -> "cccloud icon readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) ATLAS * ATLAS * 4);
        var encoder = device.createCommandEncoder();
        encoder.copyTextureToBuffer(texture, buffer, 0L, () -> {
            var results = new ArrayList<Result>(ids.size());
            try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false)) {
                var data = view.data();
                for (int i = 0; i < ids.size(); i++) {
                    int left = (i % SLOTS_PER_ROW) * SLOT;
                    int top = (i / SLOTS_PER_ROW) * SLOT;
                    var argb = new int[SLOT * SLOT];
                    boolean any = false;
                    for (int y = 0; y < SLOT; y++) {
                        int row = ATLAS - 1 - (top + y); // GPU textures are bottom-up
                        for (int x = 0; x < SLOT; x++) {
                            int o = (row * ATLAS + left + x) * 4;
                            int r = data.get(o) & 0xFF, g = data.get(o + 1) & 0xFF, b = data.get(o + 2) & 0xFF, a = data.get(o + 3) & 0xFF;
                            argb[y * SLOT + x] = a << 24 | r << 16 | g << 8 | b;
                            any |= a != 0;
                        }
                    }
                    results.add(new Result(ids.get(i), any ? PngCodec.encode(SLOT, SLOT, argb) : new byte[0]));
                }
            } finally {
                buffer.close();
                device.createCommandEncoder().clearColorAndDepthTextures(texture, 0, depth, 1.0);
                readbackPending = false;
            }
            output.accept(results);
        }, 0);
    }

    public void close() {
        if (texture != null) texture.close();
        if (textureView != null) textureView.close();
        if (depth != null) depth.close();
        if (depthView != null) depthView.close();
        if (projectionBuffer != null) projectionBuffer.close();
        texture = null;
    }

    /** Groups results into upload payloads of at most ~24 KB / 64 icons. */
    public static List<IconService.UploadPayload> pack(List<Result> results) {
        var payloads = new ArrayList<IconService.UploadPayload>();
        var current = new ArrayList<IconService.Icon>();
        int bytes = 0;
        for (var r : results) {
            if (!current.isEmpty() && (bytes + r.png().length > 24 * 1024 || current.size() >= IconService.MAX_UPLOAD_ICONS)) {
                payloads.add(new IconService.UploadPayload(current));
                current = new ArrayList<>();
                bytes = 0;
            }
            current.add(new IconService.Icon(r.id(), r.png()));
            bytes += r.png().length + r.id().length() + 8;
        }
        if (!current.isEmpty()) payloads.add(new IconService.UploadPayload(current));
        return payloads;
    }
}
