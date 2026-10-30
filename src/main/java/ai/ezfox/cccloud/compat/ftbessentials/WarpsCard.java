package ai.ezfox.cccloud.compat.ftbessentials;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import dev.ftb.mods.ftbessentials.util.FTBEWorldData;
import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * FTB Essentials (wave 4): a read-only "Warps" mainframe card – the server's warps (what {@code /listwarps} shows every
 * player) with their dimension and coordinates, nearest to the mainframe first, at most {@link #ROWS}. Restricted
 * ({@code view.positions}) because it shows coordinates. Homes are private to each player and never shown. Nothing on
 * the card teleports anyone. Config {@code world.warpsCard}.
 */
public final class WarpsCard extends MainframeCard {
    public static final String KIND = "warps";
    public static final String COUNT = "count";
    public static final String ROW = "warp";
    public static final String EMPTY = "empty";
    static final int ROWS = 8;

    private final CloudMainframeBlockEntity mainframe;
    private int rows;

    WarpsCard(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    public static void init() {
        MainframeCards.register(KIND, () -> IntegrationConfig.get(IntegrationConfig.WARPS_CARD), WarpsCard::new);
        ai.ezfox.cccloud.compat.Integrations.test("ftbessentials_warps", () -> WarpsGameTests::warps);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Warps";
    }

    @Override
    public List<Widget> widgets() {
        var w = new CardWidgets().label(COUNT, "Warps");
        if (rows == 0) w.label(EMPTY, "List");
        for (int i = 1; i <= rows; i++) w.label(ROW + i, "Warp " + i);
        return w.build();
    }

    @Override
    public int revision() {
        return rows;
    }

    static String dimension(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> Fmt.title(id.substring(id.indexOf(':') + 1));
        };
    }

    @Override
    public void refresh(CardOutput out) {
        var data = FTBEWorldData.getInstance();
        var level = mainframe.getLevel();
        if (data == null || level == null) return;
        List<SavedTeleportManager.DestinationEntry> warps = new ArrayList<>(data.warpManager().destinations().limit(4096).toList());
        String here = level.dimension().identifier().toString();
        var center = mainframe.getBlockPos();
        warps.sort(Comparator.<SavedTeleportManager.DestinationEntry>comparingInt(e -> e.destination().getDimensionId().identifier().toString().equals(here) ? 0 : 1)
            .thenComparingDouble(e -> e.destination().getPos().distSqr(center)).thenComparing(SavedTeleportManager.DestinationEntry::name));
        rows = Math.min(ROWS, warps.size());
        out.text(COUNT, warps.size() > ROWS ? warps.size() + " (nearest " + ROWS + " shown)" : String.valueOf(warps.size()));
        if (rows == 0) out.text(EMPTY, "No warps set (/setwarp)");
        for (int i = 0; i < rows; i++) {
            var e = warps.get(i);
            var p = e.destination().getPos();
            String dim = e.destination().getDimensionId().identifier().toString();
            out.text(ROW + (i + 1), String.format(Locale.ROOT, "%s – %s %d, %d, %d", e.name(), dimension(dim), p.getX(), p.getY(), p.getZ()));
        }
    }
}
