package ai.ezfox.cccloud.compat.ftbquests.install;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.Edition;
import ai.ezfox.cccloud.compat.ftbquests.install.QuestMarker.Status;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The quest auto-install rules on plain folders: fresh books, ATM11-like books, packs with their own CC: Tweaked chapter,
 * id collisions, the once-only marker, updates guarded by content hashes, and the switches that turn it off.
 */
class QuestInstallerTest {
    private static final String CCT = "4A19A9D21CB02379";
    private static final String CLOUD = "1CDB52DC3EE2DA1E";
    private static final String ADVANCED_COMPUTER = "34CFA95D19DC2ACE";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
    private static final QuestInstaller.Settings ON = new QuestInstaller.Settings(true, Edition.STANDARD, "1.0.0-test");

    @TempDir
    Path config;

    static Path questsDir() {
        return Path.of(System.getProperty("cccloud.questsDir", "integration/ftbquests/quests"));
    }

    static QuestPack pack() {
        return QuestPack.fromDirectory(questsDir());
    }

    /** The pack with one file changed, i.e. a newer release. */
    static QuestPack pack(String file, UnaryOperator<String> change) {
        return QuestPack.load(path -> {
            try {
                String text = Files.readString(questsDir().resolve(path));
                return path.equals(file) ? change.apply(text) : text;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    static QuestPack newer() {
        return pack("lang/en_us/chapters/cc_cloud.json5", t -> t.replace("\"Cloud Core\"", "\"Cloud Core (new)\""));
    }

    QuestInstaller installer() {
        return new QuestInstaller(config, CLOCK);
    }

    Path quests() {
        return config.resolve("ftbquests/quests");
    }

    String read(String rel) throws IOException {
        return Files.readString(quests().resolve(rel));
    }

    Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.exists(root)) return files;
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) files.put(root.relativize(p).toString(), Files.readString(p));
        }
        return files;
    }

    void write(String rel, String text) throws IOException {
        Path p = quests().resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    /** A small ATM11-like book: data, the Tech group, one chapter, English texts (one of them already ours). */
    void atmBook() throws IOException {
        write("data.json5", QuestInstaller.DEFAULT_DATA);
        write("chapter_groups.json5", "{\n  chapter_groups: [\n    {\n      id: \"" + QuestInstaller.TECH_GROUP + "\",\n    },\n  ],\n}\n");
        write("chapters/enderio.json5", "{\n  id: \"6F2A4E2B9C1D3A05\",\n  group: \"" + QuestInstaller.TECH_GROUP + "\",\n  order_index: 10,\n"
            + "  filename: \"enderio\",\n  quests: [\n    {\n      id: \"6F2A4E2B9C1D3A06\",\n      tasks: [\n        {\n"
            + "          id: \"6F2A4E2B9C1D3A07\",\n          type: \"item\",\n          item: {\n            id: \"enderio:conduit\",\n"
            + "            count: 1,\n          },\n        },\n      ],\n    },\n  ],\n}\n");
        write("lang/en_us/chapter.json5", "{\n  \"chapter.6F2A4E2B9C1D3A05.title\": \"Ender IO\",\n  \"chapter." + CCT + ".title\": \"Keep me\",\n}\n");
        write("lang/en_us/chapter_group.json5", "{\n  \"chapter_group." + QuestInstaller.TECH_GROUP + ".title\": \"Tech\",\n}\n");
        // ATM11's generic loot tables, which the milestone rewards draw from
        int i = 0;
        for (String id : new String[] {"06C4634E81851A6C", "3A3BDBA4E9AD13C4", "4D37FFAF33DF678A"}) {
            write("reward_tables/table" + i + ".json5", "{\n  id: \"" + id + "\",\n  order_index: " + i++ + ",\n  rewards: [\n  ],\n}\n");
        }
    }

    @Test
    void freshBookGetsBothChaptersInOwnGroup() throws IOException {
        var r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.changed() && !r.warning(), r.message());
        assertNull(r.backup(), "nothing to back up");
        assertTrue(r.message().contains("'CC: Tweaked' (24 quests) and 'CC: Cloud' (39 quests)"), r.message());
        assertTrue(r.message().contains("group CC: Cloud"), r.message());
        assertEquals(QuestInstaller.DEFAULT_DATA, read("data.json5"));
        Json5.parse(read("data.json5"));
        var groups = Json5.parse(read("chapter_groups.json5")).get("chapter_groups");
        assertEquals(QuestInstaller.OWN_GROUP, groups.items.getFirst().stringOf("id"));
        var cct = Json5.parse(read("chapters/cc_tweaked.json5"));
        var cloud = Json5.parse(read("chapters/cc_cloud.json5"));
        assertEquals(QuestInstaller.OWN_GROUP, cct.stringOf("group"));
        assertEquals("0", cct.get("order_index").text);
        assertEquals("1", cloud.get("order_index").text);
        assertTrue(read("chapters/cc_cloud.json5").contains(ADVANCED_COMPUTER), "cross-chapter dependency kept");
        assertEquals("CC: Cloud", Json5.parse(read("lang/en_us/chapter.json5")).stringOf("chapter." + CLOUD + ".title"));
        assertEquals("CC: Cloud", Json5.parse(read("lang/en_us/chapter_group.json5")).stringOf("chapter_group." + QuestInstaller.OWN_GROUP + ".title"));
        assertEquals("CC: Cloud", Json5.parse(read("lang/de_de/chapter.json5")).stringOf("chapter." + CLOUD + ".title"));
        assertEquals(Json5.canonical(Json5.parse(Files.readString(questsDir().resolve("lang/de_de/chapters/cc_cloud.json5")))),
            Json5.canonical(Json5.parse(read("lang/de_de/chapters/cc_cloud.json5"))));

        var marker = QuestMarker.read(installer().markerFile());
        assertEquals(Status.INSTALLED, marker.chapter("cc_tweaked").status);
        assertEquals(Status.INSTALLED, marker.chapter("cc_cloud").status);
        assertEquals(QuestInstaller.OWN_GROUP, marker.group);
        assertEquals(pack().version, marker.questVersion);
        assertEquals("1.0.0-test", marker.modVersion);
        assertTrue(marker.lang.get("en_us").containsKey("quest.78170295DDB6E973.title"));

        // Once: the next start changes nothing.
        var before = snapshot(config);
        var again = installer().run(ON, QuestInstallerTest::pack);
        assertFalse(again.changed());
        assertTrue(again.message().startsWith("quest chapters up to date"), again.message());
        assertEquals(before, snapshot(config));
    }

    @Test
    void atmBookUsesTechGroupKeepsExistingTextsAndBacksUp() throws IOException {
        atmBook();
        var original = snapshot(quests());
        var r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.changed(), r.message());
        assertTrue(r.message().contains("group Tech"), r.message());
        assertNotNull(r.backup());
        assertEquals(original, snapshot(r.backup()), "backup is the book before the install");
        assertTrue(r.backup().getFileName().toString().startsWith("quests.backup-20261030-"));
        assertEquals(original.get("chapter_groups.json5"), read("chapter_groups.json5"), "Tech group exists: groups untouched");
        assertEquals(original.get("chapters/enderio.json5"), read("chapters/enderio.json5"));
        assertEquals(original.get("data.json5"), read("data.json5"));
        var cct = Json5.parse(read("chapters/cc_tweaked.json5"));
        assertEquals(QuestInstaller.TECH_GROUP, cct.stringOf("group"));
        assertEquals("12", cct.get("order_index").text);
        assertEquals(Files.readString(questsDir().resolve("chapters/cc_cloud.json5")), read("chapters/cc_cloud.json5"), "shipped as is");
        var chapterLang = Json5.parse(read("lang/en_us/chapter.json5"));
        assertEquals("Keep me", chapterLang.stringOf("chapter." + CCT + ".title"), "existing key never overwritten");
        assertEquals("Ender IO", chapterLang.stringOf("chapter.6F2A4E2B9C1D3A05.title"));
        assertEquals("CC: Cloud", chapterLang.stringOf("chapter." + CLOUD + ".title"));
        assertTrue(read("lang/en_us/chapter.json5").startsWith("{\n  \"chapter.6F2A4E2B9C1D3A05.title\": \"Ender IO\",\n  \"chapter." + CCT + ".title\": \"Keep me\",\n"),
            "appended, the file's own lines unchanged");
        assertFalse(QuestMarker.read(installer().markerFile()).lang.get("en_us").containsKey("chapter." + CCT + ".title"), "not ours");
    }

    @Test
    void packWithOwnCcTweakedChapterGetsOnlyCcCloud() throws IOException {
        atmBook();
        write("chapters/computers.json5", "{\n  id: \"0123456789ABCDE1\",\n  group: \"\",\n  order_index: 0,\n  icon: {\n"
            + "    id: \"computercraft:computer_normal\",\n    count: 1,\n  },\n  quests: [\n  ],\n}\n");
        var r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.message().contains("'CC: Tweaked' not added: the quest book already has a CC: Tweaked chapter (chapters/computers.json5)"), r.message());
        assertFalse(Files.exists(quests().resolve("chapters/cc_tweaked.json5")));
        String cloud = read("chapters/cc_cloud.json5");
        assertFalse(cloud.contains(ADVANCED_COMPUTER), "no dependency or link into the missing chapter");
        assertTrue(Json5.parse(cloud).get("quest_links").items.isEmpty());
        assertFalse(Files.exists(quests().resolve("lang/en_us/chapters/cc_tweaked.json5")));
        var marker = QuestMarker.read(installer().markerFile());
        assertEquals(Status.SKIPPED, marker.chapter("cc_tweaked").status);
        assertEquals(Status.INSTALLED, marker.chapter("cc_cloud").status);

        // Detection by items: three different computercraft items in a chapter without a CC icon count too.
        Files.delete(quests().resolve("chapters/computers.json5"));
        Files.delete(installer().markerFile());
        Files.delete(quests().resolve("chapters/cc_cloud.json5"));
        write("chapters/misc.json5", "{\n  id: \"0123456789ABCDE4\",\n  quests: [\n    {\n      id: \"0123456789ABCDE5\",\n      tasks: [\n"
            + "        { id: \"0123456789ABCDE6\", type: \"item\", item: { id: \"computercraft:turtle_normal\" } },\n"
            + "        { id: \"0123456789ABCDE7\", type: \"item\", item: { id: \"computercraft:monitor_normal\" } },\n"
            + "        { id: \"0123456789ABCDE8\", type: \"item\", item: { id: \"computercraft:printer\" } },\n      ],\n    },\n  ],\n}\n");
        r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.message().contains("(chapters/misc.json5)"), r.message());
    }

    @Test
    void oneComputerInAnotherChapterIsNotACcTweakedChapter() throws IOException {
        atmBook();
        write("chapters/misc.json5", "{\n  id: \"0123456789ABCDE4\",\n  quests: [\n    {\n      id: \"0123456789ABCDE5\",\n"
            + "      rewards: [ { id: \"0123456789ABCDE6\", type: \"item\", item: { id: \"computercraft:computer_normal\" } } ],\n    },\n  ],\n}\n");
        installer().run(ON, QuestInstallerTest::pack);
        assertTrue(Files.exists(quests().resolve("chapters/cc_tweaked.json5")));
    }

    @Test
    void foreignChapterWithOurFileNameIsNeverReplaced() throws IOException {
        atmBook();
        String foreign = "{\n  id: \"0123456789ABCDEF\",\n  filename: \"cc_tweaked\",\n  quests: [\n  ],\n}\n";
        write("chapters/cc_tweaked.json5", foreign);
        var r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.message().contains("chapters/cc_tweaked.json5 is another chapter (id 0123456789ABCDEF)"), r.message());
        assertEquals(foreign, read("chapters/cc_tweaked.json5"));
        assertTrue(Files.exists(quests().resolve("chapters/cc_cloud.json5")));
        assertFalse(read("chapters/cc_cloud.json5").contains(ADVANCED_COMPUTER));
    }

    @Test
    void idCollisionInstallsNothingAndRetries() throws IOException {
        atmBook();
        write("chapters/other.json5", "{\n  id: \"0123456789ABCDE9\",\n  quests: [\n    {\n      id: \"" + ADVANCED_COMPUTER + "\",\n    },\n  ],\n}\n");
        var before = snapshot(config);
        var r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.warning() && !r.changed(), r.message());
        assertTrue(r.message().contains(ADVANCED_COMPUTER + " in chapters/other.json5"), r.message());
        assertTrue(r.message().contains("Nothing changed"), r.message());
        assertEquals(before, snapshot(config), "no files, no marker, no backup");
        Files.delete(quests().resolve("chapters/other.json5"));
        assertTrue(installer().run(ON, QuestInstallerTest::pack).changed(), "retried on the next start");
    }

    @Test
    void switchedOffOrModpackEditionInstallsNothing() throws IOException {
        atmBook();
        var before = snapshot(config);
        var off = installer().run(new QuestInstaller.Settings(false, Edition.STANDARD, "x"), QuestInstallerTest::pack);
        assertFalse(off.changed());
        assertTrue(off.message().contains("installQuests = false"), off.message());
        var modpack = installer().run(new QuestInstaller.Settings(true, Edition.MODPACK, "1.2.3"), () -> {
            throw new IllegalStateException("the modpack edition has no quest files and must not load them");
        });
        assertFalse(modpack.changed());
        assertTrue(modpack.message().contains("modpack edition") && modpack.message().contains("cccloud-quests-atm11-1.2.3.zip"), modpack.message());
        assertEquals(before, snapshot(config));
    }

    @Test
    void missingQuestFilesAreReported() throws IOException {
        var r = installer().run(ON, () -> QuestPack.load(path -> null));
        assertTrue(r.warning() && !r.changed() && r.message().contains("missing"), r.message());
        assertFalse(Files.exists(quests()));
    }

    @Test
    void deletedChapterIsNotAddedAgainAndLinksAreDropped() throws IOException {
        atmBook();
        installer().run(ON, QuestInstallerTest::pack);
        Files.delete(quests().resolve("chapters/cc_tweaked.json5"));
        var r = installer().run(ON, QuestInstallerTest::newer);
        assertTrue(r.message().contains("'CC: Tweaked' was deleted from the quest book: not added again."), r.message());
        assertTrue(r.message().contains("updated 'CC: Cloud' to 1.0.0-test (1 texts)"), r.message());
        assertFalse(Files.exists(quests().resolve("chapters/cc_tweaked.json5")));
        assertFalse(read("chapters/cc_cloud.json5").contains(ADVANCED_COMPUTER), "update drops the links into the deleted chapter");
        assertEquals("Cloud Core (new)", Json5.parse(read("lang/en_us/chapters/cc_cloud.json5")).stringOf("quest.78170295DDB6E973.title"));
        assertEquals(Status.REMOVED, QuestMarker.read(installer().markerFile()).chapter("cc_tweaked").status);
        // and stays deleted with the next release too
        installer().run(ON, () -> pack("lang/en_us/chapters/cc_tweaked.json5", t -> t.replace("\"Your First Computer\"", "\"First\"")));
        assertFalse(Files.exists(quests().resolve("chapters/cc_tweaked.json5")));
    }

    @Test
    void editedChapterAndEditedTextsAreKept() throws IOException {
        atmBook();
        installer().run(ON, QuestInstallerTest::pack);
        String edited = read("chapters/cc_tweaked.json5").replaceFirst("xp: 50,", "xp: 500,");
        write("chapters/cc_tweaked.json5", edited);
        Path lang = quests().resolve("lang/en_us/chapters/cc_cloud.json5");
        String text = Files.readString(lang).replace("\"Cloud Mainframe\",", "\"Our Mainframe\",");
        assertNotEquals(Files.readString(lang), text, "test edits a real title");
        Files.writeString(lang, text);
        var newer = pack("lang/en_us/chapters/cc_cloud.json5", t -> t.replace("\"Cloud Core\"", "\"Cloud Core (new)\"")
            .replace("\"Cloud Mainframe\",", "\"Cloud Mainframe (new)\","));
        var r = installer().run(ON, () -> newer);
        assertTrue(r.message().contains("'CC: Tweaked' was changed in the quest book: not updated."), r.message());
        assertTrue(r.message().contains("1 quest text changed in the quest book kept"), r.message());
        assertEquals(edited, read("chapters/cc_tweaked.json5"));
        var cloudLang = Json5.parse(Files.readString(lang));
        assertEquals("Our Mainframe", cloudLang.stringOf(keyOf(cloudLang, "Our Mainframe")));
        assertEquals("Cloud Core (new)", cloudLang.stringOf("quest.78170295DDB6E973.title"), "unchanged text updated");
        assertNotNull(r.backup());
    }

    private static String keyOf(Json5.Node root, String value) {
        for (var m : root.members) if (value.equals(m.value().string())) return m.key();
        throw new AssertionError(value);
    }

    @Test
    void ftbQuestsResaveAndMovingTheChapterAreNotEdits() throws IOException {
        installer().run(ON, QuestInstallerTest::pack);
        // FTB Quests writes its own layout: other indentation, number spelling, key order of lang files; an admin moved
        // the chapter to another position.
        String cct = read("chapters/cc_tweaked.json5").replace("\n  ", "\n    ").replace("x: 0.0,", "x: 0,")
            .replace("order_index: 0,", "order_index: 5,");
        write("chapters/cc_tweaked.json5", cct);
        var lang = Json5.parse(read("lang/en_us/chapters/cc_cloud.json5"));
        var reversed = new java.util.LinkedHashMap<String, Object>();
        for (int i = lang.members.size() - 1; i >= 0; i--) reversed.put(lang.members.get(i).key(), Json5.plain(lang.members.get(i).value()));
        write("lang/en_us/chapters/cc_cloud.json5", Json5.format(reversed, "", "\n") + "\n");
        var newer = pack("chapters/cc_tweaked.json5", t -> t.replace("xp: 50,", "xp: 60,"));
        var r = installer().run(ON, () -> newer);
        assertTrue(r.message().contains("updated 'CC: Tweaked'"), r.message());
        assertFalse(r.message().contains("changed in the quest book"), r.message());
        var updated = Json5.parse(read("chapters/cc_tweaked.json5"));
        assertEquals("5", updated.get("order_index").text, "the admin's position is kept");
        assertEquals(QuestInstaller.OWN_GROUP, updated.stringOf("group"));
        assertTrue(read("chapters/cc_tweaked.json5").contains("xp: 60,"));
    }

    @Test
    void lootRewardsWithoutTheirTablesAreLeftOut() throws IOException {
        var r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.message().contains("loot rewards from ATM11's reward tables were left out"), r.message());
        for (String c : new String[] {"cc_tweaked", "cc_cloud"}) {
            assertFalse(read("chapters/" + c + ".json5").contains("table_id"), c);
            assertTrue(read("chapters/" + c + ".json5").contains("type: \"xp\""), c + ": other rewards stay");
        }
        Files.delete(installer().markerFile());
        deleteTree(quests());
        atmBook();
        r = installer().run(ON, QuestInstallerTest::pack);
        assertFalse(r.message().contains("left out"), r.message());
        assertTrue(read("chapters/cc_cloud.json5").contains("table_id: 487623848494439020"));
    }

    @Test
    void ftbQuestsMissingItemWrappingIsNotAnEdit() throws IOException {
        atmBook();
        installer().run(ON, QuestInstallerTest::pack);
        // FTB Quests re-saves an item of a mod that is not installed (here Energy Meter) like this:
        String cct = read("chapters/cc_tweaked.json5");
        String ind = "          ";
        String wrapped = cct.replace(ind + "item: {\n" + ind + "  id: \"energymeter:meter\",\n" + ind + "  count: 1,\n" + ind + "},",
            ind + "item: {\n" + ind + "  id: \"ftbquests:missing_item\",\n" + ind + "  count: 1,\n" + ind + "  components: {\n"
                + ind + "    \"ftbquests:missing_item\": \"energymeter:meter\",\n" + ind + "    \"ftbquests:missing_item_count\": 1,\n"
                + ind + "  },\n" + ind + "},");
        assertNotEquals(cct, wrapped, "test wraps a real item");
        write("chapters/cc_tweaked.json5", wrapped);
        var r = installer().run(ON, () -> pack("chapters/cc_tweaked.json5", t -> t.replace("xp: 50,", "xp: 60,")));
        assertTrue(r.message().contains("updated 'CC: Tweaked'"), r.message());
        assertTrue(read("chapters/cc_tweaked.json5").contains("xp: 60,"));
    }

    static void deleteTree(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    @Test
    void handInstalledChaptersAreAdoptedOnlyWhenIdentical() throws IOException {
        atmBook();
        // as integration/ftbquests/install.sh leaves them (this version), no marker
        Files.createDirectories(quests().resolve("chapters"));
        Files.copy(questsDir().resolve("chapters/cc_tweaked.json5"), quests().resolve("chapters/cc_tweaked.json5"));
        String oldCloud = Files.readString(questsDir().resolve("chapters/cc_cloud.json5")).replace("xp: 75,", "xp: 70,");
        write("chapters/cc_cloud.json5", oldCloud);
        var r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.message().contains("'CC: Tweaked' was already in the quest book (this version)"), r.message());
        assertTrue(r.message().contains("'CC: Cloud' left as it is: already in the quest book (added by hand"), r.message());
        assertEquals(oldCloud, read("chapters/cc_cloud.json5"));
        var marker = QuestMarker.read(installer().markerFile());
        assertEquals(Status.INSTALLED, marker.chapter("cc_tweaked").status);
        assertEquals(Status.KEPT, marker.chapter("cc_cloud").status);
        // the kept chapter is never touched later
        installer().run(ON, QuestInstallerTest::newer);
        assertEquals(oldCloud, read("chapters/cc_cloud.json5"));
    }

    @Test
    void unreadableMarkerChangesNothing() throws IOException {
        atmBook();
        Files.createDirectories(installer().markerFile().getParent());
        Files.writeString(installer().markerFile(), "{ not json");
        var before = snapshot(config);
        var r = installer().run(ON, QuestInstallerTest::pack);
        assertTrue(r.warning() && !r.changed() && r.message().contains("Delete it"), r.message());
        assertEquals(before, snapshot(config));
    }

    @Test
    void newTextsOfANewerReleaseAreAddedButDeletedOnesNot() throws IOException {
        atmBook();
        installer().run(ON, QuestInstallerTest::pack);
        // an admin removed a text (FTB Quests drops empty texts from its files)
        Path lang = quests().resolve("lang/de_de/chapters/cc_cloud.json5");
        var root = Json5.parse(Files.readString(lang));
        var first = root.members.getFirst();
        var kept = new java.util.LinkedHashMap<String, Object>();
        for (var m : root.members) if (m != first) kept.put(m.key(), Json5.plain(m.value()));
        Files.writeString(lang, Json5.format(kept, "", "\n") + "\n");
        var newer = pack("lang/de_de/chapters/cc_cloud.json5", t -> t.replaceFirst("\\{\n", "{\n  \"quest.78170295DDB6E973.extra\": \"Neu\",\n"));
        installer().run(ON, () -> newer);
        var after = Json5.parse(Files.readString(lang));
        assertEquals("Neu", after.stringOf("quest.78170295DDB6E973.extra"));
        assertNull(after.get(first.key()), "deleted text not added again");
    }

    @Test
    void crlfFilesKeepTheirLineBreaks() throws IOException {
        atmBook();
        String crlf = read("lang/en_us/chapter.json5").replace("\n", "\r\n");
        write("lang/en_us/chapter.json5", crlf);
        installer().run(ON, QuestInstallerTest::pack);
        String out = read("lang/en_us/chapter.json5");
        assertTrue(out.startsWith(crlf.substring(0, crlf.length() - 3)), out);
        assertFalse(out.replace("\r\n", "").contains("\n"), "only CRLF");
        Json5.parse(out);
    }
}
