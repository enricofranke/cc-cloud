package ai.ezfox.cccloud.reports;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Where the mod's catch blocks hand over a failure. No game classes and no work: it costs one field read while nothing
 * is running or the owner has not agreed, and it can never throw back into the code that caught the failure.
 */
public final class ReportCapture {
    private static volatile @Nullable ReportEngine engine;

    private ReportCapture() {
    }

    static void engine(@Nullable ReportEngine current) {
        engine = current;
    }

    public static void failure(ReportPayload.Where where, Throwable failure) {
        var current = engine;
        if (current == null) return;
        try {
            current.error(where, failure);
        } catch (Throwable ignored) { // NOSONAR: called from catch blocks, nothing may come back out of here
            // no report then
        }
    }

    /** Like {@link #failure} for the web layer, where dropped connections and port scanners are everyday business. */
    public static void web(ReportPayload.Where where, Throwable failure) {
        if (failure instanceof IOException || failure.getCause() instanceof IOException) return;
        failure(where, failure);
    }
}
