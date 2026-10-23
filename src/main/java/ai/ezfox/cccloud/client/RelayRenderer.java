package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.relay.CloudRelayBlockEntity;
import ai.ezfox.cccloud.relay.RelayStatus;
import ai.ezfox.cccloud.relay.RelayType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Emitter ring (relays) and floating core (ender relay), pivots per docs/MODELS.md: rings at (8, 13, 8), core at
 * (8, 11.5, 8), both around Y. Slow spin online, faster connecting, stopped offline / without power. No per-frame
 * allocations, culled beyond 32 blocks, static with the client config {@code animations=false}.
 */
public class RelayRenderer implements BlockEntityRenderer<CloudRelayBlockEntity, RelayRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0;

    public static class State extends BlockEntityRenderState {
        RelayType type = RelayType.RELAY;
        float angle;
        float bob;
        final org.joml.Quaternionf rotation = new org.joml.Quaternionf();
    }

    public RelayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return MainframeRenderer.VIEW_DISTANCE;
    }

    @Override
    public void extractRenderState(CloudRelayBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, state, crumbling);
        state.type = be.type();
        if (!CloudClientConfig.animations()) {
            state.angle = 0;
            state.bob = 0;
            return;
        }
        var status = switch (be.getBlockState().getValue(RelayStatus.PROPERTY)) {
            case ONLINE -> CloudStatus.ONLINE;
            case CONNECTING -> CloudStatus.CONNECTING;
            case OFFLINE, NO_POWER -> CloudStatus.OFFLINE;
        };
        // ~0.25 rev/s online for the rings, ~0.5 rev/s for the ender core; connecting spins faster.
        state.angle = be.clientAnimation().advance(status, state.type == RelayType.ENDER ? 180f : 90f, 3f);
        state.bob = state.type == RelayType.ENDER && status == CloudStatus.ONLINE
            ? Mth.sin((Util.getMillis() % 3_600_000L) / 1000f * 2f) * 0.5f / 16f : 0;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var part = switch (state.type) {
            case RELAY -> CloudModels.RELAY_RING;
            case ADVANCED -> CloudModels.ADVANCED_RELAY_RING;
            case ENDER -> CloudModels.ENDER_RELAY_CORE;
        };
        var model = part.get();
        if (model == null) return;
        float pivotY = state.type == RelayType.ENDER ? 11.5f / 16 : 13f / 16;
        pose.pushPose();
        pose.translate(0, state.bob, 0);
        pose.rotateAround(state.rotation.rotationY(state.angle * Mth.DEG_TO_RAD), 8f / 16, pivotY, 8f / 16);
        model.submit(pose, collector, state.type == RelayType.ENDER ? FULL_BRIGHT : state.lightCoords, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
