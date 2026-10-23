package ai.ezfox.cccloud.compat.server;

import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/**
 * The Server card appears on a mainframe without any optional mod: values from the server's tick times, routed like a
 * section, published as restricted (canControl only); with spark installed it carries spark's extra rows too.
 */
public final class ServerCardGameTests {
    private ServerCardGameTests() {
    }

    public static void serverCard(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.startSequence()
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), ServerCard.KIND);
                helper.assertTrue(section != null, "server card on the mainframe");
                var values = section.values();
                helper.assertTrue(values.get(ServerCard.TPS).isJsonPrimitive(), "refreshed");
                double tps = values.get(ServerCard.TPS).getAsDouble();
                helper.assertTrue(tps > 0 && tps <= 20, "tps " + tps);
                helper.assertTrue(values.get(ServerCard.MSPT).getAsDouble() >= 0, "mspt");
                helper.assertTrue(values.get(ServerCard.PLAYERS).getAsString().contains(" / "), "players " + values.get(ServerCard.PLAYERS));
                helper.assertTrue(values.get(ServerCard.DIMENSIONS).getAsString().contains("overworld"), "per dimension: " + values.get(ServerCard.DIMENSIONS));
                helper.assertTrue(values.get(ServerCard.MEMORY).getAsDouble() > 0, "memory");
                helper.assertTrue(section.widget(ServerCard.HISTORY).historySize() > 0, "mspt chart sampled");
                var snapshot = section.snapshot();
                helper.assertTrue(snapshot.get("restricted").getAsBoolean() && "mainframe".equals(snapshot.get("source").getAsString()), "restricted section");
                helper.assertTrue(CloudNetwork.section(section.sectionId()) == section, "actions are routed to the section");
                helper.assertTrue("gauge".equals(WebHub.INSTANCE.widgetType(mainframe.getMainframeId(), section.sectionId().toString(), ServerCard.TPS)),
                    "published to the hub");
                if (net.neoforged.fml.ModList.get().isLoaded("spark")) {
                    helper.assertTrue(values.containsKey("spark_tps"), "spark rows with spark installed");
                }
            })
            .thenWaitUntil(() -> {
                // the section answers actions only while the mainframe is online
                var section = MainframeCards.section(mainframe.getMainframeId(), ServerCard.KIND);
                var result = section.handleAction(ServerCard.TPS, new JsonPrimitive(1), null, "gametest", true).getNow(null);
                helper.assertTrue(result != null && "not_interactive".equals(result.error()),
                    "the server card is read-only: " + (result == null ? null : result.error()) + ", mainframe " + mainframe.status());
            })
            .thenSucceed();
    }
}
