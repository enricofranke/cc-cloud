package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Registrations of the Cloud Computer block (docs/SPEC.md "Cloud Computers"): Cloud Computer and Advanced Cloud
 * Computer, their items, block entity, item data, screen and GameTests. Kept out of {@link ModRegistry};
 * {@link CCCloud} calls {@link #init} once.
 */
public final class ComputerBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CCCloud.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CCCloud.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CCCloud.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CCCloud.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CCCloud.MODID);
    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS = DeferredRegister.create(Registries.TEST_FUNCTION, CCCloud.MODID);

    public static final DeferredBlock<CloudComputerBlock> CLOUD_COMPUTER = BLOCKS.registerBlock("cloud_computer", p -> new CloudComputerBlock(false, p),
        p -> p.mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.METAL).lightLevel(s -> s.getValue(CloudComputerBlock.ON) ? 6 : 0));
    public static final DeferredBlock<CloudComputerBlock> ADVANCED_CLOUD_COMPUTER = BLOCKS.registerBlock("advanced_cloud_computer",
        p -> new CloudComputerBlock(true, p),
        p -> p.mapColor(MapColor.GOLD).strength(2.0f).sound(SoundType.METAL).lightLevel(s -> s.getValue(CloudComputerBlock.ON) ? 6 : 0));

    public static final DeferredItem<CloudComputerItem> CLOUD_COMPUTER_ITEM = ITEMS.registerItem("cloud_computer",
        p -> new CloudComputerItem(CLOUD_COMPUTER.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<CloudComputerItem> ADVANCED_CLOUD_COMPUTER_ITEM = ITEMS.registerItem("advanced_cloud_computer",
        p -> new CloudComputerItem(ADVANCED_CLOUD_COMPUTER.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloudComputerBlockEntity>> COMPUTER_BE = BLOCK_ENTITIES.register("cloud_computer",
        () -> new BlockEntityType<>(CloudComputerBlockEntity::new, true, CLOUD_COMPUTER.get(), ADVANCED_CLOUD_COMPUTER.get()));

    /** Id and label of a computer on the item of a broken computer. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ComputerItemData>> COMPUTER_DATA = COMPONENTS.registerComponentType("computer",
        b -> b.persistent(ComputerItemData.CODEC).networkSynchronized(ComputerItemData.STREAM_CODEC));

    public static final DeferredHolder<MenuType<?>, MenuType<ComputerMenu>> COMPUTER_MENU = MENUS.register("computer",
        () -> IMenuTypeExtension.create(ComputerMenu::new));

    static {
        TESTS.register("computer_place_and_boot", () -> ComputerGameTests::placeAndBoot);
        TESTS.register("computer_terminal_sync", () -> ComputerGameTests::terminalSync);
        TESTS.register("computer_program_writes", () -> ComputerGameTests::programWrites);
        TESTS.register("computer_screen_picture", () -> ComputerGameTests::screenPicture);
        TESTS.register("computer_redstone", () -> ComputerGameTests::redstone);
        TESTS.register("computer_break_keeps_id", () -> ComputerGameTests::breakKeepsId);
        TESTS.register("computer_rights", () -> ComputerGameTests::rights);
        TESTS.register("computer_dashboard_handle", () -> ComputerGameTests::dashboardHandle);
    }

    /** Which block entity hosts which computer id right now (server thread): one computer never runs in two blocks. */
    private static final Map<Integer, CloudComputerBlockEntity> HOSTS = new ConcurrentHashMap<>();

    private ComputerBlocks() {
    }

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        COMPONENTS.register(bus);
        MENUS.register(bus);
        if (net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()) TESTS.register(bus);
        bus.addListener(ComputerBlocks::creativeTab);
        ComputerScreens.init(bus);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> HOSTS.clear());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != ModRegistry.TAB.getKey()) return;
        event.accept(CLOUD_COMPUTER_ITEM.get());
        event.accept(ADVANCED_CLOUD_COMPUTER_ITEM.get());
    }

    static @Nullable CloudComputerBlockEntity host(int id) {
        var be = HOSTS.get(id);
        if (be != null && be.isRemoved()) {
            HOSTS.remove(id, be);
            return null;
        }
        return be;
    }

    /** The Cloud Computer blocks with a running computer (the claim guard's safety pass). */
    public static java.util.List<CloudComputerBlockEntity> loaded() {
        var out = new java.util.ArrayList<CloudComputerBlockEntity>(HOSTS.size());
        for (var be : HOSTS.values()) if (!be.isRemoved()) out.add(be);
        return out;
    }

    static void setHost(int id, CloudComputerBlockEntity be) {
        HOSTS.put(id, be);
    }

    /** Forgets the host if it is still {@code be}; true then (the caller unloads the computer). */
    static boolean removeHost(int id, CloudComputerBlockEntity be) {
        return HOSTS.remove(id, be);
    }

    /** The Cloud Computer at a position for the dashboard ({@link ai.ezfox.cccloud.compat.Cct#computer}), or null. */
    public static ai.ezfox.cccloud.compat.@Nullable ComputerHandle computer(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (level.isClientSide() || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof CloudComputerBlockEntity be ? be.handle() : null;
    }
}
