package ai.ezfox.cccloud.reports;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.State;
import ai.ezfox.cccloud.reports.ReportPayload.Env;
import ai.ezfox.cccloud.reports.ReportPayload.Where;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportEngineTest {
    private static final long DAY = 86_400_000L;
    private static final long NOON = 1_793_620_800_000L; // a noon in UTC, so that a few hours stay on one day

    @TempDir
    Path dir;

    private final EnumMap<Channel, State> consent = new EnumMap<>(Channel.class);
    private final List<String> sent = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(NOON);
    private final AtomicInteger jobs = new AtomicInteger();
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean hold;
    private ReportEngine.Transport transport = this::record;

    private final Executor executor = job -> {
        jobs.incrementAndGet();
        if (hold) queue.add(job);
        else job.run();
    };

    private ReportStore store = new ReportStore(null);

    private boolean record(String channel, byte[] body) {
        sent.add(channel + " " + new String(body, java.nio.charset.StandardCharsets.UTF_8));
        return true;
    }

    private ReportEngine engine() {
        return new ReportEngine(c -> consent.getOrDefault(c, State.ASK), store, () -> ReportScrubber.EMPTY, () -> new Env("1", "2", "3", 25, "linux"),
            (channel, body) -> transport.post(channel, body), executor, clock::get);
    }

    private static Throwable failure(String method) {
        var e = new IllegalStateException("boom");
        e.setStackTrace(new StackTraceElement[] {new StackTraceElement("ai.ezfox.cccloud.web.WebHub", method, "WebHub.java", 5)});
        return e;
    }

    private static JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("v", 1);
        return json;
    }

    private Path crashFile(String name, String owner, long modified) throws IOException {
        var file = Files.createDirectories(dir.resolve("crash-reports")).resolve(name);
        Files.writeString(file, "Description: x\n\njava.lang.Error: boom\n\tat " + owner + ".run(Run.java:1)\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified));
        return file;
    }

    @Test
    void withoutConsentNothingHappensAtAll() throws IOException {
        crashFile("crash-new.txt", "ai.ezfox.cccloud.Foo", NOON);
        var engine = engine();
        engine.error(Where.WEB, failure("tick"));
        engine.scanCrashes(dir.resolve("crash-reports"));
        engine.stats(snapshot());
        assertFalse(engine.statsDue());
        assertEquals(List.of(), sent);
        assertEquals(0, jobs.get(), "no job may even be queued");
    }

    @Test
    void declinedIsLikeUndecided() {
        consent.put(Channel.CRASH, State.DECLINED);
        consent.put(Channel.STATS, State.DECLINED);
        var engine = engine();
        engine.error(Where.WEB, failure("tick"));
        engine.stats(snapshot());
        assertEquals(List.of(), sent);
        assertEquals(0, jobs.get());
    }

    @Test
    void withoutConsentNoFileAppears() {
        store = new ReportStore(dir.resolve("reports").resolve("state.json"));
        var engine = engine();
        engine.error(Where.WEB, failure("tick"));
        engine.stats(snapshot());
        assertFalse(Files.exists(dir.resolve("reports")));
    }

    @Test
    void theChannelsAreSeparate() {
        consent.put(Channel.STATS, State.ACTIVE);
        var engine = engine();
        engine.error(Where.WEB, failure("tick"));
        assertEquals(List.of(), sent);
        engine.stats(snapshot());
        assertEquals(1, sent.size());

        sent.clear();
        consent.put(Channel.STATS, State.DECLINED);
        consent.put(Channel.CRASH, State.ACTIVE);
        clock.addAndGet(DAY);
        engine.stats(snapshot());
        assertEquals(List.of(), sent);
        engine.error(Where.WEB, failure("tick"));
        assertEquals(1, sent.size());
    }

    @Test
    void anErrorWithConsentIsSent() {
        consent.put(Channel.CRASH, State.ACTIVE);
        var engine = engine();
        engine.error(Where.WEB, failure("tick"));
        assertEquals(1, sent.size());
        assertTrue(sent.getFirst().startsWith("crash {\"v\":1,"));
        assertNotNull(engine.lastCrash());
    }

    @Test
    void aForeignErrorIsNotSent() {
        consent.put(Channel.CRASH, State.ACTIVE);
        var e = new IllegalStateException("x");
        e.setStackTrace(new StackTraceElement[] {new StackTraceElement("org.example.Foo", "run", "Foo.java", 1)});
        var engine = engine();
        engine.error(Where.WEB, e);
        assertEquals(List.of(), sent);
        assertNull(engine.lastCrash());
    }

    @Test
    void oneReportPerFingerprintPerDay() {
        consent.put(Channel.CRASH, State.ACTIVE);
        var engine = engine();
        engine.error(Where.WEB, failure("tick"));
        engine.error(Where.WEB, failure("tick"));
        assertEquals(1, sent.size());
        clock.addAndGet(DAY);
        engine.error(Where.WEB, failure("tick"));
        assertEquals(2, sent.size());
    }

    @Test
    void atMostFivePerRun() {
        consent.put(Channel.CRASH, State.ACTIVE);
        var engine = engine();
        for (int i = 0; i < 20; i++) engine.error(Where.WEB, failure("m" + i));
        assertEquals(ReportEngine.MAX_PER_RUN, sent.size());
    }

    @Test
    void aNewRunMaySendAgainButNotTheSameFingerprint() {
        consent.put(Channel.CRASH, State.ACTIVE);
        engine().error(Where.WEB, failure("tick"));
        var next = engine();
        next.error(Where.WEB, failure("tick"));
        assertEquals(1, sent.size());
        next.error(Where.WEB, failure("other"));
        assertEquals(2, sent.size());
    }

    @Test
    void withdrawingStopsWhatIsQueued() {
        consent.put(Channel.CRASH, State.ACTIVE);
        consent.put(Channel.STATS, State.ACTIVE);
        hold = true;
        var engine = engine();
        engine.error(Where.WEB, failure("tick"));
        engine.stats(snapshot());
        consent.put(Channel.CRASH, State.DECLINED);
        consent.put(Channel.STATS, State.DECLINED);
        queue.forEach(Runnable::run);
        assertEquals(List.of(), sent);
    }

    @Test
    void withdrawingWhileAScanRunsStopsIt() throws IOException {
        consent.put(Channel.CRASH, State.ACTIVE);
        crashFile("crash-a.txt", "ai.ezfox.cccloud.A", NOON);
        crashFile("crash-b.txt", "ai.ezfox.cccloud.B", NOON + 1);
        store.crashesAfter(NOON - 1);
        transport = (channel, body) -> {
            record(channel, body);
            consent.put(Channel.CRASH, State.DECLINED);
            return true;
        };
        engine().scanCrashes(dir.resolve("crash-reports"));
        assertEquals(1, sent.size());
    }

    @Test
    void onlyCrashesAfterTheConsentAreRead() throws IOException {
        consent.put(Channel.CRASH, State.ACTIVE);
        store.crashesAfter(NOON);
        crashFile("crash-old.txt", "ai.ezfox.cccloud.Old", NOON - 1000);
        crashFile("crash-new.txt", "ai.ezfox.cccloud.New", NOON + 1000);
        engine().scanCrashes(dir.resolve("crash-reports"));
        assertEquals(1, sent.size());
        assertTrue(sent.getFirst().contains("New.run"));
        assertEquals(NOON + 1000, store.crashesAfter());
    }

    @Test
    void aCrashFileIsReadOnce() throws IOException {
        consent.put(Channel.CRASH, State.ACTIVE);
        crashFile("crash-new.txt", "ai.ezfox.cccloud.New", NOON + 1000);
        store.crashesAfter(NOON);
        engine().scanCrashes(dir.resolve("crash-reports"));
        clock.addAndGet(DAY);
        engine().scanCrashes(dir.resolve("crash-reports"));
        assertEquals(1, sent.size());
    }

    @Test
    void foreignCrashFilesAreSkipped() throws IOException {
        consent.put(Channel.CRASH, State.ACTIVE);
        store.crashesAfter(NOON);
        crashFile("crash-new.txt", "org.example.Other", NOON + 1000);
        engine().scanCrashes(dir.resolve("crash-reports"));
        assertEquals(List.of(), sent);
    }

    @Test
    void linksAndOtherFilesAreSkipped() throws IOException {
        consent.put(Channel.CRASH, State.ACTIVE);
        store.crashesAfter(NOON);
        var real = crashFile("elsewhere.log", "ai.ezfox.cccloud.Log", NOON + 1000);
        Files.createSymbolicLink(dir.resolve("crash-reports").resolve("link.txt"), real);
        Files.createDirectory(dir.resolve("crash-reports").resolve("folder.txt"));
        engine().scanCrashes(dir.resolve("crash-reports"));
        assertEquals(List.of(), sent);
    }

    @Test
    void aMissingCrashFolderIsFine() {
        consent.put(Channel.CRASH, State.ACTIVE);
        assertDoesNotThrow(() -> engine().scanCrashes(dir.resolve("nothing")));
    }

    @Test
    void statsOncePerUtcDay() {
        consent.put(Channel.STATS, State.ACTIVE);
        var engine = engine();
        assertTrue(engine.statsDue());
        engine.stats(snapshot());
        assertFalse(engine.statsDue());
        engine.stats(snapshot());
        assertEquals(1, sent.size());
        clock.addAndGet(DAY);
        assertTrue(engine.statsDue());
        engine.stats(snapshot());
        assertEquals(2, sent.size());
    }

    @Test
    void aFailedStatsSendIsNotRepeatedThatDay() {
        consent.put(Channel.STATS, State.ACTIVE);
        transport = (channel, body) -> false;
        var engine = engine();
        engine.stats(snapshot());
        assertFalse(engine.statsDue());
    }

    @Test
    void feedbackNeedsNoChannelButIsSentOnce() {
        var engine = engine();
        var result = new ArrayList<Boolean>();
        engine.feedback(snapshot(), result::add);
        assertEquals(List.of(true), result);
        assertEquals(1, sent.size());
        assertTrue(sent.getFirst().startsWith("feedback "));
    }

    @Test
    void feedbackReportsAFailure() {
        transport = (channel, body) -> false;
        var result = new ArrayList<Boolean>();
        engine().feedback(snapshot(), result::add);
        assertEquals(List.of(false), result);
    }

    @Test
    void aBrokenTransportStaysInside() {
        consent.put(Channel.CRASH, State.ACTIVE);
        transport = (channel, body) -> {
            throw new IllegalStateException("network is on fire");
        };
        assertDoesNotThrow(() -> engine().error(Where.WEB, failure("tick")));
    }

    @Test
    void aFullQueueDrops() {
        consent.put(Channel.CRASH, State.ACTIVE);
        var called = new AtomicBoolean();
        var engine = new ReportEngine(c -> State.ACTIVE, store, () -> ReportScrubber.EMPTY, () -> new Env("1", "2", "3", 25, "linux"), (c, b) -> {
            called.set(true);
            return true;
        }, job -> {
            throw new RejectedExecutionException();
        }, clock::get);
        assertDoesNotThrow(() -> engine.error(Where.WEB, failure("tick")));
        var result = new ArrayList<Boolean>();
        engine.feedback(snapshot(), result::add);
        assertEquals(List.of(false), result);
        assertFalse(called.get());
    }

    @Test
    void theCaptureHookNeverThrowsBack() {
        ReportCapture.engine(null);
        assertDoesNotThrow(() -> ReportCapture.failure(Where.WEB, failure("tick")));
        consent.put(Channel.CRASH, State.ACTIVE);
        ReportCapture.engine(new ReportEngine(c -> {
            throw new IllegalStateException("consent is broken");
        }, store, () -> ReportScrubber.EMPTY, () -> new Env("1", "2", "3", 25, "linux"), this::record, executor, clock::get));
        assertDoesNotThrow(() -> ReportCapture.failure(Where.WEB, failure("tick")));
        ReportCapture.engine(null);
        assertEquals(List.of(), sent);
    }

    @Test
    void theWebLayerIgnoresDroppedConnections() {
        consent.put(Channel.CRASH, State.ACTIVE);
        ReportCapture.engine(engine());
        ReportCapture.web(Where.WEB, new IOException("Connection reset by peer"));
        ReportCapture.web(Where.WEB, new RuntimeException(new IOException("reset")));
        assertEquals(List.of(), sent);
        ReportCapture.web(Where.WEB, failure("tick"));
        ReportCapture.engine(null);
        assertEquals(1, sent.size());
    }
}
