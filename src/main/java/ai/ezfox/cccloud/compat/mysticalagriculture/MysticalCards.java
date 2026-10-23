package ai.ezfox.cccloud.compat.mysticalagriculture;

import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.blakebr0.cucumber.energy.CEnergyStorage;
import com.blakebr0.mysticalagriculture.api.machine.IUpgradeableMachine;
import com.blakebr0.mysticalagriculture.tileentity.EssenceFurnaceTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.HarvesterTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.OreInfuserTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.ReprocessorTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.SoulExtractorTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.SouliumSpawnerTileEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Mystical Agriculture machines (docs/SPEC.md v0.8 item 5): Essence Furnace, Reprocessor, Soul Extractor, Harvester,
 * Ore Infuser, Soulium Spawner – progress, operation time, fuel left, energy and the machine upgrade tier. The
 * machines share no interface for this, so each class is matched explicitly. Mystical Automation's machines only
 * expose energy and keep the generic Energy card.
 */
public final class MysticalCards {
    public static final String STATUS = "status";
    public static final String PROGRESS = "progress";
    public static final String FUEL = "fuel";
    public static final String ENERGY = "energy";
    public static final String TIER = "tier";

    /** The machine getters, read the same way for every machine class. */
    record Reading(int progress, int operationTime, int fuelLeft, @Nullable CEnergyStorage energy) {
    }

    private MysticalCards() {
    }

    public static void init() {
        DeviceCards.register("mysticalagriculture", DeviceCards.SPECIFIC, MysticalCards::detect);
    }

    static @Nullable Reading read(BlockEntity be) {
        return switch (be) {
            case EssenceFurnaceTileEntity m -> new Reading(m.getProgress(), m.getOperationTime(), m.getFuelLeft(), m.getEnergy());
            case ReprocessorTileEntity m -> new Reading(m.getProgress(), m.getOperationTime(), m.getFuelLeft(), m.getEnergy());
            case SoulExtractorTileEntity m -> new Reading(m.getProgress(), m.getOperationTime(), m.getFuelLeft(), m.getEnergy());
            case HarvesterTileEntity m -> new Reading(m.getProgress(), m.getOperationTime(), m.getFuelLeft(), m.getEnergy());
            case OreInfuserTileEntity m -> new Reading(m.getProgress(), m.getOperationTime(), m.getFuelLeft(), m.getEnergy());
            case SouliumSpawnerTileEntity m -> new Reading(m.getProgress(), m.getOperationTime(), m.getFuelLeft(), m.getEnergy());
            default -> null;
        };
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        return be != null && read(be) != null ? new Machine(level, pos, side, be) : null;
    }

    static final class Machine extends BlockCard<BlockEntity> {
        Machine(ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
            super(level, pos, side, be);
        }

        @Override
        public String kind() {
            return "mysticalagriculture_machine";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .progress(PROGRESS, "Progress", 0, 100, "%")
                .percent(ENERGY, "Energy", "color", "green", "icon", "bolt")
                .label(FUEL, "Fuel left")
                .label(TIER, "Upgrade")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var r = read(be);
            if (r == null) return;
            boolean working = r.progress() > 0;
            long stored = r.energy() == null ? 0 : r.energy().getAmountAsLong();
            long capacity = r.energy() == null ? 0 : r.energy().getCapacityAsLong();
            out.text(STATUS, working ? "Working" : stored <= 0 && r.fuelLeft() <= 0 ? "No power" : "Idle");
            out.number(PROGRESS, r.operationTime() > 0 ? Math.round(Math.min(100, r.progress() * 100.0 / r.operationTime())) : 0);
            out.number(ENERGY, Fmt.percent(stored, capacity));
            out.text(FUEL, r.fuelLeft() > 0 ? Fmt.si(r.fuelLeft()) + " FE" : "–");
            var tier = be instanceof IUpgradeableMachine m ? m.getMachineTier() : null;
            out.text(TIER, tier == null ? "None" : Fmt.title(tier.name().toLowerCase(Locale.ROOT)));
        }
    }
}
