package ai.ezfox.cccloud.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config ({@code config/cccloud-client.toml}). */
public final class CloudClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ANIMATIONS;
    public static final ModConfigSpec.BooleanValue SHARE_ICON_RENDERS;
    public static final ModConfigSpec.BooleanValue ANALYZER_OVERLAY;
    public static final ModConfigSpec.BooleanValue JOURNEYMAP_WAYPOINTS;

    static {
        var b = new ModConfigSpec.Builder();
        ANIMATIONS = b.comment("Animate the mainframe fan/hologram and the uplink rings (only within 32 blocks). false = static parts.")
            .translation("cccloud.configuration.animations")
            .define("animations", true);
        SHARE_ICON_RENDERS = b.comment("Render item icons for the CC: Cloud dashboard in the background and send them to the server (≤ 2 ms per frame).")
            .translation("cccloud.configuration.shareIconRenders")
            .define("shareIconRenders", true);
        ANALYZER_OVERLAY = b.comment("Show the network overlay (ranges, links, placement preview, signal line) while holding a Cloud Analyzer, Link Card or cloud device.")
            .translation("cccloud.configuration.analyzerOverlay")
            .define("analyzerOverlay", true);
        JOURNEYMAP_WAYPOINTS = b.comment("With JourneyMap installed: show the mainframes, relays and devices of every network where you may see positions as (temporary) JourneyMap waypoints.")
            .translation("cccloud.configuration.journeyMapWaypoints")
            .define("journeyMapWaypoints", true);
        SPEC = b.build();
    }

    private CloudClientConfig() {
    }

    public static boolean animations() {
        return SPEC.isLoaded() ? ANIMATIONS.get() : ANIMATIONS.getDefault();
    }

    public static boolean analyzerOverlay() {
        return SPEC.isLoaded() ? ANALYZER_OVERLAY.get() : ANALYZER_OVERLAY.getDefault();
    }

    public static boolean journeyMapWaypoints() {
        return SPEC.isLoaded() ? JOURNEYMAP_WAYPOINTS.get() : JOURNEYMAP_WAYPOINTS.getDefault();
    }

    public static boolean shareIconRenders() {
        return SPEC.isLoaded() ? SHARE_ICON_RENDERS.get() : SHARE_ICON_RENDERS.getDefault();
    }
}
