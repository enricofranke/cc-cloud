package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.cc.CcComputers;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.script.ComputerTarget;
import ai.ezfox.cccloud.uplink.Op;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/**
 * A canvas with a real CC computer next to a mainframe: the program's frame reaches the widget, a click comes back as a
 * Lua event with the user's name, a hostile frame is an error in Lua, and the mainframe peripheral reads the cards.
 */
public final class CanvasTests {
    private static final BlockPos COMPUTER = new BlockPos(3, 1, 2);
    private static final BlockPos UPLINK = new BlockPos(4, 1, 2);
    private static final String PROGRAM = """
        local ui = require("cloud.ui")
        local dash = ui.connect()
        dash:clear()
        dash:label("seen", "waiting")
        dash:label("hostile", "waiting")
        dash:label("cards", "waiting")
        local paint
        paint = dash:canvas("paint", "Paint", {width=64, height=32, input="click"}, function(user, ev)
            dash:set("seen", user .. ":" .. ev.type .. ":" .. ev.x .. ":" .. ev.y)
            paint:clear("panel")
            paint:rect(ev.x, ev.y, 4, 4, "red")
            paint:draw()
        end)
        paint:clear("panel")
        paint:text(2, 2, "ready", "text")
        paint:draw()
        local ok, err = pcall(peripheral.find("cloud_uplink").draw, "paint", {{"t", 1, 2, "<img src=x>", "url(evil)"}})
        dash:set("hostile", ok and "accepted" or "refused")
        local mainframe = ui.mainframe()
        dash:every(0.25, function()
            local cards = mainframe.getCards()
            if not cards or #cards == 0 then return end
            local card = mainframe.getCard(cards[1].id)
            for _, widget in ipairs(card.widgets) do
                if widget.type == "canvas" then dash:set("cards", cards[1].id .. (widget.value == nil and ":no-frame" or ":frame")) end
            end
        end)
        dash:run()
        """;

    private CanvasTests() {
    }

    public static void realComputer(GameTestHelper helper) {
        CloudGameTests.throttleForComputers(helper);
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(COMPUTER, BridgeTestSupport.block(helper, "computercraft:computer_normal"));
        // facing = direction from the uplink towards the block it sticks to (the computer is at -x).
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
        var computerPos = helper.absolutePos(COMPUTER);

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                uplink(helper).forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
                CcComputers.find(helper.getLevel(), computerPos).turnOn();
            })
            .thenWaitUntil(() -> helper.assertTrue(uplink(helper).computerCount() > 0, "computer attached"))
            // The shell's prompt: this boot is past its startup files, so it cannot start the program the reboot would then kill.
            .thenWaitUntil(() -> helper.assertTrue(terminal(helper).contains(">"), "shell prompt: " + terminal(helper)))
            .thenExecute(() -> {
                var target = ComputerTarget.of(uplink(helper));
                helper.assertTrue(target != null, "computer target");
                try {
                    target.store(helper.getLevel().getServer()).writeNew("startup.lua", PROGRAM.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw helper.assertionException(e.toString());
                }
                // No runner tab on a first run, so the program starts with the computer.
                target.reboot();
            })
            // The frame comes through the budget in the uplink's tick.
            .thenWaitUntil(() -> {
                var value = widget(helper, "paint").value();
                helper.assertTrue(value != null && value.isJsonObject() && value.getAsJsonObject().has("c"), "frame reached the widget: " + value);
                helper.assertTrue(value.toString().contains("ready"), "the program's text: " + value);
            })
            .thenWaitUntil(() -> helper.assertTrue("refused".equals(text(helper, "hostile")), "a hostile frame is an error in Lua: " + text(helper, "hostile")))
            .thenExecute(() -> {
                helper.assertTrue(!widget(helper, "paint").value().toString().contains("img"), "a refused frame never reaches the widget");
                var outside = uplink(helper).handleAction("paint", JsonNull.INSTANCE, new Op.CanvasInput("click", 999, 9), "Alice", false).join();
                helper.assertTrue(outside.error() != null, "a click outside the surface is refused");
                var drag = uplink(helper).handleAction("paint", JsonNull.INSTANCE, new Op.CanvasInput("drag", 7, 9), "Alice", false).join();
                helper.assertTrue(drag.error() != null, "a click canvas takes no drags");
                var click = uplink(helper).handleAction("paint", JsonNull.INSTANCE, new Op.CanvasInput("click", 7, 9), "Alice", false).join();
                helper.assertTrue(click.error() == null, "click: " + click.error());
            })
            .thenWaitUntil(() -> {
                helper.assertTrue("Alice:click:7:9".equals(text(helper, "seen")), "event with the user's name: " + text(helper, "seen") + ", terminal: " + terminal(helper));
                helper.assertTrue(widget(helper, "paint").value().toString().contains("[\"r\",7,9,4,4,\"red\"]"), "the handler drew: " + widget(helper, "paint").value());
            })
            // The peripheral reads this uplink's card, without the frame.
            .thenWaitUntil(() -> helper.assertTrue((uplink(helper).getUplinkId() + ":no-frame").equals(text(helper, "cards")), "cards: " + text(helper, "cards")))
            .thenExecute(() -> CcComputers.find(helper.getLevel(), computerPos).shutdown())
            .thenSucceed();
    }

    private static Widget widget(GameTestHelper helper, String id) {
        var widget = uplink(helper).getWidget(id);
        if (widget == null) throw helper.assertionException("no widget " + id + ", terminal: " + terminal(helper));
        return widget;
    }

    /** What the computer shows, so a failing test names the Lua error. */
    private static String terminal(GameTestHelper helper) {
        var handle = CcComputers.find(helper.getLevel(), helper.absolutePos(COMPUTER));
        var frame = handle == null ? null : handle.frame();
        return frame == null ? "off" : String.join(" | ", frame.text()).replaceAll(" {2,}", " ");
    }

    private static String text(GameTestHelper helper, String id) {
        var value = widget(helper, id).value();
        return value == null || !value.isJsonPrimitive() ? String.valueOf(value) : value.getAsString();
    }

    private static CloudUplinkBlockEntity uplink(GameTestHelper helper) {
        return (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
    }
}
