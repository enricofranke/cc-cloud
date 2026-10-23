package ai.ezfox.cccloud;

import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.command.CloudCommand;
import ai.ezfox.cccloud.network.CloudNetwork;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
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
        ai.ezfox.cccloud.gui.GuiNetwork.init(modEventBus);
        ai.ezfox.cccloud.icons.IconService.init(modEventBus);
        // Config changes (ranges, power) change the topology.
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> ai.ezfox.cccloud.network.NetworkService.markDirty());
        // Soft dependencies: the compat classes (and with them every AE2/RS class) are only loaded if the mod is there.
        if (ModList.get().isLoaded("ae2")) ai.ezfox.cccloud.compat.ae2.Ae2Compat.init(modEventBus);
        if (ModList.get().isLoaded("refinedstorage")) ai.ezfox.cccloud.compat.rs.RsCompat.init(modEventBus);
        // v0.5 integrations (device cards, server card, FTB Teams/Chunks, Simple Backups): compat/<modid> packages.
        ai.ezfox.cccloud.compat.Integrations.init(modEventBus, modContainer);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.UPLINK_BE.get(), (uplink, side) -> uplink.peripheral());
        // The mainframe is a peripheral on both halves; the block entity lives in the lower half.
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> {
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            return mainframe == null ? null : mainframe.peripheral();
        }, ModRegistry.CLOUD_MAINFRAME.get());
        // Relays: FE input from every side.
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, ModRegistry.RELAY_BE.get(), (relay, side) -> relay.energy());
        // FE input on both halves, all sides (receive-only).
        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, (level, pos, state, be, side) -> {
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            return mainframe == null ? null : mainframe.energy();
        }, ModRegistry.CLOUD_MAINFRAME.get());
    }
}
