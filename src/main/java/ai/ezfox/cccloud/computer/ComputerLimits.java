package ai.ezfox.cccloud.computer;

/**
 * The limits of one running Cloud Computer. CPU and memory work like Cloud Scripts (docs/SPEC.md "Cloud Scripts",
 * "Runtime and limits"): a CPU share per second after which the program pauses, a longest run between two waits
 * (then the program fails with "too long without yielding"), a memory estimate cap and an allocation backstop.
 *
 * @param cpuMsPerSecond   CPU time per second, then the program pauses until the next second
 * @param cpuMsPerResume   CPU time between two event waits before the program is stopped
 * @param memoryBytes      memory estimate cap
 * @param allocBytesPerSecond allocation backstop per second
 * @param maxEvents        events waiting in the queue; more are dropped
 * @param maxTimers        timers running at once
 * @param diskBytes        size of the computer's drive
 * @param maxOpenFiles     open file handles at once
 * @param redstonePerSecond redstone output changes per second (burst twice that)
 */
public record ComputerLimits(int cpuMsPerSecond, int cpuMsPerResume, long memoryBytes, long allocBytesPerSecond, int maxEvents, int maxTimers,
                             long diskBytes, int maxOpenFiles, int redstonePerSecond) {
    public static final ComputerLimits DEFAULT = new ComputerLimits(100, 1000, 16L << 20, 1L << 30, 256, 128, 1L << 20, 32, 20);

    public ComputerLimits {
        if (cpuMsPerSecond < 1 || cpuMsPerResume < 1 || memoryBytes < 1 || maxEvents < 1 || maxTimers < 1 || diskBytes < 0 || maxOpenFiles < 1
            || redstonePerSecond < 1) {
            throw new IllegalArgumentException("bad computer limits");
        }
    }
}
