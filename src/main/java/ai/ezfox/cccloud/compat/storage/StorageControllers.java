package ai.ezfox.cccloud.compat.storage;

import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.FluidTankCard;
import ai.ezfox.cccloud.compat.card.ItemStorageCard;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * v0.8 item 1: storage controllers as Storage cards. Everything goes through NeoForge's item/fluid capabilities, so
 * these integrations reference no class of the storage mods – only their block ids (checked against the ATM11 jars).
 * Each mod registers its own provider (only when it is loaded, see {@code Integrations}); the block must also expose
 * the capability, so a controller without connected storage simply shows an empty card.
 */
public final class StorageControllers {
    /** Mod id → (block path → card title). */
    static final Map<String, Map<String, String>> ITEM_BLOCKS = Map.of(
        "functionalstorage", Map.of(
            "storage_controller", "Storage Controller",
            "framed_storage_controller", "Storage Controller",
            "controller_extension", "Controller Access Point",
            "framed_controller_extension", "Controller Access Point"),
        "sophisticatedstorage", Map.of("controller", "Storage Controller"),
        "occultism", Map.of(
            "storage_controller", "Dimensional Storage Actuator",
            "storage_controller_dark", "Dimensional Storage Actuator",
            "storage_controller_stabilized", "Dimensional Storage Actuator",
            "storage_controller_stabilized_dark", "Dimensional Storage Actuator"),
        "dimstorage", Map.of("dimensional_chest", "Dimensional Chest"),
        // wave 4: kitchen storage, plain and in 16 colours ("*_fridge" = any colour)
        "cookingforblockheads", Map.of(
            "fridge", "Fridge", "*_fridge", "Fridge",
            "cabinet", "Kitchen Cabinet", "*_cabinet", "Kitchen Cabinet",
            "counter", "Kitchen Counter", "*_counter", "Kitchen Counter"));

    static final Map<String, Map<String, String>> FLUID_BLOCKS = Map.of(
        "dimstorage", Map.of("dimensional_tank", "Dimensional Tank"));

    private StorageControllers() {
    }

    /** Registers the Storage card (and tank card) provider of one mod. */
    public static void init(String modId) {
        var items = ITEM_BLOCKS.getOrDefault(modId, Map.of());
        var fluids = FLUID_BLOCKS.getOrDefault(modId, Map.of());
        if (items.isEmpty() && fluids.isEmpty()) throw new IllegalArgumentException("no storage blocks for " + modId);
        DeviceCards.register(modId, DeviceCards.SPECIFIC, (level, pos, side) -> {
            if (!ai.ezfox.cccloud.compat.IntegrationConfig.get(ai.ezfox.cccloud.compat.IntegrationConfig.STORAGE_CARDS)) return null;
            var be = level.getBlockEntity(pos);
            if (be == null) return null;
            var id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
            if (!modId.equals(id.getNamespace())) return null;
            String itemTitle = title(items, id.getPath());
            if (itemTitle != null && ItemStorageCard.handler(level, pos, side) != null) {
                return new ItemStorageCard(modId + "_storage", itemTitle, level, pos, side, be);
            }
            String tankTitle = fluids.get(id.getPath());
            if (tankTitle != null && FluidTankCard.handler(level, pos, side) != null) {
                return new FluidTankCard(modId + "_tank", tankTitle, level, pos, side, be);
            }
            return null;
        });
    }

    /** The title for a block path: an exact key, else a {@code *_suffix} key (colour variants). */
    static @org.jspecify.annotations.Nullable String title(Map<String, String> titles, String path) {
        String exact = titles.get(path);
        if (exact != null) return exact;
        for (var e : titles.entrySet()) {
            if (e.getKey().startsWith("*") && path.endsWith(e.getKey().substring(1))) return e.getValue();
        }
        return null;
    }

    /** Mods with a storage card (for Integrations and docs/COMPAT.md). */
    public static Set<String> mods() {
        var all = new java.util.TreeSet<>(ITEM_BLOCKS.keySet());
        all.addAll(FLUID_BLOCKS.keySet());
        return all;
    }
}
