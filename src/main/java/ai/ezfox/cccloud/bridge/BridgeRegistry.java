package ai.ezfox.cccloud.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Filled by compat/ae2 and compat/rs during mod construction, only if the storage mod is loaded. Holds nothing that
 * references AE2/RS classes, so the rest of the mod can use it unconditionally.
 */
public final class BridgeRegistry {
    /** Bridge kind ("me", "rs") → block entity type. */
    static final Map<String, Supplier<? extends BlockEntityType<?>>> BLOCK_ENTITIES = new ConcurrentHashMap<>();
    /** Bridge items, for the creative tab. */
    public static final List<Supplier<? extends Item>> ITEMS = new ArrayList<>();

    private BridgeRegistry() {
    }

    public static void register(String kind, Supplier<? extends BlockEntityType<?>> type, Supplier<? extends Item> item) {
        BLOCK_ENTITIES.put(kind, type);
        ITEMS.add(item);
    }
}
