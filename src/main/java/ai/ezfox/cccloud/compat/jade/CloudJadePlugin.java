package ai.ezfox.cccloud.compat.jade;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Soft Jade integration (only loaded by Jade): looking at the upper half of a mainframe is redirected to the lower half
 * (where the block entity lives), so Jade's own energy display and this tooltip are the same on both halves. The
 * tooltip shows status, FE, FE/t, linked uplinks, devices and whether the dashboard is on.
 */
@WailaPlugin(CCCloud.MODID)
public final class CloudJadePlugin implements IWailaPlugin {
    static final Identifier UID = Identifier.fromNamespaceAndPath(CCCloud.MODID, "mainframe");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DataProvider.INSTANCE, CloudMainframeBlockEntity.class);
        // Cloud Analyzer: signal, hops, route and reason on uplinks, relays and bridges.
        // Uplinks, relays and the bridges (whose classes only exist with AE2/RS); the provider skips other mods' blocks.
        registration.registerBlockDataProvider(NetworkJadeProvider.INSTANCE, net.minecraft.world.level.block.entity.BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TooltipProvider.INSTANCE, CloudMainframeBlock.class);
        registration.registerBlockComponent(NetworkJadeProvider.Tooltip.INSTANCE, ai.ezfox.cccloud.block.CloudUplinkBlock.class);
        registration.registerBlockComponent(NetworkJadeProvider.Tooltip.INSTANCE, ai.ezfox.cccloud.relay.CloudRelayBlock.class);
        registration.registerBlockComponent(NetworkJadeProvider.Tooltip.INSTANCE, ai.ezfox.cccloud.bridge.CloudBridgeBlock.class);
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (!(accessor instanceof BlockAccessor block) || !(block.getBlock() instanceof CloudMainframeBlock)) return accessor;
            if (block.getBlockState().getValue(CloudMainframeBlock.HALF) != DoubleBlockHalf.UPPER) return accessor;
            var lowerPos = block.getPosition().below();
            var level = block.getLevel();
            var lowerState = level.getBlockState(lowerPos);
            if (!(lowerState.getBlock() instanceof CloudMainframeBlock)) return accessor;
            BlockHitResult lowerHit = block.getHitResult().withPosition(lowerPos);
            return registration.blockAccessor().from(block).hit(lowerHit).blockState(lowerState)
                .blockEntity(() -> level.getBlockEntity(lowerPos)).build();
        });
    }

    private enum DataProvider implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof CloudMainframeBlockEntity mainframe)) return;
            String reason = mainframe.reason();
            data.putString("status", reason != null ? reason : mainframe.status().getSerializedName());
            data.putInt("energy", mainframe.energy().energy());
            data.putInt("capacity", mainframe.energy().capacity());
            data.putLong("usage", mainframe.energyUsage());
            data.putInt("uplinks", mainframe.getLinkedUplinks().size());
            data.putBoolean("enabled", mainframe.isEnabled());
            data.putInt("clients", WebHub.INSTANCE.clientCount(mainframe.getMainframeId()));
            var server = accessor.getLevel().getServer();
            if (server != null) data.putInt("devices", WebAuthData.get(server).devices(mainframe.getMainframeId()).size());
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    private enum TooltipProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var data = accessor.getServerData();
            if (!data.contains("status")) return;
            String status = data.getStringOr("status", "offline");
            var color = switch (status) {
                case "online" -> ChatFormatting.GREEN;
                case "connecting" -> ChatFormatting.YELLOW;
                default -> ChatFormatting.RED;
            };
            tooltip.add(Component.translatable("cccloud.gui.status." + status).withStyle(color));
            tooltip.add(Component.translatable("cccloud.jade.energy", String.format("%,d", data.getIntOr("energy", 0)),
                String.format("%,d", data.getIntOr("capacity", 0)), data.getLongOr("usage", 0)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("cccloud.jade.links", data.getIntOr("uplinks", 0), data.getIntOr("devices", 0),
                data.getIntOr("clients", 0)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(data.getBooleanOr("enabled", false) ? "cccloud.jade.dashboard_on" : "cccloud.jade.dashboard_off")
                .withStyle(ChatFormatting.GRAY));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
