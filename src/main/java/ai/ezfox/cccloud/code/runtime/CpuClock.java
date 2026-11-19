package ai.ezfox.cccloud.code.runtime;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import org.jspecify.annotations.Nullable;

/**
 * The clock the CPU budget of Cloud Scripts is measured with: the CPU time of a thread, so time the thread does not run
 * (the server is under load, a GC pause, the OS gives the core to another process) is not charged to the script. Read
 * {@link #now()} on the thread that runs the script; a thread that decides from outside reads {@link #of(long)}.
 *
 * <p>Falls back to wall time ({@link #WALL}) on a JVM without per-thread CPU time; then waiting is charged too, and the
 * engines report the time they block ({@link ai.ezfox.cccloud.code.engine.Budget#blocked(long)}) so it is taken off.
 */
public interface CpuClock {
    /** CPU nanoseconds of the calling thread so far (only differences mean something). */
    long now();

    /** CPU nanoseconds of the live thread {@code threadId} so far, or -1 when it is not known. */
    long of(long threadId);

    /** Whether this clock counts wall time, waiting included (the fallback). */
    boolean wall();

    /** Wall time: the fallback, and for tests that need the old behaviour. */
    CpuClock WALL = new CpuClock() {
        @Override
        public long now() {
            return System.nanoTime();
        }

        @Override
        public long of(long threadId) {
            return System.nanoTime();
        }

        @Override
        public boolean wall() {
            return true;
        }

        @Override
        public String toString() {
            return "wall time";
        }
    };

    /** Thread CPU time when this JVM measures it, else {@link #WALL} (checked once, logged once). */
    static CpuClock system() {
        return Holder.SYSTEM;
    }

    /** Thread CPU time from {@code bean}, or null when the JVM cannot measure it. */
    static @Nullable CpuClock threadCpu(ThreadMXBean bean) {
        try {
            if (!bean.isCurrentThreadCpuTimeSupported() || !bean.isThreadCpuTimeSupported()) return null;
            if (!bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
            if (bean.getCurrentThreadCpuTime() < 0) return null;
        } catch (UnsupportedOperationException | SecurityException e) {
            return null;
        }
        return new CpuClock() {
            @Override
            public long now() {
                return bean.getCurrentThreadCpuTime();
            }

            @Override
            public long of(long threadId) {
                return bean.getThreadCpuTime(threadId);
            }

            @Override
            public boolean wall() {
                return false;
            }

            @Override
            public String toString() {
                return "thread CPU time";
            }
        };
    }

    final class Holder {
        static final CpuClock SYSTEM = detect();

        private Holder() {
        }

        private static CpuClock detect() {
            CpuClock clock = null;
            try {
                clock = threadCpu(ManagementFactory.getThreadMXBean());
            } catch (LinkageError ignored) {
                // no java.management: wall time below
            }
            if (clock != null) return clock;
            ai.ezfox.cccloud.CloudLog.LOGGER.warn("[cccloud] This JVM cannot measure thread CPU time: Cloud Script CPU limits use wall time, "
                + "so a lagging server can stop scripts early");
            return WALL;
        }
    }
}
