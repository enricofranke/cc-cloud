package ai.ezfox.cccloud.code.runtime;

import java.util.concurrent.atomic.AtomicLong;

/** A {@link CpuClock} the test moves by hand; {@code wall} says whether it stands for wall time (the fallback). */
public final class HandClock implements CpuClock {
    public final AtomicLong nanos = new AtomicLong(1);
    private final boolean wall;

    public HandClock() {
        this(false);
    }

    public HandClock(boolean wall) {
        this.wall = wall;
    }

    public void advanceMs(long ms) {
        nanos.addAndGet(ms * 1_000_000L);
    }

    @Override
    public long now() {
        return nanos.get();
    }

    @Override
    public long of(long threadId) {
        return nanos.get();
    }

    @Override
    public boolean wall() {
        return wall;
    }
}
