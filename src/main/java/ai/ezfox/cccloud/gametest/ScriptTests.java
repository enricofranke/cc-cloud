package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.compat.cc.CcComputers;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.web.AccessApi;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.GameBridge;
import ai.ezfox.cccloud.web.Router;
import ai.ezfox.cccloud.web.ScriptApi;
import ai.ezfox.cccloud.web.WebAuthData;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.GameType;

/**
 * Script import on a real CC computer: upload through the server-side API (admin), refuse overwrite without the flag,
 * traversal and viewers, then "run now" – the uploaded file runs (via the ROM helper's background tab) and sets a
 * redstone output.
 */
public final class ScriptTests {
    private static final BlockPos COMPUTER = new BlockPos(4, 1, 4);
    private static final BlockPos UPLINK = new BlockPos(5, 1, 4);

    private ScriptTests() {
    }

    public static void uploadAndRun(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var viewer = UUID.randomUUID();
        var mainframe = CloudGameTests.placeMainframe(helper, owner);
        CloudGameTests.keepPowered(helper, mainframe);
        helper.setBlock(COMPUTER, BridgeTestSupport.block(helper, "computercraft:computer_advanced"));
        helper.setBlock(UPLINK, ModRegistry.CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.WEST));
        var computerPos = helper.absolutePos(COMPUTER);
        var auth = WebAuthData.get(server);
        AuthStore.Device[] devices = new AuthStore.Device[2];
        UUID[] uplinkId = {null};

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(mainframe.isOnline(), "mainframe online"))
            .thenExecute(() -> {
                var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
                uplink.forceLinkForTest(new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId()));
                uplinkId[0] = uplink.getUplinkId();
                devices[0] = device(auth, mainframe.getMainframeId(), owner.getUUID(), "Owner");
                mainframe.setRole(new NameAndId(viewer, "viewer"), Role.VIEWER);
                devices[1] = device(auth, mainframe.getMainframeId(), viewer, "Viewer");
                CcComputers.find(helper.getLevel(), computerPos).turnOn();
            })
            // Booted with the uplink attached (the ROM autorun helper starts its "cloud" tab).
            .thenWaitUntil(() -> {
                var uplink = (CloudUplinkBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(UPLINK));
                helper.assertTrue(uplink.computerCount() > 0, "computer attached");
            })
            .thenIdle(20)
            .thenExecute(() -> {
                var mf = mainframe.getMainframeId();
                byte[] lua = "redstone.setOutput(\"top\", true)\n".getBytes(StandardCharsets.UTF_8);
                var put = call(server, Router.Id.SCRIPT_PUT, devices[0], mf, uplinkId[0], "blink.lua", Map.of(), lua);
                helper.assertTrue(put.status() == 200, "upload: " + put.body());
                var again = call(server, Router.Id.SCRIPT_PUT, devices[0], mf, uplinkId[0], "blink.lua", Map.of(), lua);
                helper.assertTrue(again.status() == 409 && "exists".equals(again.body().get("error").getAsString()), "no overwrite without the flag: " + again.body());
                var over = call(server, Router.Id.SCRIPT_PUT, devices[0], mf, uplinkId[0], "blink.lua", Map.of("overwrite", "true"), lua);
                helper.assertTrue(over.status() == 200, "overwrite: " + over.body());
                var traversal = call(server, Router.Id.SCRIPT_PUT, devices[0], mf, uplinkId[0], "../evil.lua", Map.of(), lua);
                helper.assertTrue(traversal.status() == 400, "traversal: " + traversal.body());
                var rom = call(server, Router.Id.SCRIPT_GET, devices[0], mf, uplinkId[0], "rom/startup.lua", Map.of(), new byte[0]);
                helper.assertTrue(rom.status() == 400, "rom: " + rom.body());
                var asViewer = call(server, Router.Id.SCRIPT_PUT, devices[1], mf, uplinkId[0], "x.lua", Map.of(), lua);
                helper.assertTrue(asViewer.status() == 403 && "not_allowed".equals(asViewer.body().get("error").getAsString()), "viewer refused: " + asViewer.body());
                var list = call(server, Router.Id.SCRIPT_LIST, devices[0], mf, uplinkId[0], null, Map.of(), new byte[0]);
                helper.assertTrue(list.body().toString().contains("blink.lua") && list.body().getAsJsonObject("computer").get("advanced").getAsBoolean(), "list: " + list.body());
                var read = call(server, Router.Id.SCRIPT_GET, devices[0], mf, uplinkId[0], "blink.lua", Map.of(), new byte[0]);
                helper.assertTrue(read.raw() != null && new String(read.raw(), StandardCharsets.UTF_8).contains("setOutput"), "read back");
                var run = call(server, Router.Id.SCRIPT_RUN, devices[0], mf, uplinkId[0], null, Map.of(),
                    "{\"path\":\"blink.lua\",\"mode\":\"auto\"}".getBytes(StandardCharsets.UTF_8));
                helper.assertTrue(run.status() == 200 && "event".equals(run.body().get("started").getAsString()), "run: " + run.body());
            })
            .thenWaitUntil(() -> {
                var handle = CcComputers.find(helper.getLevel(), computerPos);
                helper.assertTrue(handle.output("top") == 15, "the uploaded script ran and set the top output");
            })
            .thenSucceed();
    }

    private static AuthStore.Device device(AuthStore auth, UUID mainframe, UUID player, String name) {
        var pending = auth.pair(auth.currentCode(mainframe, player).code(), name, "127.0.0.1");
        return auth.authenticate(auth.approve(pending.id(), player, name, true).token());
    }

    private static AccessApi.Response call(net.minecraft.server.MinecraftServer server, Router.Id route, AuthStore.Device device, UUID mainframe,
                                           UUID uplink, String file, Map<String, String> query, byte[] body) {
        return ScriptApi.handle(server, new GameBridge.ScriptRequest(route, device, mainframe, uplink, file, query, body, "127.0.0.1"));
    }
}
