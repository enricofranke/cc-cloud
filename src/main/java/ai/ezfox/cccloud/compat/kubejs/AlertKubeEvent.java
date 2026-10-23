package ai.ezfox.cccloud.compat.kubejs;

import ai.ezfox.cccloud.api.CloudEvents;
import dev.latvian.mods.kubejs.event.KubeEvent;
import org.jspecify.annotations.Nullable;

/** {@code CCCloudEvents.alert(event => ...)}: an alert went to a mainframe's dashboards (from Lua, a card, a rule or a script). */
public final class AlertKubeEvent implements KubeEvent {
    private final CloudEvents.Alert alert;

    AlertKubeEvent(CloudEvents.Alert alert) {
        this.alert = alert;
    }

    public String getMainframeId() {
        return alert.mainframeId().toString();
    }

    /** The section the alert belongs to, or null for mainframe alerts. */
    public @Nullable String getSectionId() {
        return alert.sectionId();
    }

    public String getText() {
        return alert.text();
    }

    /** info, warn or crit. */
    public String getLevel() {
        return alert.level();
    }
}
