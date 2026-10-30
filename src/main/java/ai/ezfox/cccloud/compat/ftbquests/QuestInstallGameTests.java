package ai.ezfox.cccloud.compat.ftbquests;

import ai.ezfox.cccloud.Edition;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.ftbquests.install.QuestInstaller;
import ai.ezfox.cccloud.compat.ftbquests.install.QuestMarker;
import ai.ezfox.cccloud.compat.ftbquests.install.QuestPack;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The quest auto-install against the real FTB Quests: what the installer writes is loaded with FTB Quests' own reader
 * ({@code ServerQuestFile.readDataFull} on a temporary book, never the server's), and the live server must have loaded
 * the chapters that were installed before FTB Quests read its book.
 */
public final class QuestInstallGameTests {
    private static final String CCT = "4A19A9D21CB02379";
    private static final String CLOUD = "1CDB52DC3EE2DA1E";
    /** CC: Cloud's first quest; it depends on CC: Tweaked's "Advanced Computer". */
    private static final String CLOUD_START = "78170295DDB6E973";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);

    private QuestInstallGameTests() {
    }

    public static void init() {
        Integrations.test("ftbquests_install", () -> QuestInstallGameTests::scenarios);
        Integrations.test("ftbquests_install_live", () -> QuestInstallGameTests::live);
    }

    private static QuestInstaller.Settings on() {
        return new QuestInstaller.Settings(true, Edition.STANDARD, "test");
    }

    /** The shipped pack with one changed text: a "newer release". */
    private static QuestPack newer() {
        return QuestPack.load(path -> {
            try (var in = QuestPack.class.getResourceAsStream(QuestPack.CLASSPATH_ROOT + path)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return path.equals("lang/en_us/chapters/cc_cloud.json5") ? text.replace("\"Cloud Core\"", "\"Cloud Core (new)\"") : text;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static ServerQuestFile load(MinecraftServer server, Path config) {
        var file = new ServerQuestFile(server);
        file.readDataFull(config.resolve("ftbquests/quests"), server.registryAccess());
        return file;
    }

    private static Chapter chapter(ServerQuestFile file, String id) {
        return file.getChapter(Long.parseUnsignedLong(id, 16));
    }

    public static void scenarios(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path root = null;
        try {
            root = Files.createTempDirectory("cccloud-quests-");
            // 1. A fresh server: no quest book at all. Both chapters, our own group, FTB Quests reads it.
            Path fresh = root.resolve("fresh");
            var installer = new QuestInstaller(fresh, CLOCK);
            var r = installer.run(on(), QuestPack::fromClasspath);
            helper.assertTrue(r.changed() && !r.warning(), "fresh install: " + r.message());
            var file = load(server, fresh);
            helper.assertTrue(file.getAllChapters().size() == 2, "fresh: 2 chapters, got " + file.getAllChapters().size());
            Chapter cloud = chapter(file, CLOUD);
            helper.assertTrue(cloud != null && cloud.getQuests().size() == 39, "CC: Cloud with 39 quests");
            helper.assertTrue(chapter(file, CCT).getQuests().size() == 24, "CC: Tweaked with 24 quests");
            helper.assertTrue(cloud.getGroup().id == Long.parseUnsignedLong(QuestInstaller.OWN_GROUP, 16), "own chapter group");
            helper.assertTrue("CC: Cloud".equals(file.getTranslationManager().getStringTranslation(cloud, "en_us", TranslationKey.TITLE).orElse(null)),
                "chapter title from the merged lang files");
            helper.assertTrue("CC: Cloud".equals(file.getTranslationManager().getStringTranslation(cloud.getGroup(), "en_us", TranslationKey.TITLE).orElse(null)),
                "group title");
            helper.assertTrue(file.getQuest(Long.parseUnsignedLong(CLOUD_START, 16)).streamDependencies().count() == 1, "cross-chapter dependency kept");
            helper.assertTrue(cloud.getQuestLinks().size() == 1, "quest link to CC: Tweaked kept");

            // 2. FTB Quests re-saves the book in its own layout (chapters and lang): still ours, so a newer release updates it.
            file.writeDataFull(fresh.resolve("ftbquests/quests"), server.registryAccess());
            file.getTranslationManager().saveToFile(file, fresh.resolve("ftbquests/quests/lang"), true);
            QuestPack newer = newer();
            helper.assertTrue(!newer.version.equals(QuestPack.fromClasspath().version), "the test pack is a newer version");
            r = installer.run(on(), () -> newer);
            helper.assertTrue(r.changed() && r.message().contains("updated") && !r.message().contains("changed in the quest book"),
                "an FTB Quests re-save is not an edit, so the newer release updates: " + r.message());
            file = load(server, fresh);
            Quest core = file.getQuest(Long.parseUnsignedLong(CLOUD_START, 16));
            helper.assertTrue("Cloud Core (new)".equals(file.getTranslationManager().getStringTranslation(core, "en_us", TranslationKey.TITLE).orElse(null)),
                "updated text");

            // 3. A pack with its own CC: Tweaked chapter: only CC: Cloud, without links into the missing chapter.
            Path pack = root.resolve("pack");
            Path quests = pack.resolve("ftbquests/quests");
            Files.createDirectories(quests.resolve("chapters"));
            Files.writeString(quests.resolve("data.json5"), "{\n  version: 13,\n  default_reward_team: false,\n  default_consume_items: false,\n"
                + "  default_autoclaim_rewards: \"disabled\",\n  default_quest_shape: \"circle\",\n  default_quest_disable_jei: false,\n"
                + "  drop_loot_crates: false,\n}\n");
            Files.writeString(quests.resolve("chapters/computers.json5"), """
                {
                  id: "0123456789ABCDE1",
                  group: "",
                  order_index: 0,
                  icon: {
                    id: "computercraft:computer_normal",
                    count: 1,
                  },
                  filename: "computers",
                  quests: [
                    {
                      x: 0.0,
                      y: 0.0,
                      id: "0123456789ABCDE2",
                      tasks: [
                        {
                          id: "0123456789ABCDE3",
                          type: "item",
                          item: {
                            id: "computercraft:computer_normal",
                            count: 1,
                          },
                        },
                      ],
                    },
                  ],
                }
                """);
            var packInstaller = new QuestInstaller(pack, CLOCK);
            r = packInstaller.run(on(), QuestPack::fromClasspath);
            helper.assertTrue(r.changed() && r.message().contains("already has a CC: Tweaked chapter"), "own CC chapter: " + r.message());
            file = load(server, pack);
            helper.assertTrue(chapter(file, CCT) == null && chapter(file, CLOUD) != null && chapter(file, "0123456789ABCDE1") != null,
                "only CC: Cloud added, the pack's chapter kept");
            helper.assertTrue(file.getQuest(Long.parseUnsignedLong(CLOUD_START, 16)).streamDependencies().findAny().isEmpty(), "no dependency into CC: Tweaked");
            helper.assertTrue(chapter(file, CLOUD).getQuestLinks().isEmpty(), "no quest link into CC: Tweaked");

            // 4. installQuests = false and the modpack edition: nothing at all.
            for (var s : new QuestInstaller.Settings[] {new QuestInstaller.Settings(false, Edition.STANDARD, "test"),
                new QuestInstaller.Settings(true, Edition.MODPACK, "test")}) {
                Path off = root.resolve("off-" + s.edition() + "-" + s.installQuests());
                r = new QuestInstaller(off, CLOCK).run(s, QuestPack::fromClasspath);
                helper.assertTrue(!r.changed() && !Files.exists(off), "nothing installed: " + r.message());
            }

            // 5. An admin deletes CC: Tweaked and edits CC: Cloud: a newer release adds nothing back and keeps the edit.
            Path admin = root.resolve("admin");
            var adminInstaller = new QuestInstaller(admin, CLOCK);
            adminInstaller.run(on(), QuestPack::fromClasspath);
            Path aq = admin.resolve("ftbquests/quests");
            Files.delete(aq.resolve("chapters/cc_tweaked.json5"));
            Path cloudFile = aq.resolve("chapters/cc_cloud.json5");
            String edited = Files.readString(cloudFile).replaceFirst("x: 0\\.0,", "x: 2.5,");
            Files.writeString(cloudFile, edited);
            r = adminInstaller.run(on(), () -> newer);
            helper.assertTrue(r.message().contains("deleted from the quest book") && r.message().contains("changed in the quest book"),
                "deleted + edited reported: " + r.message());
            helper.assertTrue(!Files.exists(aq.resolve("chapters/cc_tweaked.json5")), "deleted chapter not added again");
            helper.assertTrue(edited.equals(Files.readString(cloudFile)), "edited chapter not overwritten");
            var marker = QuestMarker.read(adminInstaller.markerFile());
            helper.assertTrue(marker.chapter("cc_tweaked").status == QuestMarker.Status.REMOVED, "marker: removed");
            file = load(server, admin);
            helper.assertTrue(chapter(file, CCT) == null && chapter(file, CLOUD) != null, "FTB Quests reads the edited book");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (root != null) delete(root);
        }
        helper.succeed();
    }

    /**
     * The real server of this test run: FTB Quests read its book after we installed into it (ServerAboutToStart before
     * ServerStarted), so every chapter the marker lists as installed and present is loaded, in the marker's group.
     */
    public static void live(GameTestHelper helper) {
        try {
            Path config = FMLPaths.CONFIGDIR.get();
            var marker = QuestMarker.read(config.resolve(QuestInstaller.MARKER));
            helper.assertTrue(marker != null, "marker written on server start");
            var file = ServerQuestFile.getInstance();
            int checked = 0;
            for (var e : marker.chapters.entrySet()) {
                if (e.getValue().status != QuestMarker.Status.INSTALLED) continue;
                if (!Files.exists(config.resolve("ftbquests/quests/chapters/" + e.getKey() + ".json5"))) continue;
                Chapter c = chapter(file, e.getValue().id);
                helper.assertTrue(c != null, "FTB Quests loaded " + e.getKey());
                if (marker.group != null && !marker.group.isEmpty()) {
                    helper.assertTrue(c.getGroup().id == Long.parseUnsignedLong(marker.group, 16), e.getKey() + " in group " + marker.group);
                }
                checked++;
            }
            helper.assertTrue(checked > 0, "at least one installed chapter checked");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.succeed();
    }

    private static void delete(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp folder
        }
    }
}
