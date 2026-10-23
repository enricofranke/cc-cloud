package ai.ezfox.cccloud.compat.server;

import ai.ezfox.cccloud.compat.card.DeviceCard;
import com.google.gson.JsonObject;

/**
 * A card that belongs to a mainframe itself instead of an uplink (docs/SPEC.md v0.5 B): the Server card, Claims
 * (FTB Chunks) and Backups (Simple Backups). Published as its own section with {@code "restricted":true}, so only
 * devices with canControl receive it. Server thread only.
 */
public abstract class MainframeCard extends DeviceCard {
    @Override
    public boolean isValid() {
        return true;
    }

    /** Extra fields for the section snapshot (e.g. the claims overlay); sent with every structure update. */
    public void snapshotExtras(JsonObject snapshot) {
    }
}
