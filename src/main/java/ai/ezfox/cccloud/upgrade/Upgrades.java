package ai.ezfox.cccloud.upgrade;

import ai.ezfox.cccloud.CloudConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Last known upgrade effects per mainframe (also while the mainframe is unloaded, for rules and the dashboard).
 * Mainframes publish here when their upgrade slots change or they load.
 */
public final class Upgrades {
    private static final Map<UUID, UpgradeEffects> EFFECTS = new ConcurrentHashMap<>();

    private Upgrades() {
    }

    public static UpgradeEffects.Base base() {
        return new UpgradeEffects.Base(CloudConfig.get(CloudConfig.BASE_DEVICES), CloudConfig.get(CloudConfig.BASE_BROWSERS),
            CloudConfig.get(CloudConfig.BASE_RULES), CloudConfig.get(CloudConfig.BASE_HISTORY_HOURS));
    }

    public static UpgradeEffects effects(UUID mainframeId) {
        var e = EFFECTS.get(mainframeId);
        return e != null ? e : UpgradeEffects.none(base());
    }

    public static void publish(UUID mainframeId, UpgradeEffects effects) {
        EFFECTS.put(mainframeId, effects);
    }

    public static void forget(UUID mainframeId) {
        EFFECTS.remove(mainframeId);
    }

    public static void clear() {
        EFFECTS.clear();
    }
}
