package ai.ezfox.cccloud.compat.generatorgalore;

import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import ai.ezfox.cccloud.compat.card.Fmt;
import cy.jdkdigital.generatorgalore.common.block.entity.GeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/** Generator Galore: the Energy card plus the generator's running state, generation and fuel burn. No redstone mode. */
public final class GeneratorGaloreCards {
    private GeneratorGaloreCards() {
    }

    public static void init() {
        DeviceCards.register("generatorgalore", DeviceCards.SPECIFIC, GeneratorGaloreCards::detect);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator ? new Generator(level, pos, side, generator) : null;
    }

    static final class Generator extends EnergyCard {
        private final GeneratorBlockEntity generator;

        Generator(ServerLevel level, BlockPos pos, Direction side, GeneratorBlockEntity generator) {
            super("generatorgalore_generator", null, level, pos, side, generator);
            this.generator = generator;
        }

        private boolean running() {
            var state = generator.getBlockState();
            return state.hasProperty(BlockStateProperties.LIT) ? state.getValue(BlockStateProperties.LIT) : generator.isLit();
        }

        @Override
        protected void extraWidgets(CardWidgets w) {
            w.indicator("running", "Generating", "onColor", "green", "offColor", "gray")
                .label("generation", "Generation")
                .progress("burn", "Fuel burn", 0, 100, "%");
        }

        @Override
        protected void refreshExtra(CardOutput out) {
            boolean running = running();
            out.bool("running", running);
            // Generator Galore keeps the rate on the shared generator definition: only meaningful while burning.
            out.text("generation", running ? Fmt.flow(generator.getGenerationRate()) : "0 FE/t");
            out.number("burn", generator.litDuration > 0 ? Fmt.percent(generator.litTime, generator.litDuration) : 0);
        }
    }
}
