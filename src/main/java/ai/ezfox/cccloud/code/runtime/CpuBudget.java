package ai.ezfox.cccloud.code.runtime;

import java.lang.management.ManagementFactory;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The CPU and memory accounting of one running program (a Cloud Script or a Cloud Computer boot): a CPU share per
 * second of wall time, a longest run between two waits, an allocation cap per second and the timing of the heap
 * estimate. CPU time comes from a {@link CpuClock} and is read on the thread that runs the program, so a stalled worker
 * (a lagging server, a GC pause) is not charged; the windows themselves are wall time.
 *
 * <p>Not thread-safe: the worker that runs the program calls everything except the getters marked as any thread.
 * The caller keeps the CPU of a run (per task) and passes it to {@link #check(long)}.
 */
public final class CpuBudget {
    public static final long SECOND = 1_000_000_000L;
    /** For the allocation and memory arguments: no limit (an engine with a hard memory cap of its own). */
    public static final long UNLIMITED = Long.MAX_VALUE / 4;
    private static final com.sun.management.@Nullable ThreadMXBean ALLOC = allocationBean();

    /** The book of a job that another one interrupts (a task that starts inside another task's wait). */
    public record Job(long start, long blocked, long allocStart) {
    }

    /** What the budget says while a job runs. */
    public enum Verdict {
        CONTINUE,
        /** Pause the task so the heap can be measured (the CPU share may be left). */
        MEASURE_HEAP,
        /** The CPU share of this second is used up: pause until {@link #windowEnd()}. */
        PAUSE,
        /** Allocated more than the cap within a second. */
        TOO_MUCH_ALLOCATION,
        /** Ran longer than the limit per run without waiting. */
        TOO_LONG
    }

    private final CpuClock clock;
    private final long share;
    private final long perRun;
    private final long allocPerSecond;
    private final long memoryBytes;

    private long windowStart;
    private long windowUsed;
    private long lastWindowUsed;
    private long windowAlloc;
    private long jobStart;
    private long jobBlocked;
    private long jobAllocStart;
    private long lastHeapCheck;
    private long allocSinceHeap;
    private long heapThreshold;
    private boolean heapRequested;
    private volatile long totalCpuNanos;
    private volatile long heapEstimate;
    private volatile int sharePauses;

    /**
     * @param cpuMsPerSecond the CPU share per second
     * @param cpuMsPerRun    the longest run between two waits
     */
    public CpuBudget(CpuClock clock, long cpuMsPerSecond, long cpuMsPerRun, long allocBytesPerSecond, long memoryBytes) {
        this.clock = clock;
        this.share = cpuMsPerSecond * 1_000_000L;
        this.perRun = cpuMsPerRun * 1_000_000L;
        this.allocPerSecond = allocBytesPerSecond;
        this.memoryBytes = memoryBytes;
        this.heapThreshold = Math.max(1, memoryBytes / 4);
    }

    // ---- windows (wall time) -------------------------------------------------------------------------------------------

    /**
     * Starts a new window when a second (wall time) has passed since the current one began. Returns how many seconds
     * passed (0: still the same window); then {@link #lastWindowUsed()} is the CPU time of the window that ended.
     */
    public long roll(long now) {
        if (windowStart == 0) windowStart = now;
        if (now - windowStart < SECOND) return 0;
        long seconds = (now - windowStart) / SECOND;
        lastWindowUsed = windowUsed;
        windowStart += seconds * SECOND;
        windowUsed = 0;
        windowAlloc = 0;
        return seconds;
    }

    /** CPU nanoseconds of the window that ended at the last {@link #roll} that started a new one. */
    public long lastWindowUsed() {
        return lastWindowUsed;
    }

    /** Whether the CPU share of the current window is used up. */
    public boolean shareUsed() {
        return windowUsed >= share;
    }

    /** When the current window ends (wall time, {@link System#nanoTime()}). */
    public long windowEnd() {
        return windowStart + SECOND;
    }

    /** The program is paused until {@link #windowEnd()} because its share is used up; returns that time. */
    public long pauseForShare() {
        sharePauses++;
        return windowEnd();
    }

    /** Whether a pause was for a heap measurement only and the program may go on in this window. */
    public boolean heapPauseOnly() {
        return heapRequested && !shareUsed();
    }

    // ---- jobs (CPU time) -----------------------------------------------------------------------------------------------

    /** A job (a stretch of running until the engine returns) starts on this thread. */
    public void startJob() {
        jobStart = clock.now();
        jobBlocked = 0;
        jobAllocStart = allocated();
    }

    /** The current job's book, to {@link #resume} it after a nested job. */
    public Job job() {
        return new Job(jobStart, jobBlocked, jobAllocStart);
    }

    /** Goes on with a job saved by {@link #job()}. */
    public void resume(Job job) {
        jobStart = job.start();
        jobBlocked = job.blocked();
        jobAllocStart = job.allocStart();
    }

    /** The job ended: books its CPU time and allocation; returns the CPU time (for the caller's run). */
    public long endJob() {
        long used = Math.max(0, clock.now() - jobStart - jobBlocked);
        long alloc = allocated() - jobAllocStart;
        windowUsed += used;
        windowAlloc += alloc;
        allocSinceHeap += alloc;
        totalCpuNanos += used;
        return used;
    }

    /** A mark for {@link #chargeSince(long)}: work outside a job that counts against the share (loading, heap walks). */
    public long mark() {
        return clock.now();
    }

    public void chargeSince(long mark) {
        windowUsed += Math.max(0, clock.now() - mark);
    }

    /** The engine waited this long inside a call: not CPU time. Only wall time counts waiting, so only then it is taken off. */
    public void blocked(long nanos) {
        if (clock.wall()) jobBlocked += nanos;
    }

    /** During a job (engine interrupt hook): {@code runCpu} is the CPU of the current run before this job. */
    public Verdict check(long runCpu) {
        long used = clock.now() - jobStart - jobBlocked;
        long jobAlloc = ALLOC == null ? 0 : allocated() - jobAllocStart;
        if (ALLOC != null && allocSinceHeap + jobAlloc > heapThreshold) {
            heapRequested = true;
            return Verdict.MEASURE_HEAP;
        }
        if (ALLOC != null && windowAlloc + jobAlloc > allocPerSecond) return Verdict.TOO_MUCH_ALLOCATION;
        if (runCpu + used > perRun) return Verdict.TOO_LONG;
        if (windowUsed + used >= share) return Verdict.PAUSE;
        return Verdict.CONTINUE;
    }

    // ---- heap ----------------------------------------------------------------------------------------------------------

    /**
     * Measures the heap when it is due: requested by {@link #check}, or the program allocated max(memory / 4, twice its
     * last size) since the last estimate (so the walk costs about as much as the allocation it follows), else every
     * 10 seconds (wall time). The walk counts against the share. Returns the estimate, or -1 when it was not due.
     */
    public long measureHeap(long now, LongSupplier estimate) {
        long since = now - lastHeapCheck;
        boolean due = heapRequested || (ALLOC != null ? allocSinceHeap > heapThreshold || allocSinceHeap > 0 && since > 10 * SECOND : since > 10 * SECOND);
        heapRequested = false;
        if (!due) return -1;
        long mark = mark();
        long value = estimate.getAsLong();
        chargeSince(mark);
        lastHeapCheck = System.nanoTime();
        allocSinceHeap = 0;
        heapEstimate = value;
        heapThreshold = Math.max(memoryBytes / 4, 2 * Math.min(value, memoryBytes));
        return value;
    }

    /** An estimate the engine measured itself (an engine with its own heap): shown, not checked here. */
    public void heapMeasured(long estimate) {
        heapEstimate = estimate;
    }

    // ---- status (any thread) -------------------------------------------------------------------------------------------

    public long totalCpuNanos() {
        return totalCpuNanos;
    }

    public long heapEstimate() {
        return heapEstimate;
    }

    /** How often the program was paused until the next second because its CPU share was used up. */
    public int sharePauses() {
        return sharePauses;
    }

    /** Whether allocation per second can be limited on this JVM. */
    public static boolean allocationLimited() {
        return ALLOC != null;
    }

    private static long allocated() {
        return ALLOC == null ? 0 : ALLOC.getCurrentThreadAllocatedBytes();
    }

    private static com.sun.management.@Nullable ThreadMXBean allocationBean() {
        try {
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                return bean.getCurrentThreadAllocatedBytes() >= 0 ? bean : null;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // not a HotSpot-like JVM: memory is only bounded by the heap estimate
        }
        return null;
    }
}
