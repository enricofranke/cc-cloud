package ai.ezfox.cccloud.compat.forbiddenarcanus;

import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity;
import com.stal111.forbidden_arcanus.common.essence.EssenceType;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Forbidden &amp; Arcanus Hephaestus Forge (wave 3): forge tier, ritual running, main item, and the four essences
 * (aureal, ectoplasm/souls, blood, experience) as gauges with amount / limit; info alert when a ritual finishes.
 */
public final class HephaestusForgeCards {
    public static final String STATUS = "status";
    public static final String ITEM = "item";

    private HephaestusForgeCards() {
    }

    public static void init() {
        DeviceCards.register("forbiddenarcanus", DeviceCards.SPECIFIC, HephaestusForgeCards::detect);
        Integrations.test("forbiddenarcanus_forge", () -> ForbiddenArcanusGameTests::forge);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof HephaestusForgeBlockEntity forge ? new Forge(level, pos, side, forge) : null;
    }

    static String id(EssenceType type) {
        return "essence_" + type.getSerializedName().toLowerCase(Locale.ROOT);
    }

    static final class Forge extends BlockCard<HephaestusForgeBlockEntity> {
        private boolean wasActive;

        Forge(ServerLevel level, BlockPos pos, Direction side, HephaestusForgeBlockEntity forge) {
            super(level, pos, side, forge);
        }

        @Override
        public String kind() {
            return "forbiddenarcanus_forge";
        }

        @Override
        public String title() {
            return "Hephaestus Forge";
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(ITEM, "Main item", "span", 2);
            for (var type : EssenceType.values()) w.percent(id(type), Fmt.title(type.getSerializedName()));
            for (var type : EssenceType.values()) w.label(id(type) + "_text", Fmt.title(type.getSerializedName()) + " stored");
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            var state = be.getCurrenState();
            boolean active = be.getRitualManager().isRitualActive();
            out.text(STATUS, "Tier " + (state.tier().ordinal() + 1) + (active ? ", ritual running" : ", idle"));
            out.text(ITEM, state.mainItem().isEmpty() ? "None" : ItemNames.displayName(state.mainItem().getItem()));
            for (var type : EssenceType.values()) {
                var storage = be.getEssence(type);
                out.number(id(type), Fmt.percent(storage.amount(), storage.limit()));
                out.text(id(type) + "_text", Fmt.si(storage.amount()) + " / " + Fmt.si(storage.limit()));
            }
            if (wasActive && !active) out.alert("Hephaestus Forge: ritual finished", "info");
            wasActive = active;
        }
    }
}
