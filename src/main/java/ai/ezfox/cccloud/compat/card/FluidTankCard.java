package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.uplink.Widget;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * A tank card over the fluid capability ({@code Capabilities.Fluid.BLOCK}): fill gauge, fluid and amount per tank (at
 * most {@link #MAX_TANKS}). Used for storage tanks that the integrations name (DimStorage's dimensional tank).
 */
public class FluidTankCard extends BlockCard<BlockEntity> {
    public static final String FILL = "fill";
    public static final String FLUID = "fluid";
    public static final String AMOUNT = "amount";
    static final int MAX_TANKS = 8;

    private final String kind;
    private final String title;

    public FluidTankCard(String kind, @Nullable String title, ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
        super(level, pos, side, be);
        this.kind = kind;
        this.title = title != null ? title : blockTitle();
    }

    /**
     * Generic provider (wave 3, lowest priority): any block entity with a fluid capability, no energy capability and at
     * most {@link #MAX_ITEM_SLOTS} item slots – such as EvilCraft's Dark Tank or Stellaris tanks. Machines with fluid
     * tanks keep their own or the Energy card.
     */
    public static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be == null) return null;
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
        if (ai.ezfox.cccloud.CCCloud.MODID.equals(id.getNamespace())) return null;
        var fluids = handler(level, pos, side);
        if (fluids == null || fluids.size() == 0) return null;
        if (EnergyCard.handler(level, pos, side) != null) return null;
        var items = ItemStorageCard.handler(level, pos, side);
        if (items != null && items.size() > MAX_ITEM_SLOTS) return null;
        return new FluidTankCard(KIND, null, level, pos, side, be);
    }

    public static final String KIND = "tank";
    /** Tanks may have a couple of bucket/container slots (EvilCraft's Dark Tank); more slots means a machine. */
    static final int MAX_ITEM_SLOTS = 2;

    public static @Nullable ResourceHandler<FluidResource> handler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        var handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        if (handler == null && side != null) handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
        return handler;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public List<Widget> widgets() {
        return new CardWidgets()
            .percent(FILL, "Filled", "color", "cyan")
            .label(FLUID, "Fluid")
            .label(AMOUNT, "Amount")
            .build();
    }

    @Override
    public void refresh(CardOutput out) {
        var handler = handler(level, pos, side);
        long amount = 0, capacity = 0;
        String fluid = "Empty";
        if (handler != null) {
            int n = Math.min(handler.size(), MAX_TANKS);
            for (int i = 0; i < n; i++) {
                var resource = handler.getResource(i);
                capacity += Math.max(0, handler.getCapacityAsLong(i, resource));
                if (resource.isEmpty()) continue;
                long a = handler.getAmountAsLong(i);
                if (a <= 0) continue;
                if (amount == 0) fluid = Fmt.title(BuiltInRegistries.FLUID.getKey(resource.getFluid()).getPath());
                amount += a;
            }
        }
        out.number(FILL, Fmt.percent(amount, capacity));
        out.text(FLUID, fluid);
        out.text(AMOUNT, Fmt.si(amount) + " / " + Fmt.si(capacity) + " mB");
    }
}
