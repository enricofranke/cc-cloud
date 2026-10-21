package ai.ezfox.cccloud;

import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.command.CloudCommand;
import ai.ezfox.cccloud.network.CloudNetwork;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

@Mod(CCCloud.MODID)
public class CCCloud {
    public static final String MODID = "cccloud";
    public static final Logger LOGGER = CloudLog.LOGGER;

    public CCCloud(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistry.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, CloudConfig.SPEC);
        modEventBus.addListener(CCCloud::registerCapabilities);
        CloudNetwork.init();
        CloudCommand.init();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.UPLINK_BE.get(), (uplink, side) -> uplink.peripheral());
        // The mainframe is a peripheral on both halves; the block entity lives in the lower half.
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> {
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            return mainframe == null ? null : mainframe.peripheral();
        }, ModRegistry.CLOUD_MAINFRAME.get());
    }
}
