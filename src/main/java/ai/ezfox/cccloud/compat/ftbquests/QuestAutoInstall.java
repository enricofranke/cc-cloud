package ai.ezfox.cccloud.compat.ftbquests;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.Edition;
import ai.ezfox.cccloud.compat.ftbquests.install.QuestInstaller;
import ai.ezfox.cccloud.compat.ftbquests.install.QuestPack;
import java.time.Clock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Adds CC: Cloud's quest chapters to FTB Quests' quest book on server start (dedicated, singleplayer and LAN alike:
 * the book is {@code <config>/ftbquests/quests} of the server or the game instance). Only registered when FTB Quests
 * is loaded.
 * <p>
 * Ordering, checked against FTB Quests 26.1.2.9 (ATM11's jar): FTB Quests creates its {@code ServerQuestFile} on
 * {@link ServerAboutToStartEvent} and reads the quest folder on {@code ServerStartedEvent}
 * ({@code FTBQuestsEventHandler.serverStarted} → {@code ServerQuestFile.load}); NeoForge loads the server configs
 * ({@code cccloud-server.toml}) right before posting {@link ServerAboutToStartEvent}. So we install on
 * {@link ServerAboutToStartEvent}: the config is readable and FTB Quests has not read the book yet. Clients get the
 * chapters with FTB Quests' own sync when they join ({@code SyncQuestsMessage} plus the translations).
 */
public final class QuestAutoInstall {
    private QuestAutoInstall() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ServerAboutToStartEvent.class, e -> run());
    }

    static void run() {
        String version = ModList.get().getModContainerById(CCCloud.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        var settings = new QuestInstaller.Settings(CloudConfig.get(CloudConfig.INSTALL_QUESTS), Edition.current(), version);
        var installer = new QuestInstaller(FMLPaths.CONFIGDIR.get(), Clock.systemDefaultZone());
        try {
            log(installer.run(settings, QuestPack::fromClasspath), settings);
        } catch (Exception e) {
            CCCloud.LOGGER.warn("[cccloud] FTB Quests: adding the quest chapters failed ({}). The quest book may be partly changed; "
                + "the backup is next to it (config/ftbquests/quests.backup-*). Turn this off with installQuests = false in "
                + "cccloud-server.toml.", e.toString(), e);
        }
    }

    static void log(QuestInstaller.Result result, QuestInstaller.Settings settings) {
        String hint = settings.edition() == Edition.MODPACK || !settings.installQuests() ? ""
            : " Turn this off with installQuests = false in cccloud-server.toml.";
        String line = "[cccloud] FTB Quests: " + result.message() + hint;
        if (result.warning()) CCCloud.LOGGER.warn(line);
        else CCCloud.LOGGER.info(line);
    }
}
