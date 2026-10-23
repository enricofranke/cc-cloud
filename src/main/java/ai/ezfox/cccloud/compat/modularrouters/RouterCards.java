package ai.ezfox.cccloud.compat.modularrouters;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import java.util.List;
import me.desht.modularrouters.block.tile.ModularRouterBlockEntity;
import me.desht.modularrouters.logic.settings.RedstoneBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Modular Routers (docs/SPEC.md v0.8 item 6): modules installed, tick rate, items per operation, eco mode, the buffer
 * and the router's redstone mode – the on/off switch of a router – as a select ({@code act.redstone}, confirm).
 */
public final class RouterCards {
    public static final String STATUS = "status";
    public static final String MODULES = "modules";
    public static final String SPEED = "speed";
    public static final String BUFFER = "buffer";
    public static final String REDSTONE = "redstone";
    static final List<String> MODES = List.of("Always", "Redstone low", "Redstone high", "Never", "Redstone pulse");
    static final List<RedstoneBehaviour> BEHAVIOURS = List.of(RedstoneBehaviour.ALWAYS, RedstoneBehaviour.LOW, RedstoneBehaviour.HIGH,
        RedstoneBehaviour.NEVER, RedstoneBehaviour.PULSE);

    private RouterCards() {
    }

    public static void init() {
        DeviceCards.register("modularrouters", DeviceCards.SPECIFIC, RouterCards::detect);
        Integrations.test("modularrouters_router", () -> RouterGameTests::router);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof ModularRouterBlockEntity r ? new Router(level, pos, side, r) : null;
    }

    static final class Router extends BlockCard<ModularRouterBlockEntity> {
        Router(ServerLevel level, BlockPos pos, Direction side, ModularRouterBlockEntity r) {
            super(level, pos, side, r);
        }

        @Override
        public String kind() {
            return "modularrouters_router";
        }

        @Override
        public String title() {
            return "Modular Router";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(MODULES, "Modules")
                .label(SPEED, "Speed")
                .label(BUFFER, "Buffer")
                .select(REDSTONE, "Redstone mode", MODES, "confirm", true)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var mode = be.getRedstoneBehaviour();
            int modules = be.getModuleCount();
            boolean off = mode == RedstoneBehaviour.NEVER;
            out.text(STATUS, modules == 0 ? "No modules" : off ? "Switched off (redstone mode Never)" : be.isBufferFull() ? "Buffer full" : "Running");
            out.text(MODULES, String.valueOf(modules));
            out.text(SPEED, "every " + be.getTickRate() + " ticks, " + be.getItemsPerTick() + " items" + (be.getEcoMode() ? ", eco" : ""));
            var buffer = be.getBufferItemStack();
            out.text(BUFFER, buffer.isEmpty() ? "Empty" : buffer.getCount() + "× " + ItemNames.displayName(buffer.getItem()));
            int i = BEHAVIOURS.indexOf(mode);
            out.text(REDSTONE, MODES.get(Math.max(0, i)));
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!REDSTONE.equals(widgetId)) return "not_interactive";
            int i = MODES.indexOf(value.getAsString());
            if (i < 0) return "invalid_value";
            be.setRedstoneBehaviour(BEHAVIOURS.get(i));
            changed();
            CCCloud.LOGGER.info("[cccloud] {} set the redstone mode of the router at {} to {}", user, pos.toShortString(), MODES.get(i));
            return null;
        }
    }
}
