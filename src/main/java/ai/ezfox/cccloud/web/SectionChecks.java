package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.network.CloudNetwork;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Can a dashboard action reach this section right now? Server thread. */
public final class SectionChecks {
    private SectionChecks() {
    }

    /**
     * Null if the mainframe is online and enabled and the section is loaded and linked to it; else
     * {@code offline|no_power|disabled|unloaded|unknown_uplink}.
     */
    public static @Nullable String check(UUID mainframeId, UUID uplinkId) {
        var mainframe = CloudNetwork.mainframe(mainframeId);
        if (mainframe == null || !mainframe.isOnline()) return mainframe != null && "no_power".equals(mainframe.reason()) ? "no_power" : "offline";
        if (!mainframe.isEnabled()) return "disabled";
        var section = CloudNetwork.section(uplinkId);
        // Known but its chunk is not loaded: the dashboard shows the cached card, actions are refused.
        if (section == null && WebHub.INSTANCE.hasUplink(mainframeId, uplinkId.toString())) return "unloaded";
        if (section == null || section.getLink() == null || !section.getLink().id().equals(mainframeId)) return "unknown_uplink";
        return null;
    }
}
