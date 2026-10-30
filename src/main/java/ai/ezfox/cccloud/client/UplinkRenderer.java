package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
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
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spinning ring (normal uplink) or ender core (advanced uplink): fast while connecting, steady online, stopped offline.
 * Compact uplinks (blockstate {@code mount} other than {@code full}) have no moving part.
 */
public class UplinkRenderer implements BlockEntityRenderer<CloudUplinkBlockEntity, UplinkRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean advanced;
        /** Compact puck (non-full face): no moving part. */
        boolean compact;
        CloudStatus status = CloudStatus.OFFLINE;
        float angle;
        float seconds;
        final org.joml.Quaternionf rotation = new org.joml.Quaternionf();
    }

    @Override
    public int getViewDistance() {
        return MainframeRenderer.VIEW_DISTANCE;
    }

    public UplinkRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CloudUplinkBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        var blockState = be.getBlockState();
        state.facing = blockState.getValue(CloudUplinkBlock.FACING);
        state.status = blockState.getValue(CloudUplinkBlock.STATUS);
        state.advanced = blockState.getBlock() instanceof CloudUplinkBlock b && b.isAdvanced();
        state.compact = blockState.getValue(CloudUplinkBlock.MOUNT).compact();
        if (CloudClientConfig.animations()) {
            state.seconds = (Util.getMillis() % 3_600_000L) / 1000f;
            state.angle = be.clientAnimation().advance(state.status, state.advanced ? 45f : 90f, 3f);
        } else {
            state.angle = 0;
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.compact) return;
        var model = state.advanced ? CloudModels.UPLINK_ADVANCED_CORE.get() : CloudModels.UPLINK_RING.get();
        if (model == null) return;
        pose.pushPose();
        Orientation.applyUplink(pose, state.facing);
        // Both parts spin in place around (8, 8, 1.375) on the Z axis (model space, before the facing rotation).
        pose.rotateAround(state.rotation.rotationZ(state.angle * net.minecraft.util.Mth.DEG_TO_RAD), 8f / 16, 8f / 16, 1.375f / 16);
        model.submit(pose, collector, state.advanced ? FULL_BRIGHT : state.lightCoords, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
