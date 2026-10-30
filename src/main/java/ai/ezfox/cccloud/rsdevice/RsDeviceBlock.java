package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Shared parts of the Cloud Redstone blocks (plates: switch, button, sensor; the lamp). */
public interface RsDeviceBlock {
    RsKind kind();

    static BlockEntity newEntity(BlockPos pos, BlockState state) {
        return new RsDeviceBlockEntity(pos, state);
    }

    static <T extends BlockEntity> @Nullable BlockEntityTicker<T> ticker(Level level, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModRegistry.RS_DEVICE_BE.get()) return null;
        return (l, p, s, be) -> ((RsDeviceBlockEntity) be).serverTick((ServerLevel) l);
    }

    /**
     * Right-click: sneak (or any click on a sensor) opens the device's screen, otherwise the device reacts like its
     * vanilla counterpart (switch and lamp toggle, button presses).
     */
    static InteractionResult use(RsKind kind, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof RsDeviceBlockEntity device)) return InteractionResult.PASS;
        if (player.isSecondaryUseActive() || kind == RsKind.SENSOR) {
            if (player instanceof ServerPlayer serverPlayer) RsScreens.open(serverPlayer, device);
            return InteractionResult.SUCCESS;
        }
        device.useInWorld(player);
        return InteractionResult.SUCCESS;
    }
}
