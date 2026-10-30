package ai.ezfox.cccloud.compat.ftbquests.install;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/cccloud/quests-installed.json}: what the installer did, so it acts only once and later only touches
 * what it wrote itself. Per chapter a status and the content hash of what was written; per locale the hash of every
 * text (lang key) it wrote. Hashes are of the canonical content ({@link Json5#canonical}), so FTB Quests re-saving the
 * book in its own layout does not count as a change.
 */
public final class QuestMarker {
    public static final int FORMAT = 1;

    public enum Status {
        /** Ours, kept up to date while unchanged. */
        INSTALLED,
        /** Was ours, an admin deleted it: never added again. */
        REMOVED,
        /** Not installed on purpose (e.g. the pack has its own CC: Tweaked chapter): never added later. */
        SKIPPED,
        /** Already in the quest book when we came (added by hand, another version): never touched. */
        KEPT
    }

    public static final class ChapterState {
        public Status status;
        public String id;
        public String hash;
        public String reason;

        ChapterState(Status status, String id, String hash, String reason) {
            this.status = status;
            this.id = id;
            this.hash = hash;
            this.reason = reason;
        }

        ChapterState copy() {
            return new ChapterState(status, id, hash, reason);
        }
    }

    public String questVersion;
    public String modVersion;
    public String updated;
    /** The chapter group our chapters went into (ATM11's Tech group or our own), or "" for the default group. */
    public String group;
    public final Map<String, ChapterState> chapters = new LinkedHashMap<>();
    public final Map<String, Map<String, String>> lang = new LinkedHashMap<>();

    QuestMarker copy() {
        var m = new QuestMarker();
        m.questVersion = questVersion;
        m.modVersion = modVersion;
        m.updated = updated;
        m.group = group;
        chapters.forEach((k, v) -> m.chapters.put(k, v.copy()));
        lang.forEach((k, v) -> m.lang.put(k, new LinkedHashMap<>(v)));
        return m;
    }

    public ChapterState chapter(String name) {
        return chapters.get(name);
    }

    Map<String, String> lang(String locale) {
        return lang.computeIfAbsent(locale, k -> new LinkedHashMap<>());
    }

    /** The marker, or null if there is none. Throws if the file exists but cannot be read. */
    public static QuestMarker read(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!o.has("format") || o.get("format").getAsInt() != FORMAT) throw new IOException("unknown format");
        var m = new QuestMarker();
        m.questVersion = str(o, "questVersion");
        m.modVersion = str(o, "modVersion");
        m.updated = str(o, "updated");
        m.group = str(o, "group");
        if (o.has("chapters")) {
            for (var e : o.getAsJsonObject("chapters").entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                m.chapters.put(e.getKey(), new ChapterState(Status.valueOf(str(c, "status").toUpperCase(java.util.Locale.ROOT)),
                    str(c, "id"), str(c, "hash"), str(c, "reason")));
            }
        }
        if (o.has("lang")) {
            for (var e : o.getAsJsonObject("lang").entrySet()) {
                Map<String, String> keys = new LinkedHashMap<>();
                for (var k : e.getValue().getAsJsonObject().entrySet()) keys.put(k.getKey(), k.getValue().getAsString());
                m.lang.put(e.getKey(), keys);
            }
        }
        return m;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    public String toJson() {
        var o = new JsonObject();
        o.addProperty("format", FORMAT);
        o.addProperty("note", "Written by CC: Cloud. Chapters marked removed/skipped/kept are never added or changed. "
            + "Delete this file to let CC: Cloud check the quest book again; installQuests = false in cccloud-server.toml turns it off.");
        o.addProperty("questVersion", questVersion);
        o.addProperty("modVersion", modVersion);
        o.addProperty("updated", updated);
        o.addProperty("group", group);
        var cs = new JsonObject();
        chapters.forEach((name, c) -> {
            var j = new JsonObject();
            j.addProperty("status", c.status.name().toLowerCase(java.util.Locale.ROOT));
            j.addProperty("id", c.id);
            if (c.hash != null) j.addProperty("hash", c.hash);
            if (c.reason != null) j.addProperty("reason", c.reason);
            cs.add(name, j);
        });
        o.add("chapters", cs);
        var ls = new JsonObject();
        lang.forEach((locale, keys) -> {
            var j = new JsonObject();
            keys.forEach(j::addProperty);
            ls.add(locale, j);
        });
        o.add("lang", ls);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n";
    }
}
