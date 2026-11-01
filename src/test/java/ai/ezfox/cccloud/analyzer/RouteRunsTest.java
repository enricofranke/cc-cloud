package ai.ezfox.cccloud.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.ezfox.cccloud.analyzer.RouteRuns.Run;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteRunsTest {
    @Test
    void repeatedHopsBecomeOneRun() {
        assertEquals(List.of(new Run("mainframe", 1), new Run("relay", 4), new Run("uplink", 1)),
            RouteRuns.of(List.of("mainframe", "relay", "relay", "relay", "relay", "uplink")));
    }

    @Test
    void mixedLongRouteLosesItsMiddle() {
        assertEquals(List.of(new Run("a", 1), new Run("b", 1), new Run(null, 0), new Run("b", 1), new Run("c", 1)),
            RouteRuns.of(List.of("a", "b", "a", "b", "a", "b", "c")));
    }
}
