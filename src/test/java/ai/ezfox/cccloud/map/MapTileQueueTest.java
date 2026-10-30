package ai.ezfox.cccloud.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Security review 2026-10-30: map tiles wait for the server thread in a queue that had no limit; each waiting tile holds
 * its sample buffers and outlives the HTTP timeout. More than {@link MapTileService#MAX_QUEUE} waiting tiles are refused
 * (the route answers 503), the same tile asked again joins the waiting job.
 */
class MapTileQueueTest {
    @AfterEach
    void clear() {
        MapTileService.clearForTest();
    }

    @Test
    void theQueueIsBounded() {
        MapTileService.clearForTest();
        for (int i = 0; i < MapTileService.MAX_QUEUE; i++) {
            assertFalse(MapTileService.queueForTest(new MapTileService.Key("minecraft:overworld", MapTileService.Layer.SURFACE, 0, i, 0)).isCompletedExceptionally());
        }
        var same = MapTileService.queueForTest(new MapTileService.Key("minecraft:overworld", MapTileService.Layer.SURFACE, 0, 0, 0));
        assertFalse(same.isDone(), "the same tile joins its waiting job");
        var over = MapTileService.queueForTest(new MapTileService.Key("minecraft:overworld", MapTileService.Layer.SURFACE, 0, 0, 1));
        assertTrue(over.isCompletedExceptionally(), "one more is refused");
    }
}
