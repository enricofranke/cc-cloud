package ai.ezfox.cccloud.code.lua;

import org.jspecify.annotations.Nullable;

/**
 * Is the Cobalt on the class path one Cloud Scripts can use? With CC: Tweaked installed, NeoForge picks one Cobalt for
 * both mods (the newest of the jar-in-jar copies); a later CC: Tweaked may ship a Cobalt whose API moved. This runs a
 * tiny script through every Cobalt feature the engine relies on (interrupt hook with suspend, coroutines, the
 * libraries) and says why not, so the mod switches Cloud Scripts off instead of failing later.
 */
public final class CobaltCheck {
    private static volatile @Nullable String result;
    private static volatile boolean done;

    private CobaltCheck() {
    }

    /** Null if Cobalt works, else what is wrong. */
    public static @Nullable String problem() {
        if (done) return result;
        String r;
        try {
            r = run();
        } catch (LinkageError | RuntimeException | org.squiddev.cobalt.LuaError | org.squiddev.cobalt.compiler.CompileException e) {
            r = "Cobalt is missing or not compatible: " + e;
        }
        result = r;
        done = true;
        return r;
    }

    /** Where the Cobalt classes come from (for the log). */
    public static String origin() {
        try {
            var cls = Class.forName("org.squiddev.cobalt.LuaState");
            var source = cls.getProtectionDomain().getCodeSource();
            String version = cls.getPackage() == null ? null : cls.getPackage().getImplementationVersion();
            return (source == null ? "?" : String.valueOf(source.getLocation())) + (version == null ? "" : " (" + version + ")")
                + (cls.getModule().isNamed() ? " module " + cls.getModule().getName() : "");
        } catch (ClassNotFoundException | LinkageError e) {
            return "not found";
        }
    }

    private static @Nullable String run() throws org.squiddev.cobalt.LuaError, org.squiddev.cobalt.compiler.CompileException {
        var calls = new int[1];
        var state = org.squiddev.cobalt.LuaState.builder().interruptHandler(() -> {
            calls[0]++;
            return calls[0] == 1 ? org.squiddev.cobalt.interrupt.InterruptAction.SUSPEND : org.squiddev.cobalt.interrupt.InterruptAction.CONTINUE;
        }).build();
        org.squiddev.cobalt.lib.CoreLibraries.standardGlobals(state);
        var fn = org.squiddev.cobalt.compiler.LoadState.load(state, new java.io.ByteArrayInputStream(
            "local co = coroutine.wrap(function(a) local b = coroutine.yield(a + 1) return b * 2 end) local x = co(1) return co(x) + #string.rep('a', 3)"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), "=check", state.globals());
        var thread = new org.squiddev.cobalt.LuaThread(state, fn);
        state.interrupt();
        var r = org.squiddev.cobalt.LuaThread.run(thread, org.squiddev.cobalt.Constants.NONE);
        if (r != null) return "Cobalt did not suspend on an interrupt";
        r = org.squiddev.cobalt.LuaThread.run(state.getCurrentThread(), org.squiddev.cobalt.Constants.NONE);
        if (r == null || r.first().toInteger() != 7) return "Cobalt gave a wrong result: " + (r == null ? "nothing" : r.first());
        return null;
    }
}
