package ai.ezfox.cccloud.reports;

import ai.ezfox.cccloud.CloudLog;
import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.State;
import ai.ezfox.cccloud.reports.ReportPayload.Env;
import ai.ezfox.cccloud.reports.ReportPayload.Where;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Decides what is sent and sends it, without any game class, so the rules are unit tested. Every step asks
 * {@code consent} again: when work is queued, right before the payload is built and right before it goes out, so a
 * withdrawal stops everything that has not left yet. All work runs on the given executor, never on the caller's thread;
 * a full queue drops the report. Failed sends are not repeated.
 * <p>
 * Without consent this class creates nothing: no thread, no file, no connection.
 */
public final class ReportEngine {
    /** Crash and error reports per server run. */
    public static final int MAX_PER_RUN = 5;
    /** Crash files looked at per start, and how much of each: the stack trace is at the top. */
    static final int MAX_CRASH_FILES = 16, MAX_CRASH_BYTES = 256 * 1024;

    /** The way out. Blocking; returns whether the service accepted the payload. */
    @FunctionalInterface
    public interface Transport {
        boolean post(String channel, byte[] body);
    }

    private final Function<Channel, State> consent;
    private final ReportStore store;
    private final Supplier<ReportScrubber> scrubber;
    private final Supplier<Env> env;
    private final Transport transport;
    private final Executor executor;
    private final LongSupplier clock;
    private final AtomicInteger sentThisRun = new AtomicInteger();
    private volatile @Nullable JsonObject lastCrash;

    public ReportEngine(Function<Channel, State> consent, ReportStore store, Supplier<ReportScrubber> scrubber, Supplier<Env> env, Transport transport,
                        Executor executor, LongSupplier clock) {
        this.consent = consent;
        this.store = store;
        this.scrubber = scrubber;
        this.env = env;
        this.transport = transport;
        this.executor = executor;
        this.clock = clock;
    }

    private boolean allowed(Channel channel) {
        return consent.apply(channel) == State.ACTIVE;
    }

    /** The current UTC day as YYYY-MM-DD. */
    private String today() {
        return Instant.ofEpochMilli(clock.getAsLong()).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** Runs a job off the caller's thread; a full queue drops it. Anything that goes wrong stays in here. */
    private boolean submit(Runnable job) {
        try {
            executor.execute(() -> {
                try {
                    job.run();
                } catch (Throwable t) { // NOSONAR: a report must never be able to hurt the game
                    CloudLog.LOGGER.debug("[cccloud] Report job failed: {}", t.getClass().getName());
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /** A failure caught by the mod. Safe to call from any thread at any time; does nothing without consent. */
    public void error(Where where, Throwable failure) {
        if (!allowed(Channel.CRASH) || sentThisRun.get() >= MAX_PER_RUN) return;
        submit(() -> report(where, ReportStack.of(failure)));
    }

    /** The reports of crashes since the last look, from the game's crash folder: a JVM that crashes cannot send. */
    public void scanCrashes(Path folder) {
        if (!allowed(Channel.CRASH)) return;
        submit(() -> {
            if (!allowed(Channel.CRASH)) return;
            long after = store.crashesAfter();
            long newest = after;
            for (var file : newestFiles(folder, after)) {
                newest = Math.max(newest, file.modified());
                var stack = ReportStack.parse(read(file.path()));
                if (stack != null) report(Where.CRASH_FILE, stack);
            }
            if (newest > after) store.crashesAfter(newest);
        });
    }

    private record CrashFile(Path path, long modified) {
    }

    private static List<CrashFile> newestFiles(Path folder, long after) {
        var files = new ArrayList<CrashFile>();
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) return files;
        try (var list = Files.list(folder)) {
            list.forEach(path -> {
                try {
                    if (!path.getFileName().toString().endsWith(".txt") || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return;
                    long modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
                    if (modified > after) files.add(new CrashFile(path, modified));
                } catch (IOException e) {
                    CloudLog.LOGGER.debug("[cccloud] Skipping a crash file: {}", e.getClass().getName());
                }
            });
        } catch (IOException e) {
            CloudLog.LOGGER.debug("[cccloud] Could not list the crash folder: {}", e.getClass().getName());
        }
        files.sort(Comparator.comparingLong(CrashFile::modified).reversed());
        return files.size() > MAX_CRASH_FILES ? files.subList(0, MAX_CRASH_FILES) : files;
    }

    private static String read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return new String(in.readNBytes(MAX_CRASH_BYTES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void report(Where where, ReportStack stack) {
        if (!allowed(Channel.CRASH) || sentThisRun.get() >= MAX_PER_RUN) return;
        var payload = ReportPayload.crash(env.get(), where, stack, scrubber.get());
        if (payload == null) return;
        if (!store.claimFingerprint(payload.get("fingerprint").getAsString(), today())) return;
        sentThisRun.incrementAndGet();
        if (!allowed(Channel.CRASH)) return;
        lastCrash = payload;
        transport.post("crash", ReportPayload.bytes(payload));
    }

    /** The newest crash report of this run, for the preview. */
    public @Nullable JsonObject lastCrash() {
        return lastCrash;
    }

    /** Whether a stats snapshot is due: allowed, and none went out today. Cheap; the snapshot is built only then. */
    public boolean statsDue() {
        return allowed(Channel.STATS) && !store.statsSentOn(today());
    }

    /** Sends a stats snapshot, at most one per UTC day. */
    public void stats(JsonObject snapshot) {
        if (!allowed(Channel.STATS)) return;
        submit(() -> {
            if (!allowed(Channel.STATS) || !store.claimStatsDay(today())) return;
            transport.post("stats", ReportPayload.bytes(snapshot));
        });
    }

    /**
     * Sends one feedback message the writer has just confirmed. It needs no channel consent: the writer confirmed this
     * very text. {@code done} gets the result on the worker, or false at once when the queue is full.
     */
    public void feedback(JsonObject payload, @Nullable Consumer<Boolean> done) {
        boolean queued = submit(() -> {
            boolean ok = transport.post("feedback", ReportPayload.bytes(payload));
            if (done != null) done.accept(ok);
        });
        if (!queued && done != null) done.accept(false);
    }
}
