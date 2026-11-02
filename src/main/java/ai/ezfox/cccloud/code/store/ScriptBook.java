package ai.ezfox.cccloud.code.store;

import ai.ezfox.cccloud.code.runtime.Limits;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The saved scripts of one mainframe and every change to them: create, save (a new version), rename, delete, autostart,
 * revert, the scripts' storage. Names, sizes and counts are checked here; rights are not (the caller checked
 * {@code code.edit}). Plain Java, unit tested; {@code CodeService} keeps one per mainframe on the server thread.
 *
 * <p>Versions: every save that changes the source adds one (numbered 1, 2, …, never reused); the newest
 * {@link Limits#versionsKept()} are kept, and older ones go first while the history is over {@link #MAX_HISTORY} bytes.
 */
public final class ScriptBook {
    /** Source bytes of all kept versions of one script. */
    public static final int MAX_HISTORY = 256 * 1024;
    public static final int MAX_NAME = 32;

    /** {@code error} (+ {@code detail}) or the changed script. */
    public record Result(@Nullable String error, @Nullable String detail, @Nullable ScriptRecord script) {
        static Result fail(String error, @Nullable String detail) {
            return new Result(error, detail, null);
        }

        static Result ok(ScriptRecord script) {
            return new Result(null, null, script);
        }
    }

    private final List<ScriptRecord> scripts = new ArrayList<>();

    public ScriptBook(List<ScriptRecord> scripts) {
        this.scripts.addAll(scripts);
    }

    public List<ScriptRecord> scripts() {
        return List.copyOf(scripts);
    }

    public int size() {
        return scripts.size();
    }

    public @Nullable ScriptRecord get(String id) {
        for (var s : scripts) if (s.id().equals(id)) return s;
        return null;
    }

    /** By id, else by name (case-insensitive). */
    public @Nullable ScriptRecord find(String ref) {
        var byId = get(ref);
        if (byId != null) return byId;
        String lower = ref.strip().toLowerCase(Locale.ROOT);
        for (var s : scripts) if (s.name().toLowerCase(Locale.ROOT).equals(lower)) return s;
        return null;
    }

    /**
     * A script name: trimmed, 1–32 characters, no control characters, not {@code all} (that is {@code /cccloud scripts
     * stop all}). Null if invalid.
     */
    public static @Nullable String validName(@Nullable String raw) {
        if (raw == null) return null;
        String name = raw.strip();
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > MAX_NAME) return null;
        if (name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.LINE_SEPARATOR
            || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) {
            return null;
        }
        if (name.equalsIgnoreCase("all")) return null;
        return name;
    }

    private @Nullable String nameError(String name, @Nullable String exceptId) {
        for (var s : scripts) {
            if (!s.id().equals(exceptId) && s.name().equalsIgnoreCase(name)) return "name_taken";
        }
        return null;
    }

    private static int bytes(String source) {
        return source.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Creates ({@code id} null) or saves a script. A save that changes neither source nor name changes nothing; one that
     * only changes the name renames. {@code baseVersion}: the version the editor started from; another current version
     * is {@code changed} (detail: the current version), so two people do not overwrite each other unknowingly.
     */
    public Result save(@Nullable String id, String rawName, String source, @Nullable Integer baseVersion, UUID author, String authorName, long now, Limits limits,
                       Supplier<String> newId) {
        String name = validName(rawName);
        if (name == null) return Result.fail("invalid", "name");
        if (bytes(source) > limits.maxSource()) return Result.fail("too_large", String.valueOf(limits.maxSource()));
        String taken = nameError(name, id);
        if (taken != null) return Result.fail(taken, name);
        if (id == null) {
            if (scripts.size() >= limits.maxScripts()) return Result.fail("too_many", String.valueOf(limits.maxScripts()));
            String fresh;
            do {
                fresh = newId.get();
            } while (get(fresh) != null);
            var record = new ScriptRecord(fresh, name, "lua", false, null, "", now, List.of(new ScriptVersion(1, now, author, authorName, source, null)), Map.of());
            scripts.add(record);
            return Result.ok(record);
        }
        var old = get(id);
        if (old == null) return Result.fail("unknown_script", null);
        if (baseVersion != null && baseVersion != old.version()) return Result.fail("changed", String.valueOf(old.version()));
        var record = old.name().equals(name) ? old : old.withName(name);
        if (!old.source().equals(source)) record = withVersion(record, new ScriptVersion(old.version() + 1, now, author, authorName, source, null), limits);
        replace(record);
        return Result.ok(record);
    }

    /** Old source as a new version ({@code note: "revert of vN"}); the old version stays where it was. */
    public Result revert(String id, int version, UUID author, String authorName, long now, Limits limits) {
        var old = get(id);
        if (old == null) return Result.fail("unknown_script", null);
        var target = old.version(version);
        if (target == null) return Result.fail("unknown_version", null);
        var record = withVersion(old, new ScriptVersion(old.version() + 1, now, author, authorName, target.source(), "revert of v" + version), limits);
        replace(record);
        return Result.ok(record);
    }

    public Result rename(String id, String rawName) {
        var old = get(id);
        if (old == null) return Result.fail("unknown_script", null);
        String name = validName(rawName);
        if (name == null) return Result.fail("invalid", "name");
        String taken = nameError(name, id);
        if (taken != null) return Result.fail(taken, name);
        var record = old.withName(name);
        replace(record);
        return Result.ok(record);
    }

    public Result setAutostart(String id, boolean on, UUID by, String byName) {
        var old = get(id);
        if (old == null) return Result.fail("unknown_script", null);
        var record = old.withAutostart(on, by, byName);
        replace(record);
        return Result.ok(record);
    }

    /** The script's {@code cloud.store} contents (already within the storage limit). */
    public void setStore(String id, Map<String, String> store) {
        var old = get(id);
        if (old != null) replace(old.withStore(store));
    }

    public @Nullable ScriptRecord delete(String id) {
        var old = get(id);
        if (old != null) scripts.remove(old);
        return old;
    }

    private void replace(ScriptRecord record) {
        for (int i = 0; i < scripts.size(); i++) {
            if (scripts.get(i).id().equals(record.id())) {
                scripts.set(i, record);
                return;
            }
        }
        scripts.add(record);
    }

    /** Adds a version and drops old ones beyond the version count and the history size (the newest always stays). */
    static ScriptRecord withVersion(ScriptRecord record, ScriptVersion next, Limits limits) {
        var list = new ArrayList<>(record.versions());
        list.add(next);
        while (list.size() > Math.max(1, limits.versionsKept())) list.removeFirst();
        int total = 0;
        for (var v : list) total += v.size();
        while (list.size() > 1 && total > MAX_HISTORY) total -= list.removeFirst().size();
        return record.withVersions(list);
    }
}
