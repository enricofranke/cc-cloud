package ai.ezfox.cccloud.compat.integratedtunnels;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.ScannedItemsCard;
import ai.ezfox.cccloud.uplink.Widget;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integrateddynamics.core.blockentity.BlockEntityMultipartTicking;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integratedtunnels.Capabilities;
import org.cyclops.integratedtunnels.api.network.IFluidNetwork;
import org.cyclops.integratedtunnels.api.network.IItemNetwork;
import org.jspecify.annotations.Nullable;

/**
 * Integrated Tunnels (also what Integrated Terminals' Storage Terminal shows): an uplink on a logic cable of a network
 * with item or fluid interfaces shows the whole network: items, item types, fluids, the number of interfaces and an item
 * search. It reads the index ID keeps for its terminals (all channels), so a big network costs no storage scan; the
 * index is filled a moment after the first read. Read-only. A cable with Display Panels keeps its Panels card.
 */
public final class ItemNetworkCards {
    public static final String ITEMS = "items";
    public static final String TYPES = "types";
    public static final String FLUIDS = "fluids";
    public static final String INTERFACES = "interfaces";

    private ItemNetworkCards() {
    }

    public static void init() {
        DeviceCards.register("integratedtunnels", DeviceCards.SPECIFIC, ItemNetworkCards::detect);
        Integrations.test("integratedtunnels_network", () -> ItemNetworkGameTests::network);
    }

    static @Nullable INetwork network(ServerLevel level, BlockPos pos, Direction side) {
        return NetworkHelpers.getNetwork(level, pos, side).orElse(null);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof BlockEntityMultipartTicking cable)) return null;
        var network = network(level, pos, side);
        if (network == null) return null;
        return interfaces(network) > 0 ? new Network(level, pos, side, cable) : null;
    }

    /** Item and fluid interfaces (and importers, exporters) of the network. */
    static int interfaces(INetwork network) {
        int items = network.getCapability(Capabilities.ItemNetwork.NETWORK).map(n -> n.getPrioritizedPositions().size()).orElse(0);
        int fluids = network.getCapability(Capabilities.FluidNetwork.NETWORK).map(n -> n.getPrioritizedPositions().size()).orElse(0);
        return items + fluids;
    }

    static final class Network extends ScannedItemsCard<BlockEntityMultipartTicking> {
        Network(ServerLevel level, BlockPos pos, Direction side, BlockEntityMultipartTicking cable) {
            super(level, pos, side, cable);
        }

        @Override
        public String kind() {
            return "integratedtunnels_network";
        }

        @Override
        public String title() {
            return "Item Network";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(ITEMS, "Total items", "span", 2)
                .label(TYPES, "Item types", "span", 2)
                .label(FLUIDS, "Fluids", "span", 2)
                .label(INTERFACES, "Interfaces", "span", 2)
                .itemSearch(SEARCH, "Search network")
                .build();
        }

        @Override
        protected Map<Item, Long> countItems() {
            var counts = new HashMap<Item, Long>();
            var network = network(level, pos, side);
            IItemNetwork items = network == null ? null : network.getCapability(Capabilities.ItemNetwork.NETWORK).orElse(null);
            if (items == null) return counts;
            // The wildcard channel's index holds what every channel holds.
            for (var stack : items.getChannel(IPositionedAddonsNetwork.WILDCARD_CHANNEL)) {
                if (!stack.isEmpty()) counts.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
            }
            return counts;
        }

        @Override
        public void refresh(CardOutput out) {
            scan();
            out.text(ITEMS, Fmt.si(totalItems()));
            out.text(TYPES, String.valueOf(itemTypes()));
            var network = network(level, pos, side);
            IFluidNetwork fluids = network == null ? null : network.getCapability(Capabilities.FluidNetwork.NETWORK).orElse(null);
            long amount = 0;
            var kinds = new HashSet<Fluid>();
            if (fluids != null) {
                for (var stack : fluids.getChannel(IPositionedAddonsNetwork.WILDCARD_CHANNEL)) {
                    if (stack.isEmpty()) continue;
                    amount += stack.getAmount();
                    kinds.add(stack.getFluid());
                }
            }
            out.text(FLUIDS, kinds.isEmpty() ? "None" : Fmt.si(amount) + " mB, " + kinds.size() + (kinds.size() == 1 ? " type" : " types"));
            out.text(INTERFACES, network == null ? "0" : String.valueOf(interfaces(network)));
        }
    }
}
