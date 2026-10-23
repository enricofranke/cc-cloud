package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.bridge.ItemIndex;
import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.bridge.ItemRow;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Widget;
import ai.ezfox.cccloud.web.QueryResult;
import ai.ezfox.cccloud.web.TokenBucket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The Storage card of a storage controller (docs/SPEC.md v0.8 item 1): fill gauge, totals and an item search over the
 * controller's aggregated item capability ({@code Capabilities.Item.BLOCK}) – Functional Storage controllers,
 * Sophisticated Storage controllers, Occultism's storage actuator, DimStorage chests. Read-only: no crafting, no
 * extraction. Only the integrations decide which blocks get it (a plain chest keeps no card).
 * <p>
 * The inventory is scanned at most every {@link #SCAN_TICKS} ticks and at most {@link #MAX_SLOTS} slots per scan;
 * searches run on the last scan's {@link ItemIndex}.
 */
public class ItemStorageCard extends BlockCard<BlockEntity> {
    public static final String STORAGE = "storage";
    public static final String ITEMS = "items";
    public static final String TYPES = "types";
    public static final String SLOTS = "slots";
    public static final String SEARCH = "search";
    /** Rescan interval (ticks) for totals and the search index. */
    static final int SCAN_TICKS = 100;
    /** Slots read per scan; bigger inventories are reported as truncated. */
    public static final int MAX_SLOTS = 32_768;

    private final String kind;
    private final String title;
    private final TokenBucket searchBudget = new TokenBucket(20, 20);
    private ItemIndex index = ItemIndex.EMPTY;
    private long scannedAt = Long.MIN_VALUE;
    private Scan last = new Scan(0, 0, 0, 0, 0, false);

    record Scan(long items, long capacity, int types, int usedSlots, int slots, boolean truncated) {
    }

    public ItemStorageCard(String kind, @Nullable String title, ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
        super(level, pos, side, be);
        this.kind = kind;
        this.title = title != null ? title : blockTitle();
    }

    /** The block's item handler from the uplink's side, else the side-less one. */
    public static @Nullable ResourceHandler<ItemResource> handler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        var handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        if (handler == null && side != null) handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
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
            .percent(STORAGE, "Storage used", "warn", 85, "crit", 95)
            .label(ITEMS, "Total items")
            .label(TYPES, "Item types")
            .label(SLOTS, "Slots used")
            .itemSearch(SEARCH, "Search storage")
            .build();
    }

    private void scan() {
        long now = level.getGameTime();
        if (scannedAt != Long.MIN_VALUE && now - scannedAt < SCAN_TICKS && now >= scannedAt) return;
        scannedAt = now;
        var handler = handler(level, pos, side);
        if (handler == null) {
            index = ItemIndex.EMPTY;
            last = new Scan(0, 0, 0, 0, 0, false);
            return;
        }
        int size = handler.size();
        int limit = Math.min(size, MAX_SLOTS);
        var counts = new HashMap<Item, Long>();
        long items = 0, capacity = 0;
        int used = 0;
        for (int i = 0; i < limit; i++) {
            var resource = handler.getResource(i);
            long amount = resource.isEmpty() ? 0 : handler.getAmountAsLong(i);
            long cap = handler.getCapacityAsLong(i, resource);
            if (cap > 0) capacity += cap;
            if (amount <= 0) continue;
            used++;
            items += amount;
            counts.merge(resource.getItem(), amount, Long::sum);
        }
        var rows = new ArrayList<ItemRow>(Math.min(counts.size(), ItemIndex.MAX_ENTRIES));
        for (var e : counts.entrySet()) {
            if (rows.size() >= ItemIndex.MAX_ENTRIES) break;
            rows.add(new ItemRow(ItemNames.id(e.getKey()), ItemNames.displayName(e.getKey()), e.getValue(), false));
        }
        boolean truncated = size > limit || counts.size() > ItemIndex.MAX_ENTRIES;
        index = new ItemIndex(rows, truncated);
        last = new Scan(items, capacity, counts.size(), used, size, truncated);
    }

    @Override
    public void refresh(CardOutput out) {
        scan();
        var s = last;
        out.number(STORAGE, Fmt.percent(s.items(), s.capacity()));
        out.text(ITEMS, Fmt.si(s.items()) + (s.capacity() > 0 ? " / " + Fmt.si(s.capacity()) : "") + (s.truncated() ? "+" : ""));
        out.text(TYPES, String.valueOf(s.types()));
        out.text(SLOTS, s.usedSlots() + " / " + s.slots());
    }

    @Override
    public @Nullable QueryResult query(String widgetId, String query) {
        if (!SEARCH.equals(widgetId)) return null;
        if (!searchBudget.tryAcquire()) return QueryResult.fail("rate_limited");
        scan();
        var page = index.search(query, Limits.QUERY_ROWS);
        var rows = new JsonArray(page.rows().size());
        for (var row : page.rows()) rows.add(row.toJson());
        return new QueryResult(rows, page.truncated(), null);
    }

    @Override
    public @Nullable String action(String widgetId, JsonElement value, String user) {
        // Storage controllers have no autocrafting; the rows are never craftable.
        return SEARCH.equals(widgetId) ? "not_craftable" : "not_interactive";
    }
}
