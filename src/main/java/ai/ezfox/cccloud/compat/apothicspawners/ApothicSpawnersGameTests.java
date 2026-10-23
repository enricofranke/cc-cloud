package ai.ezfox.cccloud.compat.apothicspawners;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

/** Apothic Spawners: a (replaced) vanilla spawner set to pigs shows the mob, its default stats and no upgrades. */
public final class ApothicSpawnersGameTests {
    private ApothicSpawnersGameTests() {
    }

    public static void spawner(GameTestHelper helper) {
        helper.setBlock(DEVICE, Blocks.SPAWNER);
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenExecute(() -> {
                var be = (SpawnerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                be.setEntityId(EntityType.PIG, helper.getLevel().getRandom());
                be.setChanged();
            })
            .thenWaitUntil(() -> assertCard(helper, "apothicspawners_spawner", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "apothicspawners_spawner", linked);
                helper.assertTrue(text(helper, ApothicSpawnerCards.MOB).equals("Pig"), "mob: " + text(helper, ApothicSpawnerCards.MOB));
                helper.assertTrue(text(helper, ApothicSpawnerCards.DELAY).equals("200–800 ticks"), "delay: " + text(helper, ApothicSpawnerCards.DELAY));
                helper.assertTrue(text(helper, ApothicSpawnerCards.COUNT).equals("4"), "count: " + text(helper, ApothicSpawnerCards.COUNT));
                helper.assertTrue(text(helper, ApothicSpawnerCards.UPGRADES).equals("None"), "upgrades: " + text(helper, ApothicSpawnerCards.UPGRADES));
                helper.assertTrue(!bool(helper, ApothicSpawnerCards.REDSTONE_CONTROL), "no redstone upgrade");
                helper.assertTrue(!bool(helper, ApothicSpawnerCards.ACTIVE), "no player nearby");
            })
            .thenSucceed();
    }
}
