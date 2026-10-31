package ai.ezfox.cccloud.compat.ftbranks;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import dev.ftb.mods.ftbranks.api.FTBRanksAPI;
import dev.ftb.mods.ftbranks.api.Rank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Ranks (wave 5): a read-only "Ranks" mainframe card for server owners: the ranks of the server, strongest first,
 * with their power and how many online players have them right now (counts only, no names). A server's rank setup is not
 * for everyone, so this is a restricted section ({@code view.positions}); config {@code world.ranksCard}. Read every 10 s.
 */
public final class RanksCard extends MainframeCard {
    public static final String KIND = "ftbranks_ranks";
    public static final String COUNT = "count";
    public static final String ROW = "rank";
    public static final String EMPTY = "empty";
    static final int ROWS = 8;

    private final CloudMainframeBlockEntity mainframe;
    private int refreshes;
    private int rows;

    RanksCard(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    public static void init() {
        MainframeCards.register(KIND, () -> IntegrationConfig.get(IntegrationConfig.RANKS_CARD), RanksCard::new);
        ai.ezfox.cccloud.compat.Integrations.test("ftbranks_ranks", () -> RanksGameTests::ranks);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Ranks";
    }

    @Override
    public int revision() {
        return rows;
    }

    @Override
    public List<Widget> widgets() {
        var w = new CardWidgets().label(COUNT, "Ranks");
        if (rows == 0) w.label(EMPTY, "List");
        for (int i = 1; i <= rows; i++) w.label(ROW + i, "Rank " + i);
        return w.build();
    }

    @Override
    public void refresh(CardOutput out) {
        if (refreshes++ % 10 != 0) return;
        var ranks = new ArrayList<Rank>(FTBRanksAPI.manager().getAllRanks());
        ranks.sort(Comparator.comparingInt(Rank::getPower).reversed().thenComparing(Rank::getName));
        List<ServerPlayer> online = mainframe.getLevel().getServer().getPlayerList().getPlayers();
        rows = Math.min(ROWS, ranks.size());
        out.text(COUNT, String.valueOf(ranks.size()));
        if (rows == 0) out.text(EMPTY, "No ranks");
        for (int i = 1; i <= rows; i++) {
            var rank = ranks.get(i - 1);
            long active = online.stream().filter(rank::isActive).count();
            out.text(ROW + i, rank.getName() + " (power " + rank.getPower() + "): " + active + " online");
        }
    }
}
