package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.cc.CcComputers;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.terminal.TerminalInput;
import ai.ezfox.cccloud.terminal.TerminalService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/**
 * v0.5 E browser terminal on a real CC computer: the full picture after opening (CraftOS banner), typed input runs in
 * the shell ({@code id} prints the computer id in a changed row), the viewer is dropped when it loses its rights.
 */
public final class TerminalTests {
    private static final BlockPos COMPUTER = new BlockPos(4, 1, 4);
    private static final BlockPos UPLINK = new BlockPos(5, 1, 4);

    private TerminalTests() {
    }

    static final class Capture implements TerminalService.Viewer {
        final List<JsonObject> messages = new ArrayList<>();
        volatile boolean allowed = true;

        @Override
        public synchronized void send(List<String> list) {
            for (String m : list) messages.add(JsonParser.parseString(m).getAsJsonObject());
        }

        @Override
        public boolean allowed() {
            return allowed;
        }

        synchronized String screen() {
            var rows = new java.util.TreeMap<Integer, String>();
            for (var m : messages) {
                if (!"term".equals(m.get("t").getAsString()) || !m.has("rows")) continue;
                if (m.get("full").getAsBoolean()) rows.clear();
                for (var r : m.getAsJsonArray("rows")) rows.put(r.getAsJsonObject().get("y").getAsInt(), r.getAsJsonObject().get("text").getAsString());
            }
            return String.join("\n", rows.values());
        }

        synchronized boolean has(String type) {
            return messages.stream().anyMatch(m -> type.equals(m.get("t").getAsString()));
        }
    }

    public static void typeAndSee(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(COMPUTER, BridgeTestSupport.block(helper, "computercraft:computer_advanced"));
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
        var computerPos = helper.absolutePos(COMPUTER);
        var viewer = new Capture();
        UUID[] uplinkId = {null};

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
                uplink.forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
                uplinkId[0] = uplink.getUplinkId();
                helper.assertTrue("unknown_computer".equals(TerminalService.open(mainframe.getMainframeId(), UUID.randomUUID(), viewer)), "unknown uplink");
                helper.assertTrue(TerminalService.open(mainframe.getMainframeId(), uplinkId[0], viewer) == null, "opened");
                CcComputers.find(helper.getLevel(), computerPos).turnOn();
            })
            .thenWaitUntil(() -> helper.assertTrue(viewer.screen().contains("CraftOS"), "the shell banner arrives: " + viewer.screen()))
            .thenWaitUntil(() -> helper.assertTrue(viewer.screen().contains("> "), "prompt: " + viewer.screen()))
            .thenExecute(() -> {
                var json = new StringBuilder("[");
                for (char c : "id".toCharArray()) json.append("{\"type\":\"char\",\"c\":\"").append(c).append("\"},");
                json.append("{\"type\":\"key\",\"key\":257,\"held\":false}]");
                var parsed = TerminalInput.parse(JsonParser.parseString(json.toString()), true, 51, 19);
                helper.assertTrue(parsed.events() != null, "input valid");
                helper.assertTrue(TerminalService.input(mainframe.getMainframeId(), uplinkId[0], viewer, parsed.events()) == null, "input queued");
            })
            .thenWaitUntil(() -> helper.assertTrue(viewer.screen().contains("This is computer #"), "the typed program ran: " + viewer.screen()))
            .thenExecute(() -> viewer.allowed = false)
            .thenWaitUntil(() -> helper.assertTrue(viewer.has("term_closed") && !TerminalService.isOpen(mainframe.getMainframeId(), uplinkId[0], viewer),
                "rights lost → closed"))
            .thenSucceed();
    }
}
