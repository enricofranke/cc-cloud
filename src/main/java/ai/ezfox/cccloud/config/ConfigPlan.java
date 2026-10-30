package ai.ezfox.cccloud.config;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a reload has to do for a set of changed keys (pure, unit tested). Values read where they are used need nothing;
 * these steps re-apply what was cached when the server started or a block loaded.
 */
public enum ConfigPlan {
    /** {@code [web]}: restart the dashboard's web server (rebind first, keep the old one on failure). */
    WEB,
    /** {@code [api]}: the running web server takes the new settings at once; streams end when switched off. */
    API,
    /** Ranges, power, capacity weights: recompute every network (NetworkService). Always done after a change. */
    NETWORK,
    /** {@code [upgrades]} base limits: recompute the published upgrade effects of every mainframe. */
    UPGRADES,
    /** Integrations {@code [cards]}: detect every uplink's device and wired machine cards again. */
    CARDS,
    /** {@code [store]}: forget verified store indexes (a new key or URL is checked again). */
    STORE,
    /** {@code mapTileMaxAgeDays}: sweep the map tile cache with the new age now. */
    MAP_SWEEP,
    /** A feature switch the dashboard reads from {@code GET /api/config}: tell open dashboards to fetch it again. */
    FEATURES,
    /** Team providers / FTB Teams trust: re-check every open session's role now (not within 5 s). */
    ACCESS,
    /** {@code [icons]}: open the vanilla source again and let the server renderer draw what is still missing. */
    ICONS;

    /** Keys the dashboard shows as features ({@code GET /api/config}). */
    static final Set<String> FEATURE_KEYS = Set.of("terminal.terminalEnabled", "map.voxelView", "push.pushEnabled", "rules.rulesEnabled",
        "scenes.scenesEnabled", "production.productionEnabled", "api.apiEnabled", "store.scriptStore", "store.scriptStoreUrl", "store.scriptStoreKey");

    public static EnumSet<ConfigPlan> of(Collection<String> changedKeys) {
        var plan = EnumSet.noneOf(ConfigPlan.class);
        if (changedKeys.isEmpty()) return plan;
        plan.add(NETWORK);
        for (String key : changedKeys) {
            if (key.startsWith("web.")) plan.add(WEB);
            if (key.startsWith("api.")) plan.add(API);
            if (key.startsWith("upgrades.base")) plan.add(UPGRADES);
            if (key.startsWith("cards.")) plan.add(CARDS);
            if (key.startsWith("store.")) plan.add(STORE);
            if (key.equals("map.mapTileMaxAgeDays")) plan.add(MAP_SWEEP);
            if (FEATURE_KEYS.contains(key)) plan.add(FEATURES);
            if (key.equals("teams.disabledTeamProviders") || key.equals("server.ftbTeamsTrust")) plan.add(ACCESS);
            if (key.startsWith("icons.")) plan.add(ICONS);
        }
        return plan;
    }
}
