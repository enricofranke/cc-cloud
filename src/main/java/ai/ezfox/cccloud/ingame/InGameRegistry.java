package ai.ezfox.cccloud.ingame;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.display.CloudDisplayBlock;
import ai.ezfox.cccloud.display.CloudDisplayBlockEntity;
import ai.ezfox.cccloud.display.DisplayMenu;
import ai.ezfox.cccloud.tablet.CloudTabletItem;
import ai.ezfox.cccloud.tablet.TabletMenu;
import ai.ezfox.cccloud.tablet.TabletService;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registrations of the v0.7 in-game clients: Cloud Tablet, Advanced Cloud Tablet, Cloud Display and Cloud Analyzer (with their
 * menus, payloads and GameTests). Kept out of {@link ModRegistry}; {@link CCCloud} calls {@link #init} once.
 */
public final class InGameRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CCCloud.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CCCloud.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CCCloud.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CCCloud.MODID);
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, CCCloud.MODID);

    public static final DeferredBlock<CloudDisplayBlock> CLOUD_DISPLAY = BLOCKS.registerBlock("cloud_display", CloudDisplayBlock::new,
        p -> p.mapColor(MapColor.GOLD).strength(2.0f).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> CLOUD_DISPLAY_ITEM = ITEMS.registerSimpleBlockItem(CLOUD_DISPLAY);
    public static final DeferredItem<CloudTabletItem> CLOUD_TABLET = ITEMS.registerItem("cloud_tablet", p -> new CloudTabletItem(false, p), p -> p.stacksTo(1));
    public static final DeferredItem<CloudTabletItem> ADVANCED_CLOUD_TABLET = ITEMS.registerItem("advanced_cloud_tablet", p -> new CloudTabletItem(true, p),
        p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final DeferredItem<ai.ezfox.cccloud.analyzer.CloudAnalyzerItem> CLOUD_ANALYZER = ITEMS.registerItem("cloud_analyzer",
        ai.ezfox.cccloud.analyzer.CloudAnalyzerItem::new, p -> p.stacksTo(1));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloudDisplayBlockEntity>> DISPLAY_BE = BLOCK_ENTITIES.register("cloud_display",
        () -> new BlockEntityType<>(CloudDisplayBlockEntity::new, CLOUD_DISPLAY.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<TabletMenu>> TABLET_MENU = MENUS.register("tablet", () -> IMenuTypeExtension.create(TabletMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<DisplayMenu>> DISPLAY_MENU = MENUS.register("display", () -> IMenuTypeExtension.create(DisplayMenu::new));

    static {
        TESTS.register("tablet_range", () -> ai.ezfox.cccloud.tablet.TabletGameTests::range);
        TESTS.register("tablet_actions", () -> ai.ezfox.cccloud.tablet.TabletGameTests::actions);
        TESTS.register("display_forming", () -> ai.ezfox.cccloud.display.DisplayGameTests::forming);
        TESTS.register("display_content", () -> ai.ezfox.cccloud.display.DisplayGameTests::content);
        TESTS.register("analyzer_filtering", () -> ai.ezfox.cccloud.analyzer.AnalyzerGameTests::filtering);
        TESTS.register("analyzer_preview", () -> ai.ezfox.cccloud.analyzer.AnalyzerGameTests::preview);
    }

    private InGameRegistry() {
    }

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TESTS.register(bus);
        bus.addListener(InGameRegistry::creativeTab);
        InGamePayloads.init(bus);
        TabletService.init();
        ai.ezfox.cccloud.analyzer.AnalyzerService.init();
        ai.ezfox.cccloud.dev.DemoScene.init();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != ModRegistry.TAB.getKey()) return;
        event.accept(CLOUD_TABLET.get());
        event.accept(ADVANCED_CLOUD_TABLET.get());
        event.accept(CLOUD_DISPLAY_ITEM.get());
        event.accept(CLOUD_ANALYZER.get());
    }
}
