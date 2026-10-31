package ai.ezfox.cccloud.compat.neovitae;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import com.breakinblocks.neovitae.util.helper.AnimaHelper;
import java.util.List;

/**
 * Neo Vitae (wave 5): an "Anima" mainframe card with the mainframe owner's EV (the mod's life points, which rituals and
 * sigils draw from) and a 60-minute chart. One player's EV is private, so this is a restricted section
 * ({@code view.positions}), never a device card. Read every 10 s; the mod keeps the value in its saved data, so it also
 * works while the owner is offline.
 */
public final class AnimaCard extends MainframeCard {
    public static final String KIND = "neovitae_anima";
    public static final String EV = "ev";
    public static final String HISTORY = "history";

    private final CloudMainframeBlockEntity mainframe;
    private int refreshes;

    AnimaCard(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    static void init() {
        MainframeCards.register(KIND, () -> IntegrationConfig.get(IntegrationConfig.ANIMA_CARD), AnimaCard::new);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Anima";
    }

    @Override
    public List<Widget> widgets() {
        return new CardWidgets()
            .label(EV, "EV of the owner", "icon", "drop")
            .history(HISTORY, "EV", null, null, "EV")
            .build();
    }

    @Override
    public void refresh(CardOutput out) {
        if (refreshes++ % 10 != 0) return;
        var owner = mainframe.getOwner();
        if (owner == null) {
            out.text(EV, "Owner unknown");
            return;
        }
        int ev = AnimaHelper.getAnima(owner).getCurrentEV();
        out.text(EV, Fmt.si(ev) + " EV");
        out.number(HISTORY, ev);
    }
}
