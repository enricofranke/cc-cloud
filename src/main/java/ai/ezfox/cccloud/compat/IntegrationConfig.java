package ai.ezfox.cccloud.compat;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config of the v0.5 integrations ({@code serverconfig/cccloud-integrations-server.toml} in the world folder).
 * A file of its own so the integrations stay optional and separate from the core config.
 */
public final class IntegrationConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue DEVICE_CARDS;
    public static final ModConfigSpec.BooleanValue MFFS_REQUIRE_PIN;
    public static final ModConfigSpec.BooleanValue STORAGE_CARDS;
    public static final ModConfigSpec.IntValue WIRED_MACHINE_CARDS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> GENERIC_CARD_EXCLUDES;
    public static final ModConfigSpec.BooleanValue SERVER_CARD;
    public static final ModConfigSpec.BooleanValue FTB_TEAMS_TRUST;
    public static final ModConfigSpec.BooleanValue FTB_CHUNKS_CLAIMS;
    public static final ModConfigSpec.BooleanValue BACKUP_CARD;
    public static final ModConfigSpec.BooleanValue GATEWAYS_CARD;
    public static final ModConfigSpec.BooleanValue WAYSTONES_CARD;
    public static final ModConfigSpec.BooleanValue QUESTS_CARD;

    static {
        var b = new ModConfigSpec.Builder();
        b.push("cards");
        DEVICE_CARDS = b.comment("An uplink attached directly to a supported machine (any FE block, Powah, Ender IO, Energy Meter, MFFS, ...) shows a ready-made card.")
            .translation("cccloud.configuration.deviceCards")
            .define("deviceCards", true);
        MFFS_REQUIRE_PIN = b.comment("Switching an MFFS force field projector from the dashboard needs the mainframe PIN (in addition to canControl).")
            .translation("cccloud.configuration.mffsRequirePin")
            .define("mffsRequirePin", true);
        STORAGE_CARDS = b.comment("Storage controllers (Functional Storage, Sophisticated Storage, Occultism, DimStorage) show a Storage card with item search. Read-only.")
            .translation("cccloud.configuration.storageCards")
            .define("storageCards", true);
        GENERIC_CARD_EXCLUDES = b.comment("Blocks that never get the generic Energy or Tank card (false positives): block ids (\"mod:block\") or whole mods (\"mod:*\").",
                "The block tag #cccloud:no_generic_card does the same for data packs.")
            .translation("cccloud.configuration.genericCardExcludes")
            .defineListAllowEmpty("genericCardExcludes", java.util.List.of(), () -> "somemod:machine",
                o -> o instanceof String s && s.matches("[a-z0-9_.-]+:([a-z0-9_./-]+|\\*)"));
        WIRED_MACHINE_CARDS = b.comment("An uplink on a CC computer also shows the cards of machines the computer reaches through its wired modems",
                "(CC Networking Cable): at most this many per uplink (0 = none). One card per machine and mainframe; machines with",
                "an uplink or probe of their own keep that one.")
            .translation("cccloud.configuration.wiredMachineCards")
            .defineInRange("wiredMachineCards", 8, 0, 32);
        b.pop();

        b.push("server");
        SERVER_CARD = b.comment("Every mainframe shows a Server card (TPS/MSPT, players, uptime, memory) to devices with canControl.")
            .translation("cccloud.configuration.serverCard")
            .define("serverCard", true);
        FTB_TEAMS_TRUST = b.comment("FTB Teams: members of the mainframe owner's team count as trusted players (live, follows team changes).")
            .translation("cccloud.configuration.ftbTeamsTrust")
            .define("ftbTeamsTrust", true);
        FTB_CHUNKS_CLAIMS = b.comment("FTB Chunks: a Claims card with the chunks claimed by the owner's team (overlay data for the network map).")
            .translation("cccloud.configuration.ftbChunksClaims")
            .define("ftbChunksClaims", true);
        BACKUP_CARD = b.comment("Simple Backups: a Backups card with the last backup time and a 'Backup now' button (canControl + confirm).")
            .translation("cccloud.configuration.backupCard")
            .define("backupCard", true);
        b.pop();

        b.push("world");
        GATEWAYS_CARD = b.comment("Gateways to Eternity: a Gateways card with the gates open near the mainframe (needs view.positions).")
            .translation("cccloud.configuration.gatewaysCard")
            .define("gatewaysCard", true);
        WAYSTONES_CARD = b.comment("Waystones: a Waystones card and network-map overlay with the waystones of the owner's team and global ones (needs view.positions).")
            .translation("cccloud.configuration.waystonesCard")
            .define("waystonesCard", true);
        QUESTS_CARD = b.comment("FTB Quests: a Quests card with the quest progress of the owner's team (needs view.positions).")
            .translation("cccloud.configuration.questsCard")
            .define("questsCard", true);
        b.pop();
        SPEC = b.build();
    }

    private IntegrationConfig() {
    }

    /** Reads a value, falling back to the default while the server config is not loaded. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
