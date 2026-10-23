package ai.ezfox.cccloud.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Auto-stock (level maintainer): requests, one job per item, cooldown, waiting for a CPU, pause after 3 failures. */
class AutoStockTest {
    static final String IRON = "minecraft:iron_ingot";

    static final class FakeCrafter implements Watchlist.Crafter {
        final List<String> started = new ArrayList<>();
        final Map<String, String> status = new HashMap<>();
        final Map<String, String> errors = new HashMap<>();
        boolean craftable = true;
        @Nullable String immediateError;
        int next;

        @Override
        public boolean craftable(String name) {
            return craftable;
        }

        @Override
        public String start(String name, long count) {
            if (immediateError != null) return "error:" + immediateError;
            String id = "job" + (next++);
            started.add(name + "x" + count);
            status.put(id, "running");
            return id;
        }

        @Override
        public @Nullable String status(String jobId) {
            return status.get(jobId);
        }

        @Override
        public @Nullable String error(String jobId) {
            return errors.get(jobId);
        }
    }

    @Test
    void requestsUpToTargetInBatchesOneJobAtATime() {
        var list = new Watchlist();
        assertNull(list.setAutoStock(IRON, 100, 500, true, n -> true));
        list.check(n -> 20);
        var crafter = new FakeCrafter();
        list.autoStock(0, 64, crafter);
        assertEquals(List.of(IRON + "x64"), crafter.started, "min(target − count, batch)");
        assertEquals("crafting", list.info(IRON).state());
        list.autoStock(60_000, 64, crafter);
        assertEquals(1, crafter.started.size(), "one active job per item");
        crafter.status.put("job0", "done");
        list.check(n -> 84);
        list.autoStock(61_000, 64, crafter);
        assertEquals(2, crafter.started.size(), "next batch after the job is done and the cooldown passed");
        crafter.status.put("job1", "done");
        list.check(n -> 200);
        list.autoStock(200_000, 64, crafter);
        assertEquals(2, crafter.started.size(), "enough stock: nothing to do");
        assertEquals("idle", list.info(IRON).state());
    }

    @Test
    void cooldownAndWaitingForACpu() {
        var list = new Watchlist();
        list.setAutoStock(IRON, 10, 10, true, n -> true);
        list.check(n -> 0);
        var crafter = new FakeCrafter();
        crafter.immediateError = "busy";
        list.autoStock(0, 64, crafter);
        assertEquals("waiting_cpu", list.info(IRON).state());
        assertEquals(0, list.info(IRON).failures(), "no CPU is not a failure");
        crafter.immediateError = null;
        list.autoStock(10_000, 64, crafter);
        assertTrue(crafter.started.isEmpty(), "30 s cooldown");
        list.autoStock(Watchlist.COOLDOWN_MS, 64, crafter);
        assertEquals(List.of(IRON + "x10"), crafter.started);
    }

    @Test
    void pausesAfterThreeFailuresAndEditingResumes() {
        var list = new Watchlist();
        list.setAutoStock(IRON, 10, 20, true, n -> true);
        list.check(n -> 0);
        var crafter = new FakeCrafter();
        long now = 0;
        for (int i = 0; i < 3; i++) {
            list.autoStock(now, 64, crafter);
            String job = "job" + i;
            crafter.status.put(job, "failed");
            crafter.errors.put(job, "missing_resources");
            now += Watchlist.COOLDOWN_MS;
            list.autoStock(now, 64, crafter);
            if (i < 2) assertEquals("missing", list.info(IRON).state(), "failure " + (i + 1));
        }
        assertEquals("paused", list.info(IRON).state());
        int requests = crafter.started.size();
        list.autoStock(now + 10 * Watchlist.COOLDOWN_MS, 64, crafter);
        assertEquals(requests, crafter.started.size(), "paused: no more requests");
        list.setAutoStock(IRON, 10, 20, true, n -> true);
        assertEquals(0, list.info(IRON).failures(), "editing resets");
        list.autoStock(now + 20 * Watchlist.COOLDOWN_MS, 64, crafter);
        assertEquals(requests + 1, crafter.started.size(), "resumed");
    }

    @Test
    void persistsSettingsAndRejectsBadValues() {
        var list = new Watchlist();
        assertEquals("invalid_value", list.setAutoStock(IRON, 10, 5, true, n -> true), "target < min");
        assertEquals("unknown_item", list.setAutoStock("mod:nothing", 1, 1, true, n -> false));
        list.setAutoStock(IRON, 10, 30, true, n -> true);
        var copy = new Watchlist();
        copy.load(list.save());
        var info = copy.info(IRON);
        assertTrue(info.autoCraft() && info.min() == 10 && info.target() == 30);
        copy.load("{\"watch\":" + list.save() + ",\"fav\":[]}");
        assertNotNull(copy.info(IRON), "v2 container format");
    }
}
