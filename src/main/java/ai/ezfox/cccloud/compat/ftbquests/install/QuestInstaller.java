package ai.ezfox.cccloud.compat.ftbquests.install;

import ai.ezfox.cccloud.Edition;
import ai.ezfox.cccloud.compat.ftbquests.install.QuestMarker.ChapterState;
import ai.ezfox.cccloud.compat.ftbquests.install.QuestMarker.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Adds CC: Cloud's FTB Quests chapters to the quest book ({@code <config>/ftbquests/quests}) once, and keeps them up
 * to date while nobody changed them. Pure file logic (no FTB Quests classes); {@code QuestAutoInstall} runs it on
 * server start before FTB Quests reads the book. The same rules as {@code integration/ftbquests/install.sh}:
 * <ul>
 *   <li>a backup of the book before anything changes ({@code ftbquests/quests.backup-<time>});</li>
 *   <li>a chapter file that is not ours (another chapter id) is never replaced;</li>
 *   <li>lang keys are only added, never overwritten; later only keys we wrote and nobody changed are updated;</li>
 *   <li>ATM11's Tech group when it exists, otherwise a "CC: Cloud" group;</li>
 *   <li>if any of our ids is already used in the book, nothing is installed (retried on the next start);</li>
 *   <li>the CC: Tweaked chapter only when the book has no CC: Tweaked chapter of its own (CC: Cloud's links to it are
 *   then left out).</li>
 * </ul>
 * Once only: the marker ({@link QuestMarker}) remembers every decision. A chapter an admin deleted is never added
 * again; a changed chapter is never updated.
 */
public final class QuestInstaller {
    public static final String TECH_GROUP = "2B51AC12041E3F89";
    /** Our own chapter group when the pack has no Tech group (checked against every id of ATM11's quest book). */
    public static final String OWN_GROUP = "754527ACD5F594C9";
    public static final String OWN_GROUP_TITLE = "CC: Cloud";
    public static final String MARKER = "cccloud/quests-installed.json";
    /** Where a chapter sits (group, position): an admin may move it, that is not a change of its content. */
    static final Set<String> PLACEMENT = Set.of("group", "order_index");
    private static final Pattern HEX_ID = Pattern.compile("\\b[0-9A-F]{16}\\b");
    private static final Pattern CHAPTER_ID = Pattern.compile("(?m)^\\s*\"?id\"?\\s*:\\s*\"([0-9A-F]{16})\"");
    private static final Pattern CC_ITEM = Pattern.compile("\"?\\bid\"?\\s*:\\s*[\"'](computercraft:[^\"']+)[\"']");
    private static final Pattern ANY_KEY = Pattern.compile("(?m)^\\s*(?:\"([^\"]+)\"|'([^']+)'|([A-Za-z_$][\\w$]*))\\s*:");

    /**
     * FTB Quests' defaults for a new quest book (BaseQuestFile's constructor and writeData in FTB Quests 26.1.2.9,
     * file version 13): written only when the book has no data.json5, so FTB Quests loads it without an error.
     */
    static final String DEFAULT_DATA = """
        {
          version: 13,
          default_reward_team: false,
          default_consume_items: false,
          default_autoclaim_rewards: "disabled",
          default_quest_shape: "circle",
          default_quest_disable_jei: false,
          emergency_items_cooldown: 300,
          drop_loot_crates: false,
          loot_crate_no_drop: {
            passive: 4000,
            monster: 600,
            boss: 0,
          },
          disable_gui: false,
          grid_scale: 0.5,
          pause_game: false,
          lock_message: "",
          progression_mode: "linear",
          detection_delay: 20,
          show_lock_icons: false,
          drop_book_on_death: false,
          hide_excluded_quests: false,
          fallback_locale: "en_us",
          verify_on_load: false,
        }
        """;

    public record Settings(boolean installQuests, Edition edition, String modVersion) {
    }

    /** What happened: one line for the log ({@code warning} = WARN level), whether files changed, the backup. */
    public record Result(boolean warning, boolean changed, String message, Path backup) {
    }

    private final Path configDir;
    private final Path questDir;
    private final Path markerFile;
    private final Clock clock;

    public QuestInstaller(Path configDir, Clock clock) {
        this.configDir = configDir;
        this.questDir = configDir.resolve("ftbquests").resolve("quests");
        this.markerFile = configDir.resolve(MARKER);
        this.clock = clock;
    }

    public Path questDir() {
        return questDir;
    }

    public Path markerFile() {
        return markerFile;
    }

    /**
     * The content hash of a chapter: where it sits (group, position) does not count, nor how FTB Quests writes an item
     * of a mod that is not installed. FTB Quests wraps such an item as {@code ftbquests:missing_item} with the original
     * in its components (lossless) whenever it saves the book; unwrapped, the hash is the same as before the save.
     */
    static String contentHash(Json5.Node chapter) {
        Object value = Json5.plain(chapter);
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(map);
            copy.keySet().removeAll(PLACEMENT);
            value = copy;
        }
        return Json5.sha256(Json5.canonicalPlain(unwrapMissingItems(value)));
    }

    static Object unwrapMissingItems(Object v) {
        if (v instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((k, x) -> out.put(k, unwrapMissingItems(x)));
            if ("ftbquests:missing_item".equals(out.get("id")) && out.get("components") instanceof Map<?, ?> c
                && c.get("ftbquests:missing_item") instanceof String real) {
                out.put("id", real);
                if (c.get("ftbquests:missing_item_count") != null) out.put("count", c.get("ftbquests:missing_item_count"));
                if (c.get("ftbquests:missing_item_data") != null) out.put("components", c.get("ftbquests:missing_item_data"));
                else out.remove("components");
            }
            return out;
        }
        if (v instanceof List<?> list) return list.stream().map(QuestInstaller::unwrapMissingItems).toList();
        return v;
    }

    // ------------------------------------------------------------------------------------------------ run

    public Result run(Settings settings, Supplier<QuestPack> packSupplier) throws IOException {
        if (settings.edition() == Edition.MODPACK) {
            return new Result(false, false, "modpack edition: the quest book is left to the modpack (CC: Cloud's chapters are in "
                + "cccloud-quests-atm11-" + settings.modVersion() + ".zip on the release page).", null);
        }
        if (!settings.installQuests()) {
            return new Result(false, false, "installQuests = false in cccloud-server.toml: the quest book is not touched.", null);
        }
        QuestPack pack;
        try {
            pack = packSupplier.get();
        } catch (RuntimeException e) {
            return new Result(true, false, "the quest files are missing from this jar (" + e.getMessage() + "); nothing changed.", null);
        }
        QuestMarker marker;
        try {
            marker = QuestMarker.read(markerFile);
        } catch (IOException | RuntimeException e) {
            return new Result(true, false, display(markerFile) + " cannot be read (" + e.getMessage() + "); nothing changed. Delete it "
                + "to let CC: Cloud check the quest book again.", null);
        }
        if (marker != null && pack.version.equals(marker.questVersion)) {
            return new Result(false, false, "quest chapters up to date (" + states(marker, pack) + ").", null);
        }
        return new Run(pack, marker, settings).execute();
    }

    private static String states(QuestMarker m, QuestPack pack) {
        List<String> parts = new ArrayList<>();
        for (QuestPack.Chapter c : pack.chapters) {
            ChapterState s = m.chapter(c.name());
            parts.add("'" + c.title() + "' " + (s == null ? "not installed" : switch (s.status) {
                case INSTALLED -> "installed";
                case REMOVED -> "deleted by an admin";
                case SKIPPED -> "skipped";
                case KEPT -> "added by hand";
            }));
        }
        return String.join(", ", parts);
    }

    private String display(Path p) {
        Path base = configDir.toAbsolutePath().getParent();
        Path abs = p.toAbsolutePath();
        return (base != null && abs.startsWith(base) ? base.relativize(abs) : abs).toString().replace('\\', '/');
    }

    /** One run: reads the book, plans every write, then backs up, writes and saves the marker. */
    private final class Run {
        final QuestPack pack;
        final QuestMarker before;
        final QuestMarker next;
        final Settings settings;
        final Book book;
        final Map<Path, String> writes = new LinkedHashMap<>();
        final Map<String, LangLocale> langs = new LinkedHashMap<>();
        final List<String> installed = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        int textsUpdated;
        int textsKept;
        boolean lootLeftOut;

        Run(QuestPack pack, QuestMarker before, Settings settings) throws IOException {
            this.pack = pack;
            this.before = before;
            this.next = before == null ? new QuestMarker() : before.copy();
            this.settings = settings;
            this.book = Book.scan(questDir);
        }

        enum Plan { INSTALL, ADOPT, UPDATE, NONE }

        Result execute() throws IOException {
            Set<String> packIds = new HashSet<>();
            for (QuestPack.Chapter c : pack.chapters) packIds.add(c.id());

            // 1. Decide per chapter.
            Map<String, Plan> plans = new LinkedHashMap<>();
            for (QuestPack.Chapter c : pack.chapters) {
                ChapterState st = next.chapter(c.name());
                if (st != null) {
                    plans.put(c.name(), st.status == Status.INSTALLED ? Plan.UPDATE : Plan.NONE);
                    continue;
                }
                BookChapter sameFile = book.chapters.get(c.name());
                BookChapter sameId = book.byId(c.id());
                if (sameFile != null && !c.id().equals(sameFile.id())) {
                    decide(c, Status.SKIPPED, "chapters/" + c.name() + ".json5 is another chapter (id " + sameFile.id() + ")");
                    plans.put(c.name(), Plan.NONE);
                } else if (sameId != null && sameId != sameFile) {
                    decide(c, Status.KEPT, "already in the quest book as chapters/" + sameId.name() + ".json5");
                    plans.put(c.name(), Plan.NONE);
                } else if (sameFile != null) {
                    plans.put(c.name(), Plan.ADOPT);
                } else if (c.name().equals("cc_tweaked") && book.otherCcTweakedChapter(packIds) != null) {
                    decide(c, Status.SKIPPED, "the quest book already has a CC: Tweaked chapter (chapters/"
                        + book.otherCcTweakedChapter(packIds).name() + ".json5)");
                    plans.put(c.name(), Plan.NONE);
                } else {
                    plans.put(c.name(), Plan.INSTALL);
                }
            }

            // 2. Our ids must be new to the book (files that are our own chapters do not count).
            Map<String, String> collisions = new TreeMap<>();
            for (QuestPack.Chapter c : pack.chapters) {
                if (plans.get(c.name()) != Plan.INSTALL) continue;
                for (String id : c.definedIds()) {
                    String where = book.idOwner(id, packIds);
                    if (where != null) collisions.put(id, where);
                }
            }
            if (!collisions.isEmpty()) {
                List<String> list = new ArrayList<>();
                collisions.entrySet().stream().limit(5).forEach(e -> list.add(e.getKey() + " in " + e.getValue()));
                return new Result(true, false, "quest chapters NOT installed: " + collisions.size() + " of their ids are already used in "
                    + "the quest book (" + String.join(", ", list) + (collisions.size() > 5 ? ", …" : "") + "). Nothing changed; "
                    + "CC: Cloud checks again on the next start.", null);
            }

            // 3. Which of our chapters the book will have; links into the others are left out.
            Set<String> missingIds = new HashSet<>();
            for (QuestPack.Chapter c : pack.chapters) {
                Plan p = plans.get(c.name());
                boolean present = p == Plan.INSTALL || book.byId(c.id()) != null;
                if (!present) missingIds.addAll(c.definedIds());
            }

            // 4. The group for new chapters.
            String group = null;
            if (plans.containsValue(Plan.INSTALL)) group = chooseGroup();

            // 5. Chapters and their texts.
            int order = 0;
            for (QuestPack.Chapter c : pack.chapters) {
                switch (plans.get(c.name())) {
                    case INSTALL -> install(c, group, order, missingIds);
                    case ADOPT -> adopt(c, missingIds);
                    case UPDATE -> update(c, missingIds);
                    case NONE -> {
                    }
                }
                order++;
            }
            for (LangLocale l : langs.values()) l.flush(writes);

            if (!Files.exists(questDir.resolve("data.json5")) && !writes.isEmpty()) writes.put(questDir.resolve("data.json5"), DEFAULT_DATA);

            // 6. Backup, write, marker.
            Path backup = null;
            if (!writes.isEmpty() && Files.isDirectory(questDir)) backup = backup();
            for (var w : writes.entrySet()) writeAtomic(w.getKey(), w.getValue());
            next.questVersion = pack.version;
            next.modVersion = settings.modVersion();
            next.updated = LocalDateTime.now(clock).withNano(0).toString();
            writeAtomic(markerFile, next.toJson());

            var msg = new StringBuilder();
            if (!installed.isEmpty()) {
                msg.append("added ").append(String.join(" and ", installed)).append(" to the quest book (")
                    .append(groupLabel(next.group)).append("); players get them from the server.");
            }
            if (lootLeftOut && !installed.isEmpty()) msg.append(" The loot rewards from ATM11's reward tables were left out (this quest book has none).");
            for (String n : notes) msg.append(msg.isEmpty() ? "" : " ").append(n);
            if (textsKept > 0) msg.append(" ").append(textsKept).append(textsKept == 1 ? " quest text" : " quest texts")
                .append(" changed in the quest book kept.");
            if (msg.isEmpty()) msg.append("nothing to change (").append(states(next, pack)).append(").");
            if (backup != null) msg.append(" Backup: ").append(display(backup)).append('.');
            return new Result(false, !writes.isEmpty(), msg.toString().trim(), backup);
        }

        void decide(QuestPack.Chapter c, Status status, String reason) {
            next.chapters.put(c.name(), new ChapterState(status, c.id(), null, reason));
            notes.add("'" + c.title() + "' " + (status == Status.SKIPPED ? "not added" : "left as it is") + ": " + reason + ".");
        }

        String chooseGroup() throws IOException {
            if (next.group != null && (next.group.isEmpty() || book.groups.contains(next.group))) return next.group;
            if (book.groups.contains(TECH_GROUP)) return next.group = TECH_GROUP;
            if (!book.groups.contains(OWN_GROUP)) {
                Path file = questDir.resolve("chapter_groups.json5");
                String text = book.groupsText;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", OWN_GROUP);
                if (text == null) {
                    Map<String, Object> root = new LinkedHashMap<>();
                    root.put("chapter_groups", List.of(item));
                    writes.put(file, Json5.format(root, "", "\n") + "\n");
                } else {
                    Json5.Node root;
                    try {
                        root = Json5.parse(text);
                    } catch (Json5.ParseException e) {
                        notes.add("chapter_groups.json5 could not be read, so the chapters went into the default group.");
                        return next.group = "";
                    }
                    Json5.Node arr = root.get("chapter_groups");
                    if (arr != null && arr.kind == Json5.Kind.ARRAY) {
                        writes.put(file, Json5.insert(text, arr, List.of(new Json5.Entry(null, item))));
                    } else {
                        writes.put(file, Json5.insert(text, root, List.of(new Json5.Entry("chapter_groups", List.of(item)))));
                    }
                }
                for (String locale : QuestPack.LOCALES) {
                    lang(locale).add("chapter_group.json5", "chapter_group." + OWN_GROUP + ".title", OWN_GROUP_TITLE, Mode.INSTALL);
                }
            }
            return next.group = OWN_GROUP;
        }

        String groupLabel(String g) {
            if (TECH_GROUP.equals(g)) return "group Tech";
            if (OWN_GROUP.equals(g)) return "group " + OWN_GROUP_TITLE;
            return "default group";
        }

        /** Our chapter text without links to absent chapters, optionally placed in another group / position. */
        String variant(QuestPack.Chapter c, Set<String> missingIds, String groupText, String orderText) {
            String text = c.text();
            Json5.Node root = c.root();
            List<Json5.Node> remove = new ArrayList<>();
            Json5.Node quests = root.get("quests");
            if (quests != null) {
                for (Json5.Node q : quests.items) {
                    Json5.Node deps = q.get("dependencies");
                    if (deps != null) for (Json5.Node d : deps.items) if (missingIds.contains(d.string())) remove.add(d);
                }
            }
            Json5.Node links = root.get("quest_links");
            if (links != null) for (Json5.Node l : links.items) if (missingIds.contains(l.stringOf("linked_quest"))) remove.add(l);
            // Loot rewards from reward tables this book does not have (ATM11's "Reward: Common/Uncommon/Rare") give
            // nothing, and FTB Quests would drop their table on its next save.
            if (quests != null) {
                for (Json5.Node q : quests.items) {
                    Json5.Node rewards = q.get("rewards");
                    if (rewards == null) continue;
                    for (Json5.Node r : rewards.items) {
                        Json5.Node table = r.get("table_id");
                        if ("random".equals(r.stringOf("type")) && table != null && !book.rewardTables.contains(Json5.number(table.text))) {
                            remove.add(r);
                            lootLeftOut = true;
                        }
                    }
                }
            }
            if (!remove.isEmpty()) {
                text = Json5.removeItems(text, remove);
                root = Json5.parse(text);
            }
            List<Object[]> edits = new ArrayList<>();
            if (groupText != null && root.get("group") != null) edits.add(new Object[] {root.get("group"), groupText});
            if (orderText != null && root.get("order_index") != null) edits.add(new Object[] {root.get("order_index"), orderText});
            edits.sort(Comparator.comparingInt((Object[] e) -> ((Json5.Node) e[0]).start).reversed());
            for (Object[] e : edits) text = Json5.replace(text, (Json5.Node) e[0], (String) e[1]);
            return text;
        }

        void install(QuestPack.Chapter c, String group, int order, Set<String> missingIds) {
            String text = TECH_GROUP.equals(group) ? variant(c, missingIds, null, null)
                : variant(c, missingIds, Json5.quote(group), Integer.toString(order));
            writes.put(questDir.resolve("chapters").resolve(c.name() + ".json5"), text);
            next.chapters.put(c.name(), new ChapterState(Status.INSTALLED, c.id(), contentHash(Json5.parse(text)), null));
            installed.add("'" + c.title() + "' (" + c.quests() + " quests)");
            texts(c, Mode.INSTALL);
        }

        void adopt(QuestPack.Chapter c, Set<String> missingIds) {
            BookChapter existing = book.chapters.get(c.name());
            String hash = existing.root() == null ? null : contentHash(existing.root());
            String ours = contentHash(Json5.parse(variant(c, missingIds, null, null)));
            if (hash != null && hash.equals(ours)) {
                next.chapters.put(c.name(), new ChapterState(Status.INSTALLED, c.id(), hash, null));
                notes.add("'" + c.title() + "' was already in the quest book (this version): CC: Cloud keeps it up to date from now on.");
                texts(c, Mode.ADOPT);
            } else {
                decide(c, Status.KEPT, "already in the quest book (added by hand, another version or changed)");
            }
        }

        void update(QuestPack.Chapter c, Set<String> missingIds) {
            ChapterState st = next.chapter(c.name());
            BookChapter existing = book.chapters.get(c.name());
            if (existing == null || !c.id().equals(existing.id())) {
                st.status = Status.REMOVED;
                st.reason = "deleted from the quest book";
                notes.add("'" + c.title() + "' was deleted from the quest book: not added again.");
                return;
            }
            String current = existing.root() == null ? null : contentHash(existing.root());
            if (st.hash == null || !st.hash.equals(current)) {
                notes.add("'" + c.title() + "' was changed in the quest book: not updated.");
                return;
            }
            Json5.Node g = existing.root().get("group");
            Json5.Node o = existing.root().get("order_index");
            String groupText = g == null ? null : existing.text().substring(g.start, g.end());
            String orderText = o == null ? null : existing.text().substring(o.start, o.end());
            String text = variant(c, missingIds, groupText, orderText);
            String hash = contentHash(Json5.parse(text));
            int before = textsUpdated;
            if (!hash.equals(current)) {
                writes.put(questDir.resolve("chapters").resolve(c.name() + ".json5"), text);
                st.hash = hash;
            }
            texts(c, Mode.UPDATE);
            if (!hash.equals(current) || textsUpdated > before) {
                notes.add("updated '" + c.title() + "' to " + settings.modVersion() + (textsUpdated > before ? " (" + (textsUpdated - before) + " texts)" : "") + ".");
            }
        }

        void texts(QuestPack.Chapter c, Mode mode) {
            for (String locale : QuestPack.LOCALES) {
                LangLocale l = lang(locale);
                c.chapterKeys().get(locale).forEach((k, v) -> l.add("chapter.json5", k, Json5.plain(v), mode));
                c.questKeys().get(locale).forEach((k, v) -> l.add("chapters/" + c.name() + ".json5", k, Json5.plain(v), mode));
            }
        }

        LangLocale lang(String locale) {
            return langs.computeIfAbsent(locale, l -> new LangLocale(this, l, book.lang(l)));
        }

        Path backup() throws IOException {
            String stamp = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path target = questDir.resolveSibling("quests.backup-" + stamp);
            for (int n = 1; Files.exists(target); n++) target = questDir.resolveSibling("quests.backup-" + stamp + "-" + n);
            Path dest = target;
            Files.walkFileTree(questDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(dest.resolve(questDir.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, dest.resolve(questDir.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
            return target;
        }
    }

    enum Mode { INSTALL, ADOPT, UPDATE }

    // ------------------------------------------------------------------------------------------------ lang

    /** The texts of one locale: what the book has (every file, FTB Quests merges them) and what we add or change. */
    private static final class LangLocale {
        final Run run;
        final String locale;
        final LangIndex index;
        final Map<String, String> markerKeys;
        /** Per file (relative to lang/<locale>/): members to add, and value replacements. */
        final Map<String, List<Json5.Entry>> additions = new LinkedHashMap<>();
        final Map<String, List<Object[]>> replacements = new LinkedHashMap<>();
        final Set<String> added = new HashSet<>();

        LangLocale(Run run, String locale, LangIndex index) {
            this.run = run;
            this.locale = locale;
            this.index = index;
            this.markerKeys = run.next.lang(locale);
        }

        void add(String file, String key, Object value, Mode mode) {
            String shipped = Json5.sha256(Json5.canonicalPlain(value));
            String recorded = markerKeys.get(key);
            LangHit hit = index.find(key, file);
            if (recorded != null) {
                if (hit == null || hit.value() == null) return; // deleted (or unreadable): leave it
                String current = Json5.hash(hit.value(), Set.of());
                if (!current.equals(recorded)) {
                    run.textsKept++;
                    return;
                }
                if (!current.equals(shipped)) {
                    replacements.computeIfAbsent(hit.file(), f -> new ArrayList<>()).add(new Object[] {hit.value(), value});
                    run.textsUpdated++;
                }
                markerKeys.put(key, shipped);
                return;
            }
            if (hit != null || added.contains(key)) {
                if (mode == Mode.ADOPT && hit != null && hit.value() != null && Json5.hash(hit.value(), Set.of()).equals(shipped)) markerKeys.put(key, shipped);
                return; // someone else's text (or already there): never overwritten
            }
            if (index.unreadable.contains(file)) return;
            additions.computeIfAbsent(file, f -> new ArrayList<>()).add(new Json5.Entry(key, value));
            added.add(key);
            markerKeys.put(key, shipped);
        }

        void flush(Map<Path, String> writes) {
            Set<String> files = new LinkedHashSet<>(replacements.keySet());
            files.addAll(additions.keySet());
            for (String rel : files) {
                Path path = index.dir.resolve(rel);
                String text = index.texts.get(rel);
                if (text == null) {
                    Map<String, Object> root = new LinkedHashMap<>();
                    for (Json5.Entry e : additions.getOrDefault(rel, List.of())) root.put(e.key(), e.value());
                    writes.put(path, Json5.format(root, "", "\n") + "\n");
                    continue;
                }
                List<Object[]> reps = new ArrayList<>(replacements.getOrDefault(rel, List.of()));
                reps.sort(Comparator.comparingInt((Object[] r) -> ((Json5.Node) r[0]).start).reversed());
                String nl = Json5.newline(text);
                for (Object[] r : reps) {
                    Json5.Node node = (Json5.Node) r[0];
                    text = Json5.replace(text, node, Json5.format(r[1], indentOf(text, node.start), nl));
                }
                List<Json5.Entry> adds = additions.getOrDefault(rel, List.of());
                if (!adds.isEmpty()) text = Json5.insert(text, Json5.parse(text), adds);
                writes.put(path, text);
            }
        }

        private static String indentOf(String text, int pos) {
            int start = pos;
            while (start > 0 && text.charAt(start - 1) != '\n') start--;
            int k = start;
            while (k < text.length() && (text.charAt(k) == ' ' || text.charAt(k) == '\t')) k++;
            return text.substring(start, k);
        }
    }

    private record LangHit(String file, Json5.Node value) {
    }

    /** Every key of every file under lang/<locale>/ (FTB Quests merges all of them). */
    private static final class LangIndex {
        final Path dir;
        final Map<String, String> texts = new LinkedHashMap<>();
        final Map<String, Map<String, Json5.Node>> keysByFile = new LinkedHashMap<>();
        final Set<String> unreadable = new HashSet<>();

        LangIndex(Path dir) {
            this.dir = dir;
        }

        LangHit find(String key, String preferredFile) {
            Map<String, Json5.Node> pref = keysByFile.get(preferredFile);
            if (pref != null && pref.containsKey(key)) return new LangHit(preferredFile, pref.get(key));
            for (var e : keysByFile.entrySet()) if (e.getValue().containsKey(key)) return new LangHit(e.getKey(), e.getValue().get(key));
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------ the book

    private record BookChapter(String name, String text, Json5.Node root, String id) {
    }

    /** What the quest book has: chapter files, ids used anywhere (except texts), groups, and texts per locale. */
    private static final class Book {
        final Path dir;
        final Map<String, BookChapter> chapters = new LinkedHashMap<>();
        /** Every 16-digit hex token per chapter, reward table, group or data file (texts do not count). */
        final Map<String, Set<String>> tokensByFile = new LinkedHashMap<>();
        final Set<String> groups = new HashSet<>();
        /** Reward table ids in the decimal form of {@code table_id}. */
        final Set<String> rewardTables = new HashSet<>();
        String groupsText;
        final Map<String, LangIndex> lang = new LinkedHashMap<>();

        private Book(Path dir) {
            this.dir = dir;
        }

        static Book scan(Path dir) throws IOException {
            var b = new Book(dir);
            if (!Files.isDirectory(dir)) return b;
            Path chapters = dir.resolve("chapters");
            for (Path p : list(chapters)) {
                String text = read(p);
                String name = p.getFileName().toString();
                name = name.substring(0, name.length() - ".json5".length()).toLowerCase(java.util.Locale.ROOT);
                Json5.Node root = null;
                try {
                    root = Json5.parse(text);
                } catch (Json5.ParseException ignored) {
                    // FTB Quests cannot read it either; its ids still count as used
                }
                String id = root != null ? root.stringOf("id") : null;
                if (id == null) {
                    Matcher m = CHAPTER_ID.matcher(text);
                    if (m.find()) id = m.group(1);
                }
                b.chapters.put(name, new BookChapter(name, text, root, id));
                b.addTokens("chapters/" + p.getFileName(), text);
            }
            for (Path p : list(dir.resolve("reward_tables"))) {
                String text = read(p);
                b.addTokens("reward_tables/" + p.getFileName(), text);
                Matcher m = CHAPTER_ID.matcher(text);
                if (m.find()) b.rewardTables.add(Long.toString(Long.parseUnsignedLong(m.group(1), 16)));
            }
            for (String f : List.of("data.json5", "chapter_groups.json5")) {
                Path p = dir.resolve(f);
                if (Files.isRegularFile(p)) b.addTokens(f, read(p));
            }
            Path groupsFile = dir.resolve("chapter_groups.json5");
            if (Files.isRegularFile(groupsFile)) {
                b.groupsText = read(groupsFile);
                try {
                    Json5.Node arr = Json5.parse(b.groupsText).get("chapter_groups");
                    if (arr != null) for (Json5.Node g : arr.items) if (g.stringOf("id") != null) b.groups.add(g.stringOf("id"));
                } catch (Json5.ParseException e) {
                    Matcher m = HEX_ID.matcher(b.groupsText);
                    while (m.find()) b.groups.add(m.group());
                }
            }
            return b;
        }

        private void addTokens(String rel, String text) {
            Matcher m = HEX_ID.matcher(text);
            Set<String> set = tokensByFile.computeIfAbsent(rel, k -> new HashSet<>());
            while (m.find()) set.add(m.group());
        }

        BookChapter byId(String id) {
            for (BookChapter c : chapters.values()) if (id.equals(c.id())) return c;
            return null;
        }

        /** The file that uses {@code id}, ignoring our own chapters (by chapter id), or null. */
        String idOwner(String id, Set<String> ourChapterIds) {
            for (var e : tokensByFile.entrySet()) {
                if (!e.getValue().contains(id)) continue;
                String rel = e.getKey();
                if (rel.startsWith("chapters/")) {
                    String name = rel.substring("chapters/".length(), rel.length() - ".json5".length()).toLowerCase(java.util.Locale.ROOT);
                    BookChapter c = chapters.get(name);
                    if (c != null && c.id() != null && ourChapterIds.contains(c.id())) continue;
                }
                return rel;
            }
            return null;
        }

        /**
         * A chapter that is not ours and is about CC: Tweaked: its icon is a computercraft item, or its tasks and icons
         * use at least three different computercraft items.
         */
        BookChapter otherCcTweakedChapter(Set<String> ourChapterIds) {
            for (BookChapter c : chapters.values()) {
                if (c.id() != null && ourChapterIds.contains(c.id())) continue;
                if (c.root() != null) {
                    Json5.Node icon = c.root().get("icon");
                    String iconId = icon == null ? null : icon.string() != null ? icon.string() : icon.stringOf("id");
                    if (iconId != null && iconId.startsWith("computercraft:")) return c;
                }
                Set<String> items = new HashSet<>();
                Matcher m = CC_ITEM.matcher(c.text());
                while (m.find()) items.add(m.group(1));
                if (items.size() >= 3) return c;
            }
            return null;
        }

        LangIndex lang(String locale) {
            return lang.computeIfAbsent(locale, l -> {
                Path ldir = dir.resolve("lang").resolve(l);
                var index = new LangIndex(ldir);
                if (!Files.isDirectory(ldir)) return index;
                try (Stream<Path> s = Files.walk(ldir)) {
                    for (Path p : s.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".json5")).sorted().toList()) {
                        String rel = ldir.relativize(p).toString().replace('\\', '/');
                        String text = read(p);
                        index.texts.put(rel, text);
                        Map<String, Json5.Node> keys = new LinkedHashMap<>();
                        try {
                            for (Json5.Member m : Json5.parse(text).members) keys.put(m.key(), m.value());
                        } catch (Json5.ParseException e) {
                            index.unreadable.add(rel);
                            Matcher m = ANY_KEY.matcher(text);
                            while (m.find()) keys.put(m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3), null);
                        }
                        index.keysByFile.put(rel, keys);
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                return index;
            });
        }

        private static List<Path> list(Path dir) throws IOException {
            if (!Files.isDirectory(dir)) return List.of();
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json5"))
                    .sorted().toList();
            }
        }

        private static String read(Path p) throws IOException {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------------------------------------ files

    static void writeAtomic(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".cccloud-tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
