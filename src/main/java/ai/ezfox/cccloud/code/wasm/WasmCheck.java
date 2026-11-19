package ai.ezfox.cccloud.code.wasm;

import java.util.ArrayList;
import java.util.Map;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.FunctionImport;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.MemoryLimits;

/**
 * Reads a module and checks it against what CC: Cloud runs: only the host functions it offers (no WASI, no other
 * imports, no imported memories, tables or globals), plain WebAssembly 2.0 (no garbage-collected types, no threads or
 * shared memory), a memory within the script's cap, small tables, a bounded number of locals per function (each call
 * frame costs host memory), and something to run ({@code main} or a start function). Runs when a script is saved and
 * again before every start.
 */
public final class WasmCheck {
    /** Locals (parameters included) one function may have. */
    public static final int MAX_LOCALS = 1000;
    /** Elements of one table. */
    public static final long MAX_TABLE = 10_000;
    /** Functions (imports included). */
    public static final int MAX_FUNCTIONS = 20_000;
    public static final String MAIN = "main";
    /** No module is read beyond this size, whatever the config says (the config allows at most 160 KB). */
    public static final int MAX_MODULE = 160 * 1024;

    /** A module that passed, with its parts the engine needs. */
    public record Checked(WasmModule module, int memoryPages, int maxPages, boolean hasMain, boolean hasStart) {
    }

    /** What is wrong with a module, in words for the editor. */
    public static final class Refused extends Exception {
        public Refused(String message) {
            super(message, null, false, false);
        }
    }

    private WasmCheck() {
    }

    /**
     * Parses and checks a module. {@code imports}: module name → function name → expected type (the only imports
     * allowed); {@code memoryBytes}: the script's memory cap.
     */
    public static Checked check(byte[] bytes, Map<String, Map<String, FunctionType>> imports, long memoryBytes) throws Refused {
        // Endive sizes its arrays from what a module declares: its structure is read and bounded first (WasmPrecheck)
        if (bytes.length > MAX_MODULE) throw new Refused("the module is larger than " + MAX_MODULE / 1024 + " KB");
        try {
            WasmPrecheck.check(bytes);
        } catch (WasmPrecheck.Bad e) {
            throw new Refused("not a valid WebAssembly module: " + e.getMessage());
        }
        WasmModule module;
        try {
            module = Parser.parse(bytes);
        } catch (RuntimeException | StackOverflowError e) {
            throw new Refused("not a valid WebAssembly module: " + message(e));
        }
        var problems = new ArrayList<String>();

        // imports: only the host functions, with the right types
        var importSection = module.importSection();
        for (int i = 0; i < importSection.importCount(); i++) {
            var imp = importSection.getImport(i);
            String what = imp.module() + "." + imp.name();
            if (imp.importType() != ExternalType.FUNCTION || !(imp instanceof FunctionImport function)) {
                problems.add("imports " + what + " (" + imp.importType().name().toLowerCase(java.util.Locale.ROOT) + "): only host functions can be imported");
                continue;
            }
            var names = imports.get(imp.module());
            FunctionType expected = names == null ? null : names.get(imp.name());
            if (expected == null) {
                problems.add("imports " + what + ", which CC: Cloud does not offer"
                    + (imp.module().startsWith("wasi") ? " (WASI is not available: build without WASI)" : ""));
                continue;
            }
            FunctionType actual = typeOf(module, function.typeIndex());
            if (actual == null || !actual.equals(expected)) problems.add("imports " + what + " with the wrong signature (expected " + expected + ")");
        }

        // types: functions only (no structs or arrays of the garbage-collection proposal)
        var types = module.typeSection();
        for (int i = 0; i < types.subTypeCount(); i++) {
            var sub = types.getSubType(i);
            if (sub == null || sub.compType() == null || sub.compType().funcType() == null) {
                problems.add("uses garbage-collected types, which CC: Cloud does not run");
                break;
            }
        }

        int functions = importSection.count(ExternalType.FUNCTION) + module.functionSection().functionCount();
        if (functions > MAX_FUNCTIONS) problems.add("has " + functions + " functions (at most " + MAX_FUNCTIONS + ")");

        // memory: at most one, not shared, within the cap
        int capPages = (int) Math.max(0, Math.min(MemoryLimits.MAX_PAGES, memoryBytes / run.endive.runtime.Memory.PAGE_SIZE));
        int pages = 0;
        int maxPages = 0;
        var memories = module.memorySection();
        if (memories.isPresent() && memories.get().memoryCount() > 0) {
            if (memories.get().memoryCount() > 1) problems.add("has more than one memory");
            var limits = memories.get().getMemory(0).limits();
            if (limits.shared()) problems.add("uses shared memory (threads), which CC: Cloud does not run");
            pages = limits.initialPages();
            maxPages = Math.min(limits.maximumPages(), capPages);
            if (pages > capPages) {
                problems.add("needs " + pages * 64 + " KB of memory at start, scripts may use " + capPages * 64 + " KB");
            }
        }

        // tables: bounded
        var tables = module.tableSection();
        for (int i = 0; i < tables.tableCount(); i++) {
            var limits = tables.getTable(i).limits();
            if (limits.min() > MAX_TABLE || limits.max() > MAX_TABLE) {
                problems.add("has a table that can grow beyond " + MAX_TABLE + " entries (give it a maximum)");
                break;
            }
        }

        // code: locals and instructions
        var bodies = module.codeSection().functionBodies();
        outer:
        for (int f = 0; f < bodies.length; f++) {
            var body = bodies[f];
            int params = 0;
            var type = typeOf(module, module.functionSection().getFunctionType(f));
            if (type != null) params = type.params().size();
            if (body.localTypes().size() + params > MAX_LOCALS) {
                problems.add("function " + f + " has more than " + MAX_LOCALS + " locals");
                break;
            }
            for (var instruction : body.instructions()) {
                int op = instruction.opcode().opcode();
                if ((op & 0xFF00) == 0xFB00) {
                    problems.add("uses garbage-collection instructions, which CC: Cloud does not run");
                    break outer;
                }
                if ((op & 0xFF00) == 0xFE00) {
                    problems.add("uses atomic instructions (threads), which CC: Cloud does not run");
                    break outer;
                }
            }
        }

        // something to run
        boolean hasMain = false;
        var exports = module.exportSection();
        for (int i = 0; i < exports.exportCount(); i++) {
            var e = exports.getExport(i);
            if (e.name().equals(MAIN) && e.exportType() == ExternalType.FUNCTION) {
                var type = typeOfFunction(module, e.index());
                if (type == null || !type.params().isEmpty()) problems.add("exports main with parameters (main takes none)");
                hasMain = true;
            }
        }
        boolean hasStart = module.startSection().isPresent();
        if (!hasMain && !hasStart) problems.add("has nothing to run: export a function main()");

        if (!problems.isEmpty()) throw new Refused(String.join("; ", problems.subList(0, Math.min(3, problems.size()))));
        return new Checked(module, pages, maxPages, hasMain, hasStart);
    }

    private static FunctionType typeOf(WasmModule module, int typeIndex) {
        try {
            return module.typeSection().getType(typeIndex);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The type of a function by its index in the function space (imports first). */
    static FunctionType typeOfFunction(WasmModule module, int index) {
        var imports = module.importSection();
        int imported = 0;
        for (int i = 0; i < imports.importCount(); i++) {
            var imp = imports.getImport(i);
            if (imp instanceof FunctionImport f) {
                if (imported == index) return typeOf(module, f.typeIndex());
                imported++;
            }
        }
        int local = index - imported;
        if (local < 0 || local >= module.functionSection().functionCount()) return null;
        return typeOf(module, module.functionSection().getFunctionType(local));
    }

    static String message(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) m = e.getClass().getSimpleName();
        return m.length() > 200 ? m.substring(0, 199) + "…" : m;
    }

}
