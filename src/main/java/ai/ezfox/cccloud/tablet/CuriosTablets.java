package ai.ezfox.cccloud.tablet;

import ai.ezfox.cccloud.CCCloud;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Tablets in Curios slots (Curios is optional; ATM11 ships it). The slot itself is data-driven (the tablets are in the
 * {@code curios:belt} item tag, and {@code data/cccloud/curios/entities/tablet.json} gives players that slot); this
 * class only looks into the slots for the "open tablet" key and the carried-tablet check.
 */
final class CuriosTablets {
    private static final boolean LOADED = ModList.get().isLoaded("curios");
    private static boolean failed;

    private CuriosTablets() {
    }

    static List<ItemStack> stacks(ServerPlayer player) {
        if (!LOADED || failed) return List.of();
        try {
            return ai.ezfox.cccloud.compat.curios.CuriosAccess.tablets(player);
        } catch (LinkageError | RuntimeException e) {
            failed = true;
            CCCloud.LOGGER.warn("[cccloud] Curios integration disabled (tablets in Curios slots are not found): {}", e.toString());
            return List.of();
        }
    }
}
