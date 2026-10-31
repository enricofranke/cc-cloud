package ai.ezfox.cccloud.compat.silentgear;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.silentchaos512.gear.block.alloymaker.AlloyMakerBlockEntity;
import net.silentchaos512.gear.block.charger.ChargerBlockEntity;
import net.silentchaos512.gear.block.grader.GraderBlockEntity;
import net.silentchaos512.gear.block.press.MetalPressBlockEntity;
import net.silentchaos512.gear.block.salvager.SalvagerBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Silent Gear (wave 5): one <b>Machine</b> card for the alloy makers (Alloy Forge, Crude and Super Mixer, Recrystallizer,
 * Refabricator), Metal Press, Material Grader and Salvager (status, progress, what goes in, what comes out) and a
 * <b>Starlight Charger</b> card (charge, progress). The machines keep no energy and their progress only reaches the
 * GUI, so neither the generic cards nor a getter see it: {@code progress} and the work times are read by reflection.
 * If that ever fails the card still shows items, the progress stays 0.
 */
public final class SilentGearCards {
    public static final String STATUS = "status";
    public static final String PROGRESS = "progress";
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String CHARGE = "charge";
    public static final String STRUCTURE = "structure";
    /** Value of a field that cannot be read. */
    static final int UNKNOWN = -1;

    private static final Map<String, @Nullable Field> FIELDS = new ConcurrentHashMap<>();

    private SilentGearCards() {
    }

    public static void init() {
        DeviceCards.register("silentgear", DeviceCards.SPECIFIC, SilentGearCards::detect);
        Integrations.test("silentgear_alloy", () -> SilentGearGameTests::alloy);
        Integrations.test("silentgear_press", () -> SilentGearGameTests::press);
        Integrations.test("silentgear_charger", () -> SilentGearGameTests::charger);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return switch (level.getBlockEntity(pos)) {
            case ChargerBlockEntity<?> charger -> new Charger(level, pos, side, charger);
            case AlloyMakerBlockEntity<?> be -> new Machine(level, pos, side, be);
            case MetalPressBlockEntity be -> new Machine(level, pos, side, be);
            case GraderBlockEntity be -> new Machine(level, pos, side, be);
            case SalvagerBlockEntity be -> new Machine(level, pos, side, be);
            case null, default -> null;
        };
    }

    /** An int field (static with a null {@code target}) of {@code owner}, or {@link #UNKNOWN}. */
    static int field(Class<?> owner, @Nullable Object target, String name) {
        var field = FIELDS.computeIfAbsent(owner.getName() + "#" + name, key -> {
            try {
                var f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        });
        try {
            return field == null ? UNKNOWN : field.getInt(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return UNKNOWN;
        }
    }

    static String name(ItemStack stack) {
        String name = Fmt.title(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        return stack.getCount() > 1 ? stack.getCount() + " × " + name : name;
    }

    /** The stacks in slots {@code from..to} (exclusive), joined; "–" when empty. */
    static String items(Container container, int from, int to) {
        var text = new StringBuilder();
        int size = container.getContainerSize();
        for (int i = from; i < Math.min(to, size); i++) {
            var stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (!text.isEmpty()) text.append(", ");
            text.append(name(stack));
        }
        return text.isEmpty() ? "–" : text.toString();
    }

    /** Which class holds the machine's fields, its work time in ticks and its last input slot. */
    private record Shape(Class<?> owner, String workTime, int inputs) {
    }

    private static Shape shape(BlockEntity be) {
        return switch (be) {
            case AlloyMakerBlockEntity<?> m -> new Shape(AlloyMakerBlockEntity.class, "WORK_TIME", m.getInputSlotCount());
            case MetalPressBlockEntity m -> new Shape(MetalPressBlockEntity.class, "WORK_TIME", 1);
            case GraderBlockEntity m -> new Shape(GraderBlockEntity.class, "BASE_ANALYZE_TIME", 2);
            default -> new Shape(SalvagerBlockEntity.class, "BASE_WORK_TIME", 1);
        };
    }

    static final class Machine extends BlockCard<BlockEntity> {
        private final Shape shape;

        Machine(ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
            super(level, pos, side, be);
            shape = shape(be);
        }

        @Override
        public String kind() {
            return "silentgear_machine";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status")
                .progress(PROGRESS, "Progress", 0, 100, "%")
                .label(INPUT, "Input")
                .label(OUTPUT, "Output")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var container = (Container) be;
            int progress = field(shape.owner(), be, PROGRESS);
            int total = field(shape.owner(), null, shape.workTime());
            out.text(STATUS, progress > 0 ? "Working" : container.isEmpty() ? "Empty" : "Idle");
            out.number(PROGRESS, total > 0 ? Math.round(Fmt.percent(Math.max(0, progress), total)) : 0);
            out.text(INPUT, items(container, 0, shape.inputs()));
            int firstOutput = be instanceof AlloyMakerBlockEntity<?> m ? m.getOutputSlotIndex() : shape.inputs();
            out.text(OUTPUT, items(container, firstOutput, be instanceof AlloyMakerBlockEntity<?> ? firstOutput + 1 : container.getContainerSize()));
        }
    }

    static final class Charger extends BlockCard<ChargerBlockEntity<?>> {
        Charger(ServerLevel level, BlockPos pos, Direction side, ChargerBlockEntity<?> charger) {
            super(level, pos, side, charger);
        }

        @Override
        public String kind() {
            return "silentgear_charger";
        }

        @Override
        public String title() {
            return "Starlight Charger";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(CHARGE, "Starlight", "span", 2)
                .progress(PROGRESS, "Progress", 0, 100, "%")
                .label(INPUT, "Input")
                .label(OUTPUT, "Output")
                .label(STRUCTURE, "Pillars")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            int progress = field(ChargerBlockEntity.class, be, PROGRESS);
            int total = field(ChargerBlockEntity.class, be, "workTime");
            int charge = field(ChargerBlockEntity.class, be, CHARGE);
            int pillars = field(ChargerBlockEntity.class, be, "structureLevel");
            out.text(STATUS, progress > 0 ? "Charging" : be.isEmpty() ? "Empty" : "Idle");
            out.text(CHARGE, charge < 0 ? "–" : Fmt.si(charge));
            out.number(PROGRESS, total > 0 ? Math.round(Fmt.percent(Math.max(0, progress), total)) : 0);
            out.text(INPUT, items(be, 0, 2));
            out.text(OUTPUT, items(be, 2, 3));
            out.text(STRUCTURE, pillars <= 0 ? "None" : "Level " + pillars);
        }
    }
}
