package ai.ezfox.cccloud.compat.ftbquests.install;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.Edition;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The shipped quest files: complete, in the jar's resources, consistent ids; and the edition flag. */
class QuestPackTest {
    @Test
    void fileListMatchesIntegrationFolder() throws IOException {
        Path dir = QuestInstallerTest.questsDir();
        List<String> onDisk;
        try (Stream<Path> s = Files.walk(dir)) {
            onDisk = s.filter(Files::isRegularFile).map(p -> dir.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
        assertEquals(onDisk, QuestPack.files().stream().sorted().toList(),
            "QuestPack.CHAPTERS / LOCALES must list every file of integration/ftbquests/quests");
    }

    @Test
    void jarResourcesAreTheIntegrationFiles() {
        var fromJar = QuestPack.fromClasspath();
        var fromRepo = QuestInstallerTest.pack();
        assertEquals(fromRepo.version, fromJar.version, "processResources copies integration/ftbquests/quests to /cccloud_quests/");
        assertEquals(16, fromJar.version.length());
    }

    @Test
    void chaptersAreConsistent() {
        var pack = QuestInstallerTest.pack();
        var cct = pack.chapter("cc_tweaked");
        var cloud = pack.chapter("cc_cloud");
        assertEquals("4A19A9D21CB02379", cct.id());
        assertEquals("1CDB52DC3EE2DA1E", cloud.id());
        assertEquals(24, cct.quests());
        assertEquals(39, cloud.quests());
        assertEquals("CC: Tweaked", cct.title());
        assertEquals("CC: Cloud", cloud.title());
        Set<String> both = new HashSet<>(cct.definedIds());
        both.retainAll(cloud.definedIds());
        assertTrue(both.isEmpty(), "ids defined twice: " + both);
        assertFalse(cct.definedIds().contains(QuestInstaller.OWN_GROUP));
        assertFalse(cloud.definedIds().contains(QuestInstaller.OWN_GROUP));
        for (String locale : QuestPack.LOCALES) {
            assertEquals(2, cloud.chapterKeys().get(locale).size(), locale + ": title + subtitle");
            assertTrue(cloud.questKeys().get(locale).containsKey("quest.78170295DDB6E973.title"), locale);
        }
        assertEquals(QuestInstaller.TECH_GROUP, cloud.root().stringOf("group"));
    }

    @Test
    void editionFlag() throws IOException {
        assertEquals(Edition.STANDARD, Edition.read(null));
        assertEquals(Edition.MODPACK, Edition.read(stream("edition=modpack\n")));
        assertEquals(Edition.MODPACK, Edition.read(stream("# comment\nedition = Modpack\n")));
        assertEquals(Edition.STANDARD, Edition.read(stream("edition=standard\n")));
        assertEquals(Edition.STANDARD, Edition.current(), "dev and unit test runs are the standard edition");
        assertTrue(Edition.STANDARD.installsQuests());
        assertFalse(Edition.MODPACK.installsQuests());
        // the file the modpack jar carries
        try (var in = Files.newInputStream(Path.of(System.getProperty("cccloud.questsDir")).resolve("../../../src/modpack/cccloud-edition.properties").normalize())) {
            assertEquals(Edition.MODPACK, Edition.read(in));
        }
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
