package ai.ezfox.cccloud.compat.appliedthaumaturge;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.thaumaturge.ThaumaturgeCards;
import ai.ezfox.cccloud.uplink.Widget;
import com.traverse.appliedthaumaturge.assembler.ArcaneAssemblerBlockEntity;
import com.traverse.appliedthaumaturge.block.entity.EssentiaNetworkBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Applied Thaumaturge (wave 5), the bridge between Thaumaturge and AE2: <b>Arcane Assembler</b> (status, progress, vis it
 * can draw, link to a vis source, patterns) and <b>ME Essentia</b> for the Essentia Interface and Infusion Provider
 * (network online, the essentia stored in the ME network).
 */
public final class AppliedThaumaturgeCards {
    public static final String STATUS = "status";
    public static final String PROGRESS = "progress";
    public static final String VIS = "vis";
    public static final String LINK = "link";
    public static final String PATTERNS = "patterns";
    public static final String ESSENTIA = "essentia";
    /** The assembler counts its progress up to 100. */
    static final int PROGRESS_MAX = 100;

    private AppliedThaumaturgeCards() {
    }

    public static void init() {
        DeviceCards.register("appliedthaumaturge", DeviceCards.SPECIFIC, AppliedThaumaturgeCards::detect);
        Integrations.test("appliedthaumaturge_assembler", () -> AppliedThaumaturgeGameTests::assembler);
        Integrations.test("appliedthaumaturge_interface", () -> AppliedThaumaturgeGameTests::essentiaInterface);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return switch (level.getBlockEntity(pos)) {
            case ArcaneAssemblerBlockEntity assembler -> new Assembler(level, pos, side, assembler);
            case EssentiaNetworkBlockEntity network -> new MeEssentia(level, pos, side, network);
            case null, default -> null;
        };
    }

    static final class Assembler extends BlockCard<ArcaneAssemblerBlockEntity> {
        Assembler(ServerLevel level, BlockPos pos, Direction side, ArcaneAssemblerBlockEntity assembler) {
            super(level, pos, side, assembler);
        }

        @Override
        public String kind() {
            return "appliedthaumaturge_assembler";
        }

        @Override
        public String title() {
            return "Arcane Assembler";
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "Status", "span", 2)
                .label(PATTERNS, "Patterns", "span", 2)
                .progress(PROGRESS, "Progress", 0, 100, "%")
                .label(VIS, "Vis available", "span", 2)
                .label(LINK, "Vis link", "span", 2)
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            out.text(STATUS, !be.isNetworkActive() ? "No ME network" : be.isBusy() ? "Crafting" : "Idle");
            out.text(PATTERNS, String.valueOf(be.getAvailablePatterns().size()));
            out.number(PROGRESS, Fmt.percent(be.getProgress(), PROGRESS_MAX));
            out.text(VIS, Fmt.si(be.getAvailableVis()));
            out.text(LINK, be.isVisLinked() ? "Linked" : "Not linked");
        }
    }

    static final class MeEssentia extends BlockCard<EssentiaNetworkBlockEntity> {
        MeEssentia(ServerLevel level, BlockPos pos, Direction side, EssentiaNetworkBlockEntity network) {
            super(level, pos, side, network);
        }

        @Override
        public String kind() {
            return "appliedthaumaturge_essentia";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public List<Widget> widgets() {
            return new CardWidgets()
                .label(STATUS, "ME network")
                .label(ESSENTIA, "Essentia in the network")
                .build();
        }

        @Override
        public void refresh(CardOutput out) {
            boolean active = be.isNetworkActive();
            out.text(STATUS, active ? "Online" : "Offline");
            out.text(ESSENTIA, active ? ThaumaturgeCards.aspects(be.storage().contents()) : "–");
        }
    }
}
