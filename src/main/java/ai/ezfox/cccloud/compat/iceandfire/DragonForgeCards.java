package ai.ezfox.cccloud.compat.iceandfire;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.iafenvoy.iceandfire.item.block.entity.DragonForgeBlockEntity;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Ice and Fire (wave 5): the <b>Dragon Forge</b> (fire, ice and lightning core): whether the multiblock stands, whether a
 * dragon is breathing on it, progress, the two inputs and the output. The forge is fed by a dragon, so there is no fuel
 * or energy to show. Its cook time has no getter ({@code cookTime} is read by reflection; if that fails the bar stays 0).
 */
public final class DragonForgeCards {
    public static final String STATUS = "status";
    public static final String TYPE = "type";
    public static final String PROGRESS = "progress";
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";

    private static final @Nullable Field COOK_TIME = cookTimeField();

    private DragonForgeCards() {
    }

    public static void init() {
        DeviceCards.register("iceandfire", DeviceCards.SPECIFIC, DragonForgeCards::detect);
        Integrations.test("iceandfire_forge", () -> DragonForgeGameTests::forge);
    }

    private static @Nullable Field cookTimeField() {
        try {
            var field = DragonForgeBlockEntity.class.getDeclaredField("cookTime");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The forge's cook time in ticks, 0 if it cannot be read. */
    static double cookTime(DragonForgeBlockEntity forge) {
        try {
            return COOK_TIME == null ? 0 : COOK_TIME.getDouble(forge);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof DragonForgeBlockEntity forge ? new Forge(level, pos, side, forge) : null;
    }

    static String name(ItemStack stack) {
        if (stack.isEmpty()) return "–";
        String name = Fmt.title(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        return stack.getCount() > 1 ? stack.getCount() + " × " + name : name;
    }

    static final class Forge extends BlockCard<DragonForgeBlockEntity> {
        private boolean wasBurning;

        Forge(ServerLevel level, BlockPos pos, Direction side, DragonForgeBlockEntity forge) {
            super(level, pos, side, forge);
        }

        @Override
        public String kind() {
            return "iceandfire_forge";
        }

        @Override
        public String title() {
            return "Dragon Forge";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(TYPE, "Element", "span", 2)
                .progress(PROGRESS, "Progress", 0, 100, "%")
                .label(INPUT, "Input", "span", 2)
                .label(OUTPUT, "Output", "span", 2)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            boolean assembled = be.assembled(), burning = be.isBurning();
            double max = be.getMaxCookTime();
            out.text(STATUS, !assembled ? "Not assembled" : burning ? "Forging" : "Waiting for a dragon");
            out.text(TYPE, Fmt.title(be.getDragonType().name()));
            out.number(PROGRESS, max > 0 ? Math.round(Fmt.percent(cookTime(be), max)) : 0);
            var a = be.getItem(0);
            var b = be.getItem(1);
            out.text(INPUT, a.isEmpty() && b.isEmpty() ? "–" : a.isEmpty() ? name(b) : b.isEmpty() ? name(a) : name(a) + " + " + name(b));
            out.text(OUTPUT, name(be.getItem(2)));
            if (wasBurning && !burning && !be.getItem(2).isEmpty()) out.alert("Dragon Forge finished", "info");
            wasBurning = burning;
        }
    }
}
