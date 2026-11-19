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

    /** A module with type () -> (), one function with this body, export main. */
    private static byte[] withBody(byte[] body) {
        var out = new java.io.ByteArrayOutputStream();
        out.writeBytes(new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0, 1, 4, 1, 0x60, 0, 0, 3, 2, 1, 0, 7, 8, 1, 4, 'm', 'a', 'i', 'n', 0, 0});
        var code = new java.io.ByteArrayOutputStream();
        code.write(1);
        leb(code, body.length);
        code.writeBytes(body);
        out.write(10);
        leb(out, code.size());
        out.writeBytes(code.toByteArray());
        return out.toByteArray();
    }

    private static void leb(java.io.ByteArrayOutputStream out, long v) {
        do {
            int b = (int) (v & 0x7f);
            v >>>= 7;
            out.write(v != 0 ? b | 0x80 : b);
        } while (v != 0);
    }

    private static byte[] nestedBody(int depth) {
        var body = new java.io.ByteArrayOutputStream();
        body.write(0); // no locals
        for (int i = 0; i < depth; i++) body.writeBytes(new byte[]{0x02, 0x40});
        for (int i = 0; i < depth; i++) body.write(0x0b);
        body.write(0x0b);
        return body.toByteArray();
    }

    private static byte[] nested(int depth) {
        return withBody(nestedBody(depth));
    }

    @Test
    void nestingIsBoundedCheaply() throws WasmCheck.Refused {
        // N6: deep nesting cost ~46 MB to check; now at most 256 levels
        var e = assertThrows(WasmCheck.Refused.class, () -> WasmCheck.check(nested(WasmPrecheck.MAX_NESTING + 1), WasmInstance.IMPORTS, 16L << 20));
        assertTrue(e.getMessage().contains("nested deeper than " + WasmPrecheck.MAX_NESTING), e.getMessage());
        WasmCheck.check(nested(WasmPrecheck.MAX_NESTING), WasmInstance.IMPORTS, 16L << 20);
        // many functions each nested to the limit, filling a module: the check stays small
        int functions = 250;
        var many = new java.io.ByteArrayOutputStream();
        many.writeBytes(new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0, 1, 4, 1, 0x60, 0, 0});
        var funcs = new java.io.ByteArrayOutputStream();
        leb(funcs, functions);
        for (int i = 0; i < functions; i++) funcs.write(0);
        many.write(3);
        leb(many, funcs.size());
        many.writeBytes(funcs.toByteArray());
        var bodies = new java.io.ByteArrayOutputStream();
        leb(bodies, functions);
        byte[] one = nestedBody(WasmPrecheck.MAX_NESTING);
        for (int i = 0; i < functions; i++) {
            leb(bodies, one.length);
            bodies.writeBytes(one);
        }
        many.write(10);
        leb(many, bodies.size());
        many.writeBytes(bodies.toByteArray());
        long used = check(many.toByteArray(), functions + " functions nested " + WasmPrecheck.MAX_NESTING);
        assertTrue(used < 64L << 20, "allocated " + (used >> 20) + " MB");
    }

    @Test
    void elementEntriesAreBounded() {
        // N7: 150 000 element entries cost ~70 MB in Endive; at most MAX_TABLE now, refused before parsing
        for (int entries : new int[]{(int) WasmCheck.MAX_TABLE + 1, 150_000}) {
            var el = new java.io.ByteArrayOutputStream();
            el.writeBytes(new byte[]{1, 0, 0x41, 0, 0x0b});
            leb(el, entries);
            for (int i = 0; i < entries; i++) el.write(0);
            var m = new java.io.ByteArrayOutputStream();
            m.writeBytes(new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0, 1, 4, 1, 0x60, 0, 0, 3, 2, 1, 0, 4, 4, 1, 0x70, 0, 1});
            m.write(9);
            leb(m, el.size());
            m.writeBytes(el.toByteArray());
            m.writeBytes(new byte[]{10, 4, 1, 2, 0, 0x0b});
            long before = allocated();
            var e = assertThrows(WasmCheck.Refused.class, () -> WasmCheck.check(m.toByteArray(), WasmInstance.IMPORTS, 16L << 20));
            assertTrue(e.getMessage().contains("table elements"), e.getMessage());
            assertTrue(allocated() - before < 4L << 20, "refused before Endive reads it: " + ((allocated() - before) >> 20) + " MB");
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
