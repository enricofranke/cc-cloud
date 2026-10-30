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
        // GameTest runs only: test machines for the Cloud Probe / side tests and a claim stand-in (ProbeTests).
        if (Boolean.getBoolean("cccloud.gametest")) {
            modEventBus.addListener(ai.ezfox.cccloud.gametest.ProbeTests::registerTestCapabilities);
            ai.ezfox.cccloud.gametest.ProbeTests.init();
        }
        CloudNetwork.init();
        CloudCommand.init();
        ai.ezfox.cccloud.block.MainframeGuard.init();
        ai.ezfox.cccloud.access.Access.init();
        ai.ezfox.cccloud.rules.RuleService.init();
        ai.ezfox.cccloud.scene.SceneService.init();
        ai.ezfox.cccloud.production.ProductionService.init();
        ai.ezfox.cccloud.push.PushService.init();
        ai.ezfox.cccloud.terminal.TerminalService.init();
        ai.ezfox.cccloud.history.HistoryService.init();
        ai.ezfox.cccloud.map.MapTileService.init();
        ai.ezfox.cccloud.voxel.VoxelService.init();
        ai.ezfox.cccloud.access.TeamProviders.setDisabled(() -> java.util.Set.copyOf(CloudConfig.get(CloudConfig.DISABLED_TEAM_PROVIDERS)));
        ai.ezfox.cccloud.gui.GuiNetwork.init(modEventBus);
        ai.ezfox.cccloud.upgrade.ChunkAnchor.init(modEventBus);
        // Bandwidth upgrade: browsers per mainframe (only while a game server runs; the web unit tests have none).
        ai.ezfox.cccloud.web.WebHub.browserLimit = id -> ai.ezfox.cccloud.network.CloudNetwork.server() == null ? Integer.MAX_VALUE
            : ai.ezfox.cccloud.upgrade.Upgrades.effects(id).browsers();
        ai.ezfox.cccloud.icons.IconService.init(modEventBus);
        ai.ezfox.cccloud.voxel.BlockTextureService.init(modEventBus);
        // Config changes (ranges, power) change the topology.
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> ai.ezfox.cccloud.network.NetworkService.markDirty());
        // Soft dependencies: the compat classes (and with them every AE2/RS class) are only loaded if the mod is there.
        if (ModList.get().isLoaded("ae2")) ai.ezfox.cccloud.compat.ae2.Ae2Compat.init(modEventBus);
        if (ModList.get().isLoaded("refinedstorage")) ai.ezfox.cccloud.compat.rs.RsCompat.init(modEventBus);
        // v0.5 integrations (device cards, server card, FTB Teams/Chunks, Simple Backups): compat/<modid> packages.
        ai.ezfox.cccloud.compat.Integrations.init(modEventBus, modContainer);
        // v0.7 in-game clients: Cloud Tablet and Cloud Display (ingame/, tablet/, display/).
        ai.ezfox.cccloud.ingame.InGameRegistry.init(modEventBus);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.UPLINK_BE.get(), (uplink, side) -> uplink.peripheral());
        // The mainframe is a peripheral on both halves; the block entity lives in the lower half.
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> {
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            return mainframe == null ? null : mainframe.peripheral();
        }, ModRegistry.CLOUD_MAINFRAME.get());
        // Cables: mainframe (both halves), relays and uplinks are CC wired elements; relays are peripherals too.
        event.registerBlockEntity(dan200.computercraft.api.network.wired.WiredElementCapability.get(), ModRegistry.UPLINK_BE.get(), (uplink, side) -> uplink.wired());
        event.registerBlockEntity(dan200.computercraft.api.network.wired.WiredElementCapability.get(), ModRegistry.RELAY_BE.get(), (relay, side) -> relay.wired());
        event.registerBlockEntity(PeripheralCapability.get(), ModRegistry.RELAY_BE.get(), (relay, side) -> relay.peripheral());
        event.registerBlock(dan200.computercraft.api.network.wired.WiredElementCapability.get(), (level, pos, state, be, side) -> {
            var mainframe = CloudMainframeBlock.getBlockEntity(level, pos, state);
            return mainframe == null ? null : mainframe.wired();
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
