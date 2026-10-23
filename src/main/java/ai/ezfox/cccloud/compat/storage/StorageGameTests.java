package ai.ezfox.cccloud.compat.storage;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.compat.card.ItemStorageCard;
import ai.ezfox.cccloud.compat.card.FluidTankCard;
import ai.ezfox.cccloud.uplink.Op;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * v0.8 storage cards: a controller (plus the storage it needs) at {@link ai.ezfox.cccloud.compat.card.CardTestSupport#DEVICE},
 * 100 iron ingots inserted through its item capability; the card shows the totals, the item search finds them, and
 * crafting is refused. No mod classes are used – only block ids and NeoForge capabilities, like the integration.
 */
public final class StorageGameTests {
    /** West of the device (the uplink is east of it). */
    static final BlockPos BEHIND = new BlockPos(1, 1, 2);

    private StorageGameTests() {
    }

    public static void dimStorageChest(GameTestHelper helper) {
        storage(helper, "dimstorage_storage", "dimstorage:dimensional_chest", null, 100);
    }

    public static void sophisticatedController(GameTestHelper helper) {
        storage(helper, "sophisticatedstorage_storage", "sophisticatedstorage:controller", "sophisticatedstorage:chest", 100);
    }

    /** Occultism's storage actuator has a base capacity of its own (stabilizers only add to it). */
    public static void occultismActuator(GameTestHelper helper) {
        storage(helper, "occultism_storage", "occultism:storage_controller", null, 100);
    }

    /** Functional Storage links drawers with its linking tool only; the bare controller shows an empty card. */
    public static void functionalStorageController(GameTestHelper helper) {
        storage(helper, "functionalstorage_storage", "functionalstorage:storage_controller", null, 0);
    }

    public static void dimStorageTank(GameTestHelper helper) {
        helper.setBlock(DEVICE, block(helper, "dimstorage:dimensional_tank"));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenExecute(() -> {
                var handler = FluidTankCard.handler(helper.getLevel(), helper.absolutePos(DEVICE), deviceSide());
                helper.assertTrue(handler != null, "tank has a fluid capability");
                try (var tx = Transaction.openRoot()) {
                    helper.assertTrue(handler.insert(FluidResource.of(Fluids.WATER), 4000, tx) == 4000, "4 buckets of water inserted");
                    tx.commit();
                }
            })
            .thenWaitUntil(() -> assertCard(helper, "dimstorage_tank", null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, "dimstorage_tank", linked);
                helper.assertTrue(text(helper, FluidTankCard.FLUID).equals("Water"), "fluid: " + text(helper, FluidTankCard.FLUID));
                helper.assertTrue(text(helper, FluidTankCard.AMOUNT).startsWith("4.00 k /"), "amount: " + text(helper, FluidTankCard.AMOUNT));
                helper.assertTrue(number(helper, FluidTankCard.FILL) > 0, "fill gauge");
                // The tank keeps broadcasting sync packets to every player while it ticks; other mods' GameTests log in mock
                // players that cannot receive them (crash). Remove it before the next batch starts.
                helper.setBlock(DEVICE, net.minecraft.world.level.block.Blocks.AIR);
            })
            .thenSucceed();
    }

    private static void storage(GameTestHelper helper, String kind, String controller, @Nullable String storage, int insert) {
        if (storage != null) helper.setBlock(BEHIND, block(helper, storage));
        helper.setBlock(DEVICE, block(helper, controller));
        placeUplink(helper);
        var linked = placeMainframe(helper);
        Consumer<GameTestHelper> fill = h -> {
            if (insert <= 0) return;
            var handler = ItemStorageCard.handler(h.getLevel(), h.absolutePos(DEVICE), deviceSide());
            h.assertTrue(handler != null, "controller has an item capability");
            try (var tx = Transaction.openRoot()) {
                int inserted = handler.insert(ItemResource.of(Items.IRON_INGOT), insert, tx);
                h.assertTrue(inserted == insert, "inserted " + inserted + " of " + insert);
                tx.commit();
            }
        };
        helper.startSequence()
            .thenIdle(5) // controllers find their storage on the next ticks
            .thenExecute(() -> fill.accept(helper))
            .thenWaitUntil(() -> assertCard(helper, kind, null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> {
                assertCard(helper, kind, linked);
                helper.assertTrue(text(helper, ItemStorageCard.ITEMS).startsWith(String.valueOf(insert)), "items: " + text(helper, ItemStorageCard.ITEMS));
                helper.assertTrue(text(helper, ItemStorageCard.TYPES).equals(insert > 0 ? "1" : "0"), "types: " + text(helper, ItemStorageCard.TYPES));
                var result = uplink(helper).handleQuery(ItemStorageCard.SEARCH, "iron");
                helper.assertTrue(result.error() == null, "search ok: " + result.error());
                if (insert > 0) {
                    helper.assertTrue(result.rows().size() == 1, "one row: " + result.rows());
                    JsonObject row = result.rows().get(0).getAsJsonObject();
                    helper.assertTrue(row.get("name").getAsString().equals("minecraft:iron_ingot") && row.get("count").getAsLong() == insert, "row: " + row);
                    helper.assertTrue(!row.get("craftable").getAsBoolean(), "never craftable");
                } else {
                    helper.assertTrue(result.rows().isEmpty(), "empty controller: " + result.rows());
                }
                var craft = uplink(helper).handleAction(ItemStorageCard.SEARCH, craftOp(), new Op.Craft("minecraft:iron_ingot", 1), "gametest", false).getNow(null);
                helper.assertTrue(craft != null && "not_craftable".equals(craft.error()), "no crafting: " + (craft == null ? null : craft.error()));
            })
            .thenSucceed();
    }

    private static JsonObject craftOp() {
        var json = new JsonObject();
        json.addProperty("op", "craft");
        json.addProperty("name", "minecraft:iron_ingot");
        json.addProperty("count", 1);
        return json;
    }
}
