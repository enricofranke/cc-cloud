package ai.ezfox.cccloud.code;

import java.io.ByteArrayOutputStream;

/** Hand-assembled WebAssembly modules for the module checks (things no AssemblyScript build produces). */
final class WasmBytes {
    private WasmBytes() {
    }

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

    private static Out voidType() {
        return new Out().u(1).b(0x60, 0, 0);
    }

    private static Out oneFunction() {
        return new Out().u(1).u(0);
    }

    private static Out exportMain() {
        return new Out().u(1).name("main").b(0).u(0);
    }

    private static Out emptyBody() {
        return new Out().u(1).u(2).b(0, 0x0b);
    }

    static byte[] empty() {
        return header().done();
    }

    static byte[] mainOnly() {
        return header().section(1, voidType()).section(3, oneFunction()).section(7, exportMain()).section(10, emptyBody()).done();
    }

    static byte[] memory(int pages, boolean withMax, boolean shared) {
        var mem = new Out().u(1);
        if (shared) mem.b(0x03).u(pages).u(pages);
        else if (withMax) mem.b(0x01).u(pages).u(pages);
        else mem.b(0x00).u(pages);
        return header().section(1, voidType()).section(3, oneFunction()).section(5, mem).section(7, exportMain()).section(10, emptyBody()).done();
    }

    /** A funcref table; {@code max} negative = no maximum. */
    static byte[] table(int min, int max) {
        var table = new Out().u(1).b(0x70);
        if (max < 0) table.b(0x00).u(min);
        else table.b(0x01).u(min).u(max);
        return header().section(1, voidType()).section(3, oneFunction()).section(4, table).section(7, exportMain()).section(10, emptyBody()).done();
    }

    static byte[] importedMemory() {
        var imports = new Out().u(1).name("env").name("memory").b(0x02, 0x00).u(1);
        return header().section(1, voidType()).section(2, imports).section(3, oneFunction()).section(7, exportMain()).section(10, emptyBody()).done();
    }

    static byte[] locals(int count) {
        var body = new Out().u(1).u(count).b(0x7f).b(0x0b);
        var code = new Out().u(1).u(body.bytes.size());
        code.bytes.writeBytes(body.done());
        return header().section(1, voidType()).section(3, oneFunction()).section(7, exportMain()).section(10, code).done();
    }

    /** Type 0: a struct with one i32 field; type 1: the function type of main. */
    static byte[] structType() {
        var types = new Out().u(2).b(0x5f).u(1).b(0x7f, 0x00).b(0x60, 0, 0);
        var funcs = new Out().u(1).u(1);
        return header().section(1, types).section(3, funcs).section(7, exportMain()).section(10, emptyBody()).done();
    }
}
