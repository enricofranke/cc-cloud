package ai.ezfox.cccloud.web;

import com.google.gson.JsonArray;
import org.jspecify.annotations.Nullable;

/** Answer to an item search: rows {name, displayName, count, craftable}, capped at 50. */
public record QueryResult(JsonArray rows, boolean truncated, @Nullable String error) {
    public static QueryResult fail(String error) {
        return new QueryResult(new JsonArray(), false, error);
    }
}
