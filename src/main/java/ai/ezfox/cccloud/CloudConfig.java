package ai.ezfox.cccloud;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config ({@code serverconfig/cccloud-server.toml} in the world folder).
 */
public final class CloudConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ALLOW_CLOUD;
    public static final ModConfigSpec.BooleanValue WEB_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> WEB_BIND;
    public static final ModConfigSpec.IntValue WEB_PORT;
    public static final ModConfigSpec.ConfigValue<String> PUBLIC_URL;
    public static final ModConfigSpec.BooleanValue WEB_ALLOW_INTERNET;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WEB_TRUSTED_PROXIES;
    public static final ModConfigSpec.BooleanValue WEB_TRUST_CLOUDFLARE_HEADER;
    public static final ModConfigSpec.ConfigValue<String> TLS_CERT_PATH;
    public static final ModConfigSpec.ConfigValue<String> TLS_KEY_PATH;
    // v0.4 network ranges (blocks, same dimension, spherical)
    public static final ModConfigSpec.IntValue RANGE_UPLINK;
    public static final ModConfigSpec.IntValue RANGE_ADVANCED_UPLINK;
    public static final ModConfigSpec.IntValue RANGE_BRIDGE;
    public static final ModConfigSpec.IntValue RANGE_MAINFRAME;
    public static final ModConfigSpec.IntValue RANGE_RELAY_NODES;
    public static final ModConfigSpec.IntValue RANGE_RELAY_DEVICES;
    public static final ModConfigSpec.IntValue RANGE_ADVANCED_RELAY_NODES;
    public static final ModConfigSpec.IntValue RANGE_ADVANCED_RELAY_DEVICES;
    public static final ModConfigSpec.IntValue RANGE_ENDER_RELAY_NODES;
    public static final ModConfigSpec.IntValue RANGE_ENDER_RELAY_DEVICES;
    public static final ModConfigSpec.BooleanValue LEGACY_RANGE_GRACE;
    public static final ModConfigSpec.DoubleValue FIBER_CAPACITY_WEIGHT;
    public static final ModConfigSpec.DoubleValue FIBER_POWER_FACTOR;
    // v0.7 mainframe upgrades (base limits without cards) and the optional Chunk Anchor card
    public static final ModConfigSpec.IntValue BASE_DEVICES;
    public static final ModConfigSpec.IntValue BASE_BROWSERS;
    public static final ModConfigSpec.IntValue BASE_RULES;
    public static final ModConfigSpec.IntValue BASE_HISTORY_HOURS;
    public static final ModConfigSpec.BooleanValue CHUNK_ANCHOR;
    public static final ModConfigSpec.IntValue ANCHOR_BASE_FE;
    public static final ModConfigSpec.IntValue ANCHOR_PER_CHUNK_FE;
    public static final ModConfigSpec.IntValue MAINFRAME_PER_ADVANCED_UPLINK_FE;
    public static final ModConfigSpec.IntValue MAINFRAME_PER_BRIDGE_FE;
    public static final ModConfigSpec.IntValue MAINFRAME_PER_HOP_FE;
    public static final ModConfigSpec.IntValue RELAY_FE;
    public static final ModConfigSpec.IntValue ADVANCED_RELAY_FE;
    public static final ModConfigSpec.IntValue ENDER_RELAY_FE;
    public static final ModConfigSpec.IntValue RELAY_BUFFER_FE;
    public static final ModConfigSpec.IntValue ADVANCED_RELAY_BUFFER_FE;
    public static final ModConfigSpec.IntValue ENDER_RELAY_BUFFER_FE;
    public static final ModConfigSpec.IntValue MAX_WIDGETS_PER_UPLINK;
    public static final ModConfigSpec.IntValue MAX_WIDGETS_WITH_PAGES;
    public static final ModConfigSpec.IntValue MAX_CRAFT_AMOUNT;
    public static final ModConfigSpec.IntValue AUTO_STOCK_BATCH;
    public static final ModConfigSpec.BooleanValue VOXEL_VIEW;
    public static final ModConfigSpec.IntValue VOXEL_RADIUS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DISABLED_TEAM_PROVIDERS;
    public static final ModConfigSpec.BooleanValue RULES_ENABLED;
    public static final ModConfigSpec.BooleanValue PUSH_ENABLED;
    public static final ModConfigSpec.BooleanValue TERMINAL_ENABLED;
    public static final ModConfigSpec.BooleanValue SCRIPT_STORE;
    public static final ModConfigSpec.ConfigValue<String> SCRIPT_STORE_URL;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> PUSH_ALLOWED_HOSTS;
    public static final ModConfigSpec.IntValue MAX_RULES;
    public static final ModConfigSpec.BooleanValue AUTO_COMPUTER_CARD;
    public static final ModConfigSpec.ConfigValue<String> VANILLA_ASSETS_JAR;
    public static final ModConfigSpec.BooleanValue REQUIRE_POWER;
    public static final ModConfigSpec.IntValue MAINFRAME_BASE_FE;
    public static final ModConfigSpec.IntValue MAINFRAME_PER_UPLINK_FE;
    public static final ModConfigSpec.IntValue MAINFRAME_PER_CLIENT_FE;
    public static final ModConfigSpec.IntValue MAINFRAME_BUFFER_FE;
    public static final ModConfigSpec.IntValue MAINFRAME_MAX_INPUT_FE;

    static {
        var b = new ModConfigSpec.Builder();
        ALLOW_CLOUD = b.comment("Master switch. When false, every mainframe on this server stays offline.")
            .translation("cccloud.configuration.allowCloud")
            .define("allowCloud", true);

        b.push("web");
        WEB_ENABLED = b.comment("Run the embedded dashboard web server (HTTP + WebSocket).")
            .translation("cccloud.configuration.webEnabled")
            .define("webEnabled", true);
        WEB_BIND = b.comment("Address the web server binds to. Use 127.0.0.1 to only allow local access.")
            .translation("cccloud.configuration.webBind")
            .define("webBind", "0.0.0.0");
        WEB_PORT = b.comment("TCP port of the web server. On a hosted server this port must be opened in the firewall.")
            .translation("cccloud.configuration.webPort")
            .defineInRange("webPort", 8787, 1, 65535);
        PUBLIC_URL = b.comment("URL shown to players, e.g. https://dash.example.com. Empty = http://<server-ip>:<port>.")
            .translation("cccloud.configuration.publicUrl")
            .define("publicUrl", "");
        WEB_ALLOW_INTERNET = b.comment("false (default): only loopback and private/LAN addresses may use the dashboard. "
                + "true: anyone who can reach the port may open it (pairing still needs a code and in-game approval). Use TLS when enabling this.")
            .translation("cccloud.configuration.webAllowInternet")
            .define("webAllowInternet", false);
        WEB_TRUSTED_PROXIES = b.comment("IPs/CIDRs of reverse proxies (e.g. 127.0.0.1 for Caddy/cloudflared on the same host). "
                + "X-Forwarded-For is only honoured when the direct peer is in this list (the right-most address that is not a proxy counts).")
            .translation("cccloud.configuration.webTrustedProxies")
            .defineListAllowEmpty("webTrustedProxies", java.util.List.of(), () -> "127.0.0.1", o -> o instanceof String);
        WEB_TRUST_CLOUDFLARE_HEADER = b.comment("Use CF-Connecting-IP from a trusted proxy as the client IP. Only enable this when the trusted proxy is",
                "cloudflared (Cloudflare Tunnel), which sets the header itself; other proxies pass a client-supplied header through.")
            .translation("cccloud.configuration.webTrustCloudflareHeader")
            .define("webTrustCloudflareHeader", false);
        TLS_CERT_PATH = b.comment("Optional TLS: path to the certificate chain (PEM). Internet-facing servers should use TLS or a reverse proxy/tunnel.")
            .translation("cccloud.configuration.tlsCertPath")
            .define("tlsCertPath", "");
        TLS_KEY_PATH = b.comment("Optional TLS: path to the private key (PKCS#8 PEM). Both paths must be set to enable HTTPS.")
            .translation("cccloud.configuration.tlsKeyPath")
            .define("tlsKeyPath", "");
        b.pop();

        b.push("uplinks");
        MAX_WIDGETS_PER_UPLINK = b.comment("Maximum number of widgets one uplink may register (the dashboard supports at most 32).")
            .translation("cccloud.configuration.maxWidgetsPerUplink")
            .defineInRange("maxWidgetsPerUplink", 32, 1, 32);
        MAX_WIDGETS_WITH_PAGES = b.comment("Maximum number of widgets per uplink once the script declares pages with cloud.page().")
            .translation("cccloud.configuration.maxWidgetsWithPages")
            .defineInRange("maxWidgetsWithPages", 64, 1, 64);
        AUTO_COMPUTER_CARD = b.comment("An uplink on a computer without Lua widgets shows an automatic Computer card (power + redstone).")
            .translation("cccloud.configuration.autoComputerCard")
            .define("autoComputerCard", true);
        b.pop();

        b.push("network");
        RANGE_UPLINK = range(b, "uplinkRange", 48, "Cloud Uplink: reach to the nearest node (mainframe or relay).");
        RANGE_ADVANCED_UPLINK = range(b, "advancedUplinkRange", 128, "Advanced Cloud Uplink: reach to the nearest node.");
        RANGE_BRIDGE = range(b, "bridgeRange", 48, "ME/RS Cloud Bridge: reach to the nearest node.");
        RANGE_MAINFRAME = range(b, "mainframeRange", 96, "Mainframe: reach to relays and devices.");
        RANGE_RELAY_NODES = range(b, "relayNodeRange", 96, "Cloud Relay: reach to other nodes.");
        RANGE_RELAY_DEVICES = range(b, "relayDeviceRange", 48, "Cloud Relay: reach to devices.");
        RANGE_ADVANCED_RELAY_NODES = range(b, "advancedRelayNodeRange", 256, "Advanced Cloud Relay: reach to other nodes.");
        RANGE_ADVANCED_RELAY_DEVICES = range(b, "advancedRelayDeviceRange", 128, "Advanced Cloud Relay: reach to devices.");
        RANGE_ENDER_RELAY_NODES = range(b, "enderRelayNodeRange", 96,
            "Ender Relay: radio reach to other nodes (linked Ender Relays connect across any distance and dimension).");
        RANGE_ENDER_RELAY_DEVICES = range(b, "enderRelayDeviceRange", 128, "Ender Relay: reach to devices.");
        LEGACY_RANGE_GRACE = b.comment("Devices linked before v0.4 that are out of range under the new rules stay connected (flagged legacy_out_of_range) "
                + "until they are re-linked or a relay is placed.")
            .translation("cccloud.configuration.legacyRangeGrace")
            .define("legacyRangeGrace", true);
        FIBER_CAPACITY_WEIGHT = b.comment("How much a device attached by Fiber Cable counts against the mainframe's device capacity (1 = like any device).")
            .translation("cccloud.configuration.fiberCapacityWeight")
            .defineInRange("fiberCapacityWeight", 0.5, 0.0, 1.0);
        b.pop();

        b.push("power");
        REQUIRE_POWER = b.comment("The mainframe needs FE (Forge Energy / RF). false = it runs for free.")
            .translation("cccloud.configuration.requirePower")
            .define("requirePower", true);
        MAINFRAME_BASE_FE = b.comment("FE per tick the mainframe always uses.")
            .translation("cccloud.configuration.mainframeBaseFE")
            .defineInRange("mainframeBaseFE", 40, 0, 1_000_000);
        MAINFRAME_PER_UPLINK_FE = b.comment("Additional FE per tick per connected Cloud Uplink.")
            .translation("cccloud.configuration.mainframePerUplinkFE")
            .defineInRange("mainframePerUplinkFE", 4, 0, 1_000_000);
        MAINFRAME_PER_ADVANCED_UPLINK_FE = b.comment("Additional FE per tick per connected Advanced Cloud Uplink.")
            .translation("cccloud.configuration.mainframePerAdvancedUplinkFE")
            .defineInRange("mainframePerAdvancedUplinkFE", 8, 0, 1_000_000);
        MAINFRAME_PER_BRIDGE_FE = b.comment("Additional FE per tick per connected ME/RS Cloud Bridge.")
            .translation("cccloud.configuration.mainframePerBridgeFE")
            .defineInRange("mainframePerBridgeFE", 10, 0, 1_000_000);
        MAINFRAME_PER_HOP_FE = b.comment("Additional FE per tick for every hop beyond the first of each routed device.")
            .translation("cccloud.configuration.mainframePerHopFE")
            .defineInRange("mainframePerHopFE", 1, 0, 1_000_000);
        MAINFRAME_PER_CLIENT_FE = b.comment("Additional FE per tick per connected dashboard client.")
            .translation("cccloud.configuration.mainframePerClientFE")
            .defineInRange("mainframePerClientFE", 2, 0, 1_000_000);
        MAINFRAME_BUFFER_FE = b.comment("Size of the mainframe's internal energy buffer (FE).")
            .translation("cccloud.configuration.mainframeBufferFE")
            .defineInRange("mainframeBufferFE", 200_000, 1, Integer.MAX_VALUE);
        MAINFRAME_MAX_INPUT_FE = b.comment("Maximum FE the mainframe accepts per tick.")
            .translation("cccloud.configuration.mainframeMaxInputFE")
            .defineInRange("mainframeMaxInputFE", 2_000, 1, Integer.MAX_VALUE);
        FIBER_POWER_FACTOR = b.comment("Share of the per-device FE (uplink/advanced uplink/bridge) a device attached by Fiber Cable pays (0.5 = 50 % less).")
            .translation("cccloud.configuration.fiberPowerFactor")
            .defineInRange("fiberPowerFactor", 0.5, 0.0, 1.0);
        RELAY_FE = fe(b, "relayFE", 20, "FE per tick of a Cloud Relay (0 = free).");
        ADVANCED_RELAY_FE = fe(b, "advancedRelayFE", 60, "FE per tick of an Advanced Cloud Relay (0 = free).");
        ENDER_RELAY_FE = fe(b, "enderRelayFE", 120, "FE per tick of an Ender Relay (0 = free).");
        RELAY_BUFFER_FE = fe(b, "relayBufferFE", 50_000, "Energy buffer of a Cloud Relay (FE).");
        ADVANCED_RELAY_BUFFER_FE = fe(b, "advancedRelayBufferFE", 100_000, "Energy buffer of an Advanced Cloud Relay (FE).");
        ENDER_RELAY_BUFFER_FE = fe(b, "enderRelayBufferFE", 200_000, "Energy buffer of an Ender Relay (FE).");
        b.pop();

        b.push("icons");
        VANILLA_ASSETS_JAR = b.comment("Optional path to a Minecraft client jar you own. Lets a dedicated server show vanilla item textures as icon fallback "
                + "before a game client rendered the real icons. Nothing from Mojang is bundled with this mod.")
            .translation("cccloud.configuration.vanillaAssetsJar")
            .define("vanillaAssetsJar", "");
        b.pop();

        b.push("bridges");
        MAX_CRAFT_AMOUNT = b.comment("Largest amount one crafting request from the dashboard or Lua may ask for (ME/RS Cloud Bridge).")
            .translation("cccloud.configuration.maxCraftAmount")
            .defineInRange("maxCraftAmount", 4096, 1, 1_000_000);
        AUTO_STOCK_BATCH = b.comment("Auto-stock (level maintainer): the largest amount one automatic craft request may ask for.")
            .translation("cccloud.configuration.autoStockBatch")
            .defineInRange("autoStockBatch", 64, 1, 4096);
        b.pop();

        b.push("map");
        VOXEL_VIEW = b.comment("The dashboard's 3D voxel view of the blocks around the network (exposed blocks only, no X-ray).")
            .translation("cccloud.configuration.voxelView")
            .define("voxelView", true);
        VOXEL_RADIUS = b.comment("Voxel view: sections within this many blocks of a network node may be requested.")
            .translation("cccloud.configuration.voxelRadius")
            .defineInRange("voxelRadius", 64, 16, 128);
        b.pop();

        b.push("rules");
        RULES_ENABLED = b.comment("Run the no-code automation rules of mainframes (dashboard: When → If → Then).")
            .translation("cccloud.configuration.rulesEnabled")
            .define("rulesEnabled", true);
        MAX_RULES = b.comment("Hard cap of rules per mainframe (the Memory upgrade raises the per-mainframe limit up to this cap).")
            .translation("cccloud.configuration.maxRules")
            .defineInRange("maxRulesPerMainframe", 128, 1, 128);
        b.pop();

        b.push("store");
        SCRIPT_STORE = b.comment("Script Store in the dashboard: admins can browse and install community scripts from the store URL (the browser",
                "fetches them; the server never connects to the store). false hides the store and keeps the URL out of the dashboard's CSP.")
            .translation("cccloud.configuration.scriptStore")
            .define("scriptStore", true);
        SCRIPT_STORE_URL = b.comment("Base URL of the Script Store index (https, ending with /). Placeholder until the official store is online.")
            .translation("cccloud.configuration.scriptStoreUrl")
            .define("scriptStoreUrl", "https://cccloud.example/store/", o -> o instanceof String s && ai.ezfox.cccloud.web.PublicConfig.validStoreUrl(s) != null);
        b.pop();

        b.push("terminal");
        TERMINAL_ENABLED = b.comment("Browser terminal: players with the 'terminal' permission (admins by default) see and type into the terminal of a",
                "computer behind an uplink. Needs the mainframe PIN when one is set.")
            .translation("cccloud.configuration.terminalEnabled")
            .define("terminalEnabled", true);
        b.pop();

        b.push("push");
        PUSH_ENABLED = b.comment("Send push notifications (Discord webhooks, ntfy, JSON webhooks) configured per mainframe in the dashboard.")
            .translation("cccloud.configuration.pushEnabled")
            .define("pushEnabled", true);
        PUSH_ALLOWED_HOSTS = b.comment("Hosts push targets may use (\"example.org\" or \"*.example.org\"). Empty = any public host.",
                "Private/LAN addresses are always refused unless the host is listed here; plain http only for listed ntfy hosts.")
            .translation("cccloud.configuration.pushAllowedHosts")
            .defineListAllowEmpty("pushAllowedHosts", java.util.List.of(), () -> "ntfy.example.org", o -> o instanceof String s && !s.isBlank() && s.length() <= 253);
        b.pop();

        b.push("upgrades");
        BASE_DEVICES = b.comment("Devices (uplinks + bridges + relays) a mainframe serves without a Capacity upgrade.")
            .translation("cccloud.configuration.baseDevices")
            .defineInRange("baseDevices", 16, 1, 100_000);
        BASE_BROWSERS = b.comment("Concurrent dashboard browsers per mainframe without a Bandwidth upgrade.")
            .translation("cccloud.configuration.baseBrowsers")
            .defineInRange("baseBrowsers", 4, 1, 10_000);
        BASE_RULES = b.comment("Rules per mainframe without a Memory upgrade.")
            .translation("cccloud.configuration.baseRules")
            .defineInRange("baseRules", 8, 1, 128);
        BASE_HISTORY_HOURS = b.comment("History retention (hours) without a Memory upgrade.")
            .translation("cccloud.configuration.baseHistoryHours")
            .defineInRange("baseHistoryHours", 1, 1, 24 * 365);
        CHUNK_ANCHOR = b.comment("Allow the Chunk Anchor upgrade card: it keeps the mainframe's and its linked devices' chunks loaded. Off by default.")
            .translation("cccloud.configuration.chunkAnchor")
            .define("chunkAnchor", false);
        ANCHOR_BASE_FE = b.comment("FE per tick of an active Chunk Anchor.")
            .translation("cccloud.configuration.anchorBaseFE")
            .defineInRange("anchorBaseFE", 200, 0, 1_000_000);
        ANCHOR_PER_CHUNK_FE = b.comment("Additional FE per tick per anchored chunk.")
            .translation("cccloud.configuration.anchorPerChunkFE")
            .defineInRange("anchorPerChunkFE", 20, 0, 1_000_000);
        b.pop();

        b.push("teams");
        DISABLED_TEAM_PROVIDERS = b.comment("Team providers that must not be used for mainframe roles (ftbteams, opac, scoreboard). "
                + "Providers are detected automatically when their mod is installed.")
            .translation("cccloud.configuration.disabledTeamProviders")
            .defineListAllowEmpty("disabledTeamProviders", java.util.List.of(), () -> "scoreboard", o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }

    private CloudConfig() {
    }

    private static ModConfigSpec.IntValue range(ModConfigSpec.Builder b, String key, int def, String comment) {
        return b.comment(comment).translation("cccloud.configuration." + key).defineInRange(key, def, 1, 100_000);
    }

    private static ModConfigSpec.IntValue fe(ModConfigSpec.Builder b, String key, int def, String comment) {
        return b.comment(comment).translation("cccloud.configuration." + key).defineInRange(key, def, 0, Integer.MAX_VALUE);
    }

    /** Reads a config value, falling back to the default while the server config is not loaded (e.g. on a client). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
