package ai.ezfox.cccloud.bridge;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Op;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WatchlistTest {
    private final Map<String, Long> counts = new HashMap<>();

    private long count(String name) {
        return counts.getOrDefault(name, 0L);
    }

    @Test
    void alertsOncePerCrossing() {
        var list = new Watchlist();
        assertNull(list.apply(new Op.WatchAdd("minecraft:iron_ingot", 100), n -> true));
        counts.put("minecraft:iron_ingot", 500L);
        assertTrue(list.check(this::count).isEmpty());
        counts.put("minecraft:iron_ingot", 50L);
        var crossings = list.check(this::count);
        assertEquals(1, crossings.size());
        assertEquals(50, crossings.getFirst().count());
        assertTrue(list.check(this::count).isEmpty(), "still below: no second alert");
        counts.put("minecraft:iron_ingot", 120L);
        assertTrue(list.check(this::count).isEmpty());
        counts.put("minecraft:iron_ingot", 10L);
        assertEquals(1, list.check(this::count).size(), "alerts again after recovering");
    }

    @Test
    void setMinCanTriggerAnAlert() {
        var list = new Watchlist();
        list.apply(new Op.WatchAdd("a:b", 0), n -> true);
        counts.put("a:b", 10L);
        list.check(this::count);
        assertNull(list.apply(new Op.WatchSetMin("a:b", 20), n -> true));
        assertEquals(1, list.check(this::count).size());
    }

    @Test
    void validatesOperations() {
        var list = new Watchlist();
        assertEquals("unknown_item", list.apply(new Op.WatchAdd("a:b", 1), n -> false));
        assertNull(list.apply(new Op.WatchAdd("a:b", 1), n -> true));
        assertEquals("already_watched", list.apply(new Op.WatchAdd("a:b", 1), n -> true));
        assertEquals("unknown_item", list.apply(new Op.WatchRemove("a:c"), n -> true));
        assertEquals("unknown_item", list.apply(new Op.WatchSetMin("a:c", 1), n -> true));
        assertEquals("invalid_value", list.apply(new Op.Craft("a:b", 1), n -> true));
        for (int i = 1; i < Limits.WATCHLIST; i++) assertNull(list.apply(new Op.WatchAdd("a:i" + i, 1), n -> true));
        assertEquals("too_many", list.apply(new Op.WatchAdd("a:full", 1), n -> true));
        assertNull(list.apply(new Op.WatchRemove("a:b"), n -> true));
        assertEquals(Limits.WATCHLIST - 1, list.size());
    }

    @Test
    void savesAndLoads() {
        var list = new Watchlist();
        list.apply(new Op.WatchAdd("minecraft:iron_ingot", 64), n -> true);
        counts.put("minecraft:iron_ingot", 3L);
        list.check(this::count);
        var copy = new Watchlist();
        copy.load(list.save());
        assertEquals(1, copy.size());
        assertTrue(copy.check(this::count).isEmpty(), "the 'below' state survives a reload, so no duplicate alert");
        var rows = copy.rows(n -> "Iron Ingot");
        assertEquals("{\"name\":\"minecraft:iron_ingot\",\"displayName\":\"Iron Ingot\",\"count\":3,\"min\":64}", rows.get(0).toString());
        var broken = new Watchlist();
        broken.load("not json");
        assertTrue(broken.isEmpty());
    }
}
