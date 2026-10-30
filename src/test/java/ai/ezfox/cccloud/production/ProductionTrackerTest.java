package ai.ezfox.cccloud.production;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.bridge.ItemIndex;
import ai.ezfox.cccloud.bridge.ItemRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * v0.10 production statistics: rates from stock deltas between samples, top producers/consumers, crafted items,
 * bounded memory (tracked items, rings), and the guards against fake deltas (offline networks, cut-off indexes).
 */
class ProductionTrackerTest {
    static final long MINUTE = 60_000L;

    static ItemIndex index(Object... nameCount) {
        var rows = new ArrayList<ItemRow>();
        for (int i = 0; i < nameCount.length; i += 2) {
            String name = (String) nameCount[i];
            rows.add(new ItemRow(name, name.substring(name.indexOf(':') + 1), ((Number) nameCount[i + 1]).longValue(), false));
        }
        return new ItemIndex(rows, false);
    }

    @Test
    void ratesPerMinuteFromStockDeltas() {
        var t = new ProductionTracker(60, 256);
        assertFalse(t.sample(index("minecraft:iron_ingot", 100), Map.of(), 0), "the first sample only records");
        assertEquals(0, t.samples());
        assertTrue(t.sample(index("minecraft:iron_ingot", 220, "minecraft:gold_ingot", 5), Map.of(), MINUTE));
        assertTrue(t.sample(index("minecraft:iron_ingot", 160, "minecraft:gold_ingot", 5), Map.of(), 2 * MINUTE));
        var o = t.overview();
        assertEquals(2, o.get("samples").getAsInt());
        assertEquals(62.5, o.get("gained").getAsDouble(), "125 gained in 2 min");
        assertEquals(30, o.get("consumed").getAsDouble(), "60 taken in 2 min");
        assertEquals(32.5, o.get("net").getAsDouble());
        assertEquals("[125.0,0.0]", o.getAsJsonObject("series").get("gained").toString());
        assertEquals("[0.0,60.0]", o.getAsJsonObject("series").get("consumed").toString());
        var top = o.getAsJsonArray("producers").get(0).getAsJsonObject();
        assertEquals("minecraft:iron_ingot", top.get("name").getAsString());
        assertEquals(60, top.get("rate").getAsDouble(), "iron: 120 gained over 2 min");
        assertEquals("minecraft:gold_ingot", o.getAsJsonArray("producers").get(1).getAsJsonObject().get("name").getAsString());
        var eater = o.getAsJsonArray("consumers").get(0).getAsJsonObject();
        assertEquals("minecraft:iron_ingot", eater.get("name").getAsString());
        assertEquals(30, eater.get("rate").getAsDouble());
        var iron = t.item("minecraft:iron_ingot");
        assertEquals(60, iron.get("gained").getAsDouble());
        assertEquals(30, iron.get("consumed").getAsDouble());
        assertEquals(30, iron.get("net").getAsDouble());
        assertEquals("[120.0,-60.0]", iron.getAsJsonObject("series").get("net").toString());
        assertEquals(-60, t.lastItemRate("minecraft:iron_ingot"));
        assertEquals(-60, t.lastNetRate());
        assertEquals(0, t.item("minecraft:diamond").get("net").getAsDouble(), "unknown items answer zeros");
        assertEquals(160, t.row("minecraft:iron_ingot").count());
    }

    @Test
    void irregularSampleTimesAreNormalisedToMinutes() {
        var t = new ProductionTracker(60, 256);
        t.sample(index("a:x", 0), Map.of(), 0);
        t.sample(index("a:x", 30), Map.of(), 30_000); // 30 in half a minute
        t.sample(index("a:x", 90), Map.of(), 150_000); // 60 in two minutes
        assertEquals("[60.0,30.0]", t.item("a:x").getAsJsonObject("series").get("net").toString(), "per-minute rate of each sample");
        assertEquals(36, t.item("a:x").get("net").getAsDouble(), "90 in 2.5 minutes");
        assertFalse(t.sample(index("a:x", 1000), Map.of(), 100_000), "a clock that goes backwards is ignored");
        assertEquals(90, t.row("a:x").count(), "and keeps the older sample");
    }

    @Test
    void disappearingItemsAreConsumedAndCraftedItemsCounted() {
        var t = new ProductionTracker(60, 256);
        t.sample(index("a:x", 50, "a:y", 10), Map.of(), 0);
        t.sample(index("a:x", 50), Map.of("a:z", 64L), MINUTE);
        var o = t.overview();
        assertEquals(10, o.get("consumed").getAsDouble(), "a:y is gone");
        assertEquals(64, o.get("crafted").getAsDouble(), "64 crafted by a finished bridge job");
        assertEquals(64, t.item("a:z").get("crafted").getAsDouble(), "crafted items are tracked even without stock movement");
    }

    @Test
    void offlineNetworksAndCutOffIndexesDoNotFakeDeltas() {
        var t = new ProductionTracker(60, 256);
        t.sample(index("a:x", 500), Map.of(), 0);
        t.pause(); // the network went offline
        assertFalse(t.sample(index(), Map.of(), MINUTE), "after a pause the next sample only records again");
        assertEquals(0, t.samples());
        assertFalse(t.sample(index("a:x", 520), Map.of(), 2 * MINUTE), "an empty index is never a baseline: this one is");
        t.sample(index("a:x", 530), Map.of(), 3 * MINUTE);
        assertEquals(10, t.overview().get("gained").getAsDouble(), "compared with the sample after the pause, not before");
        // A cut-off index (over 50 000 types) says nothing about the items it left out.
        var p = new ProductionTracker(60, 256);
        p.sample(index("a:x", 5, "a:y", 7), Map.of(), 0);
        p.sample(new ItemIndex(List.of(new ItemRow("a:x", "x", 9, false)), true), Map.of(), MINUTE);
        assertEquals(0, p.overview().get("consumed").getAsDouble(), "a:y missing from a truncated index is not consumed");
        assertEquals(4, p.overview().get("gained").getAsDouble());
    }

    @Test
    void trackedItemsAreBoundedAndIdleOnesDropped() {
        var t = new ProductionTracker(5, 3);
        Object[] first = new Object[20];
        Object[] second = new Object[20];
        for (int i = 0; i < 10; i++) {
            first[2 * i] = "m:i" + i;
            first[2 * i + 1] = 1000L;
            second[2 * i] = "m:i" + i;
            second[2 * i + 1] = 1000L + i; // i9 moves most
        }
        t.sample(index(first), Map.of(), 0);
        t.sample(index(second), Map.of(), MINUTE);
        assertEquals(3, t.tracked(), "at most maxTracked items");
        assertEquals(List.of("m:i9", "m:i8", "m:i7"), t.trackedNames(), "the ones that moved most");
        assertEquals(45, t.overview().get("gained").getAsDouble(), "the totals still count every item");
        // Without movement for a whole window the items drop out.
        for (int k = 2; k <= 7; k++) t.sample(index(second), Map.of(), k * MINUTE);
        assertEquals(0, t.tracked());
        assertEquals(5, t.samples(), "the totals ring holds the window");
    }

    @Test
    void aLargeStockChangeOnlyCreatesTheTrackedMaximum() {
        int types = ai.ezfox.cccloud.bridge.ItemIndex.MAX_ENTRIES;
        var t = new ProductionTracker(60, 256);
        var first = new ArrayList<ItemRow>(types);
        var second = new ArrayList<ItemRow>(types);
        long expectedGain = 0;
        for (int i = 0; i < types; i++) {
            first.add(new ItemRow("m:i" + i, "Item " + i, 1_000_000, false));
            // every type changes; i moves by i + 1 (the last 256 types move most)
            second.add(new ItemRow("m:i" + i, "Item " + i, 1_000_000 + i + 1, false));
            expectedGain += i + 1;
        }
        t.sample(new ItemIndex(first, false), Map.of(), 0);
        assertTrue(t.sample(new ItemIndex(second, false), Map.of("m:crafted", 3L), MINUTE));
        assertEquals(256, t.tracked(), "at most maxTracked items");
        assertTrue(t.createdForTest() <= 256, "no entry per changed item before trimming: " + t.createdForTest());
        var names = t.trackedNames();
        assertEquals("m:i" + (types - 1), names.get(0), "the one that moved most first");
        for (int i = types - 256; i < types; i++) assertTrue(names.contains("m:i" + i), "top mover tracked: m:i" + i);
        assertEquals((double) expectedGain, t.overview().get("gained").getAsDouble(), "the totals still count every item");
        assertEquals(3, t.overview().get("crafted").getAsDouble());
        assertEquals("Item " + (types - 1), t.overview().getAsJsonArray("producers").get(0).getAsJsonObject().get("displayName").getAsString());

        // the next big change: the tracked items stay on a tie, new bigger movers replace the weakest ones
        var third = new ArrayList<ItemRow>(types);
        for (int i = 0; i < types; i++) third.add(new ItemRow("m:i" + i, "Item " + i, 1_000_000 + i + 1 + (i < 10 ? 10_000_000 : 0), false));
        t.sample(new ItemIndex(third, false), Map.of(), 2 * MINUTE);
        assertEquals(256, t.tracked());
        assertTrue(t.createdForTest() <= 256 + 10, "only the ten new big movers were added: " + t.createdForTest());
        for (int i = 0; i < 10; i++) assertTrue(t.trackedNames().contains("m:i" + i), "new big mover: m:i" + i);
        assertFalse(t.trackedNames().contains("m:i" + (types - 256)), "the weakest tracked item made room");
    }

    @Test
    void craftedOnlyItemsStillCompeteForASlot() {
        var t = new ProductionTracker(5, 2);
        t.sample(index("a:x", 10, "a:y", 10, "a:z", 10), Map.of(), 0);
        t.sample(index("a:x", 11, "a:y", 15, "a:z", 10), Map.of("a:z", 100L, "a:w", 2L), MINUTE);
        assertEquals(List.of("a:z", "a:y"), t.trackedNames(), "crafted without stock change (z: 100) beats a small mover");
        assertEquals(100, t.item("a:z").get("crafted").getAsDouble());
        assertEquals(0, t.item("a:z").get("net").getAsDouble());
    }

    @Test
    void energyTotals() {
        var e = new EnergyTotals(3);
        assertTrue(e.empty());
        e.sample(List.of(new EnergyTotals.Card("a", "Cell", 500, 1000, 20, 5, false), new EnergyTotals.Card("b", "Bank", 100, 200, 0, 12.5, true)));
        var json = e.json();
        assertEquals(600, json.get("stored").getAsLong());
        assertEquals(1200, json.get("capacity").getAsLong());
        assertEquals(20, json.get("in").getAsDouble());
        assertEquals(17.5, json.get("out").getAsDouble());
        assertEquals(2.5, e.lastNet());
        assertTrue(json.getAsJsonArray("cards").get(1).getAsJsonObject().get("measured").getAsBoolean());
        for (int i = 0; i < 5; i++) e.sample(List.of(new EnergyTotals.Card("a", "Cell", 0, 1, i, 0, false)));
        assertEquals("[2.0,3.0,4.0]", e.json().getAsJsonObject("series").get("in").toString(), "a ring of the window");
    }

    @Test
    void ringBuffer() {
        var r = new RateRing(3);
        for (int i = 1; i <= 5; i++) r.add(i);
        assertEquals(3, r.size());
        assertEquals(3, r.get(0));
        assertEquals(12, r.sum());
        r.add(-20);
        assertEquals(9, r.positive());
        assertEquals(20, r.negative());
        assertEquals(29, r.activity());
    }
}
