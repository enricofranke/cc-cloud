package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.bridge.ItemIndex;
import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.bridge.ItemRow;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.web.QueryResult;
import ai.ezfox.cccloud.web.TokenBucket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Base of the cards that show a mod's item network without an aggregated item capability (Integrated Tunnels, Classic
 * Pipes): the subclass counts what the network holds, this class keeps the scan at most every {@link #SCAN_TICKS}
 * ticks and answers the item search on the last scan. Read-only, like {@link ItemStorageCard}: no crafting, no
 * extraction.
 */
public abstract class ScannedItemsCard<T extends BlockEntity> extends BlockCard<T> {
    public static final String SEARCH = "search";
    /** Rescan interval (ticks) for the counts and the search index. */
    static final int SCAN_TICKS = 100;

    private final TokenBucket searchBudget = new TokenBucket(20, 20);
    private ItemIndex index = ItemIndex.EMPTY;
    private long scannedAt = Long.MIN_VALUE;
    private long total;
    private int types;

    protected ScannedItemsCard(ServerLevel level, BlockPos pos, Direction side, T be) {
        super(level, pos, side, be);
    }

    /** What the network holds right now, by item. Server thread, at most every {@link #SCAN_TICKS} ticks. */
    protected abstract Map<Item, Long> countItems();

    /** Updates the counts if the last scan is old enough; call it first in {@link #refresh}. */
    protected final void scan() {
        long now = level.getGameTime();
        if (scannedAt != Long.MIN_VALUE && now - scannedAt < SCAN_TICKS && now >= scannedAt) return;
        scannedAt = now;
        var counts = countItems();
        var rows = new ArrayList<ItemRow>(Math.min(counts.size(), ItemIndex.MAX_ENTRIES));
        long sum = 0;
        for (var e : counts.entrySet()) {
            sum += e.getValue();
            if (rows.size() < ItemIndex.MAX_ENTRIES) rows.add(new ItemRow(ItemNames.id(e.getKey()), ItemNames.displayName(e.getKey()), e.getValue(), false));
        }
        index = new ItemIndex(rows, counts.size() > ItemIndex.MAX_ENTRIES);
        total = sum;
        types = counts.size();
    }

    /** Items in the network at the last scan. */
    protected final long totalItems() {
        return total;
    }

    /** Item types in the network at the last scan. */
    protected final int itemTypes() {
        return types;
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
        return SEARCH.equals(widgetId) ? "not_craftable" : "not_interactive";
    }
}
