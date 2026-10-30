package ai.ezfox.cccloud.config;

import java.util.ArrayList;
import java.util.List;

/**
 * What a config reload changed and did: printed by {@code /cccloud config reload} and written to the server log. Values
 * are shown with {@link ConfigText#display} (secrets hidden).
 *
 * @param source  "file" (NeoForge reloaded the file), "command" or "test"
 * @param applied notes of what was re-applied (one line each)
 * @param errors  what could not be applied (the old state stays; the keys are retried by the next reload)
 */
public record ConfigReport(String source, List<Change> changes, List<String> applied, List<String> errors) {
    /** One changed value; {@code applied=false} when applying it failed (see the errors) and the old value still holds. */
    public record Change(String key, String before, String after, Reload reload, boolean applied) {
    }

    public ConfigReport {
        changes = List.copyOf(changes);
        applied = List.copyOf(applied);
        errors = List.copyOf(errors);
    }

    public static ConfigReport none(String source) {
        return new ConfigReport(source, List.of(), List.of(), List.of());
    }

    public ConfigReport withErrors(List<String> more) {
        if (more.isEmpty()) return this;
        var all = new ArrayList<>(errors);
        all.addAll(more);
        return new ConfigReport(source, changes, applied, all);
    }

    public List<String> keys() {
        return changes.stream().map(Change::key).toList();
    }

    public List<String> restartKeys() {
        return changes.stream().filter(c -> c.reload() == Reload.RESTART).map(Change::key).toList();
    }

    public List<String> liveKeys() {
        return changes.stream().filter(c -> c.reload() == Reload.LIVE && c.applied()).map(Change::key).toList();
    }

    public List<String> failedKeys() {
        return changes.stream().filter(c -> c.reload() == Reload.LIVE && !c.applied()).map(Change::key).toList();
    }

    /** The report as lines (chat and log). */
    public List<String> lines() {
        var lines = new ArrayList<String>();
        if (changes.isEmpty()) {
            lines.add("CC: Cloud config: no changed values.");
            for (var note : applied) lines.add("  " + note);
        } else {
            lines.add("CC: Cloud config: " + changes.size() + " value(s) changed:");
            for (var c : changes) lines.add("  " + c.key() + ": " + c.before() + " -> " + c.after());
            var live = liveKeys();
            lines.add("Applied live: " + (live.isEmpty() ? "none" : String.join(", ", live)));
            for (var note : applied) lines.add("  " + note);
            var restart = restartKeys();
            lines.add("Needs a restart: " + (restart.isEmpty() ? "none" : String.join(", ", restart)));
        }
        var failed = failedKeys();
        if (!failed.isEmpty()) lines.add("Not applied (the previous values still hold, retried by the next reload): " + String.join(", ", failed));
        for (var error : errors) lines.add("Error: " + error);
        return lines;
    }
}
