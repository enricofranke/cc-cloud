package ai.ezfox.cccloud.compat.curios;

import ai.ezfox.cccloud.tablet.CloudTabletItem;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/** Curios public API (only class-loaded when Curios is present). */
public final class CuriosAccess {
    private CuriosAccess() {
    }

    public static List<ItemStack> tablets(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
            .map(inv -> inv.findCurios(stack -> stack.getItem() instanceof CloudTabletItem).stream().map(SlotResult::stack).toList())
            .orElse(List.of());
    }
}
