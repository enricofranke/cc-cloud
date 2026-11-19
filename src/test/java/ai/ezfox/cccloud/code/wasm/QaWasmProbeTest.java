package ai.ezfox.cccloud.code.wasm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Hostile modules against the module check (it runs when a script is saved, on a script worker): every one must be
 * refused quickly with {@link WasmCheck.Refused}, without exhausting the heap or the stack.
 */
class QaWasmProbeTest {
    private static final class Out {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Out b(int... values) {
            for (int v : values) bytes.write(v);
            return this;
        }

        Out u(long value) {
            do {
                int b = (int) (value & 0x7f);
                value >>>= 7;
                if (value != 0) b |= 0x80;
                bytes.write(b);
            } while (value != 0);
            return this;
        }

        Out name(String s) {
            var raw = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            u(raw.length);
            bytes.writeBytes(raw);
            return this;
        }

        Out raw(byte[] data) {
            bytes.writeBytes(data);
            return this;
        }

        Out section(int id, Out content) {
            b(id);
            u(content.bytes.size());
            bytes.writeBytes(content.bytes.toByteArray());
            return this;
        }

        byte[] done() {
            return bytes.toByteArray();
        }
    }

    private static Out header() {
        return new Out().b(0, 'a', 's', 'm', 1, 0, 0, 0);
    }

    /** type () -> (), one function, export main, and this code body. */
    private static byte[] withBody(Out body) {
        var bodyBytes = body.done();
        return header()
            .section(1, new Out().u(1).b(0x60, 0, 0))
            .section(3, new Out().u(1).u(0))
            .section(7, new Out().u(1).name("main").b(0).u(0))
            .section(10, new Out().u(1).u(bodyBytes.length).raw(bodyBytes))
            .done();
    }

    private record Outcome(long millis, long heapMb, Throwable thrown) {
    }

    private static Outcome attempt(String what, byte[] module) throws InterruptedException {
        var thrown = new AtomicReference<Throwable>();
        System.gc();
        var rt = Runtime.getRuntime();
        long before = rt.totalMemory() - rt.freeMemory();
        long start = System.nanoTime();
        var t = new Thread(() -> {
            try {
                WasmCheck.check(module, Map.of(), 16L << 20);
                thrown.set(new AssertionError("accepted"));
            } catch (Throwable e) {
                thrown.set(e);
            }
        });
        t.start();
        t.join(20_000);
        long ms = (System.nanoTime() - start) / 1_000_000;
        long after = rt.totalMemory() - rt.freeMemory();
        if (t.isAlive()) {
            t.interrupt();
            thrown.set(new AssertionError("still running after 20 s"));
        }
        var o = new Outcome(ms, Math.max(0, after - before) >> 20, thrown.get());
        System.out.println("QA wasm [" + what + "] " + module.length + " bytes: " + ms + " ms, heap +" + o.heapMb() + " MB, "
            + o.thrown().getClass().getSimpleName() + ": " + String.valueOf(o.thrown().getMessage()).lines().findFirst().orElse(""));
        return o;
    }

    private static void refusedQuickly(String what, byte[] module) throws InterruptedException {
        var o = attempt(what, module);
        assertInstanceOf(WasmCheck.Refused.class, o.thrown(), what + ": " + o.thrown());
        assertTrue(o.millis() < 5000, what + " took " + o.millis() + " ms");
    }

    @Test
    void hugeLocalCount() throws Exception {
        // one local declaration: 0xFFFFFFFF locals of type i32
        refusedQuickly("locals 2^32-1", withBody(new Out().u(1).u(0xFFFFFFFFL).b(0x7f).b(0x0b)));
    }

    @Test
    void manyLocalDeclarations() throws Exception {
        // 40 000 declarations of 100 000 locals each, 5 bytes per declaration
        var body = new Out().u(40_000);
        for (int i = 0; i < 40_000; i++) body.u(100_000).b(0x7f);
        refusedQuickly("40k x 100k locals", withBody(body.b(0x0b)));
    }

    @Test
    void hugeVectorCountInTypeSection() throws Exception {
        refusedQuickly("type count 2^31", header().section(1, new Out().u(0x7FFFFFFFL).b(0x60, 0, 0)).done());
    }

    @Test
    void hugeFunctionCount() throws Exception {
        refusedQuickly("function count 2^31", header().section(1, new Out().u(1).b(0x60, 0, 0)).section(3, new Out().u(0x7FFFFFFFL).u(0)).done());
    }

    @Test
    void hugeDataSegment() throws Exception {
        var data = new Out().u(1).u(0).b(0x41, 0, 0x0b).u(0x7FFFFFF0L);
        refusedQuickly("data length 2^31", header().section(5, new Out().u(1).b(0).u(1)).section(11, data).done());
    }

    @Test
    void deeplyNestedBlocks() throws Exception {
        var body = new Out().u(0);
        int depth = 40_000;
        for (int i = 0; i < depth; i++) body.b(0x02, 0x40);
        for (int i = 0; i < depth; i++) body.b(0x0b);
        refusedOrAcceptedQuickly("40k nested blocks", withBody(body.b(0x0b)));
    }

    @Test
    void brTableWithHugeLabelCount() throws Exception {
        refusedQuickly("br_table 2^31 labels", withBody(new Out().u(0).b(0x41, 0).b(0x0e).u(0x7FFFFFFFL).u(0).b(0x0b)));
    }

    /** Valid modules may be accepted (then the run-time limits apply), but the check must stay fast and small. */
    private static void refusedOrAcceptedQuickly(String what, byte[] module) throws InterruptedException {
        var o = attempt(what, module);
        assertFalse(o.thrown() instanceof Error && !(o.thrown() instanceof AssertionError && "accepted".equals(o.thrown().getMessage())),
            what + ": " + o.thrown());
        assertTrue(o.millis() < 5000, what + " took " + o.millis() + " ms");
    }

    /** A data segment that claims 200 MB in a 25-byte module: how much does the check allocate? */
    @Test
    void largeDataSegmentWithinTheHeap() throws Exception {
        var data = new Out().u(1).u(0).b(0x41, 0, 0x0b).u(200_000_000L);
        var module = header().section(5, new Out().u(1).b(0).u(1)).section(11, data).done();
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        Throwable thrown = null;
        try {
            WasmCheck.check(module, Map.of(), 16L << 20);
        } catch (Throwable e) {
            thrown = e;
        }
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
        System.out.println("QA wasm [data length 200M] " + module.length + " bytes: allocated " + (allocated >> 20) + " MB, " + thrown);
        assertTrue(allocated < 16L << 20, "the check allocated " + (allocated >> 20) + " MB for a " + module.length + "-byte module");
    }
}
