package ai.ezfox.cccloud.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.util.List;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * One separately animated part (docs/MODELS.md), baked from its block model and drawn by a block entity renderer. Our
 * own minimal standalone model (the quads plus the render type of their atlas), so the client needs no CC: Tweaked
 * classes; the parts use block textures only.
 */
public final class PartModel {
    private final List<BakedQuad> quads;
    private final RenderType renderType;

    private PartModel(List<BakedQuad> quads, RenderType renderType) {
        this.quads = quads;
        this.renderType = renderType;
    }

    /** Bakes a resolved model without rotation (the renderers rotate it). */
    static PartModel bake(ResolvedModel model, ModelBaker baker) {
        var slots = model.getTopTextureSlots();
        List<BakedQuad> quads = model.bakeTopGeometry(slots, baker, BlockModelRotation.IDENTITY).getAll();
        return new PartModel(quads, renderType(quads));
    }

    /** Cutout unless a texture has translucent pixels; the item sheet only for a model made of item textures. */
    private static RenderType renderType(List<BakedQuad> quads) {
        boolean translucent = quads.stream().anyMatch(q -> q.materialInfo().itemRenderType().hasBlending());
        boolean items = !quads.isEmpty() && TextureAtlas.LOCATION_ITEMS.equals(quads.getFirst().materialInfo().sprite().atlasLocation());
        if (items) return translucent ? Sheets.translucentItemSheet() : Sheets.cutoutItemSheet();
        return translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet();
    }

    /** Draws the part with the current pose, light and overlay, untinted. */
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        collector.submitCustomGeometry(pose, renderType, (at, buffer) -> {
            var instance = new QuadInstance();
            instance.setLightCoords(light);
            instance.setOverlayCoords(overlay);
            instance.setColor(-1);
            for (var quad : quads) buffer.putBakedQuad(at, quad, instance);
        });
    }
}
