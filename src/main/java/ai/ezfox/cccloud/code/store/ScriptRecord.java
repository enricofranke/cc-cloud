package ai.ezfox.cccloud.code.store;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A saved script of a mainframe: id (8 hex characters), name, language, its versions (oldest first, the last one is
 * current), autostart (and who switched it on: an autostarted run acts with that player's rights as the starter) and
 * the script's own key-value storage ({@code cloud.store}: key → JSON text). Immutable.
 */
public record ScriptRecord(String id, String name, String language, boolean autostart, @Nullable UUID autostartBy, String autostartByName, long created,
                           List<ScriptVersion> versions, Map<String, String> store) {
    public ScriptRecord {
        if (versions.isEmpty()) throw new IllegalArgumentException("a script has at least one version");
        versions = List.copyOf(versions);
        store = Map.copyOf(store);
    }

    public ScriptVersion current() {
        return versions.getLast();
    }

    public int version() {
        return current().version();
    }

    public String source() {
        return current().source();
    }

    /** Who saved the current version: the script acts with at most their rights. */
    public UUID author() {
        return current().author();
    }

    public String authorName() {
        return current().authorName();
    }

    public long updated() {
        return current().time();
    }

    public @Nullable ScriptVersion version(int number) {
        for (var v : versions) if (v.version() == number) return v;
        return null;
    }

    public ScriptRecord withName(String name) {
        return new ScriptRecord(id, name, language, autostart, autostartBy, autostartByName, created, versions, store);
    }

    public ScriptRecord withAutostart(boolean on, @Nullable UUID by, String byName) {
        return new ScriptRecord(id, name, language, on, on ? by : null, on ? byName : "", created, versions, store);
    }

    public ScriptRecord withVersions(List<ScriptVersion> list) {
        return new ScriptRecord(id, name, language, autostart, autostartBy, autostartByName, created, list, store);
    }

    public ScriptRecord withStore(Map<String, String> map) {
        return new ScriptRecord(id, name, language, autostart, autostartBy, autostartByName, created, versions, map);
    }
}
