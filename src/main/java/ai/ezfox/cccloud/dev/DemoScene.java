package ai.ezfox.cccloud.dev;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.ModRegistry;
import ai.ezfox.cccloud.block.CloudMainframeBlock;
import ai.ezfox.cccloud.block.CloudUplinkBlock;
import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.display.CloudDisplayBlock;
import ai.ezfox.cccloud.display.CloudDisplayBlockEntity;
import ai.ezfox.cccloud.ingame.InGameRegistry;
import ai.ezfox.cccloud.item.MainframeLink;
import ai.ezfox.cccloud.uplink.Json;
import ai.ezfox.cccloud.uplink.WidgetFactory;
import ai.ezfox.cccloud.uplink.WidgetType;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Dev only ({@code ./gradlew runClient -PdemoShots=<dir>}, system property {@code cccloud.demo}): builds a small scene
 * next to the joining player – a powered mainframe, a "Reactor" uplink with live widgets, a 3×2 Cloud Display showing
 * it, a powered relay and a linked Advanced Cloud Tablet – so the client can take screenshots of the display, the
 * tablet and the Cloud Analyzer overlay (the analyzer is put into the off hand after 20 s). Does
 * nothing without the property.
 */
public final class DemoScene {
    private static @Nullable CloudMainframeBlockEntity mainframe;
    private static @Nullable CloudUplinkBlockEntity uplink;
    private static ai.ezfox.cccloud.relay.@Nullable CloudRelayBlockEntity relay;
    private static @Nullable ServerPlayer demoPlayer;
    private static int ticks;
    private static int builtAt = -1;

    private DemoScene() {
    }

    public static void init() {
        if (System.getProperty("cccloud.demo") == null) return;
        NeoForge.EVENT_BUS.addListener(DemoScene::onLogin);
        NeoForge.EVENT_BUS.addListener(DemoScene::onTick);
        CCCloud.LOGGER.info("[cccloud] Demo scene enabled (dev screenshots)");
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var level = player.level();
        level.getServer().execute(() -> build(level, player));
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
        var base = player.blockPosition().offset(0, 0, 0);
        int y = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, base).getY();
        base = new BlockPos(base.getX(), y, base.getZ());
        // Clear a small area and lay a floor.
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -2; dz <= 7; dz++) {
                level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy < 5; dy++) level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        }
        // Mainframe (owned by the player) to the right.
        var lower = base.offset(-4, 0, 4);
        var state = ModRegistry.CLOUD_MAINFRAME.get().defaultBlockState().setValue(CloudMainframeBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(lower, state.setValue(CloudMainframeBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(lower.above(), state.setValue(CloudMainframeBlock.HALF, DoubleBlockHalf.UPPER));
        if (!(level.getBlockEntity(lower) instanceof CloudMainframeBlockEntity mf)) return;
        mf.setOwner(player);
        mf.setEnabled(true);
        mf.energy().set(mf.energy().capacity());
        mainframe = mf;
        // A "Reactor" uplink on a stone block to the left.
        var anchor = base.offset(4, 0, 4);
        level.setBlockAndUpdate(anchor, Blocks.POLISHED_ANDESITE.defaultBlockState());
        var uplinkPos = anchor.north();
        level.setBlockAndUpdate(uplinkPos, ModRegistry.ADVANCED_CLOUD_UPLINK.get().defaultBlockState().setValue(CloudUplinkBlock.FACING, Direction.SOUTH));
        // The display wall in front of the player (front facing north, towards the player).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                var pos = base.offset(dx, dy, 4);
                level.setBlockAndUpdate(pos, InGameRegistry.CLOUD_DISPLAY.get().defaultBlockState()
                    .setValue(CloudDisplayBlock.FACING, Direction.NORTH).setValue(CloudDisplayBlock.ORIENTATION, Direction.NORTH));
                if (level.getBlockEntity(pos) instanceof CloudDisplayBlockEntity d) d.expand();
            }
        }
        var link = new MainframeLink(GlobalPos.of(level.dimension(), lower), mf.getMainframeId());
        var wall = base.offset(0, 0, 4);
        level.getServer().execute(() -> {
            if (!(level.getBlockEntity(uplinkPos) instanceof CloudUplinkBlockEntity u)) return;
            u.bind(link, player);
            try {
                u.beginLua();
                u.setTitle("Reactor");
                u.putWidget(WidgetFactory.create(WidgetType.GAUGE, "temp", "Core temp", Map.of("min", 0.0, "max", 1200.0, "unit", "K", "warn", 900.0, "crit", 1100.0)));
                u.putWidget(WidgetFactory.create(WidgetType.INDICATOR, "running", "Running", Map.of("value", true)));
                u.putWidget(WidgetFactory.create(WidgetType.TOGGLE, "pump", "Coolant pump", Map.of("value", true)));
                u.putWidget(WidgetFactory.create(WidgetType.BUTTON, "boost", "Boost", null));
                u.putWidget(WidgetFactory.create(WidgetType.LABEL, "status", "Output stable", null));
                u.putWidget(WidgetFactory.create(WidgetType.CHART, "output", "Output (kFE/t)", Map.of("points", 60.0, "interval", 0.5)));
                u.putWidget(WidgetFactory.create(WidgetType.BUTTON, "scram", "SCRAM", Map.of("confirm", true, "color", "red")));
            } catch (WidgetFactory.WidgetException e) {
                CCCloud.LOGGER.error("[cccloud] demo widgets", e);
            }
            uplink = u;
            var origin = level.getBlockEntity(wall) instanceof CloudDisplayBlockEntity d ? d.origin() : null;
            if (origin != null) origin.configure(mf.getMainframeId(), mf.getDisplayName(), u.getUplinkId().toString(), true, player.getUUID());
        });
        // A powered relay behind the display wall (links show through walls in the analyzer overlay).
        var relayPos = base.offset(2, 0, 14);
        level.setBlockAndUpdate(relayPos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(relayPos, ModRegistry.CLOUD_RELAY.get().defaultBlockState());
        relay = level.getBlockEntity(relayPos) instanceof ai.ezfox.cccloud.relay.CloudRelayBlockEntity r ? r : null;
        demoPlayer = player;
        builtAt = ticks;
        player.getInventory().setItem((player.getInventory().getSelectedSlot() + 1) % 9, new ItemStack(ModRegistry.CLOUD_RELAY_ITEM.get()));
        var tablet = new ItemStack(InGameRegistry.ADVANCED_CLOUD_TABLET.get());
        tablet.set(ModRegistry.LINKED_MAINFRAME.get(), link);
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), tablet);
        player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, java.util.Set.of(), 0f, 5f, true);
    }

    private static void onTick(ServerTickEvent.Post event) {
        ticks++;
        var mf = mainframe;
        if (mf != null && !mf.isRemoved()) mf.energy().set(mf.energy().capacity());
        var r = relay;
        if (r != null && !r.isRemoved()) r.energy().set(r.energy().capacity());
        // After the display and tablet shots: the analyzer goes into the off hand for the overlay shot.
        var p = demoPlayer;
        if (p != null && builtAt >= 0 && ticks - builtAt == 20 * 20) {
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(InGameRegistry.CLOUD_ANALYZER.get()));
        }
        var u = uplink;
        if (u == null || u.isRemoved()) return;
        double t = ticks / 20.0;
        u.setValue("temp", Json.number(Math.round(780 + 220 * Math.sin(t / 3))));
        u.setValue("output", Json.number(Math.round(40 + 12 * Math.sin(t) + 6 * Math.sin(t * 2.7))));
        u.setValue("status", new JsonPrimitive("Output " + Math.round(40 + 12 * Math.sin(t)) + " kFE/t"));
    }
}
