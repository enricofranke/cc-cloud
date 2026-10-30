package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.bridge.ItemNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** Card texts for item slots and fluids (wave 4), the same wording as the other cards: "12× Iron Ingot", "Empty". */
public final class Slots {
    private Slots() {
    }

    public static String stack(ItemStack stack) {
        return stack.isEmpty() ? "Empty" : stack.getCount() + "× " + ItemNames.displayName(stack.getItem());
    }

    /** The item in one slot of a resource handler, or "Empty" (also for a missing handler or slot). */
    public static String item(@Nullable ResourceHandler<ItemResource> handler, int slot) {
        if (handler == null || slot < 0 || slot >= handler.size()) return "Empty";
        var resource = handler.getResource(slot);
        long amount = resource.isEmpty() ? 0 : handler.getAmountAsLong(slot);
        return amount <= 0 ? "Empty" : amount + "× " + ItemNames.displayName(resource.getItem());
    }

    /** True if the slot holds nothing. */
    public static boolean empty(@Nullable ResourceHandler<ItemResource> handler, int slot) {
        return handler == null || slot < 0 || slot >= handler.size() || handler.getResource(slot).isEmpty() || handler.getAmountAsLong(slot) <= 0;
    }

    /** "3× Bread, 1× Egg" over a range of container slots (at most three stacks, then "…"), or "Empty". */
    public static String container(net.minecraft.world.Container c, int from, int count) {
        var parts = new java.util.ArrayList<String>();
        for (int i = from; i < from + count && i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            if (parts.size() == 3) {
                parts.add("…");
                break;
            }
            parts.add(stack(s));
        }
        return parts.isEmpty() ? "Empty" : String.join(", ", parts);
    }

    /** "4.00 k / 8.00 k mB Lava", or "Empty". */
    public static String fluid(@Nullable Fluid fluid, long amount, long capacity) {
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) return "Empty";
        return Fmt.si(amount) + " / " + Fmt.si(capacity) + " mB " + Fmt.title(BuiltInRegistries.FLUID.getKey(fluid).getPath());
    }
}
