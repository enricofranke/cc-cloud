package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Spinning fan (lower half) and floating hologram (upper half) of the mainframe. */
public class MainframeRenderer implements BlockEntityRenderer<CloudMainframeBlockEntity, MainframeRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        CloudStatus status = CloudStatus.OFFLINE;
        float fanAngle;
        float seconds;
        int seed;
        boolean animate;
        /** Reused every frame (no per-frame allocations). */
        final org.joml.Quaternionf rotation = new org.joml.Quaternionf();
    }

    /** Animations are only drawn within this many blocks. */
    public static final int VIEW_DISTANCE = 32;

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    public MainframeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CloudMainframeBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        var blockState = be.getBlockState();
        state.facing = blockState.getValue(CloudMainframeBlock.FACING);
        state.status = blockState.getValue(CloudMainframeBlock.STATUS);
        state.animate = CloudClientConfig.animations();
        if (state.animate) {
            state.seconds = (Util.getMillis() % 3_600_000L) / 1000f;
            state.fanAngle = be.clientAnimation().advanceFan(state.status);
        } else {
            state.seconds = 0;
            state.fanAngle = 0;
        }
        state.seed = (int) be.getBlockPos().asLong();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var fan = CloudModels.MAINFRAME_FAN.get();
        if (fan != null) {
            pose.pushPose();
            Orientation.applyMainframe(pose, state.facing);
            pose.rotateAround(state.rotation.rotationX(state.fanAngle * Mth.DEG_TO_RAD), 13.5f / 16, 8f / 16, 8f / 16);
            fan.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        var hologram = CloudModels.MAINFRAME_HOLOGRAM.get();
        if (hologram != null && isHologramVisible(state)) {
            pose.pushPose();
            Orientation.applyMainframe(pose, state.facing);
            // Model coordinates of the hologram are relative to the lower half (y 16..32 is the upper half).
            if (state.animate) {
                float bob = Mth.sin(state.seconds * 2f) * 0.5f / 16f;
                pose.translate(0, bob, 0);
                pose.rotateAround(state.rotation.rotationY(state.seconds * 20f % 360f * Mth.DEG_TO_RAD), 8f / 16, 23.5f / 16, 8f / 16);
            }
            hologram.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /** Online: always. Connecting: flickers. Offline: hidden. */
    private static boolean isHologramVisible(State state) {
        return switch (state.status) {
            case ONLINE -> true;
            case OFFLINE -> false;
            case CONNECTING -> {
                if (!state.animate) yield true;
                int slot = (int) (state.seconds * 12f) + state.seed;
                yield (slot * 1103515245 + 12345 >>> 16) % 5 != 0;
            }
        };
    }

    @Override
    public AABB getRenderBoundingBox(CloudMainframeBlockEntity be) {
        return be.renderBox();
    }
}
