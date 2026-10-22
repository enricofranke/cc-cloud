package ai.ezfox.cccloud.network;

import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.web.ActionResult;
import ai.ezfox.cccloud.web.QueryResult;
import com.google.gson.JsonElement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * One section ("card") on the dashboard: a Cloud Uplink or a linked ME/RS Cloud Bridge. The web layer dispatches
 * actions and queries by section id ({@code uplinkId} in the protocol). Server thread only.
 */
public interface DashboardSection {
    UUID sectionId();

    @Nullable MainframeLink getLink();

    String getTitle();

    CompletableFuture<ActionResult> handleAction(String widgetId, JsonElement value, @Nullable Op op, String user, boolean pinVerified);

    QueryResult handleQuery(String widgetId, String query);
}
