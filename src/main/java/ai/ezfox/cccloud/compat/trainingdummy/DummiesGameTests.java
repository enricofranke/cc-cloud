package ai.ezfox.cccloud.compat.trainingdummy;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import com.trainingdummy.entity.DummyEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;

/** Training Dummy: the card lists a dummy next to the mainframe, then counts hits into DPS, last hit and total. */
public final class DummiesGameTests {
    private DummiesGameTests() {
    }

    public static void dps(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.CREATIVE);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        DummyEntity[] dummy = {null};
        helper.startSequence()
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), DummiesCard.KIND);
                helper.assertTrue(section != null && section.values().get(DummiesCard.EMPTY) != null, "no dummy yet");
                helper.assertTrue(section.snapshot().get("restricted").getAsBoolean(), "restricted section");
            })
            .thenExecute(() -> {
                var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("trainingdummy:dummy"));
                var entity = (DummyEntity) type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
                var p = helper.absolutePos(new BlockPos(4, 1, 0));
                entity.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                helper.getLevel().addFreshEntity(entity);
                dummy[0] = entity;
            })
            .thenWaitUntil(() -> {
                var values = MainframeCards.section(mainframe.getMainframeId(), DummiesCard.KIND).values();
                helper.assertTrue(values.get(DummiesCard.ROW + 1) != null && values.get(DummiesCard.ROW + 1).getAsString().startsWith("Dummy: "), "dummy listed: " + values.get(DummiesCard.ROW + 1));
            })
            .thenExecute(() -> {
                var level = helper.getLevel();
                dummy[0].invulnerableTime = 0;
                dummy[0].hurtServer(level, level.damageSources().generic(), 6f);
            })
            .thenWaitUntil(() -> {
                var values = MainframeCards.section(mainframe.getMainframeId(), DummiesCard.KIND).values();
                String row = values.get(DummiesCard.ROW + 1).getAsString();
                var streak = DummiesCard.STREAKS.get(dummy[0].getUUID());
                helper.assertTrue(streak != null && streak.hits.stream().anyMatch(h -> h.amount() == 6f), "the hit is counted");
                helper.assertTrue(row.contains(" DPS, last ") && row.contains("total "), "dummy row: " + row);
                helper.assertTrue(values.get(DummiesCard.HIT).getAsBoolean(), "being hit");
                helper.assertTrue(!values.get(DummiesCard.BEST).getAsString().startsWith("0 "), "best DPS: " + values.get(DummiesCard.BEST));
            })
            .thenExecute(() -> dummy[0].discard())
            .thenSucceed();
    }
}
