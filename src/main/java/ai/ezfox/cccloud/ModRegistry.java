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
import net.minecraft.world.item.DoubleHighBlockItem;
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
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, CCCloud.MODID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CCCloud.MODID);
    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, CCCloud.MODID);

    // Blocks
    public static final DeferredBlock<CloudMainframeBlock> CLOUD_MAINFRAME = BLOCKS.registerBlock("cloud_mainframe", CloudMainframeBlock::new,
        p -> p.mapColor(MapColor.GOLD).strength(2.5f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<CloudUplinkBlock> CLOUD_UPLINK = BLOCKS.registerBlock("cloud_uplink", p -> new CloudUplinkBlock(false, p),
        ModRegistry::uplinkProperties);
    public static final DeferredBlock<CloudUplinkBlock> ADVANCED_CLOUD_UPLINK = BLOCKS.registerBlock("advanced_cloud_uplink", p -> new CloudUplinkBlock(true, p),
        p -> uplinkProperties(p).mapColor(MapColor.GOLD));

    private static BlockBehaviour.Properties uplinkProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)
            .lightLevel(s -> s.getValue(CloudUplinkBlock.STATUS) == ai.ezfox.cccloud.block.CloudStatus.ONLINE ? 4 : 0);
    }

    // Items
    public static final DeferredItem<DoubleHighBlockItem> CLOUD_MAINFRAME_ITEM = ITEMS.registerItem("cloud_mainframe",
        p -> new DoubleHighBlockItem(CLOUD_MAINFRAME.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<?> CLOUD_UPLINK_ITEM = ITEMS.registerSimpleBlockItem(CLOUD_UPLINK);
    public static final DeferredItem<?> ADVANCED_CLOUD_UPLINK_ITEM = ITEMS.registerSimpleBlockItem(ADVANCED_CLOUD_UPLINK, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> CLOUD_CORE = ITEMS.registerSimpleItem("cloud_core");
    public static final DeferredItem<CloudLinkCardItem> CLOUD_LINK_CARD = ITEMS.registerItem("cloud_link_card", CloudLinkCardItem::new, p -> p.stacksTo(1));

    // Block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloudMainframeBlockEntity>> MAINFRAME_BE = BLOCK_ENTITIES.register("cloud_mainframe",
        () -> new BlockEntityType<>(CloudMainframeBlockEntity::new, CLOUD_MAINFRAME.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloudUplinkBlockEntity>> UPLINK_BE = BLOCK_ENTITIES.register("cloud_uplink",
        () -> new BlockEntityType<>(CloudUplinkBlockEntity::new, CLOUD_UPLINK.get(), ADVANCED_CLOUD_UPLINK.get()));

    // Data components
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MainframeLink>> LINKED_MAINFRAME = COMPONENTS.registerComponentType("linked_mainframe",
        b -> b.persistent(MainframeLink.CODEC).networkSynchronized(MainframeLink.STREAM_CODEC));

    // Particles
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, net.minecraft.core.particles.SimpleParticleType> DATA_PACKET =
        PARTICLES.register("data_packet", () -> new net.minecraft.core.particles.SimpleParticleType(false));

    // Menus (in-game screens)
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ai.ezfox.cccloud.gui.MainframeMenu>> MAINFRAME_MENU =
        MENUS.register("mainframe", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ai.ezfox.cccloud.gui.MainframeMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ai.ezfox.cccloud.gui.UplinkMenu>> UPLINK_MENU =
        MENUS.register("uplink", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ai.ezfox.cccloud.gui.UplinkMenu::new));

    // Creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("cccloud", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.cccloud"))
        .icon(() -> new ItemStack(CLOUD_MAINFRAME_ITEM.get()))
        .displayItems((params, out) -> {
            out.accept(CLOUD_MAINFRAME_ITEM.get());
            out.accept(CLOUD_UPLINK_ITEM.get());
            out.accept(ADVANCED_CLOUD_UPLINK_ITEM.get());
            out.accept(CLOUD_LINK_CARD.get());
            out.accept(CLOUD_CORE.get());
            for (var bridge : ai.ezfox.cccloud.bridge.BridgeRegistry.ITEMS) out.accept(bridge.get());
        })
        .build());

    // Game tests (referenced from data/cccloud/test_instance/*.json; only executed by the game test server / test command)
    static {
        TEST_FUNCTIONS.register("mainframe_place_and_break_upper", () -> CloudGameTests::placeAndBreakUpper);
        TEST_FUNCTIONS.register("mainframe_break_lower_drops_one", () -> CloudGameTests::breakLowerDropsOne);
        TEST_FUNCTIONS.register("mainframe_ownership", () -> CloudGameTests::ownership);
        TEST_FUNCTIONS.register("load_test", () -> ai.ezfox.cccloud.gametest.LoadTest::run);
        TEST_FUNCTIONS.register("rom_contents", () -> CloudGameTests::romContents);
        TEST_FUNCTIONS.register("auto_computer_card", () -> ai.ezfox.cccloud.gametest.AutoModeTests::computerCard);
    }

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
        PARTICLES.register(bus);
        MENUS.register(bus);
        TEST_FUNCTIONS.register(bus);
    }
}
