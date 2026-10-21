package ai.ezfox.cccloud.bridge;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Registry ids and (English, server-side) display names of items, cached. */
public final class ItemNames {
    private static final Map<Item, String> NAMES = new ConcurrentHashMap<>();
    private static final Map<Item, String> IDS = new ConcurrentHashMap<>();

    private ItemNames() {
    }

    public static String id(Item item) {
        return IDS.computeIfAbsent(item, i -> BuiltInRegistries.ITEM.getKey(i).toString());
    }

    public static String displayName(Item item) {
        return NAMES.computeIfAbsent(item, i -> {
            try {
                return new ItemStack(i).getHoverName().getString();
            } catch (RuntimeException e) {
                return id(i);
            }
        });
    }

    /** The item registered under {@code namespace:path}, or null (air counts as unknown). */
    public static @Nullable Item byName(@Nullable String name) {
        if (name == null) return null;
        var id = Identifier.tryParse(name.toLowerCase(Locale.ROOT));
        if (id == null) return null;
        var item = BuiltInRegistries.ITEM.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }
}
