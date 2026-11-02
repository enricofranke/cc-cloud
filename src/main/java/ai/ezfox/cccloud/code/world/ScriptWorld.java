package ai.ezfox.cccloud.code.world;

import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The game as a running script sees it: the only way out of the sandbox. The Minecraft side ({@code GameWorld}) runs
 * every call on the server thread with the script's live rights; unit tests use a fake. Called from script worker
 * threads; implementations must not block them.
 */
public interface ScriptWorld {
    /** Runs an action; completes with {@code {true}} / {@code {false, error}} (item counts: {@code {count}} or {@code {nil}}). */
    CompletableFuture<@Nullable Object[]> act(ScriptIdentity who, WorldAction action);

    enum Channel { TOAST, PUSH, BOTH }

    /** A dashboard toast and/or a push notification ({@code level}: info, warn or crit). False if it was not sent. */
    boolean notify(ScriptIdentity who, Channel channel, String text, String level, @Nullable String title);

    /** An entry in the mainframe's audit history ({@code text} already names the script and its author). */
    void audit(ScriptIdentity who, String text);
}
