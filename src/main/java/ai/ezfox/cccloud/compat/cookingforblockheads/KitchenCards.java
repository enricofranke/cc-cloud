package ai.ezfox.cccloud.compat.cookingforblockheads;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.card.Slots;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import java.util.List;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.blay09.mods.cookingforblockheads.block.entity.ToasterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Cooking for Blockheads (wave 4). Fridges, cabinets and counters are Storage cards with item search
 * ({@code compat/storage}); this adds the <b>Oven</b> (status, items cooking, cooking and burn progress, input, fuel,
 * output, energy with a heating unit; warn alert once when the output is full) and the <b>Toaster</b> (progress and
 * its on/off switch, {@code act.buttons}).
 */
public final class KitchenCards {
    public static final String STATUS = "status";
    public static final String COOKING = "cooking";
    public static final String BURN = "burn";
    public static final String INPUT = "input";
    public static final String FUEL = "fuel";
    public static final String OUTPUT = "output";
    public static final String ENERGY = "energy";
    public static final String TOASTING = "toasting";
    public static final String POWER = "power";
    /** Oven container slots: 0–2 input, 3 fuel, 4–6 output, 7–15 cooking. */
    static final int FIRST_INPUT = 0, FUEL_SLOT = 3, FIRST_OUTPUT = 4, FIRST_COOKING = 7, COOKING_SLOTS = 9;

    private KitchenCards() {
    }

    public static void init() {
        DeviceCards.register("cookingforblockheads", DeviceCards.SPECIFIC, KitchenCards::detect);
        Integrations.test("cookingforblockheads_oven", () -> KitchenGameTests::oven);
        Integrations.test("cookingforblockheads_toaster", () -> KitchenGameTests::toaster);
        Integrations.test("cookingforblockheads_fridge", () -> KitchenGameTests::fridge);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if (be instanceof OvenBlockEntity oven) return new Oven(level, pos, side, oven);
        if (be instanceof ToasterBlockEntity toaster) return new Toaster(level, pos, side, toaster);
        return null;
    }

    static final class Oven extends BlockCard<OvenBlockEntity> {
        private boolean wasFull;

        Oven(ServerLevel level, BlockPos pos, Direction side, OvenBlockEntity oven) {
            super(level, pos, side, oven);
        }

        @Override
        public String kind() {
            return "cookingforblockheads_oven";
        }

        @Override
        public String title() {
            return "Oven";
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status")
                .progress(COOKING, "Cooking", 0, 100, "%")
                .progress(BURN, "Fuel burning", 0, 100, "%")
                .label(INPUT, "Input")
                .label(FUEL, "Fuel", "span", 2)
                .label(OUTPUT, "Output", "span", 2);
            if (be.hasPowerUpgrade()) w.percent(ENERGY, "Energy", "color", "green", "icon", "bolt");
            return w.build();
        }

        @Override
        public int revision() {
            return be.hasPowerUpgrade() ? 1 : 0;
        }

        @Override
        public void refresh(CardOutput out) {
            var c = be.getContainer();
            int cooking = 0;
            double progress = 0;
            for (int i = 0; i < COOKING_SLOTS; i++) {
                if (c.getItem(FIRST_COOKING + i).isEmpty()) continue;
                cooking++;
                progress += Math.max(0, Math.min(1, be.getCookProgress(i)));
            }
            boolean outputFull = true;
            for (int i = FIRST_OUTPUT; i < FIRST_OUTPUT + 3; i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) outputFull = false;
            }
            boolean burning = be.isBurning();
            out.text(STATUS, outputFull ? "Output full" : cooking > 0 ? (burning ? "Cooking " + cooking + (cooking == 1 ? " item" : " items") : "No fuel")
                : burning ? "Hot, nothing to cook" : "Idle");
            out.number(COOKING, cooking > 0 ? Math.round(progress * 100 / cooking) : 0);
            out.number(BURN, burning ? Math.round(Math.max(0, Math.min(1, be.getBurnTimeProgress())) * 100) : 0);
            out.text(INPUT, Slots.container(c, FIRST_INPUT, 3));
            out.text(FUEL, Slots.stack(c.getItem(FUEL_SLOT)));
            out.text(OUTPUT, Slots.container(c, FIRST_OUTPUT, 3));
            if (be.hasPowerUpgrade()) {
                var energy = be.getEnergyStorage();
                out.number(ENERGY, Fmt.percent(energy.getEnergy(), energy.getCapacity()));
            }
            if (outputFull && !wasFull) out.alert("Oven: output full", "warn");
            wasFull = outputFull;
        }
    }

    static final class Toaster extends BlockCard<ToasterBlockEntity> {
        Toaster(ServerLevel level, BlockPos pos, Direction side, ToasterBlockEntity toaster) {
            super(level, pos, side, toaster);
        }

        @Override
        public String kind() {
            return "cookingforblockheads_toaster";
        }

        @Override
        public String title() {
            return "Toaster";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .toggle(POWER, "Toasting", "icon", "flame")
                .progress(TOASTING, "Progress", 0, 100, "%")
                .label(INPUT, "Inside")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            out.bool(POWER, be.isActive());
            out.number(TOASTING, be.isActive() ? Math.round(Math.max(0, Math.min(1, be.getToastProgress())) * 100) : 0);
            out.text(INPUT, Slots.container(be.getContainer(), 0, 2));
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            if (!POWER.equals(widgetId)) return "not_interactive";
            boolean on = value.getAsBoolean();
            if (on && Slots.container(be.getContainer(), 0, 2).equals("Empty")) return "unavailable"; // nothing to toast
            be.setActive(on);
            changed();
            CCCloud.LOGGER.info("[cccloud] {} switched the toaster at {} {}", user, pos.toShortString(), on ? "on" : "off");
            return null;
        }
    }
}
