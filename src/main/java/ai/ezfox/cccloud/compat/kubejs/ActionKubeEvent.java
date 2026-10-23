package ai.ezfox.cccloud.compat.kubejs;

import ai.ezfox.cccloud.api.CloudEvents;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code CCCloudEvents.action(event => ...)}: a dashboard action (browser, tablet, display) that a section accepted, after
 * the permission check and validation. Read-only: it reports what happened, the section already ran the action.
 */
public final class ActionKubeEvent implements KubeEvent {
    private final CloudEvents.Action action;

    ActionKubeEvent(CloudEvents.Action action) {
        this.action = action;
    }

    public String getMainframeId() {
        return action.mainframeId().toString();
    }

    public String getSectionId() {
        return action.sectionId().toString();
    }

    /** The section's title (the card name on the dashboard). */
    public String getSection() {
        return action.sectionTitle();
    }

    public String getWidget() {
        return action.widgetId();
    }

    /** The value as a plain JS value (boolean, number, string), or null for buttons. */
    public @Nullable Object getValue() {
        return CCCloudJS.plain(action.value());
    }

    public String getPlayerName() {
        return action.playerName();
    }

    /** The player if online, else null. */
    public @Nullable ServerPlayer getPlayer() {
        return KubeEvents.player(action.player());
    }

    /** The paired device's name (e.g. "Chrome on Mac", "in-game"). */
    public String getDevice() {
        return action.device();
    }
}
