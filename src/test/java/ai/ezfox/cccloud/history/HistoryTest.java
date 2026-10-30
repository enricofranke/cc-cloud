package ai.ezfox.cccloud.history;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.access.Perm;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Bucketing, tiers, gaps, ring buffers, storage, retention, the 2 MB cap and the history API. */
class HistoryTest {
    static final long MIN = 60_000L;
    static final long T0 = 1_000_000L * MIN; // a minute boundary

    @Test
    void averagesPerMinuteWithGaps() {
        var s = new HistorySeries(HistorySeries.Range.H24);
        s.add(T0, 10);
        s.add(T0 + 30_000, 20);
        s.add(T0 + MIN, 30);
        s.add(T0 + 3 * MIN, 50); // minute 2 has no sample
        var points = s.points(HistorySeries.Range.H24, T0 + 3 * MIN + 1);
        assertEquals(4, points.size());
        assertEquals(15, points.get(0).value(), 1e-9, "average of the first minute");
        assertEquals(T0, points.get(0).time());
        assertTrue(Double.isNaN(points.get(2).value()), "gap");
        assertEquals(50, points.get(3).value(), 1e-9, "the running bucket is included");
        assertEquals(60_000L, s.step(HistorySeries.Range.H24));
    }

    @Test
    void oneHourKeepsSixtyPointsAndLongRangesUseCoarserTiers() {
        var hour = new HistorySeries(HistorySeries.Range.H1);
        for (int i = 0; i < 200; i++) hour.add(T0 + i * MIN, i);
        var pts = hour.points(HistorySeries.Range.H1, T0 + 199 * MIN);
        assertTrue(pts.size() <= 61, "ring keeps an hour: " + pts.size());
        assertEquals(199, pts.getLast().value(), 1e-9);
        assertTrue(hour.points(HistorySeries.Range.D7, T0 + 199 * MIN).size() <= 61, "a 1 h series answers a 7 d request with what it has");

        var week = new HistorySeries(HistorySeries.Range.D7);
        for (int i = 0; i < 60; i++) week.add(T0 + i * MIN, 1);
        assertEquals(300_000L, week.step(HistorySeries.Range.D7));
        assertEquals(12, week.points(HistorySeries.Range.D7, T0 + 59 * MIN).size(), "5-minute buckets");
        assertTrue(week.bytes() > hour.bytes());
        assertTrue(HistorySeries.bytes(HistorySeries.Range.D30) < 25_000, "a 30 d series stays small");
    }

    @Test
    void storageRoundTripKeepsTheRunningBucket() {
        var s = new HistorySeries(HistorySeries.Range.D30);
        for (int i = 0; i < 100; i++) s.add(T0 + i * 7_000L, i % 3 == 0 ? Double.NaN : i);
        var back = HistorySeries.decode(s.encode());
        assertNotNull(back);
        long now = T0 + 100 * 7_000L;
        for (var r : HistorySeries.Range.values()) assertEquals(s.points(r, now), back.points(r, now), r.id);
        back.add(now, 5);
        assertNull(HistorySeries.decode("not base64!"));
        assertNull(HistorySeries.decode("AA=="));
    }

    @Test
    void retentionLimitsTheRangeAndResizesKeepData() {
        assertEquals(HistorySeries.Range.H1, HistorySeries.Range.limit(HistorySeries.Range.D30, 1), "base: 1 h");
        assertEquals(HistorySeries.Range.H24, HistorySeries.Range.limit(HistorySeries.Range.D30, 24));
        assertEquals(HistorySeries.Range.D7, HistorySeries.Range.limit(HistorySeries.Range.D7, 30 * 24), "never more than asked");
        var store = new HistoryStore(() -> { });
        var mf = UUID.randomUUID();
        for (int i = 0; i < 30; i++) store.record(mf, "w:u/temp", HistorySeries.Range.D30, 1, T0 + i * MIN, i);
        assertEquals(HistorySeries.Range.H1, store.range(mf, "w:u/temp"));
        // Memory upgrade inserted: 7 days
        store.record(mf, "w:u/temp", HistorySeries.Range.D30, 7 * 24, T0 + 30 * MIN, 30);
        assertEquals(HistorySeries.Range.D7, store.range(mf, "w:u/temp"));
        assertEquals(31, store.points(mf, "w:u/temp", HistorySeries.Range.H24, T0 + 30 * MIN).size(), "the hour recorded before is kept");
    }

    @Test
    void globalCapOverAllMainframes() {
        var store = new HistoryStore(() -> { });
        store.setMaxTotalBytes(3L * HistoryStore.MAX_BYTES);
        int perSeries = HistorySeries.bytes(HistorySeries.Range.D30);
        int fits = HistoryStore.MAX_BYTES / perSeries;
        int created = 0;
        for (int m = 0; m < 5; m++) {
            var mf = UUID.randomUUID();
            for (int i = 0; i < fits; i++) if (store.record(mf, "w:u/" + i, HistorySeries.Range.D30, 720, T0, 1)) created++;
        }
        assertEquals(3 * HistoryStore.MAX_BYTES / perSeries, created, "three mainframes' worth, then no new series anywhere");
        assertTrue(store.totalBytes() <= 3L * HistoryStore.MAX_BYTES);
        var copy = new HistoryStore(() -> { });
        copy.load(store.save());
        assertEquals(store.totalBytes(), copy.totalBytes(), "the total is counted again after loading");
    }

    @Test
    void capPruneAndPersistence() {
        var store = new HistoryStore(() -> { });
        var mf = UUID.randomUUID();
        int perSeries = HistorySeries.bytes(HistorySeries.Range.D30);
        int fits = HistoryStore.MAX_BYTES / perSeries;
        int created = 0;
        for (int i = 0; i < fits + 10; i++) if (store.record(mf, "w:u/" + i, HistorySeries.Range.D30, 720, T0, 1)) created++;
        assertEquals(fits, created, "nothing beyond 2 MB");
        assertTrue(store.bytes(mf) <= HistoryStore.MAX_BYTES);
        var copy = new HistoryStore(() -> { });
        copy.load(store.save());
        assertEquals(store.bytes(mf), copy.bytes(mf));
        copy.prune(T0 + 31L * 24 * 60 * MIN);
        assertEquals(0, copy.bytes(mf), "series without samples for longer than their range are dropped");
        assertFalse(store.record(mf, "w:u/" + "x".repeat(300), HistorySeries.Range.H1, 1, T0, 1), "key too long");
    }

    @Test
    void apiChecksPermissionsAndExportsCsv() {
        var store = new HistoryStore(() -> { });
        var mf = UUID.randomUUID();
        String u = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) store.record(mf, HistoryService.widgetKey(u, "temp"), HistorySeries.Range.H24, 24, T0 + i * MIN, 10 + i);
        var all = EnumSet.allOf(Perm.class);
        var ok = HistoryApi.handle(store, mf, all, false, "24h", "json", "w", u, "temp", "24h", 24, T0 + 5 * MIN);
        assertEquals(200, ok.status());
        assertEquals(5, ok.body().getAsJsonArray("points").size());
        assertEquals(10.0, ok.body().getAsJsonArray("points").get(0).getAsJsonArray().get(1).getAsDouble());
        var csv = HistoryApi.handle(store, mf, all, false, "24h", "csv", "w", u, "temp", "24h", 24, T0 + 5 * MIN);
        String text = new String(csv.raw(), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("time,value\n") && text.contains(",14.0") || text.contains(",14\n"), text);
        Set<Perm> viewer = EnumSet.of(Perm.VIEW, Perm.STORAGE_SEARCH);
        assertEquals(403, HistoryApi.handle(store, mf, viewer, false, "24h", "csv", "w", u, "temp", "24h", 24, T0).status(), "CSV needs act.buttons");
        assertEquals(200, HistoryApi.handle(store, mf, viewer, false, "24h", "json", "w", u, "temp", "24h", 24, T0).status());
        assertEquals(403, HistoryApi.handle(store, mf, viewer, true, "24h", "json", "w", u, "temp", "24h", 24, T0).status(), "restricted sections");
        assertEquals(403, HistoryApi.handle(store, mf, EnumSet.of(Perm.VIEW), false, "24h", "json", "i", u, "minecraft:iron_ingot", null, 24, T0).status());
        assertEquals(404, HistoryApi.handle(store, mf, all, false, "24h", "json", "w", u, "other", null, 24, T0).status());
        assertEquals(403, HistoryApi.handle(store, mf, Set.of(), false, "24h", "json", "w", u, "temp", "24h", 24, T0).status());
        assertTrue(HistoryApi.PATH.matcher("/api/mainframes/" + mf + "/history/7d/json/i/" + u + "/minecraft:iron_ingot").matches());
        assertFalse(HistoryApi.PATH.matcher("/api/mainframes/" + mf + "/history/1y/json/w/" + u + "/temp").matches());
    }
}
