package ai.ezfox.cccloud.analyzer;

import java.util.ArrayList;
import java.util.List;

/** Shortens a route for one tooltip line: repeated hop kinds become one run, long routes lose their middle. */
public final class RouteRuns {
    private static final int MAX_RUNS = 5;

    /** {@code kind} is null for the "…" that stands for the left out middle. */
    public record Run(String kind, int count) {}

    private RouteRuns() {}

    public static List<Run> of(List<String> kinds) {
        var runs = new ArrayList<Run>();
        for (String kind : kinds) {
            int last = runs.size() - 1;
            if (last >= 0 && runs.get(last).kind().equals(kind)) runs.set(last, new Run(kind, runs.get(last).count() + 1));
            else runs.add(new Run(kind, 1));
        }
        if (runs.size() <= MAX_RUNS) return runs;
        var shortened = new ArrayList<>(runs.subList(0, 2));
        shortened.add(new Run(null, 0));
        shortened.addAll(runs.subList(runs.size() - 2, runs.size()));
        return shortened;
    }
}
