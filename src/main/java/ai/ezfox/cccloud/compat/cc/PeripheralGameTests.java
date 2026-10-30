package ai.ezfox.cccloud.compat.cc;

import static ai.ezfox.cccloud.compat.card.CardTestSupport.*;

import ai.ezfox.cccloud.uplink.WidgetType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

/**
 * The generic Peripheral card (wave 4). A vanilla chest gets it through CC's generic inventory methods (only
 * {@code size} and {@code list} are offered; {@code getItemDetail}, {@code pushItems} take arguments); a lectern carries
 * a test-only peripheral (GameTest runs only) with every kind of method: getters of each result type, one that needs an
 * argument, one that asks for the computer, a main-thread task, a failing one, a side-effect method, and a boolean
 * setter that becomes the switch (confirm + PIN).
 */
public final class PeripheralGameTests {
    private PeripheralGameTests() {
    }

    // ---- test peripheral ---------------------------------------------------------------------------------------------

    private static final Map<BlockEntity, TestPeripheral> PERIPHERALS = Collections.synchronizedMap(new WeakHashMap<>());

    /** GameTest runs only ({@code -Dcccloud.gametest}): lecterns offer {@link TestPeripheral}. */
    public static void registerTestCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), BlockEntityType.LECTERN, (be, side) -> PERIPHERALS.computeIfAbsent(be, b -> new TestPeripheral()));
    }

    /** A peripheral with one method of each kind the card must handle. */
    public static final class TestPeripheral implements IPeripheral {
        boolean enabled;
        int count = 3;
        int unpickedCalls;
        int resetCalls;

        @Override
        public String getType() {
            return "test_machine";
        }

        @Override
        public boolean equals(@Nullable IPeripheral other) {
            return other == this;
        }

        @LuaFunction
        public final int getCount() {
            return count;
        }

        @LuaFunction(mainThread = true)
        public final boolean isEnabled() {
            return enabled;
        }

        @LuaFunction(mainThread = true)
        public final void setEnabled(boolean on) {
            enabled = on;
        }

        @LuaFunction
        public final String getLongText() {
            return "line one\n\u0007§cred " + "x".repeat(500);
        }

        @LuaFunction
        public final Map<String, Object> getTable() {
            var map = new LinkedHashMap<String, Object>();
            map.put("b", "two");
            map.put("a", 1);
            return map;
        }

        @LuaFunction
        public final MethodResult getViaTask(ILuaContext context) throws LuaException {
            return context.executeMainThreadTask(() -> new Object[]{42});
        }

        @LuaFunction
        public final int getFails() throws LuaException {
            throw new LuaException("broken");
        }

        @LuaFunction
        public final int getUnpicked() {
            return ++unpickedCalls;
        }

        @LuaFunction
        public final int getWithArg(int slot) {
            return slot;
        }

        @LuaFunction
        public final String getCaller(IComputerAccess computer) {
            return computer.getAttachmentName();
        }

        @LuaFunction
        public final void reset() {
            resetCalls++;
        }
    }

    private static TestPeripheral peripheral(GameTestHelper helper) {
        var be = helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
        var p = be == null ? null : PERIPHERALS.get(be);
        if (p == null) throw helper.assertionException("test peripheral missing");
        return p;
    }

    private static PeripheralCard card(GameTestHelper helper) {
        if (!(uplink(helper).deviceCard() instanceof PeripheralCard card)) throw helper.assertionException("no Peripheral card");
        return card;
    }

    private static @Nullable String settings(GameTestHelper helper, String json) {
        return uplink(helper).handleCardSettings(JsonParser.parseString(json).getAsJsonObject(), "gametest");
    }

    private static JsonObject info(GameTestHelper helper) {
        var info = uplink(helper).snapshot().getAsJsonObject("cardSettings");
        if (info == null) throw helper.assertionException("snapshot without cardSettings");
        return info;
    }

    // ---- tests -------------------------------------------------------------------------------------------------------

    /** A chest: CC's generic inventory methods; only the argument-free ones are offered; picked values show; settings stay with the block. */
    public static void chest(GameTestHelper helper) {
        helper.setBlock(DEVICE, Blocks.CHEST);
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenExecute(() -> {
                var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
                chest.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
                chest.setItem(5, new ItemStack(Items.STONE, 64));
            })
            .thenWaitUntil(() -> assertCard(helper, PeripheralCard.KIND, null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, PeripheralCard.KIND, linked))
            .thenExecute(() -> {
                var card = card(helper);
                helper.assertTrue(card.getters().containsAll(java.util.Set.of("size", "list")), "size and list offered: " + card.getters());
                helper.assertTrue(!card.getters().contains("getItemDetail") && !card.getters().contains("pushItems") && !card.getters().contains("getItemLimit"),
                    "methods with arguments are never offered: " + card.getters());
                helper.assertTrue(card.setters().isEmpty(), "a chest has no switch");
                helper.assertTrue(text(helper, PeripheralCard.TYPE).contains("inventory"), "type: " + text(helper, PeripheralCard.TYPE));
                helper.assertTrue(uplink(helper).getWidget(PeripheralCard.HINT) != null, "hint until values are picked");
                helper.assertTrue("unknown_method".equals(settings(helper, "{\"show\":[\"pushItems\"]}")), "a method with arguments is refused");
                helper.assertTrue("too_many".equals(settings(helper, "{\"show\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"]}")), "at most 6");
                helper.assertTrue("invalid_settings".equals(settings(helper, "{\"show\":[\"size\"],\"run\":\"pushItems\"}")), "unknown keys refused");
                helper.assertTrue(settings(helper, "{\"show\":[\"size\",\"list\"],\"charts\":[\"size\"]}") == null, "settings accepted");
                helper.assertTrue(info(helper).getAsJsonObject("settings").getAsJsonArray("show").size() == 2, "snapshot carries the settings");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue("27".equals(text(helper, "v1")), "size: " + text(helper, "v1"));
                helper.assertTrue("2 entries".equals(text(helper, "v2")), "list: " + text(helper, "v2"));
                var chart = uplink(helper).getWidget("c1");
                helper.assertTrue(chart != null && chart.type() == WidgetType.CHART, "size chart");
                helper.assertTrue(uplink(helper).getWidget(PeripheralCard.HINT) == null, "no hint with values");
            })
            // the chest is replaced by another chest: a new card, the saved settings come back
            .thenExecute(() -> helper.setBlock(DEVICE, Blocks.STONE))
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).deviceKind().isEmpty(), "card gone with the chest"))
            .thenExecute(() -> helper.setBlock(DEVICE, Blocks.CHEST))
            .thenWaitUntil(() -> {
                assertCard(helper, PeripheralCard.KIND, linked);
                helper.assertTrue("27".equals(text(helper, "v1")), "settings kept for the same block: " + text(helper, "v1"));
            })
            .thenSucceed();
    }

    /**
     * A peripheral's own methods: getters of every kind, methods with arguments, the computer or side effects are not
     * offered, only picked getters are ever called, results are cut and cleaned, a main-thread task resolves, a failing
     * getter shows its error, and the setter switch needs confirm + PIN and calls the setter.
     */
    public static void ownMethods(GameTestHelper helper) {
        helper.setBlock(DEVICE, Blocks.LECTERN);
        placeUplink(helper);
        var linked = placeMainframe(helper);
        helper.startSequence()
            .thenWaitUntil(() -> assertCard(helper, PeripheralCard.KIND, null))
            .thenExecute(() -> bind(helper, linked))
            .thenWaitUntil(() -> assertCard(helper, PeripheralCard.KIND, linked))
            .thenExecute(() -> {
                var card = card(helper);
                helper.assertTrue(card.getters().equals(java.util.Set.of("getCount", "isEnabled", "getLongText", "getTable", "getViaTask", "getFails", "getUnpicked")),
                    "getters: " + card.getters());
                helper.assertTrue(card.setters().equals(java.util.Set.of("setEnabled")), "setters: " + card.setters());
                var getters = info(helper).getAsJsonArray("getters");
                boolean typed = false;
                for (var g : getters) {
                    var o = g.getAsJsonObject();
                    if (o.get("name").getAsString().equals("isEnabled")) typed = o.get("value").getAsString().equals("bool");
                }
                helper.assertTrue(typed, "isEnabled is offered as a yes/no value");
                helper.assertTrue("unknown_method".equals(settings(helper, "{\"show\":[\"getWithArg\"]}")), "argument method refused");
                helper.assertTrue("unknown_method".equals(settings(helper, "{\"show\":[\"getCount\"],\"toggle\":\"reset\"}")), "only boolean setters");
                helper.assertTrue("unknown_method".equals(settings(helper, "{\"show\":[\"getTable\"],\"charts\":[\"getTable\"]}")), "charts only for numbers");
                helper.assertTrue(settings(helper, "{\"show\":[\"getCount\",\"isEnabled\",\"getLongText\",\"getTable\",\"getViaTask\",\"getFails\"],"
                    + "\"charts\":[\"getCount\"],\"toggle\":\"setEnabled\",\"pin\":true}") == null, "settings accepted");
                var sw = uplink(helper).getWidget(PeripheralCard.SWITCH);
                helper.assertTrue(sw != null && sw.type() == WidgetType.TOGGLE && sw.opts().has("pin") && sw.opts().has("confirm"), "switch with PIN and confirm");
                helper.assertTrue(uplink(helper).getWidget("v2").type() == WidgetType.INDICATOR, "boolean getter as indicator");
            })
            .thenWaitUntil(() -> {
                helper.assertTrue("3".equals(text(helper, "v1")), "count: " + text(helper, "v1"));
                helper.assertTrue(!bool(helper, "v2"), "disabled");
                String longText = text(helper, "v3");
                helper.assertTrue(longText.length() <= 64 && longText.startsWith("line one red x") && longText.endsWith("…"), "cut and cleaned: " + longText);
                helper.assertTrue("a 1, b two".equals(text(helper, "v4")), "small table: " + text(helper, "v4"));
                helper.assertTrue("42".equals(text(helper, "v5")), "main-thread task: " + text(helper, "v5"));
                helper.assertTrue("error: broken".equals(text(helper, "v6")), "error shown: " + text(helper, "v6"));
            })
            .thenExecute(() -> {
                var p = peripheral(helper);
                helper.assertTrue(p.unpickedCalls == 0 && p.resetCalls == 0, "methods that were not picked are never called");
                helper.assertTrue("pin_required".equals(act(helper, PeripheralCard.SWITCH, new JsonPrimitive(true), false)), "switch needs the PIN");
                helper.assertTrue(!p.enabled, "not switched without the PIN");
                helper.assertTrue(act(helper, PeripheralCard.SWITCH, new JsonPrimitive(true), true) == null, "switched with the PIN");
                helper.assertTrue(p.enabled, "setter called");
                p.count = 7;
            })
            .thenWaitUntil(() -> {
                helper.assertTrue(bool(helper, PeripheralCard.SWITCH), "switch shows isEnabled");
                helper.assertTrue(bool(helper, "v2"), "value follows");
                helper.assertTrue("7".equals(text(helper, "v1")), "polled again: " + text(helper, "v1"));
            })
            .thenExecute(() -> {
                helper.assertTrue(settings(helper, "{\"show\":[\"getCount\"],\"toggle\":\"setEnabled\",\"pin\":false}") == null, "PIN off");
                helper.assertTrue(act(helper, PeripheralCard.SWITCH, new JsonPrimitive(false), false) == null, "no PIN needed now");
                helper.assertTrue(!peripheral(helper).enabled, "switched off");
                helper.assertTrue(peripheral(helper).unpickedCalls == 0 && peripheral(helper).resetCalls == 0, "still never called");
            })
            .thenSucceed();
    }
}
