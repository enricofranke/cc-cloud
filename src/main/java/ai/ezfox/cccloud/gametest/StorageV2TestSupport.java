package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.bridge.BridgeCore;
import ai.ezfox.cccloud.bridge.StorageAdapter;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;

/**
 * Storage card v2 checks shared by the AE2 and RS2 crafting GameTests (a pattern for oak planks from oak logs and a
 * crafter): a preview with missing items, a preview that works, cancelling a running job, auto-stock triggering a craft.
 */
public final class StorageV2TestSupport {
    public static final String PLANKS = "minecraft:oak_planks";
    public static final String LOGS = "minecraft:oak_log";

    private StorageV2TestSupport() {
    }

    /**
     * @param core     the bridge
     * @param addLogs  inserts oak logs into the network
     */
    public static GameTestSequence run(GameTestHelper helper, GameTestSequence sequence, Supplier<BridgeCore> core, Runnable addLogs) {
        StorageAdapter.Plan[] plan = {null};
        String[] job = {null};
        return sequence
            .thenWaitUntil(() -> helper.assertTrue(core.get().isConnected() && core.get().isCraftable(PLANKS), "pattern for oak planks known"))
            // 1. Preview without logs: missing resources, nothing started.
            .thenExecute(() -> core.get().preview(PLANKS, 8, p -> plan[0] = p))
            .thenWaitUntil(() -> {
                helper.assertTrue(plan[0] != null, "preview calculated");
                helper.assertTrue(plan[0].error() == null, "preview error: " + plan[0].error());
                helper.assertTrue(plan[0].missing(), "logs are missing: " + plan[0]);
                helper.assertTrue(plan[0].entries().stream().anyMatch(e -> e.item().builtInRegistryHolder().key().identifier().toString().equals(LOGS) && e.missing() > 0),
                    "missing entry for oak logs: " + plan[0].entries());
                helper.assertTrue(core.get().jobRows(16).isEmpty(), "a preview starts nothing");
            })
            // 2. With logs the plan works.
            .thenExecute(() -> {
                addLogs.run();
                plan[0] = null;
                core.get().preview(PLANKS, 8, p -> plan[0] = p);
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(plan[0] != null, "second preview");
                helper.assertTrue(!plan[0].missing(), "nothing missing with logs: " + plan[0]);
                helper.assertTrue(plan[0].entries().stream().anyMatch(e -> e.item().builtInRegistryHolder().key().identifier().toString().equals(PLANKS)
                    && e.toCraft() >= 8), "planks to craft: " + plan[0].entries());
            })
            // 3. A big job can be cancelled.
            .thenExecute(() -> {
                var ticket = core.get().craft(PLANKS, 200, "gametest");
                helper.assertTrue(ticket.error() == null, "craft started: " + ticket.error());
                job[0] = ticket.jobId();
            })
            .thenWaitUntil(() -> helper.assertTrue(status(core.get(), job[0]).equals("running"), "job running: " + core.get().jobRows(16)))
            .thenExecute(() -> helper.assertTrue(core.get().cancelAny(job[0]), "cancel"))
            .thenWaitUntil(() -> helper.assertTrue(status(core.get(), job[0]).equals("cancelled"), "job cancelled: " + core.get().jobRows(16)))
            // 4. Auto-stock: keeps 4 planks by itself.
            .thenExecute(() -> helper.assertTrue(core.get().setAutoStock(PLANKS, 4, 8, true) == null, "auto-stock set"))
            .thenWaitUntil(() -> {
                var rows = core.get().jobRows(16).toString();
                helper.assertTrue(rows.contains("\"source\":\"autostock\""), "auto-stock requested a craft: " + rows);
            })
            .thenWaitUntil(() -> {
                var row = core.get().getItem(PLANKS);
                helper.assertTrue(row != null && row.count() >= 4, "auto-stock filled planks up to the minimum: " + row);
            });
    }

    private static String status(BridgeCore core, String jobId) {
        for (var row : core.jobRows(32)) {
            var o = row.getAsJsonObject();
            if (o.get("jobId").getAsString().equals(jobId)) return o.get("status").getAsString();
        }
        return "unknown";
    }
}
