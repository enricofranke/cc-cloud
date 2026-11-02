package ai.ezfox.cccloud.code.api;

import ai.ezfox.cccloud.code.world.ScriptWorld;
import ai.ezfox.cccloud.code.world.WorldAction;
import ai.ezfox.cccloud.code.world.WorldEvents;
import ai.ezfox.cccloud.code.world.WorldSnapshot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * What one running script can do, implemented by the runtime ({@code ScriptRuntime}) and used by the {@link CloudApi}
 * handlers. Every method is called on the script's worker thread while the script runs; futures complete on other
 * threads and resume the script.
 */
public interface ScriptHost {
    /** The newest snapshot of the mainframe, made for this script's rights. */
    WorldSnapshot snapshot();

    /** Runs an action on the server thread (rate limited, audited): {@code {true}} or {@code {false, error}}. */
    CompletableFuture<@Nullable Object[]> act(WorldAction action);

    int on(WorldEvents.Filter filter, ScriptFunction handler);

    /** Completes with the event table, or null after {@code timeout} seconds (negative: no timeout). */
    CompletableFuture<@Nullable Object> waitEvent(WorldEvents.Filter filter, double timeout);

    boolean off(int id);

    CompletableFuture<@Nullable Object> sleep(double seconds);

    int every(double seconds, ScriptFunction handler);

    int after(double seconds, ScriptFunction handler);

    int at(TimeSpec time, ScriptFunction handler);

    boolean notify(ScriptWorld.Channel channel, String text, String level, @Nullable String title);

    @Nullable Object storeGet(String key);

    /** Stores a value (null deletes); false if it does not fit the storage limit. */
    boolean storeSet(String key, @Nullable Object value);

    List<String> storeKeys();

    /** A line in the script's log ({@code info}, {@code warn}); false if it was dropped by the rate limit. */
    boolean log(String level, String text);

    /** Seconds since the script started. */
    double clock();

    /** {@code cloud.script}: id, name, author, version, language. */
    Map<String, Object> info();

    /** When {@code cloud.at} fires: a real time of day on some week days, or an in-game time of day. */
    sealed interface TimeSpec {
        /** {@code minute} of the day (0–1439) in the server's time zone; {@code days} bit 0 = Monday … bit 6 = Sunday. */
        record Daily(int minute, int days) implements TimeSpec {
        }

        /** In-game time of day, 0–23999. */
        record Game(long time) implements TimeSpec {
        }
    }
}
