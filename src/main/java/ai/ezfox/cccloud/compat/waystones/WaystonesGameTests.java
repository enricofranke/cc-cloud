package ai.ezfox.cccloud.compat.waystones;

import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import java.util.UUID;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/**
 * Waystones: the owner's waystone is listed (card row + map overlay in the snapshot, restricted section); a stranger's
 * private waystone is not.
 */
public final class WaystonesGameTests {
    private WaystonesGameTests() {
    }

    public static void card(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        String name = "Base " + helper.getLevel().getRandom().nextInt(100000);
        helper.startSequence()
            .thenExecute(() -> {
                var ours = WaystonesAPI.placeWaystone(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 6)), WaystoneTypes.ANDESITE).orElseThrow();
                var m = (MutableWaystone) ours;
                m.setOwnerUid(owner.getUUID());
                m.setName(Component.literal(name));
                var theirs = WaystonesAPI.placeWaystone(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 2)), WaystoneTypes.ANDESITE).orElseThrow();
                ((MutableWaystone) theirs).setOwnerUid(UUID.randomUUID());
                ((MutableWaystone) theirs).setName(Component.literal("Stranger"));
            })
            .thenWaitUntil(() -> {
                var section = MainframeCards.section(mainframe.getMainframeId(), WaystonesCard.KIND);
                helper.assertTrue(section != null, "waystones card");
                var snapshot = section.snapshot();
                helper.assertTrue(snapshot.get("restricted").getAsBoolean(), "restricted section");
                var items = snapshot.getAsJsonObject("waystones").getAsJsonArray("items");
                boolean found = false;
                for (var item : items) {
                    String n = item.getAsJsonObject().get("name").getAsString();
                    helper.assertTrue(!n.equals("Stranger"), "foreign waystone hidden");
                    if (n.equals(name)) found = true;
                }
                helper.assertTrue(found, "our waystone in the overlay: " + items);
                boolean row = false;
                for (int i = 1; i <= WaystonesCard.ROWS; i++) {
                    var v = section.values().get(WaystonesCard.ROW + i);
                    if (v != null && v.getAsString().startsWith(name + " – ")) row = true;
                }
                helper.assertTrue(section.values().get(WaystonesCard.EMPTY) == null, "no empty line while waystones exist");
                helper.assertTrue(row, "card row");
            })
            .thenSucceed();
    }
}
