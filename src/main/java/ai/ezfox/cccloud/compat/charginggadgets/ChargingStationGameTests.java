package ai.ezfox.cccloud.compat.charginggadgets;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import com.direwolf20.charginggadgets.blocks.chargingstation.ChargingStationTile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Charging Gadgets: a Charging Station alone with coal in its fuel slot burns it; its energy and fuel show on the card. */
public final class ChargingStationGameTests {
    private ChargingStationGameTests() {
    }

    public static void station(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "charginggadgets:charging_station"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, "charginggadgets_station", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "charginggadgets_station", linked);
                helper.assertTrue("Idle".equals(text(helper, ChargingStationCards.STATUS)), "status: " + text(helper, ChargingStationCards.STATUS));
                helper.assertTrue("Empty".equals(text(helper, ChargingStationCards.ITEM)), "nothing charging");
            })
            .thenExecute(() -> {
                var station = (ChargingStationTile) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                try (var tx = Transaction.openRoot()) {
                    station.inventory.insert(ChargingStationTile.Slots.FUEL.getId(), ItemResource.of(Items.COAL), 8, tx);
                    tx.commit();
                }
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(number(helper, ChargingStationCards.ENERGY) > 0, "coal turned into FE: " + value(helper, ChargingStationCards.ENERGY));
                helper.assertTrue(text(helper, ChargingStationCards.FUEL).endsWith("Coal"), "fuel: " + text(helper, ChargingStationCards.FUEL));
            })
            .thenSucceed();
    }
}
