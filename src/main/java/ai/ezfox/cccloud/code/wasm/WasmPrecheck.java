package ai.ezfox.cccloud.code.wasm;

/**
 * Reads a module's binary structure before Endive parses it, so a few hostile bytes cannot make the parser allocate:
 * Endive sizes its arrays from the counts and lengths the module declares (a 25-byte module can declare a 2 GB data
 * segment or 2^31 types), so every such number is checked here against the bytes that are really there. Allocates
 * nothing in proportion to declared sizes; runs in time linear in the module.
 *
 * <ul>
 *   <li>Magic and version; every section's length within the file, known section ids only, in order.</li>
 *   <li>Every vector count at most the bytes left in its section (each element takes at least one byte), every byte
 *   length (names, custom sections, data segments, function bodies) within the bytes left.</li>
 *   <li>LEB128 numbers with their length limit (5 bytes for 32 bits) and no overflow.</li>
 *   <li>Function bodies: the sum of their locals at most {@link WasmCheck#MAX_LOCALS}; every instruction known
 *   (WebAssembly 2.0 without SIMD, threads, exceptions and garbage collection), its immediates within the body,
 *   at most {@link WasmCheck#MAX_TABLE} element entries in all element segments together (Endive keeps an object per
 *   entry),
 *   {@code br_table} label counts within the body, blocks nested at most {@link #MAX_NESTING} deep, the body ending
 *   exactly at its {@code end}.</li>
 * </ul>
 * Anything else (types, indices, validation) is Endive's job once this passed.
 */
final class WasmPrecheck {
    /** Blocks nested inside each other in one function (Endive keeps a frame per level while it reads them). */
    static final int MAX_NESTING = 256;

    /** A structural problem, in words for the editor. */
    static final class Bad extends Exception {
        Bad(String message) {
            super(message, null, false, false);
        }
    }

    private final byte[] b;
    private int pos;

    private WasmPrecheck(byte[] bytes) {
        this.b = bytes;
    }

    /** Throws {@link Bad} when the module's structure is not plain, bounded WebAssembly. */
    static void check(byte[] bytes) throws Bad {
        new WasmPrecheck(bytes).module();
    }

    // ---- reading -------------------------------------------------------------------------------------------------------

    private int u8(int end) throws Bad {
        if (pos >= end) throw new Bad("unexpected end at byte " + pos);
        return b[pos++] & 0xff;
    }

    /** An unsigned LEB128 of at most {@code bits} bits. */
    private long uleb(int end, int bits) throws Bad {
        long result = 0;
        int shift = 0;
        int maxBytes = (bits + 6) / 7;
        for (int i = 0; i < maxBytes; i++) {
            int v = u8(end);
            result |= (long) (v & 0x7f) << shift;
            if ((v & 0x80) == 0) {
                if (bits < 64 && (result >>> bits) != 0) throw new Bad("number too large at byte " + (pos - 1));
                return result;
            }
            shift += 7;
        }
        throw new Bad("number too long at byte " + pos);
    }

    private long u32(int end) throws Bad {
        return uleb(end, 32);
    }

    /** A signed LEB128 of at most {@code bits} bits (only skipped: its value is not needed here). */
    private void sleb(int end, int bits) throws Bad {
        int maxBytes = (bits + 6) / 7;
        for (int i = 0; i < maxBytes; i++) {
            if ((u8(end) & 0x80) == 0) return;
        }
        throw new Bad("number too long at byte " + pos);
    }

    /** A vector count: each element takes at least {@code minBytes} bytes of what is left before {@code end}. */
    private long count(int end, int minBytes, String what) throws Bad {
        long n = u32(end);
        if (n * minBytes > end - pos) throw new Bad(what + ": " + n + " declared, more than the bytes that follow");
        return n;
    }

    /** A byte length that must fit before {@code end}; returns where it ends. */
    private int bytes(int end, String what) throws Bad {
        long n = u32(end);
        if (n > end - pos) throw new Bad(what + ": " + n + " bytes declared, only " + (end - pos) + " follow");
        int stop = pos + (int) n;
        return stop;
    }

    private void skipTo(int stop) {
        pos = stop;
    }

    private void name(int end) throws Bad {
        skipTo(bytes(end, "name"));
    }

    // ---- module --------------------------------------------------------------------------------------------------------

    private void module() throws Bad {
        int end = b.length;
        if (end < 8 || b[0] != 0 || b[1] != 'a' || b[2] != 's' || b[3] != 'm') throw new Bad("not a WebAssembly module");
        if (b[4] != 1 || b[5] != 0 || b[6] != 0 || b[7] != 0) throw new Bad("unsupported WebAssembly version (only version 1)");
        pos = 8;
        int last = 0;
        while (pos < end) {
            int id = u8(end);
            int stop = bytes(end, "section " + id);
            if (id != 0) {
                int order = order(id);
                if (order <= last) throw new Bad("section " + id + " out of order or twice");
                last = order;
            }
            section(id, stop);
            if (pos != stop) throw new Bad("section " + id + " does not end where its length says");
        }
    }

    /** The order of sections in a module (data count 12 comes before code 10). */
    private static int order(int id) throws Bad {
        return switch (id) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 5;
            case 13 -> 6; // tags (after memory, before globals); refused later by the instruction set
            case 6 -> 7;
            case 7 -> 8;
            case 8 -> 9;
            case 9 -> 10;
            case 12 -> 11;
            case 10 -> 12;
            case 11 -> 13;
            default -> throw new Bad("unknown section " + id);
        };
    }

    private void section(int id, int end) throws Bad {
        switch (id) {
            case 0 -> {
                name(end);
                skipTo(end); // a custom section's content is not read
            }
            case 1 -> {
                long n = count(end, 3, "types");
                for (long i = 0; i < n; i++) {
                    if (u8(end) != 0x60) throw new Bad("only function types are supported (no garbage-collected types)");
                    long params = count(end, 1, "parameters");
                    for (long k = 0; k < params; k++) valtype(end);
                    long results = count(end, 1, "results");
                    for (long k = 0; k < results; k++) valtype(end);
                }
            }
            case 2 -> {
                long n = count(end, 4, "imports");
                for (long i = 0; i < n; i++) {
                    name(end);
                    name(end);
                    switch (u8(end)) {
                        case 0x00 -> u32(end);
                        case 0x01 -> tabletype(end);
                        case 0x02 -> limits(end);
                        case 0x03 -> globaltype(end);
                        default -> throw new Bad("unsupported import kind");
                    }
                }
            }
            case 3 -> {
                long n = count(end, 1, "functions");
                for (long i = 0; i < n; i++) u32(end);
            }
            case 4 -> {
                long n = count(end, 2, "tables");
                for (long i = 0; i < n; i++) tabletype(end);
            }
            case 5 -> {
                long n = count(end, 2, "memories");
                for (long i = 0; i < n; i++) limits(end);
            }
            case 6 -> {
                long n = count(end, 3, "globals");
                for (long i = 0; i < n; i++) {
                    globaltype(end);
                    expr(end);
                }
            }
            case 7 -> {
                long n = count(end, 3, "exports");
                for (long i = 0; i < n; i++) {
                    name(end);
                    int kind = u8(end);
                    if (kind > 3) throw new Bad("unsupported export kind");
                    u32(end);
                }
            }
            case 8 -> u32(end);
            case 9 -> elements(end);
            case 10 -> {
                long n = count(end, 2, "function bodies");
                for (long i = 0; i < n; i++) {
                    int stop = bytes(end, "function body");
                    body(stop, i);
                    if (pos != stop) throw new Bad("function " + i + " does not end where its length says");
                }
            }
            case 11 -> {
                long n = count(end, 2, "data segments");
                for (long i = 0; i < n; i++) {
                    long flag = u32(end);
                    if (flag == 2) u32(end);
                    else if (flag != 0 && flag != 1) throw new Bad("unsupported data segment kind");
                    if (flag != 1) expr(end);
                    skipTo(bytes(end, "data segment"));
                }
            }
            case 12 -> u32(end);
            case 13 -> throw new Bad("uses exceptions (tags), which CC: Cloud does not run");
            default -> throw new Bad("unknown section " + id);
        }
    }

    private void valtype(int end) throws Bad {
        int t = u8(end);
        switch (t) {
            case 0x7f, 0x7e, 0x7d, 0x7c, 0x70, 0x6f -> {
            }
            case 0x7b -> throw new Bad("uses SIMD (v128), which CC: Cloud does not run");
            default -> throw new Bad("unsupported value type 0x" + Integer.toHexString(t) + " (no garbage-collected references)");
        }
    }

    private void reftype(int end) throws Bad {
        int t = u8(end);
        if (t != 0x70 && t != 0x6f) throw new Bad("unsupported reference type 0x" + Integer.toHexString(t));
    }

    private void limits(int end) throws Bad {
        int flags = u8(end);
        if (flags > 3) throw new Bad("unsupported memory or table limits (no 64-bit memory)");
        u32(end);
        if ((flags & 1) != 0) u32(end);
    }

    private void tabletype(int end) throws Bad {
        reftype(end);
        limits(end);
    }

    private void globaltype(int end) throws Bad {
        valtype(end);
        if (u8(end) > 1) throw new Bad("bad global mutability");
    }

    private void elements(int end) throws Bad {
        long n = count(end, 2, "element segments");
        long total = 0;
        for (long i = 0; i < n; i++) {
            long flags = u32(end);
            if (flags > 7) throw new Bad("unsupported element segment kind");
            boolean passiveOrDeclared = (flags & 1) != 0;
            boolean explicitTable = (flags & 2) != 0;
            boolean exprs = (flags & 4) != 0;
            if (!passiveOrDeclared) {
                if (explicitTable) u32(end);
                expr(end);
            }
            if (passiveOrDeclared || explicitTable) {
                if (exprs) reftype(end);
                else if (u8(end) != 0x00) throw new Bad("unsupported element kind");
            }
            long items = count(end, 1, "elements");
            total += items;
            if (total > WasmCheck.MAX_TABLE) throw new Bad("more than " + WasmCheck.MAX_TABLE + " table elements");
            for (long k = 0; k < items; k++) {
                if (exprs) expr(end);
                else u32(end);
            }
        }
    }

    /** A constant expression: instructions up to its {@code end}. */
    private void expr(int end) throws Bad {
        instructions(end, true);
    }

    private void body(int end, long index) throws Bad {
        long decls = count(end, 2, "local declarations");
        long total = 0;
        for (long i = 0; i < decls; i++) {
            long n = u32(end);
            total += n;
            if (total > WasmCheck.MAX_LOCALS) throw new Bad("function " + index + " has more than " + WasmCheck.MAX_LOCALS + " locals");
            valtype(end);
        }
        instructions(end, false);
        if (pos != end) throw new Bad("function " + index + " has bytes after its end");
    }

    /**
     * Instructions up to the {@code end} that closes the outermost level ({@code constant}: a constant expression, which
     * is not nested).
     */
    private void instructions(int end, boolean constant) throws Bad {
        int depth = 0;
        while (true) {
            int op = u8(end);
            switch (op) {
                case 0x0b -> { // end
                    if (depth == 0) return;
                    depth--;
                }
                case 0x02, 0x03, 0x04 -> { // block, loop, if
                    blocktype(end);
                    if (++depth > MAX_NESTING) throw new Bad("blocks nested deeper than " + MAX_NESTING);
                }
                case 0x05, 0x00, 0x01, 0x0f, 0x1a, 0x1b, 0xd1 -> {
                    // else, unreachable, nop, return, drop, select, ref.is_null
                }
                case 0x0c, 0x0d -> u32(end); // br, br_if
                case 0x0e -> { // br_table
                    long labels = count(end, 1, "br_table labels");
                    for (long i = 0; i < labels; i++) u32(end);
                    u32(end);
                }
                case 0x10, 0x12 -> u32(end); // call, return_call
                case 0x11, 0x13 -> { // call_indirect, return_call_indirect
                    u32(end);
                    u32(end);
                }
                case 0x1c -> { // select t*
                    long n = count(end, 1, "select types");
                    for (long i = 0; i < n; i++) valtype(end);
                }
                case 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26 -> u32(end); // locals, globals, table.get/set
                case 0x3f, 0x40 -> { // memory.size, memory.grow
                    if (u8(end) != 0) throw new Bad("only one memory is supported");
                }
                case 0x41 -> sleb(end, 32); // i32.const
                case 0x42 -> sleb(end, 64); // i64.const
                case 0x43 -> fixed(end, 4); // f32.const
                case 0x44 -> fixed(end, 8); // f64.const
                case 0xd0 -> reftype(end); // ref.null
                case 0xd2 -> u32(end); // ref.func
                case 0xfc -> prefixed(end);
                case 0xfb -> throw new Bad("uses garbage-collection instructions, which CC: Cloud does not run");
                case 0xfd -> throw new Bad("uses SIMD instructions, which CC: Cloud does not run");
                case 0xfe -> throw new Bad("uses atomic instructions (threads), which CC: Cloud does not run");
                case 0x06, 0x07, 0x08, 0x09, 0x0a, 0x18, 0x19, 0x1f -> throw new Bad("uses exceptions, which CC: Cloud does not run");
                default -> {
                    if (op >= 0x28 && op <= 0x3e) { // loads and stores
                        memarg(end);
                    } else if (op >= 0x45 && op <= 0xc4) {
                        // numeric instructions without immediates (comparisons, arithmetic, conversions, sign extension)
                    } else {
                        throw new Bad("unknown instruction 0x" + Integer.toHexString(op) + " at byte " + (pos - 1));
                    }
                }
            }
            if (constant && depth > 0) throw new Bad("blocks in a constant expression");
        }
    }

    private void blocktype(int end) throws Bad {
        int t = b[Math.min(pos, b.length - 1)] & 0xff;
        if (pos >= end) throw new Bad("unexpected end at byte " + pos);
        if (t == 0x40) {
            pos++;
        } else if (t == 0x7f || t == 0x7e || t == 0x7d || t == 0x7c || t == 0x70 || t == 0x6f) {
            pos++;
        } else if (t == 0x7b) {
            throw new Bad("uses SIMD (v128), which CC: Cloud does not run");
        } else if ((t & 0x40) != 0 && (t & 0x80) == 0) {
            throw new Bad("unsupported block type 0x" + Integer.toHexString(t));
        } else {
            sleb(end, 33); // a type index (multi-value blocks)
        }
    }

    private void memarg(int end) throws Bad {
        long align = u32(end);
        if (align >= 64) throw new Bad("unsupported memory access (multiple memories)");
        u32(end);
    }

    private void fixed(int end, int n) throws Bad {
        if (end - pos < n) throw new Bad("unexpected end at byte " + pos);
        pos += n;
    }

    /** The 0xFC instructions: saturating conversions, bulk memory, table operations. */
    private void prefixed(int end) throws Bad {
        long sub = u32(end);
        switch ((int) Math.min(sub, Integer.MAX_VALUE)) {
            case 0, 1, 2, 3, 4, 5, 6, 7 -> {
            } // i32/i64.trunc_sat
            case 8 -> { // memory.init
                u32(end);
                if (u8(end) != 0) throw new Bad("only one memory is supported");
            }
            case 9 -> u32(end); // data.drop
            case 10 -> { // memory.copy
                if (u8(end) != 0 || u8(end) != 0) throw new Bad("only one memory is supported");
            }
            case 11 -> { // memory.fill
                if (u8(end) != 0) throw new Bad("only one memory is supported");
            }
            case 12, 14 -> { // table.init, table.copy
                u32(end);
                u32(end);
            }
            case 13, 15, 16, 17 -> u32(end); // elem.drop, table.grow, table.size, table.fill
            default -> throw new Bad("unknown instruction 0xfc " + sub);
        }
    }
}
