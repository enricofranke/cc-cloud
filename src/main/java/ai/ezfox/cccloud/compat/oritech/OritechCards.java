package ai.ezfox.cccloud.compat.oritech;

import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.EnergyCard;
import ai.ezfox.cccloud.compat.card.Fmt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import rearth.oritech.block.base.entity.ExpandableEnergyStorageBlockEntity;
import rearth.oritech.block.base.entity.UpgradableGeneratorBlockEntity;

/**
 * Oritech (oritech 2.0, experimental builds): energy storages show Oritech's own in/out statistics, generators their
 * working state. Oritech has no redstone mode (a signal simply disables the block), so the card has no mode select.
 */
public final class OritechCards {
    private OritechCards() {
    }

    public static void init() {
        DeviceCards.register("oritech", DeviceCards.SPECIFIC, OritechCards::detect);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof ExpandableEnergyStorageBlockEntity storage) return new Storage(level, pos, side, storage);
        if (be instanceof UpgradableGeneratorBlockEntity generator) return new Generator(level, pos, side, generator);
        return null;
    }

    static final class Storage extends EnergyCard {
        private final ExpandableEnergyStorageBlockEntity storage;

        Storage(ServerLevel level, BlockPos pos, Direction side, ExpandableEnergyStorageBlockEntity storage) {
            super("oritech_storage", null, level, pos, side, storage);
            this.storage = storage;
        }

        @Override
        protected long stored() {
            return storage.energyStorage.getAmountAsLong();
        }

        @Override
        protected long capacity() {
            return storage.energyStorage.getCapacityAsLong();
        }

        @Override
        protected boolean hasRates() {
            return true;
        }

        @Override
        protected @Nullable Double inputRate() {
            return (double) storage.energyStorage.getCurrentStatistics(level.getGameTime()).avgInsertSecond();
        }

        @Override
        protected @Nullable Double outputRate() {
            return (double) storage.energyStorage.getCurrentStatistics(level.getGameTime()).avgExtractSecond();
        }
    }

    static final class Generator extends EnergyCard {
        private final UpgradableGeneratorBlockEntity generator;

        Generator(ServerLevel level, BlockPos pos, Direction side, UpgradableGeneratorBlockEntity generator) {
            super("oritech_generator", null, level, pos, side, generator);
            this.generator = generator;
        }

        @Override
        protected void extraWidgets(CardWidgets w) {
            w.indicator("running", "Generating", "onColor", "green", "offColor", "gray").label("generation", "Generation");
        }

        @Override
        protected void refreshExtra(CardOutput out) {
            boolean running = generator.isActivelyWorking();
            out.bool("running", running);
            out.text("generation", running ? Fmt.flow(generator.getDisplayedEnergyUsage()) : "0 FE/t");
        }
    }
}
