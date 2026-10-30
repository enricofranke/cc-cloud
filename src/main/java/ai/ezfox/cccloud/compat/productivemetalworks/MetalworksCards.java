package ai.ezfox.cccloud.compat.productivemetalworks;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import cy.jdkdigital.productivemetalworks.common.block.entity.FoundryControllerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * Productive Metalworks foundry (docs/SPEC.md v0.8 item 5; Silent Gear Metalworks only adds recipes to it): an uplink
 * on the foundry controller shows whether the multiblock is formed, the fuel (or the power of an energy coil), items
 * melting and the molten metals in the tank. An uplink on any linked part of the foundry (tank, drain, capacitor) shows
 * the controller's card.
 */
public final class MetalworksCards {
    public static final String STATUS = "status";
    public static final String FUEL = "fuel";
    public static final String POWER = "power";
    public static final String MELTING = "melting";
    public static final String MOLTEN = "molten";

    private MetalworksCards() {
    }

    public static void init() {
        DeviceCards.register("productivemetalworks", DeviceCards.SPECIFIC, MetalworksCards::detect);
        Integrations.test("productivemetalworks_foundry", () -> MetalworksGameTests::foundry);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (level.getBlockEntity(pos) instanceof FoundryControllerBlockEntity c) return new Foundry(level, pos, side, c);
        // Any part of a formed foundry (tank, drain, capacitor) shows the foundry: productivelib links parts to the controller.
        if (level.getBlockEntity(pos) instanceof cy.jdkdigital.productivelib.common.block.entity.IMultiBlockPeripheralBlockEntity part) {
            BlockPos controller = part.getMultiblockController();
            if (controller != null && !controller.equals(pos) && level.isLoaded(controller)
                && level.getBlockEntity(controller) instanceof FoundryControllerBlockEntity c) {
                return new Foundry(level, controller, side, c);
            }
        }
        return null;
    }

    static String fluidName(Fluid fluid) {
        return Fmt.title(BuiltInRegistries.FLUID.getKey(fluid).getPath());
    }

    static final class Foundry extends BlockCard<FoundryControllerBlockEntity> {
        Foundry(ServerLevel level, BlockPos pos, Direction side, FoundryControllerBlockEntity c) {
            super(level, pos, side, c);
        }

        @Override
        public String kind() {
            return "productivemetalworks_foundry";
        }

        @Override
        public String title() {
            return "Foundry";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(FUEL, "Fuel")
                .percent(POWER, "Power", "color", "yellow", "icon", "bolt")
                .label(MELTING, "Items melting")
                .label(MOLTEN, "Molten", "span", 2)
                .build();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void refresh(CardOutput out) {
            boolean formed = be.getMultiblockData() != null;
            out.text(STATUS, formed ? "Formed (" + Fmt.title(be.getCoilType().name().toLowerCase(java.util.Locale.ROOT)) + " coil)" : "Not formed");
            var fuel = be.getFuel();
            out.text(FUEL, fuel == null || fuel.isEmpty() ? "None" : fluidName(fuel.getFluid()) + " " + Fmt.si(fuel.getAmount()) + " mB");
            out.number(POWER, Fmt.percent(be.getPower(), be.getPowerMax()));
            var items = be.getItemHandler();
            int melting = 0;
            if (items != null) for (int i = 0; i < items.size(); i++) if (!items.getResource(i).isEmpty()) melting += (int) Math.min(Integer.MAX_VALUE, items.getAmountAsLong(i));
            out.text(MELTING, String.valueOf(melting));
            var tank = (ResourceHandler<FluidResource>) (ResourceHandler<?>) be.getFluidHandler();
            var parts = new ArrayList<String>();
            if (tank != null) {
                for (int i = 0; i < tank.size() && parts.size() < 4; i++) {
                    var r = tank.getResource(i);
                    long amount = tank.getAmountAsLong(i);
                    if (!r.isEmpty() && amount > 0) parts.add(fluidName(r.getFluid()) + " " + Fmt.si(amount) + " mB");
                }
            }
            out.text(MOLTEN, parts.isEmpty() ? "Empty" : String.join(", ", parts));
        }
    }
}
