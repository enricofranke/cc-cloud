package ai.ezfox.cccloud.compat.classicpipes;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.ScannedItemsCard;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jagm.classicpipes.blockentity.NetworkedPipeEntity;
import jagm.classicpipes.util.PipeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Classic Pipes: the logistics network of an uplink on any networked pipe (routing, request, provider, stocking and the
 * like): what the Request Pipe's list offers (items, types, craftable types) with an item search, the pipes, requests
 * under way and what stocking pipes are short of; a warn alert once when something goes missing. Read-only: a request
 * needs a player and a pipe to deliver to. Destinations (coordinates) are not shown.
 */
public final class PipeNetworkCards {
    public static final String STATUS = "status";
    public static final String ITEMS = "items";
    public static final String TYPES = "types";
    public static final String CRAFTABLE = "craftable";
    public static final String PIPES = "pipes";
    public static final String REQUESTS = "requests";
    public static final String MISSING = "missing";

    private PipeNetworkCards() {
    }

    public static void init() {
        DeviceCards.register("classicpipes", DeviceCards.SPECIFIC, PipeNetworkCards::detect);
        Integrations.test("classicpipes_network", () -> PipeNetworkGameTests::network);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof NetworkedPipeEntity pipe ? new Network(level, pos, side, pipe) : null;
    }

    /** "12× Iron Ingot, 3× Coal, …": at most three stacks. */
    static String stacks(List<ItemStack> stacks) {
        var parts = new ArrayList<String>();
        for (var s : stacks) {
            if (parts.size() == 3) {
                parts.add("…");
                break;
            }
            parts.add(Slots.stack(s));
        }
        return String.join(", ", parts);
    }

    static final class Network extends ScannedItemsCard<NetworkedPipeEntity> {
        private int craftable;
        private boolean wasMissing;

        Network(ServerLevel level, BlockPos pos, Direction side, NetworkedPipeEntity pipe) {
            super(level, pos, side, pipe);
        }

        @Override
        public String kind() {
            return "classicpipes_network";
        }

        @Override
        public String title() {
            return "Pipe Network";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status")
                .label(ITEMS, "Total items", "span", 2)
                .label(TYPES, "Item types", "span", 2)
                .label(CRAFTABLE, "Craftable types", "span", 2)
                .label(PIPES, "Pipes", "span", 2)
                .label(REQUESTS, "Requests under way")
                .label(MISSING, "Stocking short of")
                .itemSearch(SEARCH, "Search network")
                .build();
        }

        @Override
        protected Map<Item, Long> countItems() {
            var counts = new HashMap<Item, Long>();
            craftable = 0;
            if (!be.hasNetwork()) return counts;
            var list = be.getNetwork().requestItemList(pos);
            for (var stack : list.existingItems()) counts.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
            craftable = list.craftableItems().size();
            return counts;
        }

        @Override
        public void refresh(CardOutput out) {
            scan();
            PipeNetwork network = be.hasNetwork() ? be.getNetwork() : null;
            out.text(STATUS, network == null ? "Not connected" : "Connected");
            out.text(ITEMS, Fmt.si(totalItems()));
            out.text(TYPES, String.valueOf(itemTypes()));
            out.text(CRAFTABLE, String.valueOf(craftable));
            var requests = new ArrayList<ItemStack>();
            var missing = new ArrayList<ItemStack>();
            int pipes = 0;
            if (network != null) {
                pipes = network.getRoutingPipes().size() + network.getStockingPipes().size() + network.getMatchingPipes().size();
                for (var request : network.getRequestedItems()) {
                    requests.add(request.getStack().copyWithCount(request.getAmountRemaining()));
                }
                for (var stocking : network.getStockingPipes()) missing.addAll(stocking.getMissingItemsCache());
            }
            out.text(PIPES, String.valueOf(pipes));
            out.text(REQUESTS, requests.isEmpty() ? "None" : stacks(requests));
            out.text(MISSING, missing.isEmpty() ? "Nothing" : stacks(missing));
            if (!missing.isEmpty() && !wasMissing) out.alert("Pipe Network: stocking pipes are short of " + stacks(missing), "warn");
            wasMissing = !missing.isEmpty();
        }
    }
}
