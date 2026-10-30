package ai.ezfox.cccloud.compat.ftbquests.install;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The quest chapters CC: Cloud ships: the files of {@code integration/ftbquests/quests}, copied into the standard jar as
 * {@code /cccloud_quests/} at build time (the modpack jar has none). {@link #version} changes whenever the content does,
 * so a newer mod release with changed chapters triggers an update.
 */
public final class QuestPack {
    /** Chapter file names (without .json5), in quest book order. */
    public static final List<String> CHAPTERS = List.of("cc_tweaked", "cc_cloud");
    /** Locales with text. */
    public static final List<String> LOCALES = List.of("en_us", "de_de");
    public static final String CLASSPATH_ROOT = "/cccloud_quests/";

    /**
     * One chapter: its source text, chapter id, every id it defines (chapter, quests, tasks, rewards, quest links,
     * images) and per locale its texts: {@code chapterKeys} go to {@code lang/<locale>/chapter.json5}, {@code questKeys}
     * to {@code lang/<locale>/chapters/<name>.json5}.
     */
    public record Chapter(String name, String id, String text, Json5.Node root, Set<String> definedIds, int quests,
                          Map<String, Map<String, Json5.Node>> chapterKeys, Map<String, Map<String, Json5.Node>> questKeys) {
        public String title() {
            var en = chapterKeys.get("en_us");
            Json5.Node t = en == null ? null : en.get("chapter." + id + ".title");
            return t != null && t.string() != null ? t.string() : name;
        }
    }

    public final List<Chapter> chapters;
    public final String version;

    private QuestPack(List<Chapter> chapters, String version) {
        this.chapters = chapters;
        this.version = version;
    }

    public Chapter chapter(String name) {
        for (Chapter c : chapters) if (c.name().equals(name)) return c;
        return null;
    }

    /** The pack from the jar ({@code /cccloud_quests/}); throws if the files are missing. */
    public static QuestPack fromClasspath() {
        return load(path -> {
            try (InputStream in = QuestPack.class.getResourceAsStream(CLASSPATH_ROOT + path)) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** The pack from a folder laid out like {@code integration/ftbquests/quests}. */
    public static QuestPack fromDirectory(Path dir) {
        return load(path -> {
            Path p = dir.resolve(path);
            try {
                return Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Every file path of the pack (relative, '/'-separated). */
    public static List<String> files() {
        List<String> out = new ArrayList<>();
        for (String c : CHAPTERS) out.add("chapters/" + c + ".json5");
        for (String l : LOCALES) {
            out.add("lang/" + l + "/chapter.additions.json5");
            for (String c : CHAPTERS) out.add("lang/" + l + "/chapters/" + c + ".json5");
        }
        return out;
    }

    public static QuestPack load(Function<String, String> reader) {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String f : files()) {
            String text = reader.apply(f);
            if (text == null) throw new IllegalStateException("quest file missing: " + f);
            texts.put(f, text.replace("\r\n", "\n"));
        }
        var versionInput = new StringBuilder();
        for (var e : texts.entrySet()) versionInput.append(e.getKey()).append(':').append(Json5.canonical(Json5.parse(e.getValue()))).append('\n');
        String version = Json5.sha256(versionInput.toString()).substring(0, 16);

        Map<String, Json5.Node> additionRoots = new LinkedHashMap<>();
        for (String l : LOCALES) additionRoots.put(l, Json5.parse(texts.get("lang/" + l + "/chapter.additions.json5")));

        List<Chapter> chapters = new ArrayList<>();
        for (String name : CHAPTERS) {
            String text = texts.get("chapters/" + name + ".json5");
            Json5.Node root = Json5.parse(text);
            String id = root.stringOf("id");
            if (id == null) throw new IllegalStateException("chapter " + name + " has no id");
            Set<String> ids = new LinkedHashSet<>();
            collectIds(root, ids);
            Json5.Node quests = root.get("quests");
            Map<String, Map<String, Json5.Node>> chapterKeys = new LinkedHashMap<>();
            Map<String, Map<String, Json5.Node>> questKeys = new LinkedHashMap<>();
            for (String l : LOCALES) {
                Map<String, Json5.Node> ck = new LinkedHashMap<>();
                for (Json5.Member m : additionRoots.get(l).members) {
                    if (m.key().startsWith("chapter." + id + ".")) ck.put(m.key(), m.value());
                }
                chapterKeys.put(l, ck);
                Map<String, Json5.Node> qk = new LinkedHashMap<>();
                for (Json5.Member m : Json5.parse(texts.get("lang/" + l + "/chapters/" + name + ".json5")).members) qk.put(m.key(), m.value());
                questKeys.put(l, qk);
            }
            chapters.add(new Chapter(name, id, text, root, ids, quests == null ? 0 : quests.items.size(), chapterKeys, questKeys));
        }
        return new QuestPack(List.copyOf(chapters), version);
    }

    /** Every 16-digit hex value of an {@code id} key, anywhere in the tree. */
    static void collectIds(Json5.Node n, Set<String> out) {
        if (n.kind == Json5.Kind.OBJECT) {
            for (Json5.Member m : n.members) {
                if (m.key().equals("id") && m.value().string() != null && m.value().string().matches("[0-9A-F]{16}")) out.add(m.value().string());
                collectIds(m.value(), out);
            }
        } else if (n.kind == Json5.Kind.ARRAY) {
            for (Json5.Node item : n.items) collectIds(item, out);
        }
    }
}
