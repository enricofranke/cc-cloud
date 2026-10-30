package ai.ezfox.cccloud;

import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.item.CloudLinkCardItem;
import ai.ezfox.cccloud.item.MainframeLink;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CCCloud.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CCCloud.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CCCloud.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CCCloud.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CCCloud.MODID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CCCloud.MODID);
    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, CCCloud.MODID);

    // Blocks
    public static final DeferredBlock<CloudMainframeBlock> CLOUD_MAINFRAME = BLOCKS.registerBlock("cloud_mainframe", CloudMainframeBlock::new,
        p -> p.mapColor(MapColor.GOLD).strength(2.5f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<CloudUplinkBlock> CLOUD_UPLINK = BLOCKS.registerBlock("cloud_uplink", p -> new CloudUplinkBlock(false, p),
        ModRegistry::uplinkProperties);
    public static final DeferredBlock<CloudUplinkBlock> ADVANCED_CLOUD_UPLINK = BLOCKS.registerBlock("advanced_cloud_uplink", p -> new CloudUplinkBlock(true, p),
        p -> uplinkProperties(p).mapColor(MapColor.GOLD));
    /** Reads one machine at a distance (docs/SPEC.md "Cloud Probe"); shares the uplink block entity. */
    public static final DeferredBlock<ai.ezfox.cccloud.block.CloudProbeBlock> CLOUD_PROBE = BLOCKS.registerBlock("cloud_probe",
        ai.ezfox.cccloud.block.CloudProbeBlock::new, p -> uplinkProperties(p).mapColor(MapColor.COLOR_CYAN).strength(2.0f));

    // v0.4 relays
    public static final DeferredBlock<ai.ezfox.cccloud.relay.CloudRelayBlock> CLOUD_RELAY = BLOCKS.registerBlock("cloud_relay",
        p -> new ai.ezfox.cccloud.relay.CloudRelayBlock(ai.ezfox.cccloud.relay.RelayType.RELAY, p), ModRegistry::relayProperties);
    public static final DeferredBlock<ai.ezfox.cccloud.relay.CloudRelayBlock> ADVANCED_CLOUD_RELAY = BLOCKS.registerBlock("advanced_cloud_relay",
        p -> new ai.ezfox.cccloud.relay.CloudRelayBlock(ai.ezfox.cccloud.relay.RelayType.ADVANCED, p), p -> relayProperties(p).mapColor(MapColor.GOLD));
    public static final DeferredBlock<ai.ezfox.cccloud.relay.CloudRelayBlock> ENDER_RELAY = BLOCKS.registerBlock("ender_relay",
        p -> new ai.ezfox.cccloud.relay.CloudRelayBlock(ai.ezfox.cccloud.relay.RelayType.ENDER, p), p -> relayProperties(p).mapColor(MapColor.GOLD));

    // Cables part B: fiber (docs/SPEC.md "Cables")
    public static final DeferredBlock<ai.ezfox.cccloud.cable.FiberCableBlock> FIBER_CABLE = BLOCKS.registerBlock("fiber_cable",
        ai.ezfox.cccloud.cable.FiberCableBlock::new, p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.3f).sound(SoundType.WOOL).noOcclusion()
            .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<ai.ezfox.cccloud.cable.FiberJunctionBlock> FIBER_JUNCTION = BLOCKS.registerBlock("fiber_junction",
        ai.ezfox.cccloud.cable.FiberJunctionBlock::new, p -> p.mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredBlock<ai.ezfox.cccloud.cable.EnderFiberLinkBlock> ENDER_FIBER_LINK = BLOCKS.registerBlock("ender_fiber_link",
        ai.ezfox.cccloud.cable.EnderFiberLinkBlock::new, p -> p.mapColor(MapColor.GOLD).strength(2.5f).sound(SoundType.METAL).noOcclusion()
            .pushReaction(PushReaction.BLOCK).lightLevel(s -> 4));

    // Cloud Redstone devices (docs/SPEC.md "Cloud Redstone devices"): small, cheap, linked with the Link Card.
    public static final DeferredBlock<ai.ezfox.cccloud.rsdevice.RsPlateBlock> CLOUD_SWITCH = BLOCKS.registerBlock("cloud_switch",
        p -> new ai.ezfox.cccloud.rsdevice.RsPlateBlock(ai.ezfox.cccloud.rsdevice.RsKind.SWITCH, p), ModRegistry::plateProperties);
    public static final DeferredBlock<ai.ezfox.cccloud.rsdevice.RsPlateBlock> CLOUD_BUTTON = BLOCKS.registerBlock("cloud_button",
        p -> new ai.ezfox.cccloud.rsdevice.RsPlateBlock(ai.ezfox.cccloud.rsdevice.RsKind.BUTTON, p), ModRegistry::plateProperties);
    public static final DeferredBlock<ai.ezfox.cccloud.rsdevice.RsPlateBlock> CLOUD_SENSOR = BLOCKS.registerBlock("cloud_sensor",
        p -> new ai.ezfox.cccloud.rsdevice.RsPlateBlock(ai.ezfox.cccloud.rsdevice.RsKind.SENSOR, p), ModRegistry::plateProperties);
    public static final DeferredBlock<ai.ezfox.cccloud.rsdevice.RsLampBlock> CLOUD_LAMP = BLOCKS.registerBlock("cloud_lamp",
        ai.ezfox.cccloud.rsdevice.RsLampBlock::new, p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.3f).sound(SoundType.GLASS)
            .lightLevel(s -> s.getValue(ai.ezfox.cccloud.rsdevice.RsLampBlock.LEVEL)));

    private static BlockBehaviour.Properties plateProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.STONE).noCollision().noOcclusion().strength(0.5f).sound(SoundType.STONE).pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties relayProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)
            .lightLevel(s -> s.getValue(ai.ezfox.cccloud.relay.RelayStatus.PROPERTY) == ai.ezfox.cccloud.relay.RelayStatus.ONLINE ? 5 : 0);
    }

    private static BlockBehaviour.Properties uplinkProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)
            .lightLevel(s -> s.getValue(CloudUplinkBlock.STATUS) == ai.ezfox.cccloud.block.CloudStatus.ONLINE ? 4 : 0);
    }

    // Items
    public static final DeferredItem<ai.ezfox.cccloud.item.CloudMainframeItem> CLOUD_MAINFRAME_ITEM = ITEMS.registerItem("cloud_mainframe",
        p -> new ai.ezfox.cccloud.item.CloudMainframeItem(CLOUD_MAINFRAME.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<?> CLOUD_UPLINK_ITEM = ITEMS.registerSimpleBlockItem(CLOUD_UPLINK);
    public static final DeferredItem<?> ADVANCED_CLOUD_UPLINK_ITEM = ITEMS.registerSimpleBlockItem(ADVANCED_CLOUD_UPLINK, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<ai.ezfox.cccloud.item.CloudProbeItem> CLOUD_PROBE_ITEM = ITEMS.registerItem("cloud_probe",
        p -> new ai.ezfox.cccloud.item.CloudProbeItem(CLOUD_PROBE.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.RARE));
    public static final DeferredItem<?> CLOUD_RELAY_ITEM = ITEMS.registerSimpleBlockItem(CLOUD_RELAY);
    public static final DeferredItem<?> ADVANCED_CLOUD_RELAY_ITEM = ITEMS.registerSimpleBlockItem(ADVANCED_CLOUD_RELAY, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<?> ENDER_RELAY_ITEM = ITEMS.registerSimpleBlockItem(ENDER_RELAY, p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<?> FIBER_CABLE_ITEM = ITEMS.registerSimpleBlockItem(FIBER_CABLE);
    public static final DeferredItem<?> FIBER_JUNCTION_ITEM = ITEMS.registerSimpleBlockItem(FIBER_JUNCTION);
    public static final DeferredItem<?> ENDER_FIBER_LINK_ITEM = ITEMS.registerSimpleBlockItem(ENDER_FIBER_LINK, p -> p.rarity(Rarity.EPIC));
    public static final DeferredItem<?> CLOUD_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(CLOUD_SWITCH);
    public static final DeferredItem<?> CLOUD_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(CLOUD_BUTTON);
    public static final DeferredItem<?> CLOUD_LAMP_ITEM = ITEMS.registerSimpleBlockItem(CLOUD_LAMP);
    public static final DeferredItem<?> CLOUD_SENSOR_ITEM = ITEMS.registerSimpleBlockItem(CLOUD_SENSOR);
    // v0.7 mainframe upgrades: 15 cards (type × tier), the Quantum Core and the optional Chunk Anchor card.
    public static final java.util.Map<String, DeferredItem<ai.ezfox.cccloud.upgrade.UpgradeItem>> UPGRADE_CARDS = new java.util.LinkedHashMap<>();

    static {
        for (var type : ai.ezfox.cccloud.upgrade.UpgradeType.values()) {
            for (var tier : ai.ezfox.cccloud.upgrade.UpgradeTier.values()) {
                String id = ai.ezfox.cccloud.upgrade.UpgradeItem.cardId(type, tier);
                var rarity = switch (tier) {
                    case ALLTHEMODIUM -> Rarity.UNCOMMON;
                    case VIBRANIUM -> Rarity.RARE;
                    case UNOBTAINIUM -> Rarity.EPIC;
                };
                UPGRADE_CARDS.put(id, ITEMS.registerItem(id, p -> new ai.ezfox.cccloud.upgrade.UpgradeItem(ai.ezfox.cccloud.upgrade.UpgradeItem.Kind.CARD,
                    type, tier, p), p -> p.stacksTo(1).rarity(rarity)));
            }
        }
    }

    public static final DeferredItem<ai.ezfox.cccloud.upgrade.UpgradeItem> QUANTUM_CORE = ITEMS.registerItem("quantum_core",
        p -> new ai.ezfox.cccloud.upgrade.UpgradeItem(ai.ezfox.cccloud.upgrade.UpgradeItem.Kind.QUANTUM_CORE, null, null, p), p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<ai.ezfox.cccloud.upgrade.UpgradeItem> UPGRADE_CHUNK_ANCHOR = ITEMS.registerItem("upgrade_chunk_anchor",
        p -> new ai.ezfox.cccloud.upgrade.UpgradeItem(ai.ezfox.cccloud.upgrade.UpgradeItem.Kind.CHUNK_ANCHOR, null, null, p), p -> p.stacksTo(1).rarity(Rarity.RARE));

    public static DeferredItem<ai.ezfox.cccloud.upgrade.UpgradeItem> card(ai.ezfox.cccloud.upgrade.UpgradeType type, ai.ezfox.cccloud.upgrade.UpgradeTier tier) {
        return UPGRADE_CARDS.get(ai.ezfox.cccloud.upgrade.UpgradeItem.cardId(type, tier));
    }

    public static final DeferredItem<Item> CLOUD_CORE = ITEMS.registerSimpleItem("cloud_core");
    public static final DeferredItem<CloudLinkCardItem> CLOUD_LINK_CARD = ITEMS.registerItem("cloud_link_card", CloudLinkCardItem::new, p -> p.stacksTo(1));

    // Block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloudMainframeBlockEntity>> MAINFRAME_BE = BLOCK_ENTITIES.register("cloud_mainframe",
        () -> new BlockEntityType<>(CloudMainframeBlockEntity::new, CLOUD_MAINFRAME.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ai.ezfox.cccloud.relay.CloudRelayBlockEntity>> RELAY_BE = BLOCK_ENTITIES.register("cloud_relay",
        () -> new BlockEntityType<>(ai.ezfox.cccloud.relay.CloudRelayBlockEntity::new, CLOUD_RELAY.get(), ADVANCED_CLOUD_RELAY.get(), ENDER_RELAY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloudUplinkBlockEntity>> UPLINK_BE = BLOCK_ENTITIES.register("cloud_uplink",
        () -> new BlockEntityType<>(CloudUplinkBlockEntity::new, CLOUD_UPLINK.get(), ADVANCED_CLOUD_UPLINK.get(), CLOUD_PROBE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity>> RS_DEVICE_BE = BLOCK_ENTITIES.register(
        "redstone_device", () -> new BlockEntityType<>(ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity::new, CLOUD_SWITCH.get(), CLOUD_BUTTON.get(),
            CLOUD_LAMP.get(), CLOUD_SENSOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ai.ezfox.cccloud.cable.EnderFiberLinkBlockEntity>> ENDER_FIBER_LINK_BE =
        BLOCK_ENTITIES.register("ender_fiber_link", () -> new BlockEntityType<>(ai.ezfox.cccloud.cable.EnderFiberLinkBlockEntity::new, ENDER_FIBER_LINK.get()));

    // Data components
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MainframeLink>> LINKED_MAINFRAME = COMPONENTS.registerComponentType("linked_mainframe",
        b -> b.persistent(MainframeLink.CODEC).networkSynchronized(MainframeLink.STREAM_CODEC));
    /** A machine picked for a Cloud Probe with the Link Card (sneak + right-click), see {@link ai.ezfox.cccloud.item.ProbeTarget}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ai.ezfox.cccloud.item.ProbeTarget>> PROBE_TARGET = COMPONENTS.registerComponentType("probe_target",
        b -> b.persistent(ai.ezfox.cccloud.item.ProbeTarget.CODEC).networkSynchronized(ai.ezfox.cccloud.item.ProbeTarget.STREAM_CODEC));
    /** A mainframe item dropped by a broken mainframe: placing it restores that mainframe (docs/SPEC.md "Mainframe removal"). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ai.ezfox.cccloud.item.MainframeIdentity>> MAINFRAME_IDENTITY =
        COMPONENTS.registerComponentType("mainframe_identity",
            b -> b.persistent(ai.ezfox.cccloud.item.MainframeIdentity.CODEC).networkSynchronized(ai.ezfox.cccloud.item.MainframeIdentity.STREAM_CODEC));


    // Menus (in-game screens)
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ai.ezfox.cccloud.gui.MainframeMenu>> MAINFRAME_MENU =
        MENUS.register("mainframe", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ai.ezfox.cccloud.gui.MainframeMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ai.ezfox.cccloud.gui.UplinkMenu>> UPLINK_MENU =
        MENUS.register("uplink", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ai.ezfox.cccloud.gui.UplinkMenu::new));

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ai.ezfox.cccloud.rsdevice.RsDeviceMenu>> RS_DEVICE_MENU =
        MENUS.register("redstone_device", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ai.ezfox.cccloud.rsdevice.RsDeviceMenu::new));

    // Creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("cccloud", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.cccloud"))
        .icon(() -> new ItemStack(CLOUD_MAINFRAME_ITEM.get()))
        .displayItems((params, out) -> {
            out.accept(CLOUD_MAINFRAME_ITEM.get());
            out.accept(CLOUD_UPLINK_ITEM.get());
            out.accept(ADVANCED_CLOUD_UPLINK_ITEM.get());
            out.accept(CLOUD_PROBE_ITEM.get());
            out.accept(CLOUD_RELAY_ITEM.get());
            out.accept(ADVANCED_CLOUD_RELAY_ITEM.get());
            out.accept(ENDER_RELAY_ITEM.get());
            out.accept(FIBER_CABLE_ITEM.get());
            out.accept(FIBER_JUNCTION_ITEM.get());
            out.accept(ENDER_FIBER_LINK_ITEM.get());
            out.accept(CLOUD_SWITCH_ITEM.get());
            out.accept(CLOUD_BUTTON_ITEM.get());
            out.accept(CLOUD_LAMP_ITEM.get());
            out.accept(CLOUD_SENSOR_ITEM.get());
            out.accept(CLOUD_LINK_CARD.get());
            out.accept(CLOUD_CORE.get());
            for (var card : UPGRADE_CARDS.values()) out.accept(card.get());
            out.accept(QUANTUM_CORE.get());
            out.accept(UPGRADE_CHUNK_ANCHOR.get());
            for (var bridge : ai.ezfox.cccloud.bridge.BridgeRegistry.ITEMS) out.accept(bridge.get());
        })
        .build());

    // Game tests (referenced from data/cccloud/test_instance/*.json; only executed by the game test server / test command)
    static {
        TEST_FUNCTIONS.register("mainframe_place_and_break_upper", () -> CloudGameTests::placeAndBreakUpper);
        TEST_FUNCTIONS.register("mainframe_break_lower_drops_one", () -> CloudGameTests::breakLowerDropsOne);
        TEST_FUNCTIONS.register("mainframe_ownership", () -> CloudGameTests::ownership);
        TEST_FUNCTIONS.register("uplink_mount_slab_side", () -> ai.ezfox.cccloud.gametest.UplinkMountTests::slabSide);
        TEST_FUNCTIONS.register("probe_remote_target", () -> ai.ezfox.cccloud.gametest.ProbeTests::remoteTarget);
        TEST_FUNCTIONS.register("uplink_side_fallback_hint", () -> ai.ezfox.cccloud.gametest.ProbeTests::sideFallbackAndHint);
        TEST_FUNCTIONS.register("load_test", () -> ai.ezfox.cccloud.gametest.LoadTest::run);
        TEST_FUNCTIONS.register("rom_contents", () -> CloudGameTests::romContents);
        TEST_FUNCTIONS.register("recipes_live_catalog", () -> ai.ezfox.cccloud.gametest.RecipeCatalogTests::liveRecipes);
        TEST_FUNCTIONS.register("auto_computer_card", () -> ai.ezfox.cccloud.gametest.AutoModeTests::computerCard);
        TEST_FUNCTIONS.register("mainframe_power", () -> ai.ezfox.cccloud.gametest.PowerTests::power);
        TEST_FUNCTIONS.register("access_permission_nodes", () -> ai.ezfox.cccloud.gametest.AccessTests::permissionNodes);
        TEST_FUNCTIONS.register("access_scoreboard_team", () -> ai.ezfox.cccloud.gametest.AccessTests::scoreboardTeam);
        TEST_FUNCTIONS.register("access_migration_v03", () -> ai.ezfox.cccloud.gametest.AccessTests::migrationV03);
        TEST_FUNCTIONS.register("script_upload_run", () -> ai.ezfox.cccloud.gametest.ScriptTests::uploadAndRun);
        TEST_FUNCTIONS.register("rules_widget_redstone", () -> ai.ezfox.cccloud.gametest.RuleTests::widgetThresholdSetsRedstone);
        TEST_FUNCTIONS.register("terminal_type_and_see", () -> ai.ezfox.cccloud.gametest.TerminalTests::typeAndSee);
        TEST_FUNCTIONS.register("history_widget_series", () -> ai.ezfox.cccloud.gametest.HistoryTests::widgetSeries);
        TEST_FUNCTIONS.register("security_no_escalation", () -> ai.ezfox.cccloud.gametest.SecurityTests::noEscalationInGame);
        TEST_FUNCTIONS.register("security_no_link_takeover", () -> ai.ezfox.cccloud.gametest.SecurityTests::noLinkTakeover);
        TEST_FUNCTIONS.register("security_screen_parts", () -> ai.ezfox.cccloud.gametest.SecurityTests::screenShowsOnlyAllowedParts);
        TEST_FUNCTIONS.register("uplink_screen_card", () -> ai.ezfox.cccloud.gametest.SecurityTests::uplinkScreenCard);
        TEST_FUNCTIONS.register("section_cache", () -> ai.ezfox.cccloud.gametest.SectionCacheTests::unloadAndReload);
        TEST_FUNCTIONS.register("section_orphan_broken", () -> ai.ezfox.cccloud.gametest.SectionCacheTests::brokenUplinkDropsSection);
        TEST_FUNCTIONS.register("section_orphan_silent", () -> ai.ezfox.cccloud.gametest.SectionCacheTests::silentlyRemovedUplinkDropsSection);
        TEST_FUNCTIONS.register("map_tiles", () -> ai.ezfox.cccloud.gametest.MapTileTests::tiles);
        TEST_FUNCTIONS.register("voxel_exposed", () -> ai.ezfox.cccloud.gametest.VoxelTests::exposedOnly);
        TEST_FUNCTIONS.register("voxel_dirty", () -> ai.ezfox.cccloud.gametest.VoxelTests::dirtyInvalidation);
        TEST_FUNCTIONS.register("mainframe_never_powered", () -> ai.ezfox.cccloud.gametest.PowerTests::neverPowered);
        TEST_FUNCTIONS.register("network_routing", () -> ai.ezfox.cccloud.gametest.NetworkTests::routing);
        TEST_FUNCTIONS.register("network_ender", () -> ai.ezfox.cccloud.gametest.NetworkTests::enderAcrossDimensions);
        TEST_FUNCTIONS.register("cable_cc_wired", () -> ai.ezfox.cccloud.gametest.CableTests::ccCable);
        TEST_FUNCTIONS.register("cable_prefers_cable", () -> ai.ezfox.cccloud.gametest.CableTests::prefersCable);
        TEST_FUNCTIONS.register("cable_fiber_connections", () -> ai.ezfox.cccloud.gametest.CableTests::fiberConnections);
        TEST_FUNCTIONS.register("cable_fiber_capacity", () -> ai.ezfox.cccloud.gametest.CableTests::fiberCapacity);
        TEST_FUNCTIONS.register("cable_ender_fiber", () -> ai.ezfox.cccloud.gametest.CableTests::enderFiberAcrossDimensions);
        TEST_FUNCTIONS.register("upgrades_effects_capacity", () -> ai.ezfox.cccloud.gametest.UpgradeTests::effectsAndCapacity);
        TEST_FUNCTIONS.register("upgrades_chunk_anchor", () -> ai.ezfox.cccloud.gametest.UpgradeTests::chunkAnchor);
        TEST_FUNCTIONS.register("wireless_redstone", () -> ai.ezfox.cccloud.gametest.UpgradeTests::wirelessRedstone);
        TEST_FUNCTIONS.register("wireless_redstone_load", () -> ai.ezfox.cccloud.gametest.UpgradeTests::wirelessRedstoneAfterLoad);
        TEST_FUNCTIONS.register("mainframe_break_restore", () -> ai.ezfox.cccloud.gametest.MainframeRemovalTests::breakAndRestore);
        TEST_FUNCTIONS.register("mainframe_break_guard", () -> ai.ezfox.cccloud.gametest.MainframeRemovalTests::breakGuard);
        TEST_FUNCTIONS.register("mainframe_retention_purge", () -> ai.ezfox.cccloud.gametest.MainframeRemovalTests::retentionPurge);
        TEST_FUNCTIONS.register("mainframe_recover_command", () -> ai.ezfox.cccloud.gametest.MainframeRemovalTests::recoverCommand);
        TEST_FUNCTIONS.register("scenes_rights_delays_pin", () -> ai.ezfox.cccloud.gametest.SceneTests::rightsDelaysPin);
        TEST_FUNCTIONS.register("schedules_clocks", () -> ai.ezfox.cccloud.gametest.ScheduleTests::clocks);
        TEST_FUNCTIONS.register("api_scopes_rights", () -> ai.ezfox.cccloud.gametest.ApiTests::scopesAndRights);
        TEST_FUNCTIONS.register("rsdevice_switch_faces", () -> ai.ezfox.cccloud.rsdevice.RsGameTests::switchFaces);
        TEST_FUNCTIONS.register("rsdevice_button_pulse", () -> ai.ezfox.cccloud.rsdevice.RsGameTests::buttonPulse);
        TEST_FUNCTIONS.register("rsdevice_lamp_levels", () -> ai.ezfox.cccloud.rsdevice.RsGameTests::lampLevels);
        TEST_FUNCTIONS.register("rsdevice_sensor_load", () -> ai.ezfox.cccloud.rsdevice.RsGameTests::sensorAfterLoad);
        TEST_FUNCTIONS.register("rsdevice_group_cards", () -> ai.ezfox.cccloud.rsdevice.RsGameTests::groupCards);
        TEST_FUNCTIONS.register("rsdevice_rule_scene", () -> ai.ezfox.cccloud.rsdevice.RsGameTests::ruleAndScene);
        TEST_FUNCTIONS.register("rsdevice_permissions", () -> ai.ezfox.cccloud.rsdevice.RsGameTests::permissions);
        TEST_FUNCTIONS.register("rsdevice_capacity_power", () -> ai.ezfox.cccloud.rsdevice.RsGameTests::capacityAndPower);
    }

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        // GameTests only in development and on the GameTest server; the release jar carries no test instances.
        if (net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()) TEST_FUNCTIONS.register(bus);
    }
}
