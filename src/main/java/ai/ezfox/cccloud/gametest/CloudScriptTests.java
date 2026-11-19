package ai.ezfox.cccloud.gametest;

import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.code.game.CodeProtocol;
import ai.ezfox.cccloud.code.game.CodeService;
import ai.ezfox.cccloud.code.runtime.ScriptLog;
import ai.ezfox.cccloud.code.web.CodeViewer;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.rsdevice.RsCards;
import ai.ezfox.cccloud.rsdevice.RsDeviceBlockEntity;
import ai.ezfox.cccloud.rsdevice.RsDevices;
import ai.ezfox.cccloud.rsdevice.RsPlateBlock;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.ClientMessages;
import ai.ezfox.cccloud.web.GameBridge.CodeReply;
import ai.ezfox.cccloud.web.InGameClients;
import ai.ezfox.cccloud.web.WebAuthData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jspecify.annotations.Nullable;

/**
 * Cloud Scripts in a real world, without CC: Tweaked: saved and started through the dashboard protocol by the owner (a
 * viewer may not), the script reads the card of a Cloud Sensor and a Cloud Lamp, reacts when the lever by the sensor
 * is switched on and turns the lamp on (audited as the script, acting for its author); the redstone kill switch stops
 * it, autostart brings it back when the switch is released, and {@code /cccloud scripts stop <name>} stops it again (not
 * for players without op).
 */
public final class CloudScriptTests {
    private static final CodeViewer NOBODY = new CodeViewer() {
        @Override
        public void send(List<String> messages) {
        }

        @Override
        public boolean allowed() {
            return true;
        }
    };

    private CloudScriptTests() {
    }

    private static AuthStore.Device device(UUID player, String name, UUID mainframeId) {
        long now = System.currentTimeMillis();
        return new AuthStore.Device(InGameClients.DEVICE_PREFIX + player, mainframeId, name + " (tablet)", true, now, now, player, name, "in-game", "");
    }

    private static CompletableFuture<CodeReply> code(MinecraftServer server, AuthStore.Device device, UUID mainframeId, String op, @Nullable String id,
                                                     @Nullable String name, @Nullable String source, @Nullable Boolean enabled) {
        var command = new ClientMessages.CodeCommand("r1", op, id, name, source, null, null, enabled, null);
        return CodeProtocol.handle(server, device, mainframeId, command, NOBODY);
    }

    private static CompletableFuture<CodeReply> saveWasm(MinecraftServer server, AuthStore.Device device, UUID mainframeId, String name, String stored) {
        var command = new ClientMessages.CodeCommand("r1", "save", null, name, stored, null, null, null, null, "wasm");
        return CodeProtocol.handle(server, device, mainframeId, command, NOBODY);
    }

    private static byte[] resource(String path) {
        try (var in = CloudScriptTests.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A module that imports WASI's fd_write: the server must refuse it. */
    private static final byte[] WASI_MODULE = {0, 'a', 's', 'm', 1, 0, 0, 0,
        1, 4, 1, 0x60, 0, 0, // type 0: () -> ()
        2, 0x23, 1, 22, 'w', 'a', 's', 'i', '_', 's', 'n', 'a', 'p', 's', 'h', 'o', 't', '_', 'p', 'r', 'e', 'v', 'i', 'e', 'w', '1', 8, 'f', 'd', '_', 'w', 'r',
        'i', 't', 'e', 0, 0, // the import
        7, 8, 1, 4, 'm', 'a', 'i', 'n', 0, 0}; // export main = the import

    private static String logText(UUID mainframeId, String name) {
        ScriptLog log = CodeService.logForTest(mainframeId, name);
        if (log == null) return "";
        var sb = new StringBuilder();
        for (var line : log.since(0)) sb.append(line.level()).append(": ").append(line.text()).append('\n');
        return sb.toString();
    }

    private static int run(CommandDispatcher<CommandSourceStack> dispatcher, String command, CommandSourceStack source) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    private static RsDeviceBlockEntity at(GameTestHelper helper, BlockPos pos) {
        if (!(helper.getLevel().getBlockEntity(pos) instanceof RsDeviceBlockEntity device)) throw helper.assertionException("no redstone device at " + pos);
        return device;
    }

    private static boolean ready(UUID mainframeId, RsDeviceBlockEntity device) {
        var section = RsDevices.sectionOfDevice(mainframeId, device.deviceId());
        return device.status() == CloudStatus.ONLINE && section != null && section.isOnline();
    }

    /**
     * The same scene with a WebAssembly script: the AssemblyScript example {@code porch-light.wasm} (built from
     * {@code wasm/assemblyscript/examples/porch-light.ts}) finds the card with the sensor and the lamp itself and turns the
     * lamp on when the lever switches the sensor; a module with a WASI import is refused when saved and cannot start.
     */
    public static void wasmSensorTurnsLampOn(GameTestHelper helper) {
        CloudGameTests.throttleForComputers(helper);
        var level = helper.getLevel();
        var server = level.getServer();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        CloudMainframeBlockEntity mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        UUID mf = mainframe.getMainframeId();
        var link = new MainframeLink(GlobalPos.of(level.dimension(), mainframe.getBlockPos()), mf);
        var base = mainframe.getBlockPos();
        var sensor = base.west(2).north(2).above(1);
        var lever = sensor.west();
        var lamp = base.east(2).north(2).above(1);
        var placed = new ArrayList<BlockPos>(List.of(lever, lever.below(), lamp, sensor.below(), sensor));
        level.setBlock(lever.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR), Block.UPDATE_ALL);
        level.setBlock(sensor.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(sensor, ModRegistry.CLOUD_SENSOR.get().defaultBlockState().setValue(RsPlateBlock.FACING, Direction.DOWN), Block.UPDATE_ALL);
        level.setBlock(lamp, ModRegistry.CLOUD_LAMP.get().defaultBlockState(), Block.UPDATE_ALL);
        var ownerDevice = device(owner.getUUID(), "Owner", mf);
        String name = "Porch light wasm";
        String[] ids = {null, null};
        @SuppressWarnings("unchecked") CompletableFuture<CodeReply>[] pending = new CompletableFuture[2];

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(CloudNetwork.mainframe(mf) != null && mainframe.isOnline(), "mainframe registered and online"))
            .thenExecute(() -> {
                for (var pos : List.of(sensor, lamp)) at(helper, pos).bind(link, owner);
                helper.assertTrue(CodeService.enabled(), "Cloud Scripts are on");
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(mf, at(helper, sensor)) && ready(mf, at(helper, lamp)), "sensor and lamp online"))
            .thenExecute(() -> {
                var wasi = new ai.ezfox.cccloud.code.wasm.WasmSource(WASI_MODULE, null, null).encode();
                pending[0] = saveWasm(server, ownerDevice, mf, "Wasi", wasi);
                var stored = new ai.ezfox.cccloud.code.wasm.WasmSource(resource("/cccloud_gametest/wasm/porch-light.wasm"), "// porch-light.ts", "assemblyscript").encode();
                pending[1] = saveWasm(server, ownerDevice, mf, name, stored);
            })
            .thenWaitUntil(() -> helper.assertTrue(pending[0].isDone() && pending[1].isDone(), "saved (checked on a script worker)"))
            .thenExecute(() -> {
                var wasi = pending[0].join();
                helper.assertTrue(wasi.error() == null, "a module with problems is still saved: " + wasi.error());
                String problems = wasi.data().getAsJsonArray("diagnostics").toString();
                helper.assertTrue(problems.contains("wasi_snapshot_preview1.fd_write"), "the WASI import is named: " + problems);
                ids[1] = wasi.data().get("id").getAsString();
                var refused = code(server, ownerDevice, mf, "start", ids[1], null, null, null).join();
                helper.assertTrue("has_errors".equals(refused.error()), "a refused module cannot start: " + refused.error());
                var saved = pending[1].join();
                helper.assertTrue(saved.error() == null, "save: " + saved.error() + " " + saved.detail());
                helper.assertTrue(saved.data().getAsJsonArray("diagnostics").isEmpty(), "the example passes the check: " + saved.data());
                ids[0] = saved.data().get("id").getAsString();
                var record = CodeService.all().getOrDefault(mf, List.of()).stream().filter(r -> r.id().equals(ids[0])).findFirst().orElse(null);
                helper.assertTrue(record != null && "wasm".equals(record.language()), "stored as a WebAssembly script");
                var started = code(server, ownerDevice, mf, "start", ids[0], null, null, null).join();
                helper.assertTrue(started.error() == null, "started: " + started.error() + " " + started.detail());
            })
            .thenWaitUntil(() -> {
                var runtime = CodeService.runtimeForTest(mf, name);
                helper.assertTrue(logText(mf, name).contains("sees ") && runtime != null && runtime.handlerCount() > 0,
                    "the script found the card and listens: " + logText(mf, name));
            })
            .thenIdle(10)
            .thenExecute(() -> level.setBlock(lever, level.getBlockState(lever).setValue(LeverBlock.POWERED, true), Block.UPDATE_ALL))
            .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(lamp).getLightEmission() == 15, "the script turned the lamp on: " + logText(mf, name)))
            // the lamp is lit on the server thread; the script logs the answer a moment later on its own thread
            .thenWaitUntil(() -> helper.assertTrue(logText(mf, name).contains("lamp true true nil"), "the action went through: " + logText(mf, name)))
            .thenExecute(() -> {
                var runtime = CodeService.runtimeForTest(mf, name);
                helper.assertTrue(runtime != null && runtime.cpuTotalNanos() > 0, "it runs on its own thread and books CPU time");
                var stopped = code(server, ownerDevice, mf, "stop", ids[0], null, null, null).join();
                helper.assertTrue(stopped.error() == null, "stop: " + stopped.error());
            })
            .thenWaitUntil(() -> helper.assertTrue(CodeService.runtimeForTest(mf, name) == null, "stopped"))
            .thenExecute(() -> {
                for (var id : ids) if (id != null) code(server, ownerDevice, mf, "delete", id, null, null, null).join();
                for (var pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenSucceed();
    }

    public static void sensorTurnsLampOn(GameTestHelper helper) {
        CloudGameTests.throttleForComputers(helper); // scripts run on their own threads in wall-clock time
        var level = helper.getLevel();
        var server = level.getServer();
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        CloudMainframeBlockEntity mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        UUID mf = mainframe.getMainframeId();
        var link = new MainframeLink(GlobalPos.of(level.dimension(), mainframe.getBlockPos()), mf);
        var base = mainframe.getBlockPos();
        var sensor = base.west(2).north(2).above(1);
        var lever = sensor.west();
        var lamp = base.east(2).north(2).above(1);
        var killSwitch = base.west();
        var placed = new ArrayList<BlockPos>(List.of(lever, lever.below(), lamp, sensor.below(), sensor, killSwitch));
        level.setBlock(lever.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR), Block.UPDATE_ALL);
        level.setBlock(sensor.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(sensor, ModRegistry.CLOUD_SENSOR.get().defaultBlockState().setValue(RsPlateBlock.FACING, Direction.DOWN), Block.UPDATE_ALL);
        level.setBlock(lamp, ModRegistry.CLOUD_LAMP.get().defaultBlockState(), Block.UPDATE_ALL);

        var ownerDevice = device(owner.getUUID(), "Owner", mf);
        UUID viewer = UUID.randomUUID();
        var viewerDevice = device(viewer, "Vic", mf);
        String name = "Porch light";
        String[] scriptId = {null};
        @SuppressWarnings("unchecked") CompletableFuture<CodeReply>[] pending = new CompletableFuture[1];

        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(CloudNetwork.mainframe(mf) != null && mainframe.isOnline(), "mainframe registered and online"))
            .thenExecute(() -> {
                for (var pos : List.of(sensor, lamp)) {
                    at(helper, pos).bind(link, owner);
                    helper.assertTrue(at(helper, pos).getLink() != null, "linked: " + pos);
                }
                WebAuthData.access(server).setRole(mf, viewer, "Vic", Role.VIEWER);
                helper.assertTrue(CodeService.enabled(), "Cloud Scripts are on (Cobalt loaded and compatible)");
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(mf, at(helper, sensor)) && ready(mf, at(helper, lamp)), "sensor and lamp online"))
            .thenExecute(() -> {
                String card = RsCards.sectionId(mf, "", 0).toString();
                String sensorWidget = RsCards.widgetId(at(helper, sensor).deviceId());
                String lampWidget = RsCards.widgetId(at(helper, lamp).deviceId());
                String source = """
                    local card, sensor, lamp = "%s", "%s", "%s"
                    local c = cloud.getCard(card)
                    print("sees", c ~= nil, c and #c.widgets, cloud.getValue(card, lamp))
                    cloud.on("change", {card = card, widget = sensor}, function(e)
                      local on = e.value == true or (type(e.value) == "number" and e.value > 0)
                      local ok, err = cloud.set(card, lamp, on)
                      print("lamp", tostring(on), tostring(ok), tostring(err))
                    end)
                    """.formatted(card, sensorWidget, lampWidget);
                var refused = code(server, viewerDevice, mf, "save", null, name, source, null).getNow(null);
                helper.assertTrue(refused != null && "not_allowed".equals(refused.error()), "a viewer may not write scripts: " + (refused == null ? "pending" : refused.error()));
                pending[0] = code(server, ownerDevice, mf, "save", null, name, source, null);
            })
            .thenWaitUntil(() -> helper.assertTrue(pending[0].isDone(), "saved (compiled on a script worker)"))
            .thenExecute(() -> {
                var saved = pending[0].join();
                helper.assertTrue(saved.error() == null, "save: " + saved.error() + " " + saved.detail());
                scriptId[0] = saved.data().get("id").getAsString();
                helper.assertTrue(saved.data().getAsJsonArray("diagnostics").isEmpty(), "no compile problems: " + saved.data());
                var refused = code(server, viewerDevice, mf, "start", scriptId[0], null, null, null).join();
                helper.assertTrue("not_allowed".equals(refused.error()), "a viewer may not start it: " + refused.error());
                var started = code(server, ownerDevice, mf, "start", scriptId[0], null, null, null).join();
                helper.assertTrue(started.error() == null, "started: " + started.error() + " " + started.detail());
            })
            .thenWaitUntil(() -> helper.assertTrue(logText(mf, name).contains("sees true"), "the script read the card: " + logText(mf, name)))
            .thenIdle(10) // a snapshot with the lever off first
            .thenExecute(() -> level.setBlock(lever, level.getBlockState(lever).setValue(LeverBlock.POWERED, true), Block.UPDATE_ALL))
            .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(lamp).getLightEmission() == 15, "the script turned the lamp on: " + logText(mf, name)))
            .thenExecute(() -> {
                helper.assertTrue(logText(mf, name).contains("lamp true true nil"), "the action went through: " + logText(mf, name));
                var audit = WebAuthData.access(server).audit(mf).stream().map(e -> e.text()).toList();
                helper.assertTrue(audit.stream().anyMatch(t -> t.contains("Owner created Cloud Script \"" + name + "\"")), "created audited: " + audit);
                helper.assertTrue(audit.stream().anyMatch(t -> t.contains("Owner started Cloud Script \"" + name + "\"")), "started audited: " + audit);
                helper.assertTrue(audit.stream().anyMatch(t -> t.startsWith("script " + name + " (by Owner): ")), "the action audited as the script: " + audit);
                var autostart = code(server, ownerDevice, mf, "autostart", scriptId[0], null, null, true).join();
                helper.assertTrue(autostart.error() == null, "autostart on: " + autostart.error());
                // the kill switch: a redstone signal into the mainframe
                level.setBlock(killSwitch, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenWaitUntil(() -> helper.assertTrue("kill_switch".equals(CodeService.reasonForTest(mf, name)), "the kill switch stopped it: "
                + CodeService.reasonForTest(mf, name)))
            .thenExecute(() -> {
                helper.assertTrue(CodeService.runtimeForTest(mf, name) == null, "no run left");
                var refused = code(server, ownerDevice, mf, "start", scriptId[0], null, null, null).join();
                helper.assertTrue("offline".equals(refused.error()), "cannot start while the kill switch is on: " + refused.error());
                level.setBlock(killSwitch, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenWaitUntil(() -> {
                var runtime = CodeService.runtimeForTest(mf, name);
                helper.assertTrue(runtime != null, "autostart started it again after the kill switch: " + CodeService.reasonForTest(mf, name));
            })
            .thenExecute(() -> {
                var dispatcher = server.getCommands().getDispatcher();
                var player = server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL).withSuppressedOutput();
                helper.assertTrue(run(dispatcher, "cccloud scripts stop " + name, player) < 0, "without op level 2 nobody may stop scripts");
                var admin = server.createCommandSourceStack().withSuppressedOutput();
                // the number of running scripts on the server: other script tests may run at the same time
                helper.assertTrue(run(dispatcher, "cccloud scripts list", admin) >= 1, "listed");
                helper.assertTrue(run(dispatcher, "cccloud scripts stop porch", admin) == 0, "names must match whole (case aside)");
                helper.assertTrue(run(dispatcher, "cccloud scripts stop porch light", admin) == 1, "/cccloud scripts stop <name>");
            })
            .thenWaitUntil(() -> helper.assertTrue(CodeService.runtimeForTest(mf, name) == null && "command".equals(CodeService.reasonForTest(mf, name)),
                "stopped by the command: " + CodeService.reasonForTest(mf, name)))
            .thenExecute(() -> {
                var deleted = code(server, ownerDevice, mf, "delete", scriptId[0], null, null, null).join();
                helper.assertTrue(deleted.error() == null, "deleted: " + deleted.error());
                for (var pos : placed) if (level.getBlockEntity(pos) instanceof RsDeviceBlockEntity) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                for (var pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenSucceed();
    }
}
