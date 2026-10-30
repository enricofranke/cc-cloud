package ai.ezfox.cccloud.rsdevice;

import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.access.Access;
import ai.ezfox.cccloud.access.MainframeAccess;
import ai.ezfox.cccloud.access.Perm;
import ai.ezfox.cccloud.access.Role;
import ai.ezfox.cccloud.block.CloudStatus;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.gametest.CloudGameTests;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.network.NetworkService;
import ai.ezfox.cccloud.rules.RuleService;
import ai.ezfox.cccloud.scene.SceneService;
import ai.ezfox.cccloud.web.AccessGate;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.InGameClients;
import ai.ezfox.cccloud.web.WebAuthData;
import ai.ezfox.cccloud.web.WebHub;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jspecify.annotations.Nullable;

/**
 * GameTests of the Cloud Redstone devices (docs/SPEC.md "Cloud Redstone devices"): switch output on every face
 * (strong on the attached block, weak around, on/off and analog), button pulse length, lamp light levels from the
 * dashboard (and redstone), the sensor's input incl. after a chunk reload, group cards, a rule triggered by a sensor and
 * a scene step on a lamp, permissions (act.redstone, PIN, read-only, link takeover) and capacity/FE accounting.
 */
public final class RsGameTests {
    private RsGameTests() {
    }

    private record Setup(Player owner, CloudMainframeBlockEntity mainframe, MainframeLink link, BlockPos base) {
        UUID id() {
            return mainframe.getMainframeId();
        }
    }

    private static Setup mainframe(GameTestHelper helper) {
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        var mainframe = CloudGameTests.placeMainframe(helper, owner, new BlockPos(2, 0, 2));
        mainframe.setEnabled(true);
        CloudGameTests.keepPowered(helper, mainframe);
        var link = new MainframeLink(GlobalPos.of(helper.getLevel().dimension(), mainframe.getBlockPos()), mainframe.getMainframeId());
        return new Setup(owner, mainframe, link, mainframe.getBlockPos());
    }

    /** A plate attached to the block in direction {@code facing} (a stone block is put there). */
    private static void plate(ServerLevel level, Block block, BlockPos pos, Direction facing, List<BlockPos> placed) {
        level.setBlock(pos.relative(facing), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, block.defaultBlockState().setValue(RsPlateBlock.FACING, facing), Block.UPDATE_ALL);
        placed.add(pos.relative(facing));
        placed.add(pos);
    }

    private static RsDeviceBlockEntity at(GameTestHelper helper, BlockPos pos) {
        if (!(helper.getLevel().getBlockEntity(pos) instanceof RsDeviceBlockEntity device)) throw helper.assertionException("no redstone device at " + pos);
        return device;
    }

    private static void clear(ServerLevel level, List<BlockPos> placed) {
        // devices first (they pop off when their support goes), then their supports
        for (var pos : placed) if (level.getBlockEntity(pos) instanceof RsDeviceBlockEntity) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (var pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** A dashboard action on the device's card (what a browser, the tablet or the API does after its permission check). */
    private static @Nullable String press(Setup s, RsDeviceBlockEntity device, String suffix, JsonElement value, boolean pin) {
        var section = RsDevices.sectionOfDevice(s.id(), device.deviceId());
        if (section == null) return "no_card";
        var result = section.handleAction(RsCards.widgetId(device.deviceId()) + suffix, value, null, "test", pin).getNow(null);
        return result == null ? "pending" : result.error();
    }

    private static void mainframeReady(GameTestHelper helper, Setup s) {
        helper.assertTrue(ai.ezfox.cccloud.network.CloudNetwork.mainframe(s.id()) != null && s.mainframe().isOnline(), "mainframe registered and online");
    }

    /** Links devices with the Link Card's path (owner, range check) and makes sure it worked. */
    private static void link(GameTestHelper helper, Setup s, BlockPos... positions) {
        for (var pos : positions) {
            at(helper, pos).bind(s.link(), s.owner());
            helper.assertTrue(at(helper, pos).getLink() != null, "linked: " + pos);
        }
    }

    private static boolean ready(Setup s, RsDeviceBlockEntity device) {
        return device.status() == CloudStatus.ONLINE && RsDevices.sectionOfDevice(s.id(), device.deviceId()) != null
            && RsDevices.sectionOfDevice(s.id(), device.deviceId()).isOnline();
    }

    // ---- switch ------------------------------------------------------------------------------------------------------

    /**
     * A switch on each of the six faces of a stone block (bottom and top included): on → 15 strong into the block it
     * sits on and 15 weak around; analog mode → the slider's level; the in-world click toggles.
     */
    public static void switchFaces(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        var dirs = Direction.values();
        var switches = new BlockPos[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            // one stone block per face, 6 blocks apart so their signals never meet
            var support = s.base().west(2).above(4 + 6 * i);
            switches[i] = support.relative(dirs[i]);
            plate(level, ModRegistry.CLOUD_SWITCH.get(), switches[i], dirs[i].getOpposite(), placed);
        }
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> {
                link(helper, s, switches);
            })
            .thenWaitUntil(() -> {
                for (var pos : switches) helper.assertTrue(ready(s, at(helper, pos)), "switch online with a card: " + pos);
            })
            .thenExecute(() -> {
                for (int i = 0; i < dirs.length; i++) {
                    var device = at(helper, switches[i]);
                    var support = switches[i].relative(dirs[i].getOpposite());
                    var probe = switches[i].relative(dirs[i]);
                    helper.assertTrue(level.getDirectSignalTo(support) == 0 && level.getBestNeighborSignal(probe) == 0, "off at first: " + dirs[i]);
                    String error = press(s, device, "", new JsonPrimitive(true), false);
                    helper.assertTrue(error == null, "dashboard toggle on " + dirs[i] + ": " + error);
                    helper.assertTrue(level.getBlockState(switches[i]).getValue(RsPlateBlock.POWERED), "powered state: " + dirs[i]);
                    helper.assertTrue(level.getDirectSignalTo(support) == 15, "strong power into the block it sits on (" + dirs[i] + "): "
                        + level.getDirectSignalTo(support));
                    helper.assertTrue(level.getBestNeighborSignal(probe) == 15, "weak power around (" + dirs[i] + ")");
                    device.useInWorld(s.owner());
                    helper.assertTrue(device.output() == 0 && level.getDirectSignalTo(support) == 0, "in-world click turns it off: " + dirs[i]);
                }
                // analog on the bottom (hangs under a block) and the top
                for (int i : new int[]{0, 1}) {
                    var device = at(helper, switches[i]);
                    device.setAnalog(true);
                }
            })
            .thenWaitUntil(() -> {
                for (int i : new int[]{0, 1}) {
                    var section = RsDevices.sectionOfDevice(s.id(), at(helper, switches[i]).deviceId());
                    helper.assertTrue("slider".equals(WebHub.INSTANCE.widgetType(s.id(), section.sectionId().toString(),
                        RsCards.widgetId(at(helper, switches[i]).deviceId()))), "analog switch shows a slider");
                }
            })
            .thenExecute(() -> {
                for (int i : new int[]{0, 1}) {
                    var device = at(helper, switches[i]);
                    var support = switches[i].relative(dirs[i].getOpposite());
                    String error = press(s, device, "", new JsonPrimitive(7), false);
                    helper.assertTrue(error == null, "slider: " + error);
                    helper.assertTrue(device.output() == 7 && level.getDirectSignalTo(support) == 7, "analog level 7 strong (" + dirs[i] + ")");
                    helper.assertTrue(level.getBestNeighborSignal(switches[i].relative(dirs[i])) == 7, "analog level 7 weak (" + dirs[i] + ")");
                    helper.assertTrue("invalid_value".equals(press(s, device, "", new JsonPrimitive(16), false)), "16 is out of range");
                    device.useInWorld(s.owner());
                    helper.assertTrue(device.output() == 0, "click: off");
                    device.useInWorld(s.owner());
                    helper.assertTrue(device.output() == 7, "click: back on at the remembered level");
                }
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    // ---- button ------------------------------------------------------------------------------------------------------

    /** A dashboard press gives a pulse of exactly the configured length (2–40 ticks). */
    public static void buttonPulse(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        var pos = s.base().east(2).above(2);
        plate(level, ModRegistry.CLOUD_BUTTON.get(), pos, Direction.DOWN, placed);
        long[] pressed = {0};
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> {
                link(helper, s, pos);
                at(helper, pos).setPulse(6);
                helper.assertTrue(at(helper, pos).pulse() == 6, "pulse set");
                at(helper, pos).setPulse(99);
                helper.assertTrue(at(helper, pos).pulse() == RsDevice.PULSE_MAX, "pulse is capped at 40");
                at(helper, pos).setPulse(6);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(s, at(helper, pos)), "button online"))
            .thenExecute(() -> {
                helper.assertTrue("button".equals(WebHub.INSTANCE.widgetType(s.id(), RsDevices.sectionOfDevice(s.id(), at(helper, pos).deviceId()).sectionId().toString(),
                    RsCards.widgetId(at(helper, pos).deviceId()))), "a button widget");
                String error = press(s, at(helper, pos), "", new JsonPrimitive(true), false);
                helper.assertTrue(error == null, "press: " + error);
                pressed[0] = helper.getTick();
                helper.assertTrue(level.getDirectSignalTo(pos.below()) == 15, "pulse on: strong power into the floor");
            })
            .thenWaitUntil(() -> helper.assertTrue(!level.getBlockState(pos).getValue(RsPlateBlock.POWERED), "pulse ended"))
            .thenExecute(() -> {
                long length = helper.getTick() - pressed[0];
                helper.assertTrue(length >= 6 && length <= 7, "pulse length 6 ticks, was " + length);
                helper.assertTrue(level.getDirectSignalTo(pos.below()) == 0, "signal gone");
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    // ---- lamp --------------------------------------------------------------------------------------------------------

    /** The dashboard sets the lamp's light (toggle and brightness); with "reacts to redstone" a signal lights it too. */
    public static void lampLevels(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var pos = s.base().north(2).above(1);
        var placed = new ArrayList<>(List.of(pos, pos.east()));
        level.setBlock(pos, ModRegistry.CLOUD_LAMP.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> link(helper, s, pos))
            .thenWaitUntil(() -> helper.assertTrue(ready(s, at(helper, pos)), "lamp online"))
            .thenExecute(() -> {
                var lamp = at(helper, pos);
                helper.assertTrue(level.getBlockState(pos).getValue(RsLampBlock.LEVEL) == 0 && level.getBlockState(pos).getLightEmission() == 0, "dark at first");
                helper.assertTrue(press(s, lamp, "", new JsonPrimitive(true), false) == null, "toggle on");
                helper.assertTrue(level.getBlockState(pos).getValue(RsLampBlock.LEVEL) == 15 && level.getBlockState(pos).getLightEmission() == 15, "full light");
                helper.assertTrue(press(s, lamp, RsCards.BRIGHTNESS, new JsonPrimitive(6), false) == null, "dim to 6");
                helper.assertTrue(level.getBlockState(pos).getLightEmission() == 6, "light level 6");
                helper.assertTrue(press(s, lamp, RsCards.BRIGHTNESS, new JsonPrimitive(0), false) == null, "slider to 0");
                helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0, "slider 0 = off");
                helper.assertTrue(press(s, lamp, "", new JsonPrimitive(true), false) == null, "toggle on again");
                helper.assertTrue(level.getBlockState(pos).getLightEmission() == 6, "back at the remembered brightness");
                helper.assertTrue(press(s, lamp, "", new JsonPrimitive(false), false) == null, "toggle off");
                // redstone: ignored until the lamp is told to react to it
                level.setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0, "redstone ignored by default");
                lamp.setFollow(true);
                helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15, "reacts to redstone");
                level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0, "signal gone, dark again");
            })
            .thenWaitUntil(() -> {
                var section = RsDevices.sectionOfDevice(s.id(), at(helper, pos).deviceId());
                var value = WebHub.INSTANCE.widgetValue(s.id(), section.sectionId().toString(), RsCards.widgetId(at(helper, pos).deviceId()) + RsCards.BRIGHTNESS);
                helper.assertTrue(value != null && value.getAsInt() == 0, "the card shows the light level: " + value);
                helper.assertTrue("24h".equals(WebHub.INSTANCE.historyRange(s.id(), section.sectionId().toString(),
                    RsCards.widgetId(at(helper, pos).deviceId()) + RsCards.BRIGHTNESS)), "the light level is recorded as a series");
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    // ---- sensor ------------------------------------------------------------------------------------------------------

    /**
     * A sensor next to a lever that was on before it was placed, and after its chunk reloads: the input is not saved and a
     * lever that stays on sends no update, so the sensor samples on its first tick. The card shows on/off and the level.
     */
    public static void sensorAfterLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        var sensor = s.base().west(2).south(2).above(1);
        var lever = sensor.south();
        level.setBlock(lever.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.POWERED, true), Block.UPDATE_ALL);
        placed.addAll(List.of(lever, lever.below()));
        plate(level, ModRegistry.CLOUD_SENSOR.get(), sensor, Direction.DOWN, placed);
        String[] key = {null, null};
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> link(helper, s, sensor))
            .thenWaitUntil(() -> {
                helper.assertTrue(at(helper, sensor).input() == 15, "the sensor sampled the lever that was on first: " + at(helper, sensor).input());
                helper.assertTrue(ready(s, at(helper, sensor)), "sensor online");
            })
            .thenWaitUntil(() -> {
                var section = RsDevices.sectionOfDevice(s.id(), at(helper, sensor).deviceId());
                key[0] = section.sectionId().toString();
                key[1] = RsCards.widgetId(at(helper, sensor).deviceId());
                var on = WebHub.INSTANCE.widgetValue(s.id(), key[0], key[1]);
                var lvl = WebHub.INSTANCE.widgetValue(s.id(), key[0], key[1] + RsCards.LEVEL);
                helper.assertTrue(on != null && on.getAsBoolean() && lvl != null && lvl.getAsInt() == 15, "card: on, level 15 (" + on + ", " + lvl + ")");
                helper.assertTrue("indicator".equals(WebHub.INSTANCE.widgetType(s.id(), key[0], key[1])), "indicator");
                helper.assertTrue("not_interactive".equals(press(s, at(helper, sensor), "", new JsonPrimitive(true), false)), "read-only");
            })
            .thenExecute(() -> {
                // chunk unload + load of the block entity: saved and dropped, a new one from the saved data
                var old = at(helper, sensor);
                var tag = old.saveWithFullMetadata(level.registryAccess());
                old.onChunkUnloaded();
                level.removeBlockEntity(sensor);
                var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(sensor, level.getBlockState(sensor), tag, level.registryAccess());
                helper.assertTrue(loaded instanceof RsDeviceBlockEntity d && d.input() == 0 && d.getLink() != null, "reloaded sensor starts without input");
                level.setBlockEntity(loaded);
            })
            .thenWaitUntil(() -> helper.assertTrue(at(helper, sensor).input() == 15, "sampled again after loading"))
            .thenExecute(() -> level.setBlock(lever, level.getBlockState(lever).setValue(LeverBlock.POWERED, false), Block.UPDATE_ALL))
            .thenWaitUntil(() -> {
                helper.assertTrue(at(helper, sensor).input() == 0, "lever off → 0");
                var on = WebHub.INSTANCE.widgetValue(s.id(), key[0], key[1]);
                helper.assertTrue(on != null && !on.getAsBoolean(), "card: off");
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    // ---- cards -------------------------------------------------------------------------------------------------------

    /**
     * Devices with the same group (case-insensitive) share one card with "All on" / "All off", ungrouped ones go to the
     * automatic "Redstone" card; moving a device to another group moves its widgets.
     */
    public static void groupCards(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        var lamps = new BlockPos[]{s.base().west(2).above(1), s.base().west(2).above(2), s.base().west(2).above(3)};
        for (var pos : lamps) {
            level.setBlock(pos, ModRegistry.CLOUD_LAMP.get().defaultBlockState(), Block.UPDATE_ALL);
            placed.add(pos);
        }
        var sw = s.base().east(2).above(1);
        var sensor = s.base().east(2).north(2).above(1);
        plate(level, ModRegistry.CLOUD_SWITCH.get(), sw, Direction.DOWN, placed);
        plate(level, ModRegistry.CLOUD_SENSOR.get(), sensor, Direction.DOWN, placed);
        UUID hall = RsCards.sectionId(s.id(), "halle", 0);
        UUID auto = RsCards.sectionId(s.id(), "", 0);
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> {
                link(helper, s, lamps);
                link(helper, s, sw, sensor);
                at(helper, lamps[0]).setNameAndGroup("Lamp A", "Halle");
                at(helper, lamps[1]).setNameAndGroup("Lamp B", "Halle");
                at(helper, lamps[2]).setNameAndGroup("Lamp C", " halle");
                at(helper, sw).setNameAndGroup("Gate", "");
            })
            .thenWaitUntil(() -> {
                for (var pos : List.of(lamps[0], lamps[1], lamps[2], sw, sensor)) helper.assertTrue(ready(s, at(helper, pos)), "online: " + pos);
                var halle = WebHub.INSTANCE.sectionSnapshot(s.id(), hall.toString());
                helper.assertTrue(halle != null && "Halle".equals(halle.get("title").getAsString()), "one card for the group: " + halle);
                helper.assertTrue("redstone".equals(halle.get("template").getAsString()) && "direct".equals(halle.get("mode").getAsString()), "a redstone card");
                helper.assertTrue(halle.getAsJsonArray("widgets").size() == 2 + 3 * 2, "all on/off + 3 lamps × (toggle, brightness)");
                helper.assertTrue(ids(halle).contains(RsCards.ALL_ON) && ids(halle).contains(RsCards.ALL_OFF), "all on / all off");
                var redstone = WebHub.INSTANCE.sectionSnapshot(s.id(), auto.toString());
                helper.assertTrue(redstone != null && RsCards.DEFAULT_TITLE.equals(redstone.get("title").getAsString()), "automatic card: " + redstone);
                helper.assertTrue(redstone.getAsJsonArray("widgets").size() == 1 + 2, "switch + sensor (indicator, level); one switch: no all buttons");
                helper.assertTrue(RsDevices.sections(s.id()).size() == 2, "two cards");
            })
            .thenExecute(() -> {
                var section = RsDevices.sections(s.id()).stream().filter(c -> c.sectionId().equals(hall)).findFirst().orElseThrow();
                var result = section.handleAction(RsCards.ALL_ON, new JsonPrimitive(true), null, "test", false).getNow(null);
                helper.assertTrue(result != null && result.ok(), "all on: " + (result == null ? null : result.error()));
                for (var pos : lamps) helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15, "lamp on: " + pos);
                at(helper, lamps[2]).setNameAndGroup("Lamp C", "");
            })
            .thenWaitUntil(() -> {
                var halle = WebHub.INSTANCE.sectionSnapshot(s.id(), hall.toString());
                helper.assertTrue(halle.getAsJsonArray("widgets").size() == 2 + 2 * 2, "lamp C left the group: " + halle);
                var redstone = WebHub.INSTANCE.sectionSnapshot(s.id(), auto.toString());
                helper.assertTrue(ids(redstone).contains(RsCards.widgetId(at(helper, lamps[2]).deviceId())), "lamp C is on the automatic card");
                helper.assertTrue(ids(redstone).contains(RsCards.ALL_ON), "switch + lamp: all on/off");
            })
            .thenExecute(() -> {
                // breaking a device removes it from its card
                level.destroyBlock(lamps[1], false);
            })
            .thenWaitUntil(() -> {
                var halle = WebHub.INSTANCE.sectionSnapshot(s.id(), hall.toString());
                helper.assertTrue(halle == null || halle.getAsJsonArray("widgets").size() == 2, "lamp B gone: " + halle);
                helper.assertTrue(s.mainframe().getLinkedUplinks().containsKey(at(helper, lamps[0]).deviceId()), "lamp A still linked");
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    /**
     * A device that arrives with its link in the block entity data ({@code /setblock}, a structure, a schematic paste)
     * shows on its mainframe's card at once, not only after its first state change.
     */
    public static void placedBySetblock(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var s = mainframe(helper);
        var lamp = s.base().north(2).above(1);
        var sw = s.base().south(2).above(1);
        level.setBlock(sw.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var placed = new ArrayList<>(List.of(lamp, sw, sw.below()));
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> {
                String link = MainframeLink.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s.link()).getOrThrow().toString();
                var commands = List.of(
                    "setblock %d %d %d cccloud:cloud_lamp{link:%s,name:\"Porch\"}".formatted(lamp.getX(), lamp.getY(), lamp.getZ(), link),
                    "setblock %d %d %d cccloud:cloud_switch[facing=down]{link:%s,name:\"Gate\",group:\"Yard\"}".formatted(sw.getX(), sw.getY(), sw.getZ(), link));
                for (var command : commands) {
                    try {
                        int result = server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSuppressedOutput());
                        helper.assertTrue(result > 0, "placed: " + command);
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        throw helper.assertionException(command + ": " + e.getMessage());
                    }
                }
                helper.assertTrue(at(helper, lamp).getLink() != null && at(helper, sw).getLink() != null, "the link came with the block entity data");
            })
            .thenWaitUntil(() -> {
                // nothing switched, no input: the devices are on their cards without any state change
                helper.assertTrue(ready(s, at(helper, lamp)) && ready(s, at(helper, sw)), "both on a card");
                var auto = WebHub.INSTANCE.sectionSnapshot(s.id(), RsCards.sectionId(s.id(), "", 0).toString());
                helper.assertTrue(auto != null && ids(auto).contains(RsCards.widgetId(at(helper, lamp).deviceId())), "the lamp is on the automatic card: " + auto);
                var yard = WebHub.INSTANCE.sectionSnapshot(s.id(), RsCards.sectionId(s.id(), "yard", 0).toString());
                helper.assertTrue(yard != null && "Yard".equals(yard.get("title").getAsString())
                    && ids(yard).contains(RsCards.widgetId(at(helper, sw).deviceId())), "the switch is on its group's card: " + yard);
                var copy = RsDevices.device(s.id(), at(helper, lamp).deviceId());
                helper.assertTrue(copy != null && "Porch".equals(copy.name()), "the dashboard copy has the lamp's name: " + copy);
                helper.assertTrue(at(helper, lamp).signalLevel() == 0 && at(helper, sw).output() == 0, "still off");
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    private static List<String> ids(JsonObject snapshot) {
        var list = new ArrayList<String>();
        for (var w : snapshot.getAsJsonArray("widgets")) list.add(w.getAsJsonObject().get("id").getAsString());
        return list;
    }

    // ---- rules and scenes --------------------------------------------------------------------------------------------

    /**
     * A rule "when the sensor is on → lamp on" (it keeps working after the lamp moved to another card), and a scene with a
     * step on the lamp's brightness.
     */
    public static void ruleAndScene(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var server = level.getServer();
        var placed = new ArrayList<BlockPos>();
        var sensor = s.base().west(2).north(2).above(1);
        var lever = sensor.west();
        var lamp = s.base().east(2).north(2).above(1);
        level.setBlock(lever.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR), Block.UPDATE_ALL);
        placed.addAll(List.of(lever, lever.below(), lamp));
        plate(level, ModRegistry.CLOUD_SENSOR.get(), sensor, Direction.DOWN, placed);
        level.setBlock(lamp, ModRegistry.CLOUD_LAMP.get().defaultBlockState(), Block.UPDATE_ALL);
        String[] scene = {null};
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> {
                link(helper, s, sensor, lamp);
                at(helper, lamp).setNameAndGroup("Porch", "");
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(s, at(helper, sensor)) && ready(s, at(helper, lamp)), "both online"))
            .thenExecute(() -> {
                String card = RsCards.sectionId(s.id(), "", 0).toString();
                String sensorWidget = RsCards.widgetId(at(helper, sensor).deviceId());
                String lampWidget = RsCards.widgetId(at(helper, lamp).deviceId());
                var rule = RuleService.set(server, s.id(), JsonParser.parseString("{\"name\":\"Porch light\",\"cooldown\":5,"
                    + "\"when\":{\"type\":\"widget\",\"uplinkId\":\"" + card + "\",\"widgetId\":\"" + sensorWidget + "\",\"op\":\">\",\"value\":0},"
                    + "\"then\":[{\"type\":\"widget\",\"uplinkId\":\"" + card + "\",\"widgetId\":\"" + lampWidget + "\",\"value\":true}]}"));
                helper.assertTrue(rule.error() == null, "rule saved: " + rule.error());
                // the lamp moves to its own card: the rule follows it (same widget id)
                at(helper, lamp).setNameAndGroup("Porch", "Outside");
            })
            .thenIdle(25) // the rule engine records the "off" state first
            .thenExecute(() -> level.setBlock(lever, level.getBlockState(lever).setValue(LeverBlock.POWERED, true), Block.UPDATE_ALL))
            .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(lamp).getLightEmission() == 15, "the rule turned the lamp on"))
            .thenExecute(() -> {
                var perms = Access.resolver(server).resolve(s.owner().getUUID(), s.id()).perms();
                String lampCard = RsCards.sectionId(s.id(), "outside", 0).toString();
                var saved = SceneService.set(server, s.id(), JsonParser.parseString("{\"name\":\"Dim porch\",\"steps\":[{\"type\":\"widget\",\"uplinkId\":\""
                    + lampCard + "\",\"widgetId\":\"" + RsCards.widgetId(at(helper, lamp).deviceId()) + RsCards.BRIGHTNESS + "\",\"value\":4}]}"),
                    s.owner().getUUID(), "Owner", perms);
                helper.assertTrue(saved.error() == null, "scene saved: " + saved.error() + " " + saved.detail());
                scene[0] = saved.id();
            })
            .thenWaitUntil(() -> helper.assertTrue(SceneService.section(s.id()) != null, "scenes card"))
            .thenExecute(() -> {
                long now = System.currentTimeMillis();
                var device = new AuthStore.Device(InGameClients.DEVICE_PREFIX + s.owner().getUUID(), s.id(), "Owner (tablet)", true, now, now,
                    s.owner().getUUID(), "Owner", "in-game", "");
                var result = SceneService.section(s.id()).press("s_" + scene[0], new JsonPrimitive(true), SceneService.Caller.of(device), false);
                helper.assertTrue(result.ok(), "scene started: " + result.error());
            })
            .thenWaitUntil(() -> helper.assertTrue(level.getBlockState(lamp).getLightEmission() == 4, "the scene dimmed the lamp to 4"))
            .thenExecute(() -> {
                RuleService.forget(server, s.id());
                SceneService.forget(server, s.id());
                clear(level, placed);
            })
            .thenSucceed();
    }

    // ---- permissions -------------------------------------------------------------------------------------------------

    /**
     * Redstone cards need {@code act.redstone} (viewers read-only, an operator without it not allowed), a PIN switch asks
     * for the PIN, and nobody without {@code network.link} can link a device to a stranger's mainframe.
     */
    public static void permissions(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var server = level.getServer();
        var placed = new ArrayList<BlockPos>();
        var sw = s.base().south(2).above(1);
        var other = s.base().south(2).east(2).above(1);
        plate(level, ModRegistry.CLOUD_SWITCH.get(), sw, Direction.DOWN, placed);
        plate(level, ModRegistry.CLOUD_SWITCH.get(), other, Direction.DOWN, placed);
        UUID viewer = UUID.randomUUID();
        UUID operator = UUID.randomUUID();
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> {
                link(helper, s, sw);
                var stranger = helper.makeMockPlayer(GameType.SURVIVAL);
                at(helper, other).bind(s.link(), stranger);
                helper.assertTrue(at(helper, other).getLink() == null, "a stranger cannot link a device to this mainframe");
                var access = WebAuthData.access(server);
                access.setRole(s.id(), viewer, "Vic", Role.VIEWER);
                access.setRole(s.id(), operator, "Opal", Role.OPERATOR);
            })
            .thenWaitUntil(() -> helper.assertTrue(ready(s, at(helper, sw)), "switch online"))
            .thenExecute(() -> {
                var section = RsDevices.sectionOfDevice(s.id(), at(helper, sw).deviceId());
                String card = section.sectionId().toString();
                String widget = RsCards.widgetId(at(helper, sw).deviceId());
                var need = WebHub.INSTANCE.actionPerm(s.id(), card, widget);
                helper.assertTrue(need == Perm.ACT_REDSTONE, "redstone cards need act.redstone: " + need);
                var resolver = Access.resolver(server);
                helper.assertTrue("read_only".equals(AccessGate.check(Role.VIEWER, resolver.resolve(viewer, s.id()).perms(), need)), "viewer: read-only");
                helper.assertTrue(AccessGate.check(Role.OPERATOR, resolver.resolve(operator, s.id()).perms(), need) == null, "operator: allowed");
                WebAuthData.access(server).setOverrides(s.id(), operator, new MainframeAccess.Overrides(Set.of(), Set.of(Perm.ACT_REDSTONE)));
                helper.assertTrue("not_allowed".equals(AccessGate.check(Role.OPERATOR, resolver.resolve(operator, s.id()).perms(), need)),
                    "operator without act.redstone: not allowed");
                at(helper, sw).setProtection(true, false);
            })
            .thenWaitUntil(() -> {
                var section = RsDevices.sectionOfDevice(s.id(), at(helper, sw).deviceId());
                helper.assertTrue(WebHub.INSTANCE.widgetNeedsPin(s.id(), section.sectionId().toString(), RsCards.widgetId(at(helper, sw).deviceId())),
                    "the card asks for the PIN");
            })
            .thenExecute(() -> {
                helper.assertTrue("pin_required".equals(press(s, at(helper, sw), "", new JsonPrimitive(true), false)), "no PIN, no switching");
                helper.assertTrue(at(helper, sw).output() == 0, "still off");
                helper.assertTrue(press(s, at(helper, sw), "", new JsonPrimitive(true), true) == null, "with a verified PIN");
                helper.assertTrue(at(helper, sw).output() == 15, "on");
                // the dashboard's changes are rate limited per device (bursts of twice the configured rate)
                int burst = (int) Math.max(1, CloudConfig.get(CloudConfig.RS_DEVICE_CHANGES_PER_SECOND) * 2);
                boolean limited = false;
                for (int i = 0; i < burst + 2 && !limited; i++) limited = "rate_limited".equals(press(s, at(helper, sw), "", new JsonPrimitive(i % 2 == 0), true));
                helper.assertTrue(limited, "rate limited after a burst");
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }

    // ---- capacity & power --------------------------------------------------------------------------------------------

    /** A redstone device counts {@code redstoneDeviceCapacityWeight} of a device and costs {@code mainframePerRedstoneDeviceFE}. */
    public static void capacityAndPower(GameTestHelper helper) {
        var level = helper.getLevel();
        var s = mainframe(helper);
        var placed = new ArrayList<BlockPos>();
        var positions = new BlockPos[]{s.base().west(2).above(1), s.base().east(2).above(1), s.base().north(2).above(1), s.base().south(2).above(1)};
        for (int i = 0; i < positions.length; i++) {
            if (i < 2) plate(level, ModRegistry.CLOUD_SWITCH.get(), positions[i], Direction.DOWN, placed);
            else {
                level.setBlock(positions[i], ModRegistry.CLOUD_LAMP.get().defaultBlockState(), Block.UPDATE_ALL);
                placed.add(positions[i]);
            }
        }
        helper.startSequence()
            .thenWaitUntil(() -> mainframeReady(helper, s))
            .thenExecute(() -> {
                link(helper, s, positions);
            })
            .thenWaitUntil(() -> {
                for (var pos : positions) helper.assertTrue(ready(s, at(helper, pos)), "online: " + pos);
            })
            .thenExecute(() -> {
                var counts = NetworkService.counts(s.id());
                helper.assertTrue(counts.redstone() == 4, "four redstone devices: " + counts);
                double weight = CloudConfig.get(CloudConfig.REDSTONE_CAPACITY_WEIGHT);
                helper.assertTrue(Math.abs(NetworkService.capacityUsed(s.id()) - 4 * weight) < 1e-9, "capacity used: " + NetworkService.capacityUsed(s.id()));
                var power = NetworkService.power(s.id(), 0);
                long rate = CloudConfig.get(CloudConfig.MAINFRAME_PER_REDSTONE_DEVICE_FE);
                helper.assertTrue(power.get("redstone") == 4 * rate, "FE: " + power);
                helper.assertTrue(rate < CloudConfig.get(CloudConfig.MAINFRAME_PER_UPLINK_FE) && weight < 1, "cheaper than an uplink");
                var node = WebHub.INSTANCE.networkForTest(s.id(), true);
                helper.assertTrue(node != null && node.toString().contains("\"type\":\"cloud_lamp\"") && node.toString().contains("\"type\":\"cloud_switch\""),
                    "the network map shows the devices: " + node);
            })
            .thenExecute(() -> clear(level, placed))
            .thenSucceed();
    }
}
