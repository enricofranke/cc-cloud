package ai.ezfox.cccloud.compat.cobblegengalore;

import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.uplink.Widget;
import cy.jdkdigital.cobblegengalore.Config;
import cy.jdkdigital.cobblegengalore.common.block.BlockGenBlock;
import cy.jdkdigital.cobblegengalore.common.block.entity.BlockGenBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Cobblegen Galore (docs/SPEC.md v0.8 item 5): what the generator makes (from the fluids/blocks around it), its tier
 * speed, the internal buffer and an alert once when the buffer is full (nothing pulls the blocks out, so it stops).
 */
public final class CobblegenCards {
    public static final String PRODUCING = "producing";
    public static final String SPEED = "speed";
    public static final String BUFFER = "buffer";
    public static final String FULL = "full";

    private CobblegenCards() {
    }

    public static void init() {
        DeviceCards.register("cobblegengalore", DeviceCards.SPECIFIC, CobblegenCards::detect);
        Integrations.test("cobblegengalore_generator", () -> CobblegenGameTests::generator);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof BlockGenBlockEntity gen ? new Generator(level, pos, side, gen) : null;
    }

    static final class Generator extends BlockCard<BlockGenBlockEntity> {
        private boolean wasFull;

        Generator(ServerLevel level, BlockPos pos, Direction side, BlockGenBlockEntity gen) {
            super(level, pos, side, gen);
        }

        @Override
        public String kind() {
            return "cobblegengalore_generator";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(PRODUCING, "Producing", "span", 2)
                .label(SPEED, "Tier speed")
                .label(BUFFER, "Buffer")
                .indicator(FULL, "Buffer full", "onColor", "red", "offColor", "gray")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var result = be.hasResult() ? be.getResultItem() : null;
            out.text(PRODUCING, result == null || result.isEmpty() ? "Nothing (check the fluids/blocks next to it)" : ItemNames.displayName(result.getItem()));
            int modifier = be.getBlockState().getBlock() instanceof BlockGenBlock block ? block.modifier : 1;
            out.text(SPEED, "×" + modifier + " every " + Math.max(1, Config.tickRate) + " ticks");
            var buffer = be.getBuffer();
            int count = buffer.isEmpty() ? 0 : buffer.getCount();
            int max = buffer.isEmpty() ? 64 : buffer.getMaxStackSize();
            out.text(BUFFER, count + " / " + max);
            boolean full = count > 0 && count >= max;
            out.bool(FULL, full);
            if (full && !wasFull) out.alert(title() + ": buffer full, nothing pulls the blocks out", "warn");
            wasFull = full;
        }
    }
}
