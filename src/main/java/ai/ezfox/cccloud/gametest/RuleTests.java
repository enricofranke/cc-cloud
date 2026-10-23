package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.cc.CcComputers;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.rules.RuleService;
import ai.ezfox.cccloud.uplink.ComputerCard;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/**
 * v0.5 rules on a real network: a rule on the Computer card's "Computer on" widget sets a redstone output of the same
 * auto card when the computer starts (edge trigger → redstone action, logged), "Run now", loop protection refuses a
 * rule that would trigger itself, and the per-mainframe limit refuses one rule too many.
 */
public final class RuleTests {
    private static final BlockPos COMPUTER = new BlockPos(4, 1, 4);
    private static final BlockPos UPLINK = new BlockPos(5, 1, 4);

    private RuleTests() {
    }

    public static void widgetThresholdSetsRedstone(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(COMPUTER, BridgeTestSupport.block(helper, "computercraft:computer_normal"));
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
        var computerPos = helper.absolutePos(COMPUTER);
        String[] uplinkId = {null};
        String[] ruleId = {null};

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
                uplink.forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
                uplinkId[0] = uplink.getUplinkId().toString();
            })
            .thenWaitUntil(() -> {
                var value = WebHub.INSTANCE.widgetValue(mainframe.getMainframeId(), uplinkId[0], ComputerCard.POWER);
                helper.assertTrue(value != null && !value.getAsBoolean(), "the Computer card is published, computer off: " + value);
            })
            .thenExecute(() -> {
                UUID mf = mainframe.getMainframeId();
                // Loop protection: a redstone action triggered by the same card's redstone widget is refused.
                var loop = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"loop\",\"when\":{\"type\":\"widget\",\"uplinkId\":\"" + uplinkId[0]
                    + "\",\"widgetId\":\"redstone\",\"op\":\">\",\"value\":0},\"then\":[{\"type\":\"redstone\",\"uplinkId\":\"" + uplinkId[0]
                    + "\",\"side\":\"top\",\"value\":0}]}"));
                helper.assertTrue(loop.error() != null && loop.error().startsWith("invalid_rule"), "self-triggering rule refused: " + loop.error());
                var edit = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"Signal when on\",\"cooldown\":5,\"when\":{\"type\":\"widget\",\"uplinkId\":\""
                    + uplinkId[0] + "\",\"widgetId\":\"" + ComputerCard.POWER + "\",\"op\":\"==\",\"value\":1},\"then\":[{\"type\":\"redstone\",\"uplinkId\":\""
                    + uplinkId[0] + "\",\"side\":\"top\",\"value\":15}]}"));
                helper.assertTrue(edit.error() == null && edit.id() != null, "rule saved: " + edit.error());
                ruleId[0] = edit.id();
                // The limit: fill up with rules that never fire, then one more is refused.
                var extra = new ArrayList<String>();
                int limit = RuleService.limit(mf);
                for (int i = RuleService.rules(mf).size(); i < limit; i++) {
                    var filler = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"filler " + i + "\",\"when\":{\"type\":\"interval\",\"minutes\":1440},"
                        + "\"then\":[{\"type\":\"alert\",\"text\":\"x\"}]}"));
                    helper.assertTrue(filler.error() == null, "filler " + i + ": " + filler.error());
                    extra.add(filler.id());
                }
                var over = RuleService.set(server, mf, JsonParser.parseString("{\"name\":\"one too many\",\"when\":{\"type\":\"interval\",\"minutes\":5},"
                    + "\"then\":[{\"type\":\"alert\",\"text\":\"x\"}]}"));
                helper.assertTrue("too_many".equals(over.error()), "limit " + limit + " enforced: " + over.error());
                for (String id : extra) helper.assertTrue(RuleService.delete(server, mf, id) == null, "delete filler");
                helper.assertTrue(RuleService.rules(mf).size() == 1, "one rule left");
            })
            .thenIdle(20) // the rule records "computer off" first (edge trigger)
            .thenExecute(() -> CcComputers.find(helper.getLevel(), computerPos).turnOn())
            .thenWaitUntil(() -> {
                var handle = CcComputers.find(helper.getLevel(), computerPos);
                helper.assertTrue(handle.output("top") == 15, "the rule set the top output when the computer came on");
                helper.assertTrue(helper.getLevel().getBestNeighborSignal(computerPos.above()) == 15, "the world sees it");
            })
            .thenExecute(() -> {
                var doc = RuleService.document(server, mainframe.getMainframeId());
                var log = doc.getAsJsonArray("log");
                helper.assertTrue(log.size() == 1, "one run logged: " + log);
                var entry = log.get(0).getAsJsonObject();
                helper.assertTrue("ok".equals(entry.get("result").getAsString()) && !entry.get("manual").getAsBoolean(), "automatic run ok: " + entry);
                var rule = doc.getAsJsonArray("rules").get(0).getAsJsonObject();
                helper.assertTrue(rule.get("runs").getAsInt() == 1 && "ok".equals(rule.get("lastResult").getAsString()), "rule state: " + rule);
                CcComputers.find(helper.getLevel(), computerPos).setOutput("top", 0);
                helper.assertTrue(RuleService.runNow(server, mainframe.getMainframeId(), ruleId[0]) == null, "run now");
            })
            .thenWaitUntil(() -> helper.assertTrue(CcComputers.find(helper.getLevel(), computerPos).output("top") == 15, "run now set the output again"))
            .thenExecute(() -> {
                var log = RuleService.document(server, mainframe.getMainframeId()).getAsJsonArray("log");
                helper.assertTrue(log.size() == 2 && log.get(1).getAsJsonObject().get("manual").getAsBoolean(), "manual run logged: " + log);
                RuleService.forget(server, mainframe.getMainframeId());
                helper.assertTrue(RuleService.rules(mainframe.getMainframeId()).isEmpty(), "rules forgotten");
            })
            .thenSucceed();
    }
}
