package ai.ezfox.cccloud.bridge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An immutable, sorted snapshot of a storage network (count descending, then name) that searches run against. Pure
 * Java, so it is unit tested without a game. Building it is O(n); {@link BridgeCore} rebuilds it at most every
 * {@link BridgeCore#INDEX_TTL} ticks, so frequent searches do not rescan the network.
 */
public final class ItemIndex {
    /** Networks with more item types than this are cut off (the rest is reported as truncated). */
    public static final int MAX_ENTRIES = 50_000;
    public static final ItemIndex EMPTY = new ItemIndex(List.of(), false);

    public record Page(List<ItemRow> rows, boolean truncated) {
    }

    private final List<ItemRow> rows;
    private final Map<String, ItemRow> byName;
    private final String[] haystack;
    private final boolean truncated;

    public ItemIndex(List<ItemRow> rows, boolean truncated) {
        var sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingLong(ItemRow::count).reversed().thenComparing(ItemRow::name));
        this.rows = List.copyOf(sorted);
        this.byName = new HashMap<>(sorted.size() * 2);
        this.haystack = new String[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            var row = sorted.get(i);
            byName.put(row.name(), row);
            haystack[i] = (row.name() + "\u0000" + row.displayName()).toLowerCase(Locale.ROOT);
        }
        this.truncated = truncated;
    }

    public int size() {
        return rows.size();
    }

    /** Every row (count descending); immutable. */
    public List<ItemRow> rows() {
        return rows;
    }

    public boolean truncated() {
        return truncated;
    }

    public @Nullable ItemRow get(String name) {
        return byName.get(name);
    }

    /**
     * Case-insensitive search on id and display name. An empty query matches everything; {@code @modid} matches the
     * namespace; several words must all match.
     */
    public Page search(@Nullable String query, int limit) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
        var result = new ArrayList<ItemRow>(Math.min(limit, 64));
        boolean more = false;
        for (int i = 0; i < rows.size(); i++) {
            if (!matches(i, words)) continue;
            if (result.size() >= limit) {
                more = true;
                break;
            }
            result.add(rows.get(i));
        }
        return new Page(result, more || (truncated && result.size() >= limit));
    }

    private boolean matches(int i, String[] words) {
        for (var word : words) {
            if (word.startsWith("@") && word.length() > 1) {
                if (!rows.get(i).name().startsWith(word.substring(1) + ":")) return false;
            } else if (!haystack[i].contains(word)) {
                return false;
            }
        }
        return true;
    }
}
