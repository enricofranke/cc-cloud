package ai.ezfox.cccloud.bridge;

import ai.ezfox.cccloud.uplink.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Storage card v2 listings (docs/SPEC.md "Storage card v2 – protocol"): browse with sorting, filters and favorites
 * pinned on top, the craftables list and paging. Pure Java over {@link ItemIndex} rows, unit tested.
 */
public final class StorageQueries {
    public static final int MAX_LIMIT = 200;

    public enum Sort { COUNT, NAME, MOD }

    public enum Filter { ALL, CRAFTABLE, FAVORITES }

    private StorageQueries() {
    }

    /** One page: rows (with the favorite flag), the total after filtering, the offset. */
    public static JsonObject browse(ItemIndex index, Set<String> favorites, Sort sort, boolean desc, Filter filter, @Nullable String query, int offset, int limit) {
        var rows = new ArrayList<ItemRow>(index.search(query, Integer.MAX_VALUE).rows());
        rows.removeIf(r -> switch (filter) {
            case ALL -> false;
            case CRAFTABLE -> !r.craftable();
            case FAVORITES -> !favorites.contains(r.name());
        });
        Comparator<ItemRow> order = switch (sort) {
            case COUNT -> Comparator.comparingLong(ItemRow::count);
            case NAME -> Comparator.comparing((ItemRow r) -> r.displayName().toLowerCase(Locale.ROOT));
            case MOD -> Comparator.comparing((ItemRow r) -> r.name().substring(0, Math.max(0, r.name().indexOf(':'))))
                .thenComparing(r -> r.displayName().toLowerCase(Locale.ROOT));
        };
        if (desc) order = order.reversed();
        order = order.thenComparing(ItemRow::name);
        // Favorites first in every sort.
        Comparator<ItemRow> pinned = Comparator.comparing((ItemRow r) -> !favorites.contains(r.name()));
        rows.sort(pinned.thenComparing(order));
        return page(rows, favorites, offset, limit);
    }

    /** Everything with a pattern (count may be 0), by name. */
    public static JsonObject craftables(ItemIndex index, Set<String> favorites, @Nullable String query, int offset, int limit) {
        var rows = new ArrayList<ItemRow>(index.search(query, Integer.MAX_VALUE).rows());
        rows.removeIf(r -> !r.craftable());
        rows.sort(Comparator.comparing((ItemRow r) -> r.displayName().toLowerCase(Locale.ROOT)).thenComparing(ItemRow::name));
        return page(rows, favorites, offset, limit);
    }

    static JsonObject page(List<ItemRow> rows, Set<String> favorites, int offset, int limit) {
        int from = Math.max(0, Math.min(offset, rows.size()));
        int to = Math.min(rows.size(), from + Math.max(1, Math.min(MAX_LIMIT, limit)));
        var array = new JsonArray();
        for (var row : rows.subList(from, to)) {
            var json = new JsonObject();
            json.addProperty("name", row.name());
            json.addProperty("displayName", row.displayName());
            json.add("count", Json.number(row.count()));
            json.addProperty("craftable", row.craftable());
            json.addProperty("favorite", favorites.contains(row.name()));
            array.add(json);
        }
        var result = new JsonObject();
        result.add("rows", array);
        result.addProperty("total", rows.size());
        result.addProperty("offset", from);
        return result;
    }
}
