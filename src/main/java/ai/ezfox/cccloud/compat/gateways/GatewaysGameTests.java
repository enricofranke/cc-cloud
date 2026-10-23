package ai.ezfox.cccloud.compat.gateways;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import dev.shadowsoffire.gateways.gate.GatewayRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;

/**
 * Gateways to Eternity: the Gateways card shows no gate, then the "Emerald Grove" gate opened next to the mainframe
 * (as a gate pearl would), then none again after it is removed.
 */
public final class GatewaysGameTests {
    private GatewaysGameTests() {
    }

    public static void card(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.CREATIVE);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        Object[] gate = {null};
        helper.startSequence()
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), GatewaysCard.KIND);
                helper.assertTrue(section != null && "0".equals(section.values().get(GatewaysCard.COUNT).getAsString()), "no gate open");
                helper.assertTrue(section.snapshot().get("restricted").getAsBoolean(), "restricted section");
            })
            .thenExecute(() -> {
                var gateway = GatewayRegistry.INSTANCE.getValue(Identifier.parse("gateways:emerald_grove"));
                helper.assertTrue(gateway != null, "Emerald Grove gateway loaded");
                var entity = gateway.createEntity(helper.getLevel(), owner);
                var p = helper.absolutePos(new BlockPos(6, 2, 6));
                entity.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                helper.getLevel().addFreshEntity(entity);
                entity.onGateCreated();
                gate[0] = entity;
            })
            .thenWaitUntil(() -> {
                var values = MainframeCards.section(mainframe.getMainframeId(), GatewaysCard.KIND).values();
                helper.assertTrue("1".equals(values.get(GatewaysCard.COUNT).getAsString()), "one gate: " + values.get(GatewaysCard.COUNT));
                String row = values.get(GatewaysCard.GATE + 1).getAsString();
                helper.assertTrue(row.contains("wave") && row.endsWith(" m"), "gate row: " + row);
                helper.assertTrue(values.get(GatewaysCard.ACTIVE).getAsBoolean(), "indicator on");
            })
            .thenExecute(() -> ((net.minecraft.world.entity.Entity) gate[0]).discard())
            .thenWaitUntil(() -> helper.assertTrue("0".equals(MainframeCards.section(mainframe.getMainframeId(), GatewaysCard.KIND)
                .values().get(GatewaysCard.COUNT).getAsString()), "gate gone"))
            .thenSucceed();
    }
}
