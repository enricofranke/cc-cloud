package ai.ezfox.cccloud.compat.arsmagica;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import at.minecraftschurli.mods.arsmagicalegacy.api.etherium.EtheriumHandler;
import at.minecraftschurli.mods.arsmagicalegacy.api.etherium.EtheriumType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Ars Magica Legacy (wave 3): any block entity of its public etherium API ({@code EtheriumHandler}: obelisk, celestial
 * prism, black aurem, altar core) – a gauge and amount / max per etherium type. The widget set follows the block's
 * etherium types (card revision).
 */
public final class EtheriumCards {
    private EtheriumCards() {
    }

    public static void init() {
        DeviceCards.register("arsmagica", DeviceCards.SPECIFIC, EtheriumCards::detect);
        Integrations.test("arsmagica_obelisk", () -> ArsMagicaGameTests::obelisk);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        return be instanceof EtheriumHandler ? new Etherium(level, pos, side, be) : null;
    }

    static String name(Holder<EtheriumType> type) {
        return type.unwrapKey().map(k -> k.identifier().getPath()).orElse("etherium");
    }

    static final class Etherium extends BlockCard<BlockEntity> {
        Etherium(ServerLevel level, BlockPos pos, Direction side, BlockEntity be) {
            super(level, pos, side, be);
        }

        private List<Holder<EtheriumType>> types() {
            return new ArrayList<>(((EtheriumHandler) be).getEtheriumTypes());
        }

        @Override
        public String kind() {
            return "arsmagica_etherium";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public int revision() {
            return types().stream().map(EtheriumCards::name).toList().hashCode();
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets();
            for (var type : types()) {
                String n = name(type);
                w.percent("etherium_" + n, Fmt.title(n) + " etherium", "color", "cyan");
                w.label("etherium_" + n + "_text", Fmt.title(n) + " stored");
            }
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            var handler = (EtheriumHandler) be;
            for (var type : types()) {
                String n = name(type);
                int amount = handler.getAmount(type), max = handler.getMaxAmount(type);
                out.number("etherium_" + n, Fmt.percent(amount, max));
                out.text("etherium_" + n + "_text", Fmt.si(amount) + " / " + Fmt.si(max));
            }
        }
    }
}
