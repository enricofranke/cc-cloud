package ai.ezfox.cccloud.client.display;

import ai.ezfox.cccloud.display.CloudDisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a Cloud Display's content: the origin block renders one quad over the whole rectangle with the display's
 * cached texture ({@link DisplayTextures}, repainted at most 4 times per second). Same face transform as CC: Tweaked's
 * monitors. Culled beyond 48 blocks; full brightness like a lit screen.
 */
public class DisplayRenderer implements BlockEntityRenderer<CloudDisplayBlockEntity, DisplayRenderer.State> {
    public static final int VIEW_DISTANCE = 48;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Just in front of the model's screen face, so it never z-fights. */
    private static final float Z = 0.5f + 0.002f;

    public static class State extends BlockEntityRenderState {
        @Nullable Identifier texture;
        Direction direction = Direction.NORTH;
        Direction front = Direction.NORTH;
        int width = 1;
        int height = 1;
    }

    public DisplayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(CloudDisplayBlockEntity display) {
        if (!display.isOrigin()) return new AABB(display.getBlockPos());
        return new AABB(display.getBlockPos()).minmax(new AABB(display.toWorldPos(display.width() - 1, display.height() - 1)));
    }

    @Override
    public void extractRenderState(CloudDisplayBlockEntity display, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(display, state, crumbling);
        state.texture = null;
        if (!display.isOrigin() || display.content().isEmpty()) return;
        state.texture = DisplayTextures.get(display);
        state.direction = display.direction();
        state.front = display.front();
        state.width = display.width();
        state.height = display.height();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.texture == null) return;
        float yaw = state.direction.toYRot();
        float pitch = state.front == Direction.DOWN ? 90f : state.front == Direction.UP ? 270f : 0f;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YN.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(-0.5, state.height - 0.5, 0);
        float w = state.width, h = state.height;
        collector.submitCustomGeometry(pose, RenderTypes.text(state.texture), (p, buffer) -> {
            buffer.addVertex(p, 0, -h, Z).setColor(-1).setUv(0, 1).setLight(FULL_BRIGHT);
            buffer.addVertex(p, w, -h, Z).setColor(-1).setUv(1, 1).setLight(FULL_BRIGHT);
            buffer.addVertex(p, w, 0, Z).setColor(-1).setUv(1, 0).setLight(FULL_BRIGHT);
            buffer.addVertex(p, 0, 0, Z).setColor(-1).setUv(0, 0).setLight(FULL_BRIGHT);
        });
        pose.popPose();
    }
}
