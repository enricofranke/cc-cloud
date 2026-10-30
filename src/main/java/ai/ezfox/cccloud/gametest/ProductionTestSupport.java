package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.bridge.BridgeHost;
import ai.ezfox.cccloud.production.ProductionService;
import ai.ezfox.cccloud.web.ClientMessages;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonObject;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Shared steps of the production statistics GameTests (compat/ae2, compat/rs): a linked bridge on a real network is
 * sampled three times one minute apart (manual sample times, so the tick's own sampling never mixes in) while items go
 * in and out; the rates per minute, the top producers/consumers, the item view, the permission filter and a pinned
 * chart are checked. Mod-independent: the caller moves the items.
 */
public final class ProductionTestSupport {
    private static final long MINUTE = 60_000L;
    private static final String IRON = "minecraft:iron_ingot";

    /** Moves items in or out of the storage network under test. */
    public interface Network {
        long insert(Item item, long count);

        long extract(Item item, long count);
    }

    private ProductionTestSupport() {
    }

    /**
     * Appends the production steps to a bridge test's sequence, after the bridge was linked to the mainframe of
     * {@code linking} (the network already holds {@link BridgeTestSupport#AMOUNT} iron ingots).
     */
    public static GameTestSequence steps(GameTestHelper helper, GameTestSequence sequence, Supplier<BridgeHost> host, Network network,
                                         Supplier<BridgeTestSupport.Linking> linking) {
        var server = helper.getLevel().getServer();
        long t0 = System.currentTimeMillis() + 3_600_000L; // later than any automatic sample of this run
        UUID[] section = {null};
        UUID[] mainframe = {null};
        return sequence
            .thenWaitUntil(() -> {
                section[0] = host.get().bridgeSection().sectionId();
                mainframe[0] = linking.get().mainframe.getMainframeId();
                helper.assertTrue(WebHub.INSTANCE.hasUplink(mainframe[0], section[0].toString()), "the bridge section is published");
                helper.assertTrue(ProductionService.sampleForTest(server, section[0], t0, true), "the storage is known to the production statistics");
            })
            .thenExecute(() -> {
                helper.assertTrue(network.insert(Items.IRON_INGOT, 120) == 120, "120 iron in");
                helper.assertTrue(network.insert(Items.GOLD_INGOT, 5) == 5, "5 gold in");
            })
            .thenIdle(12) // the bridge's item index is rebuilt at most every 10 ticks
            .thenExecute(() -> ProductionService.sampleForTest(server, section[0], t0 + MINUTE, false))
            .thenExecute(() -> helper.assertTrue(network.extract(Items.IRON_INGOT, 60) == 60, "60 iron out"))
            .thenIdle(12)
            .thenExecute(() -> {
                ProductionService.sampleForTest(server, section[0], t0 + 2 * MINUTE, false);
                var overview = ProductionService.overview(server, mainframe[0], EnumSet.allOf(Perm.class), true);
                JsonObject storage = null;
                for (var s : overview.getAsJsonArray("storages")) if (s.getAsJsonObject().get("uplinkId").getAsString().equals(section[0].toString())) storage = s.getAsJsonObject();
                helper.assertTrue(storage != null, "the storage is in the overview: " + overview);
                helper.assertTrue(storage.get("samples").getAsInt() == 2, "two rates after three samples: " + storage);
                helper.assertTrue(storage.get("gained").getAsDouble() == 62.5, "(120 + 5) items gained in 2 minutes: " + storage);
                helper.assertTrue(storage.get("consumed").getAsDouble() == 30, "60 consumed in 2 minutes: " + storage);
                helper.assertTrue(storage.get("net").getAsDouble() == 32.5, "net: " + storage);
                var producer = storage.getAsJsonArray("producers").get(0).getAsJsonObject();
                helper.assertTrue(IRON.equals(producer.get("name").getAsString()) && producer.get("rate").getAsDouble() == 60, "top producer iron 60/min: " + producer);
                var consumer = storage.getAsJsonArray("consumers").get(0).getAsJsonObject();
                helper.assertTrue(IRON.equals(consumer.get("name").getAsString()) && consumer.get("rate").getAsDouble() == 30, "top consumer iron 30/min: " + consumer);
                // "What is eating my iron": the item view.
                var item = ProductionService.handle(server, mainframe[0], EnumSet.allOf(Perm.class),
                    new ClientMessages.Production("r", "item", section[0], IRON, null, null), "gametest");
                helper.assertTrue(item.error() == null, "item view: " + item.error());
                var data = item.data();
                helper.assertTrue(data.get("count").getAsLong() == BridgeTestSupport.AMOUNT + 60 && data.get("gained").getAsDouble() == 60
                    && data.get("consumed").getAsDouble() == 30 && data.get("net").getAsDouble() == 30, "iron: " + data);
                var net = data.getAsJsonObject("series").getAsJsonArray("net");
                helper.assertTrue(net.size() == 2 && net.get(0).getAsDouble() == 120 && net.get(1).getAsDouble() == -60, "per-minute series: " + net);
                // Only who may search the storage sees its statistics.
                var noSearch = EnumSet.allOf(Perm.class);
                noSearch.remove(Perm.STORAGE_SEARCH);
                helper.assertTrue(ProductionService.overview(server, mainframe[0], noSearch, true).getAsJsonArray("storages").isEmpty(), "no storage.search, no storages");
                var denied = ProductionService.handle(server, mainframe[0], noSearch, new ClientMessages.Production("r", "item", section[0], IRON, null, null), "gametest");
                helper.assertTrue("unknown_storage".equals(denied.error()), "item view without storage.search: " + denied.error());
                // Pin the iron rate as a card.
                var pin = ProductionService.handle(server, mainframe[0], EnumSet.allOf(Perm.class),
                    new ClientMessages.Production("r", "pin", section[0], IRON, "item", null), "gametest");
                helper.assertTrue(pin.error() == null, "pinned: " + pin.error());
            })
            .thenWaitUntil(() -> helper.assertTrue(WebHub.INSTANCE.hasUplink(mainframe[0], ProductionService.sectionId(mainframe[0]).toString()),
                "the Production section is published"))
            .thenExecute(() -> {
                helper.assertTrue(network.insert(Items.IRON_INGOT, 30) == 30, "30 iron in");
            })
            .thenIdle(12)
            .thenExecute(() -> {
                ProductionService.sampleForTest(server, section[0], t0 + 3 * MINUTE, false);
                var production = WebHub.INSTANCE.sectionSnapshot(mainframe[0], ProductionService.sectionId(mainframe[0]).toString());
                helper.assertTrue(production != null && "production".equals(production.get("template").getAsString()), "Production card: " + production);
                var chart = production.getAsJsonArray("widgets").get(0).getAsJsonObject();
                helper.assertTrue("chart".equals(chart.get("type").getAsString()) && chart.get("label").getAsString().startsWith("Iron Ingot"), "chart: " + chart);
                var value = WebHub.INSTANCE.widgetValue(mainframe[0], ProductionService.sectionId(mainframe[0]).toString(), chart.get("id").getAsString());
                helper.assertTrue(value != null && value.getAsDouble() == 30, "the pinned chart shows the last minute's iron rate: " + value);
                ProductionService.forget(server, mainframe[0]);
            });
    }
}
