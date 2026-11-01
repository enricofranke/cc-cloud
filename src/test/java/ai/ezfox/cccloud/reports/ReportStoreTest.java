package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.Decision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportStoreTest {
    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("cccloud").resolve("reports").resolve("state.json");
    }

    @Test
    void decisionsSurviveARestart() {
        var store = new ReportStore(file());
        store.decide(Channel.CRASH, true, 1, 5000);
        store.decide(Channel.STATS, false, 1, 5000);
        var again = new ReportStore(file());
        assertEquals(new Decision(true, 1), again.decision(Channel.CRASH));
        assertEquals(new Decision(false, 1), again.decision(Channel.STATS));
        assertEquals(5000, again.crashesAfter());
    }

    @Test
    void aYesToCrashReportsCoversOnlyLaterCrashes() {
        var store = new ReportStore(null);
        store.decide(Channel.STATS, true, 1, 7000);
        assertEquals(0, store.crashesAfter());
        store.decide(Channel.CRASH, false, 1, 7000);
        assertEquals(0, store.crashesAfter());
        store.decide(Channel.CRASH, true, 1, 8000);
        assertEquals(8000, store.crashesAfter());
    }

    @Test
    void nothingIsWrittenBeforeThereIsSomethingToKeep() {
        var store = new ReportStore(file());
        assertNull(store.decision(Channel.CRASH));
        assertFalse(store.statsSentOn("2026-11-01"));
        assertFalse(Files.exists(dir.resolve("cccloud")));
    }

    @Test
    void aBrokenFileMeansUndecided() throws IOException {
        Files.createDirectories(file().getParent());
        for (String text : new String[] {"{", "[]", "{\"decisions\":{\"crash\":{\"yes\":\"maybe\"}}}", "{\"decisions\":{\"crash\":{}}}", "null"}) {
            Files.writeString(file(), text);
            assertNull(new ReportStore(file()).decision(Channel.CRASH), text);
        }
    }

    @Test
    void theFileHoldsNoReportData() throws IOException {
        var store = new ReportStore(file());
        store.decide(Channel.CRASH, true, 1, 1);
        store.claimFingerprint("abcd", "2026-11-01");
        store.claimStatsDay("2026-11-01");
        var json = com.google.gson.JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        assertEquals(java.util.Set.of("dismissedVersion", "statsDay", "crashesAfter", "decisions", "sent"), json.keySet());
    }

    @Test
    void noTemporaryFileStaysBehind() throws IOException {
        new ReportStore(file()).dismiss(1);
        try (var list = Files.list(file().getParent())) {
            assertEquals(1, list.count());
        }
    }

    @Test
    void statsOncePerDay() {
        var store = new ReportStore(file());
        assertTrue(store.claimStatsDay("2026-11-01"));
        assertFalse(store.claimStatsDay("2026-11-01"));
        assertTrue(new ReportStore(file()).statsSentOn("2026-11-01"));
        assertFalse(new ReportStore(file()).claimStatsDay("2026-11-01"));
        assertTrue(store.claimStatsDay("2026-11-02"));
    }

    @Test
    void fingerprintsOncePerDay() {
        var store = new ReportStore(file());
        assertTrue(store.claimFingerprint("a", "2026-11-01"));
        assertFalse(store.claimFingerprint("a", "2026-11-01"));
        assertFalse(new ReportStore(file()).claimFingerprint("a", "2026-11-01"));
        assertTrue(store.claimFingerprint("b", "2026-11-01"));
        assertTrue(store.claimFingerprint("a", "2026-11-02"));
    }

    @Test
    void fingerprintsOfADayAreBounded() {
        var store = new ReportStore(null);
        for (int i = 0; i < 256; i++) assertTrue(store.claimFingerprint("f" + i, "2026-11-01"));
        assertFalse(store.claimFingerprint("one-more", "2026-11-01"));
        assertTrue(store.claimFingerprint("one-more", "2026-11-02"));
    }

    @Test
    void dismissingOnlyMovesForward() {
        var store = new ReportStore(file());
        store.dismiss(2);
        store.dismiss(1);
        assertEquals(2, new ReportStore(file()).dismissedVersion());
    }

    @Test
    void anUnwritableFolderIsNoProblem() throws IOException {
        Files.writeString(dir.resolve("blocked"), "a file where the folder should be");
        var store = new ReportStore(dir.resolve("blocked").resolve("state.json"));
        store.decide(Channel.CRASH, false, 1, 1);
        assertEquals(new Decision(false, 1), store.decision(Channel.CRASH));
    }
}
