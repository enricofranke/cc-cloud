package ai.ezfox.cccloud.icons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * One run of the server-side icon renderer over the registered items (docs/SPEC.md "Item icons (as implemented)"):
 * every item the store {@linkplain IconStore#wantsServerRender wants} a server render for (nothing stored, or an
 * outdated server render) is drawn by {@link ServerIconRenderer} and stored as {@link IconStore.Source#SERVER}. Client
 * renders are never touched. Runs on one low-priority background thread; it only reads the item id snapshot taken on
 * the server thread and the store (which is thread-safe), and stops between two items when cancelled.
 */
public final class ServerIconPass implements Runnable {
    public enum State { WAITING, RUNNING, DONE, CANCELLED }

    /** Progress for {@code /cccloud icons status} and the log. */
    public record Progress(State state, int total, int done, int flat, int cube, int skipped, long millis) {
    }

    private final Map<String, String> items;
    private final IconStore store;
    private final Function<String, byte @Nullable []> read;
    private final boolean force;
    private volatile boolean cancelled;
    private volatile State state = State.WAITING;
    private volatile int total, done, flat, cube, skipped;
    private volatile long startNanos, endNanos;
    private final Map<String, Integer> reasons = new HashMap<>();

    /**
     * @param items id → version of every registered item (a snapshot)
     * @param read  resource reader (mod jars, vanilla if available)
     * @param force also re-render current server renders ({@code /cccloud icons rebuild})
     */
    public ServerIconPass(Map<String, String> items, IconStore store, Function<String, byte @Nullable []> read, boolean force) {
        this.items = items;
        this.store = store;
        this.read = read;
        this.force = force;
    }

    public void cancel() {
        cancelled = true;
    }

    @Override
    public void run() {
        startNanos = System.nanoTime();
        state = State.RUNNING;
        try {
            var todo = new ArrayList<Map.Entry<String, String>>();
            for (var e : items.entrySet()) {
                if (store.wantsServerRender(e.getKey(), e.getValue()) || (force && store.source(e.getKey()) == IconStore.Source.SERVER)) todo.add(e);
            }
            total = todo.size();
            var renderer = new ServerIconRenderer(read);
            var deflater = new java.util.zip.Deflater(6); // within 1 % of level 9 for icons, at less than half the CPU
            try {
                renderAll(todo, renderer, deflater);
            } finally {
                deflater.end();
            }
            if (state == State.RUNNING) state = State.DONE;
        } finally {
            endNanos = System.nanoTime();
            if (state == State.RUNNING) state = State.CANCELLED; // an exception ended it
        }
    }

    private void renderAll(List<Map.Entry<String, String>> todo, ServerIconRenderer renderer, java.util.zip.Deflater deflater) {
        for (var e : todo) {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                state = State.CANCELLED;
                return;
            }
            String id = e.getKey();
            int colon = id.indexOf(':');
            var result = renderer.render(id.substring(0, colon), id.substring(colon + 1));
            byte[] png = result.png(deflater);
            if (png != null && store.putServer(id, e.getValue(), png)) {
                if (result.kind() == ServerIconRenderer.Kind.FLAT) flat++;
                else cube++;
            } else {
                skipped++;
                if (png == null) countReason(result.detail());
            }
            done++;
        }
    }

    private synchronized void countReason(String detail) {
        if (reasons.size() < 4096 || reasons.containsKey(detail)) reasons.merge(detail, 1, Integer::sum);
    }

    public Progress progress() {
        long end = state == State.RUNNING || state == State.WAITING ? System.nanoTime() : endNanos;
        return new Progress(state, total, done, flat, cube, skipped, startNanos == 0 ? 0 : (end - startNanos) / 1_000_000);
    }

    /** The most common reasons items were left for game clients, most frequent first. */
    public synchronized List<Map.Entry<String, Integer>> topReasons(int n) {
        return reasons.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(n)
            .map(e -> Map.entry(e.getKey(), e.getValue())).toList();
    }
}
