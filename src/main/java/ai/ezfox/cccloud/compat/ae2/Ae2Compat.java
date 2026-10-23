package ai.ezfox.cccloud.compat.ae2;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.bridge.BridgeRegistry;
import ai.ezfox.cccloud.bridge.CloudBridgeBlock;
import appeng.api.AECapabilities;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Applied Energistics 2 integration. Only class-loaded when {@code ae2} is present ({@link CCCloud} checks
 * {@code ModList} first), so nothing outside this package may reference it or any AE2 class.
 */
public final class Ae2Compat {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CCCloud.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CCCloud.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CCCloud.MODID);
    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, CCCloud.MODID);

    public static final DeferredBlock<CloudBridgeBlock> BLOCK = BLOCKS.registerBlock("me_cloud_bridge", p -> new CloudBridgeBlock("me", p),
        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(2.5f).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeBridgeBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("me_cloud_bridge",
        () -> new BlockEntityType<>(MeBridgeBlockEntity::new, BLOCK.get()));

    static {
        TEST_FUNCTIONS.register("me_bridge_storage", () -> Ae2GameTests::storage);
        TEST_FUNCTIONS.register("me_bridge_crafting", () -> Ae2CraftingGameTests::crafting);
    }

    private Ae2Compat() {
    }

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TEST_FUNCTIONS.register(bus);
        bus.addListener(Ae2Compat::registerCapabilities);
        BridgeRegistry.register("me", BLOCK_ENTITY, ITEM);
        CCCloud.LOGGER.info("[cccloud] Applied Energistics 2 found: ME Cloud Bridge enabled");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), BLOCK_ENTITY.get(), (be, side) -> be.peripheral());
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, BLOCK_ENTITY.get(), (be, ctx) -> be);
    }
}
