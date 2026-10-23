package ai.ezfox.cccloud.compat.merequester;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import appeng.api.stacks.AEItemKey;
import com.almostreliable.merequester.requester.Request;
import com.almostreliable.merequester.requester.RequesterBlockEntity;
import com.almostreliable.merequester.requester.status.RequestStatus;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/**
 * ME Requester: an empty requester shows the hint; configuring a request in-game rebuilds the card; the amount and
 * the enable toggle are changed from the dashboard and reach the requester.
 */
public final class MeRequesterGameTests {
    private MeRequesterGameTests() {
    }

    public static void requests(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "merequester:requester"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> {
                assertCard(helper, "merequester", null);
                helper.assertTrue(uplink(helper).getWidget("empty") != null, "hint while no request is configured");
            })
            .thenExecute(() -> bind(helper, linked))
            .thenExecute(() -> requester(helper).getRequestManager().get(0)
                .fromComponent(new Request.Component(true, Optional.of(AEItemKey.of(Items.IRON_INGOT)), 64, 1, RequestStatus.IDLE)))
            .thenWaitUntil(() -> {
                assertCard(helper, "merequester", linked);
                var toggle = uplink(helper).getWidget("req0_on");
                helper.assertTrue(toggle != null && "Iron Ingot".equals(toggle.label()), "request widget rebuilt: " + (toggle == null ? null : toggle.label()));
                helper.assertTrue(number(helper, "req0_amount") == 64, "amount 64: " + value(helper, "req0_amount"));
                helper.assertTrue(bool(helper, "req0_on"), "enabled");
            })
            .thenExecute(() -> {
                helper.assertTrue("invalid_value".equals(act(helper, "req0_amount", 0)), "amount 0 is out of range");
                helper.assertTrue(act(helper, "req0_amount", 128) == null, "set amount");
                helper.assertTrue(act(helper, "req0_on", false) == null, "disable");
                var request = requester(helper).getRequestManager().get(0);
                helper.assertTrue(request.getAmount() == 128 && !request.getState(), "requester updated: " + request.getAmount() + " " + request.getState());
            })
            .thenWaitUntil(() -> helper.assertTrue(text(helper, "req0_status").equals("Disabled"), "status: " + text(helper, "req0_status")))
            .thenSucceed();
    }

    private static RequesterBlockEntity requester(GameTestHelper helper) {
        return (RequesterBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
    }
}
