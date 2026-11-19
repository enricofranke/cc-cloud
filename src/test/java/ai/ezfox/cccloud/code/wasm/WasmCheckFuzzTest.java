package ai.ezfox.cccloud.code.wasm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The module check against corrupted modules: real modules (from {@code wasm/assemblyscript}) with bytes changed and
 * length or count fields raised to huge values. The check may refuse or accept, but it never throws anything else (no
 * {@link OutOfMemoryError}, no {@link StackOverflowError}), stays fast, and never allocates more than twice what the
 * intact module needs.
 */
class WasmCheckFuzzTest {
    private static final List<String> MODULES = List.of("api", "loop", "stop", "computer-hello");
    private static final com.sun.management.ThreadMXBean BEAN = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();

    private static byte[] module(String name) {
        try (InputStream in = WasmCheckFuzzTest.class.getResourceAsStream("/wasm/" + name + ".wasm")) {
            assertNotNull(in, name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static long allocated() {
        return BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    /** Runs the check; returns the bytes it allocated. Fails on anything but a clean accept or refusal. */
    private static long check(byte[] bytes, String what) {
        long before = allocated();
        long start = System.nanoTime();
        try {
            WasmCheck.check(bytes, WasmInstance.IMPORTS, 16L << 20);
        } catch (WasmCheck.Refused e) {
            // fine
        } catch (Throwable e) {
            fail(what + ": " + e, e);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 5000, what + " took " + ms + " ms");
        return allocated() - before;
    }

    /** A LEB128 of {@code value} written over the bytes at {@code at} (the module keeps its length). */
    private static byte[] withLeb(byte[] original, int at, long value) {
        var copy = original.clone();
        for (int i = 0; i < 5 && at + i < copy.length; i++) {
            int b = (int) (value & 0x7f);
            value >>>= 7;
            copy[at + i] = (byte) (i < 4 ? b | 0x80 : b);
        }
        return copy;
    }

    @Test
    void corruptedModulesNeverExhaustTheHeap() {
        var random = new Random(20261119L);
        for (String name : MODULES) {
            byte[] intact = module(name);
            check(intact, name); // warm up
            long baseline = Math.max(check(intact, name), 1L << 20);
            long limit = 2 * baseline;
            int rounds = 1500;
            for (int round = 0; round < rounds; round++) {
                byte[] bad;
                String what;
                int at = 8 + random.nextInt(intact.length - 8);
                switch (round % 3) {
                    case 0 -> {
                        long huge = switch (random.nextInt(4)) {
                            case 0 -> 0x7FFF_FFF0L;
                            case 1 -> 200_000_000L;
                            case 2 -> 0xFFFF_FFFFL;
                            default -> intact.length + random.nextInt(1 << 20);
                        };
                        bad = withLeb(intact, at, huge);
                        what = name + " length " + huge + " at " + at;
                    }
                    case 1 -> {
                        bad = intact.clone();
                        bad[at] = (byte) random.nextInt(256);
                        what = name + " byte " + (bad[at] & 0xff) + " at " + at;
                    }
                    default -> {
                        bad = java.util.Arrays.copyOf(intact, at);
                        what = name + " cut at " + at;
                    }
                }
                long used = check(bad, what);
                assertTrue(used <= limit, what + ": allocated " + (used >> 10) + " KB, the intact module needs " + (baseline >> 10) + " KB");
            }
        }
    }

    @Test
    void theHardSizeLimitComesFirst() {
        var big = new byte[WasmCheck.MAX_MODULE + 1];
        big[1] = 'a';
        big[2] = 's';
        big[3] = 'm';
        big[4] = 1;
        long before = allocated();
        var e = assertThrows(WasmCheck.Refused.class, () -> WasmCheck.check(big, WasmInstance.IMPORTS, 16L << 20));
        assertTrue(e.getMessage().contains("larger than"), e.getMessage());
        assertTrue(allocated() - before < 64 * 1024, "refused before reading");
    }
}
