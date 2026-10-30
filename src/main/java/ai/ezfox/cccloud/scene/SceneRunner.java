package ai.ezfox.cccloud.scene;

import ai.ezfox.cccloud.rules.Rule;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Runs the steps of one scene (docs/SPEC.md v0.10). Pure Java with an injected {@link Env}, so the order, the delays
 * and the per-step permission checks are unit tested. The game calls {@link #advance} on the server thread every tick
 * while runs are active: everything up to the next delay runs in that tick, a delay parks the run until its tick has
 * come (no blocking, no threads).
 * <p>
 * Every action step is checked <i>when it runs</i> ({@link Env#check}); a refused or failed step does not stop the
 * scene. When the mainframe becomes unavailable the remaining steps fail with {@code offline}.
 */
public final class SceneRunner {
    /** What a run reads and does. All methods run on the server thread. */
    public interface Env {
        /** The mainframe is loaded, online and enabled. */
        boolean available();

        /** Null if the caller may run this step now, else the error (e.g. {@code not_allowed}, {@code pin_required}). */
        @Nullable String check(Rule.Action action);

        /** Runs the step's action. Null on success, else an error code. */
        @Nullable String run(Rule.Action action);
    }

    /** One running (or finished) scene run. Results: null = not run yet, {@code "ok"} or an error code. */
    public static final class Run {
        final Scene scene;
        final String by;
        final String source;
        final long started;
        final @Nullable String[] results;
        int next;
        long wakeTick = Long.MIN_VALUE;
        boolean waiting;
        long finished;

        public Run(Scene scene, String by, String source, long started) {
            this.scene = scene;
            this.by = by;
            this.source = source;
            this.started = started;
            this.results = new String[scene.steps().size()];
        }

        public Scene scene() {
            return scene;
        }

        public String by() {
            return by;
        }

        public String source() {
            return source;
        }

        public long started() {
            return started;
        }

        /** 0 while running. */
        public long finished() {
            return finished;
        }

        public boolean done() {
            return finished != 0;
        }

        public @Nullable String[] results() {
            return Arrays.copyOf(results, results.length);
        }

        /** The first error of the run, or null (all ok so far). */
        public @Nullable String firstError() {
            for (var r : results) if (r != null && !r.equals("ok")) return r;
            return null;
        }
    }

    private SceneRunner() {
    }

    /** Ends a run early: every step that did not run yet fails with {@code error}. True if the run was still going. */
    public static boolean abort(Run run, long now, String error) {
        if (run.done()) return false;
        for (int i = run.next; i < run.results.length; i++) run.results[i] = error;
        run.next = run.results.length;
        run.finished = Math.max(now, run.started + 1);
        return true;
    }

    /**
     * Runs the steps that are due at {@code tick}.
     *
     * @return true if anything changed (a step ran, a delay ended, the run finished)
     */
    public static boolean advance(Run run, long tick, long now, Env env) {
        if (run.done()) return false;
        boolean changed = false;
        var steps = run.scene.steps();
        while (run.next < steps.size()) {
            var step = steps.get(run.next);
            if (step.isDelay()) {
                if (!run.waiting) {
                    run.waiting = true;
                    run.wakeTick = tick + Math.max(1, step.delayTicks());
                    return changed;
                }
                if (tick < run.wakeTick) return changed;
                run.waiting = false;
                run.results[run.next++] = "ok";
                changed = true;
                continue;
            }
            if (!env.available()) {
                for (int i = run.next; i < steps.size(); i++) run.results[i] = "offline";
                run.next = steps.size();
                changed = true;
                break;
            }
            String error;
            try {
                error = env.check(step.action());
                if (error == null) error = env.run(step.action());
            } catch (RuntimeException e) {
                error = "internal_error";
            }
            run.results[run.next++] = error == null ? "ok" : error;
            changed = true;
        }
        run.finished = Math.max(now, run.started + 1);
        return true;
    }
}
