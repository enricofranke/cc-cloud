package ai.ezfox.cccloud.web;

import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/** Outcome of a dashboard action: {@code error == null} means ok; craft actions also carry the job id. */
public record ActionResult(@Nullable String error, @Nullable String jobId) {
    public static final ActionResult OK = new ActionResult(null, null);

    public static ActionResult fail(String error) {
        return new ActionResult(error, null);
    }

    public static ActionResult of(@Nullable String error) {
        return error == null ? OK : fail(error);
    }

    public static CompletableFuture<ActionResult> done(@Nullable String error) {
        return CompletableFuture.completedFuture(of(error));
    }

    public boolean ok() {
        return error == null;
    }
}
