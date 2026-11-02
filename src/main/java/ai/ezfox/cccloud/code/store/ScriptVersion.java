package ai.ezfox.cccloud.code.store;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One saved version of a script: its source, when and by whom it was saved. The newest version is the script; whoever
 * saved it is the script's author (it acts with at most their rights). {@code note}: {@code "revert of v3"} and the like.
 */
public record ScriptVersion(int version, long time, UUID author, String authorName, String source, @Nullable String note) {
    /** UTF-8 bytes of the source. */
    public int size() {
        return source.getBytes(StandardCharsets.UTF_8).length;
    }
}
