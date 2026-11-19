package ai.ezfox.cccloud.computer;

/**
 * Redstone of a Cloud Computer, supplied by its block entity. Inputs are read from the world on the server thread and
 * cached; programs read the cache from their worker thread. Outputs a program sets are stored here and applied to the
 * world by the block entity on the server thread (it updates its neighbours when {@link #outputs()} changed).
 *
 * <p>Implementations must be thread-safe: {@link #input} and {@link #setOutput} are called from script workers.
 */
public interface RedstoneIo {
    /** No world behind it: every input 0, outputs kept but go nowhere (tests, a computer without a block). */
    RedstoneIo NONE = new Memory();

    /** The analog input level 0–15 on a side. */
    int input(ComputerSide side);

    /** The analog output level 0–15 this computer emits on a side. */
    int output(ComputerSide side);

    /** Sets an output level (clamped to 0–15). */
    void setOutput(ComputerSide side, int level);

    /** Counts output changes, so the block entity can tell when to update its neighbours. */
    long outputs();

    /** A plain in-memory implementation: inputs set by the owner (block entity or test), outputs remembered. */
    final class Memory implements RedstoneIo {
        private final int[] in = new int[6];
        private final int[] out = new int[6];
        private long changes;

        @Override
        public synchronized int input(ComputerSide side) {
            return in[side.ordinal()];
        }

        /** Owner side (server thread): the level the world gives on a side. */
        public synchronized void setInput(ComputerSide side, int level) {
            in[side.ordinal()] = clamp(level);
        }

        @Override
        public synchronized int output(ComputerSide side) {
            return out[side.ordinal()];
        }

        @Override
        public synchronized void setOutput(ComputerSide side, int level) {
            int v = clamp(level);
            if (out[side.ordinal()] != v) {
                out[side.ordinal()] = v;
                changes++;
            }
        }

        /** All outputs back to 0 (the computer turned off). */
        public synchronized void clearOutputs() {
            for (int i = 0; i < out.length; i++) {
                if (out[i] != 0) {
                    out[i] = 0;
                    changes++;
                }
            }
        }

        @Override
        public synchronized long outputs() {
            return changes;
        }

        private static int clamp(int level) {
            return Math.max(0, Math.min(15, level));
        }
    }
}
