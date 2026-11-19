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

    // ---- attempts to get around the precheck (agent/wasm cdd4d8aa) ---------------------------------------------------

    /** Checks a module on this thread; prints and returns the bytes it allocated. Refused or accepted both count. */
    private static long allocatedBy(String what, byte[] module) {
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(id);
        long start = System.nanoTime();
        Throwable thrown = null;
        try {
            WasmCheck.check(module, Map.of(), 16L << 20);
        } catch (Throwable e) {
            thrown = e;
        }
        long allocated = bean.getThreadAllocatedBytes(id) - before;
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("QA wasm bypass [" + what + "] " + module.length + " bytes: " + ms + " ms, allocated " + (allocated >> 20) + " MB, "
            + (thrown == null ? "accepted" : thrown.getClass().getSimpleName() + ": " + String.valueOf(thrown.getMessage()).lines().findFirst().orElse("")));
        assertFalse(thrown instanceof Error, what + ": " + thrown);
        assertTrue(ms < 5000, what + " took " + ms + " ms");
        return allocated;
    }

    private static void small(String what, byte[] module) {
        long allocated = allocatedBy(what, module);
        assertTrue(allocated < 64L << 20, what + ": allocated " + (allocated >> 20) + " MB");
    }

    private static Out custom(String name, Out content) {
        var c = new Out().name(name);
        c.raw(content.done());
        return c;
    }

    @Test
    void nameSectionWithHugeCounts() {
        // "name" custom section: function names (subsection 1) with 2^31 entries, local names (2) with 2^31
        var fnNames = new Out().u(0x7FFFFFFFL).u(0).name("a");
        var localNames = new Out().u(0x7FFFFFFFL).u(0).u(0x7FFFFFFFL);
        var content = new Out().b(1).u(fnNames.done().length).raw(fnNames.done()).b(2).u(localNames.done().length).raw(localNames.done());
        small("name section 2^31", header().section(1, new Out().u(1).b(0x60, 0, 0)).section(3, new Out().u(1).u(0))
            .section(10, new Out().u(1).u(2).u(0).b(0x0b)).section(0, custom("name", content)).done());
        var sub = new Out().u(1).u(0).u(0x7FFFFFF0L);
        small("name subsection length 2^31", header().section(0, custom("name", new Out().b(1).u(0x7FFFFFF0L).raw(sub.done()))).done());
    }

    @Test
    void otherCustomSections() {
        for (String n : new String[]{"producers", "dylink.0", "target_features", "linking", "reloc.CODE", "sourceMappingURL", "external_debug_info"}) {
            small("custom " + n, header().section(0, custom(n, new Out().u(0x7FFFFFFFL).u(0x7FFFFFFFL).name("x").u(0x7FFFFFFFL))).done());
        }
    }

    @Test
    void hugeDataCount() {
        small("data count 2^32-1", header().section(5, new Out().u(1).b(0).u(1)).section(12, new Out().u(0xFFFFFFFFL)).done());
        small("data count 2^32-1 + segment", header().section(5, new Out().u(1).b(0).u(1)).section(12, new Out().u(0xFFFFFFFFL))
            .section(11, new Out().u(1).u(1).u(4).b(1, 2, 3, 4)).done());
    }

    @Test
    void tableAndMemoryLimits() {
        small("table min 2^32-1", header().section(4, new Out().u(1).b(0x70).b(1).u(0xFFFFFFFFL).u(0xFFFFFFFFL)).done());
        small("memory 65536 pages", header().section(5, new Out().u(1).b(1).u(65536).u(65536)).done());
        small("imported memory 65536", header().section(2, new Out().u(1).name("env").name("m").b(2).b(0).u(65536)).done());
        small("start index 2^32-1", header().section(8, new Out().u(0xFFFFFFFFL)).done());
    }

    @Test
    void manyFunctions() {
        int n = 50_000; // within 160 KB: each body is 2 bytes + 1 length byte, plus 1 type index byte
        var funcs = new Out().u(n);
        var code = new Out().u(n);
        for (int i = 0; i < n; i++) {
            funcs.u(0);
            code.u(2).u(0).b(0x0b);
        }
        small(n + " functions", header().section(1, new Out().u(1).b(0x60, 0, 0)).section(3, funcs).section(10, code).done());
        int types = 50_000;
        var t = new Out().u(types);
        for (int i = 0; i < types; i++) t.b(0x60, 0, 0);
        small(types + " types", header().section(1, t).done());
    }

    @Test
    void flatHugeBody() {
        var body = new Out().u(0);
        for (int i = 0; i < 150_000; i++) body.b(0x01); // nop
        small("150k nops", withBody(body.b(0x0b)));
        var nest = new Out().u(0);
        for (int r = 0; r < 30; r++) {
            for (int i = 0; i < 1024; i++) nest.b(0x02, 0x40);
            for (int i = 0; i < 1024; i++) nest.b(0x0b);
        }
        small("30 x 1024 nested", withBody(nest.b(0x0b)));
        var bt = new Out().u(0).b(0x41, 0).b(0x0e).u(150_000);
        for (int i = 0; i < 150_000; i++) bt.u(0);
        small("br_table 150k labels", withBody(bt.u(0).b(0x0b)));
    }

    @Test
    void manyElementsAndGlobals() {
        int g = 30_000;
        var gl = new Out().u(g);
        for (int i = 0; i < g; i++) gl.b(0x7f, 0, 0x41, 0, 0x0b);
        small(g + " globals", header().section(6, gl).done());
        var el = new Out().u(1).u(0).b(0x41, 0, 0x0b).u(150_000);
        for (int i = 0; i < 150_000; i++) el.u(0);
        small("150k elements", header().section(1, new Out().u(1).b(0x60, 0, 0)).section(3, new Out().u(1).u(0))
            .section(4, new Out().u(1).b(0x70).b(1).u(1).u(1)).section(9, el).section(10, new Out().u(1).u(2).u(0).b(0x0b)).done());
    }

    @Test
    void manyImportsWithLongNames() {
        var imp = new Out().u(1000);
        String longName = "x".repeat(100);
        for (int i = 0; i < 1000; i++) imp.name(longName + i).name(longName).b(0).u(0);
        small("1000 imports", header().section(1, new Out().u(1).b(0x60, 0, 0)).section(2, imp).done());
    }
}
