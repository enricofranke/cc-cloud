package ai.ezfox.cccloud.code.lua;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Prototype;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.function.LuaInterpretedFunction;

/**
 * A rough count of the bytes a script's values take: everything reachable from its roots (globals, string metatable,
 * tasks, handlers), with JVM-like sizes (object headers, array slots, string bytes). It is an estimate: Cobalt has no
 * allocation accounting of its own, shared constants count once, and garbage the JVM has not collected yet does not
 * count. It stops as soon as it passes the limit.
 */
final class LuaHeap {
    static final int MAX_OBJECTS = 2_000_000;

    private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
    private final ArrayDeque<Object> queue = new ArrayDeque<>();
    private final LuaState state;
    private long bytes;

    private LuaHeap(LuaState state) {
        this.state = state;
    }

    static long estimate(LuaState state, Iterable<?> roots, long limit) {
        var heap = new LuaHeap(state);
        heap.add(state.globals());
        if (state.stringMetatable != null) heap.add(state.stringMetatable);
        for (var root : roots) heap.add(root);
        try {
            while (!heap.queue.isEmpty()) {
                if (heap.bytes > limit || heap.seen.size() > MAX_OBJECTS) return Math.max(heap.bytes, limit + 1);
                heap.visit(heap.queue.poll());
            }
        } catch (LuaError e) {
            return heap.bytes; // a table changed while we walked it: what we have is good enough
        }
        return heap.bytes;
    }

    private void add(Object o) {
        if (o == null || o == Constants.NIL || o instanceof Boolean) return;
        if (seen.put(o, Boolean.TRUE) == null) queue.add(o);
    }

    private void visit(Object o) throws LuaError {
        switch (o) {
            case LuaString s -> bytes += 40 + s.length();
            case LuaTable t -> {
                bytes += 64;
                LuaValue k = Constants.NIL;
                while (true) {
                    Varargs next = t.next(k);
                    k = next.first();
                    if (k.isNil()) break;
                    bytes += 24;
                    value(k);
                    value(next.arg(2));
                }
                var meta = t.getMetatable(state);
                if (meta != null) add(meta);
            }
            case LuaInterpretedFunction f -> {
                bytes += 32 + 16L * f.upvalues.length;
                for (var u : f.upvalues) if (u != null) value(u.getValue());
                add(f.getPrototype());
            }
            case Prototype p -> {
                bytes += 64 + 4L * p.code.length + 8L * p.constants.length + 4L * p.lineInfo.length;
                for (var c : p.constants) value(c);
                for (var child : p.children) add(child);
            }
            case LuaThread thread -> {
                bytes += 128;
                var ds = thread.getDebugState();
                for (int level = 0; ; level++) {
                    DebugFrame frame = ds.getFrame(level);
                    if (frame == null) break;
                    bytes += 64;
                    if (frame.func != null) add(frame.func);
                    if (frame.stack != null) {
                        bytes += 16 + 8L * frame.stack.length;
                        for (var v : frame.stack) value(v);
                    }
                    varargs(frame.varargs);
                    varargs(frame.extras);
                }
            }
            case LuaConvert.Function handle -> add(handle.function());
            default -> bytes += 16; // Java functions, userdata: small and not the script's
        }
    }

    private void varargs(Varargs v) {
        if (v == null) return;
        int n = Math.min(v.count(), 4096);
        bytes += 8L * n;
        for (int i = 1; i <= n; i++) value(v.arg(i));
    }

    private void value(LuaValue v) {
        if (v == null) return;
        switch (v.type()) {
            case Constants.TSTRING, Constants.TTABLE, Constants.TFUNCTION, Constants.TTHREAD -> add(v);
            case Constants.TNUMBER -> bytes += 16;
            default -> {
            }
        }
    }
}
