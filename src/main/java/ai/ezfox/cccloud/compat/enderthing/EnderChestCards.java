package ai.ezfox.cccloud.compat.enderthing;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.ItemStorageCard;
import dev.gigaherz.enderthing.blocks.EnderKeyChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Enderthing: an Ender Key Chest as a Storage card (the inventory behind its key, through the item capability). A
 * private chest belongs to one player and gets no card: dashboard viewers would see that player's items.
 */
public final class EnderChestCards {
    private EnderChestCards() {
    }

    public static void init() {
        DeviceCards.register("enderthing", DeviceCards.SPECIFIC, EnderChestCards::detect);
        Integrations.test("enderthing_key_chest", () -> EnderChestGameTests::keyChest);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof EnderKeyChestBlockEntity chest) || chest.isPrivate()) return null;
        return ItemStorageCard.handler(level, pos, side) == null ? null : new ItemStorageCard("enderthing_storage", "Ender Key Chest", level, pos, side, chest) {
            @Override
            public boolean isValid() {
                return super.isValid() && !chest.isPrivate();
            }
        };
    }
}
