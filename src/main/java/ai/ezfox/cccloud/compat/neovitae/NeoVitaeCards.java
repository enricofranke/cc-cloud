package ai.ezfox.cccloud.compat.neovitae;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.breakinblocks.neovitae.common.blockentity.AraVitaeTile;
import com.breakinblocks.neovitae.common.blockentity.HellfireForgeBlockEntity;
import com.breakinblocks.neovitae.common.blockentity.MasterRitualStoneBlockEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Neo Vitae (wave 5), the Blood Magic successor in ATM11: the <b>Ara Vitae</b> (the blood altar: tier, blood gauge, the
 * recipe's progress and the item on it), the <b>Master Ritual Stone</b> (ritual, running or suspended by redstone, cost)
 * and the <b>Hellfire Forge</b> (status, progress, spiritus against what the recipe wants). The owner's EV is private and
 * lives on the restricted {@link AnimaCard}.
 */
public final class NeoVitaeCards {
    public static final String STATUS = "status";
    public static final String TIER = "tier";
    public static final String BLOOD = "blood";
    public static final String BLOOD_TEXT = "blood_text";
    public static final String PROGRESS = "progress";
    public static final String ITEM = "item";
    public static final String RUNES = "runes";
    public static final String RITUAL = "ritual";
    public static final String RUNNING = "running";
    public static final String COST = "cost";
    public static final String SPIRITUS = "spiritus";

    private NeoVitaeCards() {
    }

    public static void init() {
        DeviceCards.register("neovitae", DeviceCards.SPECIFIC, NeoVitaeCards::detect);
        AnimaCard.init();
        Integrations.test("neovitae_altar", () -> NeoVitaeGameTests::altar);
        Integrations.test("neovitae_ritual", () -> NeoVitaeGameTests::ritual);
        Integrations.test("neovitae_forge", () -> NeoVitaeGameTests::forge);
        Integrations.test("neovitae_anima", () -> NeoVitaeGameTests::anima);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return switch (level.getBlockEntity(pos)) {
            case AraVitaeTile altar -> new Altar(level, pos, side, altar);
            case MasterRitualStoneBlockEntity stone -> new Ritual(level, pos, side, stone);
            case HellfireForgeBlockEntity forge -> new Forge(level, pos, side, forge);
            case null, default -> null;
        };
    }

    static String itemName(ItemStack stack) {
        if (stack.isEmpty()) return "–";
        String name = Fmt.title(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        return stack.getCount() > 1 ? stack.getCount() + " × " + name : name;
    }

    static final class Altar extends BlockCard<AraVitaeTile> {
        private boolean wasDry;

        Altar(ServerLevel level, BlockPos pos, Direction side, AraVitaeTile altar) {
            super(level, pos, side, altar);
        }

        @Override
        public String kind() {
            return "neovitae_altar";
        }

        @Override
        public String title() {
            return "Ara Vitae";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(TIER, "Tier", "span", 2)
                .percent(BLOOD, "Blood", "color", "red", "icon", "drop")
                .label(BLOOD_TEXT, "Stored")
                .progress(PROGRESS, "Progress", 0, 100, "%")
                .label(ITEM, "On the altar", "span", 2)
                .label(RUNES, "Runes", "span", 2)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            int blood = be.getCurrentBlood(), capacity = be.getCapacity();
            boolean crafting = be.getCurrentRecipe() != null;
            boolean dry = crafting && blood <= 0;
            out.text(STATUS, dry ? "No blood" : crafting ? "Crafting" : be.getStackInSlot().isEmpty() ? "Idle" : "No recipe for this item");
            out.text(TIER, "Tier " + be.getTier());
            out.number(BLOOD, Fmt.percent(blood, capacity));
            out.text(BLOOD_TEXT, Fmt.si(blood) + " / " + Fmt.si(capacity) + " mB");
            out.number(PROGRESS, Math.round(Math.max(0, Math.min(1, be.getProgressFloat())) * 100));
            out.text(ITEM, itemName(be.getStackInSlot()));
            out.text(RUNES, String.format(Locale.ROOT, "×%.2f speed, ×%.2f efficiency", 1 + be.getSpeedBonus(), 1 + be.getEfficiency()));
            if (dry && !wasDry) out.alert("Ara Vitae ran out of blood", "warn");
            wasDry = dry;
        }
    }

    static final class Ritual extends BlockCard<MasterRitualStoneBlockEntity> {
        Ritual(ServerLevel level, BlockPos pos, Direction side, MasterRitualStoneBlockEntity stone) {
            super(level, pos, side, stone);
        }

        @Override
        public String kind() {
            return "neovitae_ritual";
        }

        @Override
        public String title() {
            return "Master Ritual Stone";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(RITUAL, "Ritual", "span", 2)
                .label(STATUS, "Status", "span", 2)
                .label(RUNNING, "Running for", "span", 2)
                .label(COST, "Cost", "span", 2)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var ritual = be.getCurrentRitual();
            boolean active = be.isActive() && ritual != null;
            out.text(RITUAL, ritual == null ? "None" : Fmt.title(ritual.getName()));
            out.text(STATUS, !active ? "Stopped" : be.isSuspended() ? "Suspended by redstone" : "Running");
            out.text(RUNNING, active ? Fmt.duration(be.getRunningTime() / 20) : "–");
            out.text(COST, active ? Fmt.si(ritual.getRefreshCost()) + " EV every " + ritual.getRefreshTime() + " t" : "–");
        }
    }

    static final class Forge extends BlockCard<HellfireForgeBlockEntity> {
        Forge(ServerLevel level, BlockPos pos, Direction side, HellfireForgeBlockEntity forge) {
            super(level, pos, side, forge);
        }

        @Override
        public String kind() {
            return "neovitae_forge";
        }

        @Override
        public String title() {
            return "Hellfire Forge";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(SPIRITUS, "Spiritus", "span", 2)
                .progress(PROGRESS, "Progress", 0, 100, "%")
                .label(ITEM, "Output")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var data = be.dataAccess;
            int status = data.get(HellfireForgeBlockEntity.DATA_STATUS);
            int required = data.get(HellfireForgeBlockEntity.DATA_REQUIRED_SPIRITUS);
            int stored = data.get(HellfireForgeBlockEntity.DATA_STORED_SPIRITUS);
            out.text(STATUS, switch (status) {
                case HellfireForgeBlockEntity.STATUS_CRAFTING -> "Crafting";
                case HellfireForgeBlockEntity.STATUS_NEEDS_SPIRITUS -> "Needs spiritus";
                default -> "Idle";
            });
            out.text(SPIRITUS, required > 0 ? Fmt.si(stored) + " / " + Fmt.si(required) : Fmt.si(stored));
            out.number(PROGRESS, Fmt.percent(data.get(HellfireForgeBlockEntity.DATA_PROGRESS), HellfireForgeBlockEntity.MAX_PROGRESS));
            out.text(ITEM, itemName(be.inv.getStackInSlot(HellfireForgeBlockEntity.OUTPUT_SLOT)));
        }
    }
}
