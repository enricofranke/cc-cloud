package ai.ezfox.cccloud.compat.thaumaturge;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.crucible.BlockEntityCrucible;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import com.leclowndu93150.thaumaturge.content.essentia.reservoir.BlockEntityEssentiaReservoir;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityInfusionMatrix;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Thaumaturge (wave 5): <b>Essentia</b> for warded and void jars and the reservoir (fill gauge, what is inside), <b>Aura
 * Node</b> (type, state, aspects against the node's base, the chunk's vis and flux), <b>Runic Matrix</b> (crafting,
 * stability, essentia still to pay, chunk aura; warn alert when an infusion turns unstable) and <b>Crucible</b> (heat,
 * water, essentia, its limit). The mod's public aura and aspect API plus the block entities' getters.
 */
public final class ThaumaturgeCards {
    public static final String ESSENTIA = "essentia";
    public static final String AMOUNT = "amount";
    public static final String ASPECTS = "aspects";
    public static final String TYPE = "type";
    public static final String STATE = "state";
    public static final String AURA = "aura";
    public static final String STABILITY = "stability";
    public static final String HEAT = "heat";
    public static final String WATER = "water";
    /** Aspects named on one label, the largest first. */
    static final int ASPECTS_SHOWN = 6;

    private ThaumaturgeCards() {
    }

    public static void init() {
        DeviceCards.register("thaumaturge", DeviceCards.SPECIFIC, ThaumaturgeCards::detect);
        Integrations.test("thaumaturge_jar", () -> ThaumaturgeGameTests::jar);
        Integrations.test("thaumaturge_node", () -> ThaumaturgeGameTests::node);
        Integrations.test("thaumaturge_matrix", () -> ThaumaturgeGameTests::matrix);
        Integrations.test("thaumaturge_crucible", () -> ThaumaturgeGameTests::crucible);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return switch (level.getBlockEntity(pos)) {
            case BlockEntityJar jar -> new Jar(level, pos, side, jar);
            case BlockEntityEssentiaReservoir reservoir -> new Reservoir(level, pos, side, reservoir);
            case BlockEntityNode node -> new Node(level, pos, side, node);
            case BlockEntityInfusionMatrix matrix -> new Matrix(level, pos, side, matrix);
            case BlockEntityCrucible crucible -> new Crucible(level, pos, side, crucible);
            case null, default -> null;
        };
    }

    static String aspectName(AspectInstance entry) {
        return Fmt.title(entry.aspect().value().tag());
    }

    /** "Aer 120, Ignis 30, +2 more", largest first; "None" when empty. */
    public static String aspects(AspectList list) {
        var sorted = list.sortedByAmount();
        if (sorted.isEmpty()) return "None";
        var text = sorted.stream().limit(ASPECTS_SHOWN).map(a -> aspectName(a) + " " + a.amount()).collect(Collectors.joining(", "));
        return sorted.size() > ASPECTS_SHOWN ? text + ", +" + (sorted.size() - ASPECTS_SHOWN) + " more" : text;
    }

    /** Vis against the chunk's base, and flux, at this block. */
    static String aura(ServerLevel level, BlockPos pos) {
        return String.format(Locale.ROOT, "Vis %.0f / %d, flux %.1f", AuraHelper.getVis(level, pos), AuraHelper.getAuraBase(level, pos), AuraHelper.getFlux(level, pos));
    }

    static final class Jar extends BlockCard<BlockEntityJar> {
        Jar(ServerLevel level, BlockPos pos, Direction side, BlockEntityJar jar) {
            super(level, pos, side, jar);
        }

        @Override
        public String kind() {
            return "thaumaturge_jar";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .percent(ESSENTIA, "Essentia", "color", "cyan", "icon", "drop")
                .label(ASPECTS, "Aspect", "span", 2)
                .label(AMOUNT, "Amount", "span", 2)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            int amount = be.amount(), capacity = be.capacity();
            out.number(ESSENTIA, Fmt.percent(amount, capacity));
            out.text(ASPECTS, aspects(be.getAspects()));
            out.text(AMOUNT, amount + " / " + capacity);
        }
    }

    static final class Reservoir extends BlockCard<BlockEntityEssentiaReservoir> {
        Reservoir(ServerLevel level, BlockPos pos, Direction side, BlockEntityEssentiaReservoir reservoir) {
            super(level, pos, side, reservoir);
        }

        @Override
        public String kind() {
            return "thaumaturge_reservoir";
        }

        @Override
        public String title() {
            return "Essentia Reservoir";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .percent(ESSENTIA, "Essentia", "color", "cyan", "icon", "drop")
                .label(AMOUNT, "Amount")
                .label(ASPECTS, "Aspects")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            int amount = be.getStoredAmount();
            out.number(ESSENTIA, Fmt.percent(amount, BlockEntityEssentiaReservoir.CAPACITY));
            out.text(AMOUNT, amount + " / " + BlockEntityEssentiaReservoir.CAPACITY);
            out.text(ASPECTS, aspects(be.contents()));
        }
    }

    static final class Node extends BlockCard<BlockEntityNode> {
        Node(ServerLevel level, BlockPos pos, Direction side, BlockEntityNode node) {
            super(level, pos, side, node);
        }

        @Override
        public String kind() {
            return "thaumaturge_node";
        }

        @Override
        public String title() {
            return "Aura Node";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(TYPE, "Type", "span", 2)
                .label(STATE, "State", "span", 2)
                .label(ASPECTS, "Aspects")
                .label(AMOUNT, "Vis in the node")
                .label(AURA, "Aura here")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var type = be.getNodeType();
            var modifier = be.getNodeModifier();
            String typeName = type == null ? "–" : Fmt.title(type.getSerializedName());
            out.text(TYPE, modifier == null ? typeName : Fmt.title(modifier.getSerializedName()) + " " + typeName);
            out.text(STATE, be.isEnergized() ? "Energized" : "Natural");
            out.text(ASPECTS, aspects(be.getAspects()));
            out.text(AMOUNT, be.getAspects().totalAmount() + " / " + be.getAspectsBase().totalAmount());
            out.text(AURA, aura(level, pos));
        }
    }

    static final class Matrix extends BlockCard<BlockEntityInfusionMatrix> {
        private boolean wasUnstable;

        Matrix(ServerLevel level, BlockPos pos, Direction side, BlockEntityInfusionMatrix matrix) {
            super(level, pos, side, matrix);
        }

        @Override
        public String kind() {
            return "thaumaturge_matrix";
        }

        @Override
        public String title() {
            return "Runic Matrix";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATE, "Status", "span", 2)
                .label(STABILITY, "Stability", "span", 2)
                .label(ASPECTS, "Essentia still needed")
                .label(AURA, "Aura here")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            boolean crafting = be.isCrafting();
            float stability = be.stability();
            out.text(STATE, crafting ? "Infusing" : be.isActive() ? "Ready" : "Not activated");
            out.text(STABILITY, String.format(Locale.ROOT, "%.1f / %.0f", stability, BlockEntityInfusionMatrix.STABILITY_CAP));
            out.text(ASPECTS, crafting ? aspects(be.remainingEssentia()) : "–");
            out.text(AURA, aura(level, pos));
            boolean unstable = crafting && stability < 0;
            if (unstable && !wasUnstable) out.alert("Infusion is unstable", "warn");
            wasUnstable = unstable;
        }
    }

    static final class Crucible extends BlockCard<BlockEntityCrucible> {
        Crucible(ServerLevel level, BlockPos pos, Direction side, BlockEntityCrucible crucible) {
            super(level, pos, side, crucible);
        }

        @Override
        public String kind() {
            return "thaumaturge_crucible";
        }

        @Override
        public String title() {
            return "Crucible";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(HEAT, "Heat", "span", 2)
                .label(WATER, "Water", "span", 2)
                .percent(ESSENTIA, "Essentia", "color", "cyan", "icon", "drop")
                .label(ASPECTS, "Aspects")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            var aspects = be.getAspects();
            out.text(HEAT, String.valueOf(be.getHeat()));
            out.text(WATER, be.getTank().getAmountAsInt(0) + " / " + BlockEntityCrucible.TANK_CAPACITY + " mB");
            out.number(ESSENTIA, Fmt.percent(aspects.totalAmount(), BlockEntityCrucible.MAX_ASPECT));
            out.text(ASPECTS, aspects(aspects));
        }
    }
}
