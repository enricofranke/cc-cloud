package ai.ezfox.cccloud.client.dev;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Dev only ({@code ./gradlew runClient -PscreenShots=<dir>}, system property {@code cccloud.screenShots}): joins
 * {@code run/saves/cccloud-bench}, builds one of every case next to the player (a mainframe; uplinks on an Energy Meter
 * with {@code -PintegrationMods=energymeter}, on a computer with the automatic card, on a computer with a Lua card,
 * unlinked, at the edge of range, behind a relay and out of range; an ME bridge with {@code -PstorageMods}; a relay),
 * opens each screen in turn and saves {@code <dir>/screenshots/<case>.png}, then quits. Run it before and after a
 * screen change to compare. Does nothing without the property.
 */
public final class ScreenShots {
    /** One screenshot: what to open (server thread, the player is placed next to {@code at} first). */
    private record Shot(String name, BlockPos at, Consumer<ServerPlayer> open) {
    }

    private static final int SETTLE_TICKS = 140;
    private static final int SHOT_TICKS = 50;

    private static final List<Shot> shots = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static int step = -1;
    private static int wait;
    private static int index;
    private static volatile boolean built;

    private ScreenShots() {
    }

    public static void init() {
        if (System.getProperty("cccloud.screenShots") == null) return;
        NeoForge.EVENT_BUS.addListener(ScreenShots::tick);
    }

    private static File dir() {
        return new File(System.getProperty("cccloud.screenShots"));
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false; // the window may be in the background
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        var playerId = mc.player.getUUID();
        if (step == -1) {
            step = 0;
            wait = 40;
            new File(dir(), Screenshot.SCREENSHOT_DIR).mkdirs();
            return;
        }
        if (wait-- > 0) return;
        switch (step) {
            case 0 -> {
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) build(player.level(), player);
                });
                step = 1;
                wait = SETTLE_TICKS;
            }
            case 1 -> {
                if (!built) return;
                if (index >= shots.size()) {
                    step = 3;
                    wait = 10;
                    return;
                }
                var shot = shots.get(index);
                mc.gui.getChat().clearMessages(false);
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) return;
                    player.closeContainer();
                    standBefore(player, shot.at());
                    shot.open().accept(player);
                });
                step = 2;
                wait = SHOT_TICKS;
            }
            case 2 -> {
                var shot = shots.get(index);
                CCCloud.LOGGER.info("[cccloud] Screen shot {}: screen = {}", shot.name(), mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
                Screenshot.grab(dir(), shot.name() + ".png", mc.getMainRenderTarget(), 1,
                    msg -> CCCloud.LOGGER.info("[cccloud] Screen shot: {}", msg.getString()));
                index++;
                step = 1;
                wait = 5;
            }
            case 3 -> {
                step = 4;
                mc.stop();
            }
            default -> {
            }
        }
    }

    /** Puts the player 3 blocks north of {@code at}, looking at it. */
    private static void standBefore(ServerPlayer player, BlockPos at) {
        var level = player.level();
        player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() - 2.5, java.util.Set.of(), 0f, 20f, true);
    }

    private static @Nullable Block block(String id) {
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        return block == Blocks.AIR ? null : block;
    }

    private static void platform(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlockAndUpdate(center.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy < 4; dy++) level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** A block with an uplink on its north side (facing it); returns the uplink. */
    private static @Nullable CloudUplinkBlockEntity uplinkOn(ServerLevel level, BlockPos target, BlockState targetState, boolean advanced) {
        level.setBlockAndUpdate(target, targetState);
        var pos = target.north();
        var uplinkBlock = advanced ? ModRegistry.ADVANCED_CLOUD_UPLINK.get() : ModRegistry.CLOUD_UPLINK.get();
        level.setBlockAndUpdate(pos, uplinkBlock.defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.SOUTH));
        return level.getBlockEntity(pos) instanceof CloudUplinkBlockEntity u ? u : null;
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        var silent = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(silent, "time set noon");
        server.getCommands().performPrefixedCommand(silent, "weather clear");
        server.getCommands().performPrefixedCommand(silent, "gamerule doDaylightCycle false");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var start = player.blockPosition();
        // Up in the air: no terrain or water in the way of the far platforms.
        int y = Math.max(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, start).getY(), 100) + 8;
        var base = new BlockPos(start.getX(), y, start.getZ());
        platform(level, base.offset(4, 0, 0), 14);

        // The mainframe (owned, powered, dashboard on).
        var state = ModRegistry.CLOUD_MAINFRAME.get().defaultBlockState().setValue(CloudMainframeBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(base, state.setValue(CloudMainframeBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(base.above(), state.setValue(CloudMainframeBlock.HALF, DoubleBlockHalf.UPPER));
        if (!(level.getBlockEntity(base) instanceof CloudMainframeBlockEntity mainframe)) {
            CCCloud.LOGGER.error("[cccloud] Screen shots: no mainframe");
            return;
        }
        mainframe.setOwner(player);
        mainframe.setEnabled(true);
        mainframe.energy().set(mainframe.energy().capacity());
        var link = new MainframeLink(GlobalPos.of(level.dimension(), base), mainframe.getMainframeId());
        // Linking needs the mainframe registered (its first tick): done a moment later.
        var links = new java.util.ArrayList<Runnable>();
        shots.add(new Shot("mainframe", base, p -> ai.ezfox.cccloud.gui.GuiNetwork.openMainframe(p, mainframe)));

        // Uplink on an Energy Meter (energymeter mod).
        var meter = block("energymeter:meter");
        if (meter != null) {
            var u = uplinkOn(level, base.offset(4, 0, 0), meter.defaultBlockState(), false);
            if (u != null) {
                links.add(() -> u.bind(link, player));
                shots.add(new Shot("uplink-energy-meter", u.getBlockPos(), p -> ai.ezfox.cccloud.gui.GuiNetwork.openUplink(p, u)));
            }
        }

        // Uplink on an advanced computer: the automatic Computer card.
        var computerBlock = block("computercraft:computer_advanced");
        if (computerBlock != null) {
            var u = uplinkOn(level, base.offset(8, 0, 0), computerBlock.defaultBlockState(), false);
            if (level.getBlockEntity(base.offset(8, 0, 0)) instanceof dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity c) {
                c.createServerComputer().turnOn();
            }
            if (u != null) {
                links.add(() -> u.bind(link, player));
                shots.add(new Shot("uplink-computer", u.getBlockPos(), p -> ai.ezfox.cccloud.gui.GuiNetwork.openUplink(p, u)));
            }
            // A labelled computer whose program published a "Reactor" card (like the showcase world).
            var lua = uplinkOn(level, base.offset(12, 0, 0), computerBlock.defaultBlockState(), true);
            if (level.getBlockEntity(base.offset(12, 0, 0)) instanceof dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity c) {
                c.setLabel("reactor");
                c.createServerComputer().turnOn();
            }
            if (lua != null) {
                links.add(() -> lua.bind(link, player));
                try {
                    lua.beginLua();
                    lua.setTitle("Reactor");
                    lua.putWidget(WidgetFactory.create(WidgetType.GAUGE, "temp", "Core temp", Map.of("min", 0.0, "max", 1200.0, "unit", "K", "warn", 900.0, "crit", 1100.0)));
                    lua.putWidget(WidgetFactory.create(WidgetType.INDICATOR, "running", "Running", Map.of("value", true)));
                    lua.putWidget(WidgetFactory.create(WidgetType.TOGGLE, "pump", "Coolant pump", Map.of("value", true)));
                    lua.putWidget(WidgetFactory.create(WidgetType.LABEL, "status", "Output stable", null));
                    lua.putWidget(WidgetFactory.create(WidgetType.CHART, "output", "Output (kFE/t)", Map.of("points", 60.0, "interval", 0.5)));
                    lua.putWidget(WidgetFactory.create(WidgetType.BUTTON, "scram", "SCRAM", Map.of("confirm", true, "color", "red")));
                    lua.setValue("temp", Json.number(842));
                    for (int i = 0; i < 40; i++) lua.getWidget("output").push(40 + 12 * Math.sin(i / 3.0));
                } catch (WidgetFactory.WidgetException e) {
                    CCCloud.LOGGER.error("[cccloud] Screen shots: widgets", e);
                }
                shots.add(new Shot("uplink-lua-reactor", lua.getBlockPos(), p -> ai.ezfox.cccloud.gui.GuiNetwork.openUplink(p, lua)));
            }
        }

        // ME Cloud Bridge on a small AE2 network (storageMods).
        var bridgeBlock = block("cccloud:me_cloud_bridge");
        var energyCell = block("ae2:creative_energy_cell");
        var drive = block("ae2:drive");
        if (bridgeBlock != null && energyCell != null && drive != null) {
            var at = base.offset(16, 0, 0);
            level.setBlockAndUpdate(at.east(2), energyCell.defaultBlockState());
            level.setBlockAndUpdate(at.east(), drive.defaultBlockState());
            level.setBlockAndUpdate(at, bridgeBlock.defaultBlockState());
            if (level.getBlockEntity(at) instanceof ai.ezfox.cccloud.bridge.BridgeHost host) {
                host.onPlacedBy(player);
                links.add(() -> host.bridgeSection().bind(link, player));
                shots.add(new Shot("bridge", at, p -> ai.ezfox.cccloud.gui.GuiNetwork.openBridge(p, host)));
            }
        }

        // An uplink that is not linked yet (on a plain block).
        var loose = uplinkOn(level, base.offset(-4, 0, 0), Blocks.POLISHED_ANDESITE.defaultBlockState(), false);
        if (loose != null) shots.add(new Shot("uplink-unlinked", loose.getBlockPos(), p -> ai.ezfox.cccloud.gui.GuiNetwork.openUplink(p, loose)));

        // A relay 70 blocks west, an uplink behind it (out of the mainframe's reach), one at the edge of the
        // mainframe's range (north) and one out of range (south, linked like an old world).
        var relayPos = base.offset(-70, 0, 0);
        platform(level, relayPos, 5);
        level.setBlockAndUpdate(relayPos, ModRegistry.CLOUD_RELAY.get().defaultBlockState());
        var relay = level.getBlockEntity(relayPos) instanceof ai.ezfox.cccloud.relay.CloudRelayBlockEntity r ? r : null;
        if (relay != null) {
            relay.fillForTest();
            keepPowered = relay;
            shots.add(new Shot("relay", relayPos, p -> relay.showStatus(p)));
        }
        var behind = base.offset(-100, 0, 0);
        platform(level, behind, 5);
        var viaRelay = uplinkOn(level, behind, Blocks.POLISHED_ANDESITE.defaultBlockState(), false);
        var edge = base.offset(0, 0, -88);
        platform(level, edge, 5);
        var edgeUplink = uplinkOn(level, edge, Blocks.POLISHED_ANDESITE.defaultBlockState(), false);
        var far = base.offset(0, 0, 110);
        platform(level, far, 5);
        var farUplink = uplinkOn(level, far, Blocks.POLISHED_ANDESITE.defaultBlockState(), false);
        later(server, 20, () -> {
            for (var r : links) r.run();
        });
        later(server, 60, () -> {
            // After the relay joined the network.
            if (viaRelay != null) {
                viaRelay.forceLinkForTest(link);
                shots.add(new Shot("uplink-via-relay", viaRelay.getBlockPos(), p -> ai.ezfox.cccloud.gui.GuiNetwork.openUplink(p, viaRelay)));
            }
            if (edgeUplink != null) {
                edgeUplink.forceLinkForTest(link);
                shots.add(new Shot("uplink-range-edge", edgeUplink.getBlockPos(), p -> ai.ezfox.cccloud.gui.GuiNetwork.openUplink(p, edgeUplink)));
            }
            if (farUplink != null) {
                farUplink.forceLinkForTest(link);
                shots.add(new Shot("uplink-out-of-range", farUplink.getBlockPos(), p -> ai.ezfox.cccloud.gui.GuiNetwork.openUplink(p, farUplink)));
            }
            built = true;
        });
        NeoForge.EVENT_BUS.addListener(ScreenShots::serverTick);
        mainframePower = mainframe;
    }

    /** Runs {@code task} on the server thread after {@code ticks} server ticks. */
    private static void later(net.minecraft.server.MinecraftServer server, int ticks, Runnable task) {
        pending.add(new Object[]{server.getTickCount() + ticks, task});
    }

    private static final List<Object[]> pending = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static @Nullable CloudMainframeBlockEntity mainframePower;
    private static ai.ezfox.cccloud.relay.@Nullable CloudRelayBlockEntity keepPowered;

    /** Keeps the mainframe and the relay powered (the bench world has no generators). */
    private static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (var p : pending) {
            if (event.getServer().getTickCount() >= (int) p[0]) {
                pending.remove(p);
                ((Runnable) p[1]).run();
            }
        }
        var m = mainframePower;
        if (m != null && !m.isRemoved()) m.energy().set(m.energy().capacity());
        var r = keepPowered;
        if (r != null && !r.isRemoved()) r.fillForTest();
    }
}
