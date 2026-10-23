package ai.ezfox.cccloud.compat.simplebackups;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonElement;
import de.melanx.simplebackups.BackupChainManager;
import de.melanx.simplebackups.BackupData;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Simple Backups: a "Backups" mainframe card (canControl) with the time of the last backup, the size of all backups,
 * whether one is running or backups are paused, and a "Backup now" button (with confirmation). Simple Backups has no
 * API or events; the button runs its own command {@code /simplebackups backup start} as the server, the last backup
 * comes from its backup chain metadata (start time of the newest backup), "running" from its backup thread.
 */
public final class BackupsCard extends MainframeCard {
    public static final String KIND = "backups";
    public static final String LAST = "last";
    public static final String SIZE = "size";
    public static final String RUNNING = "running";
    public static final String PAUSED = "paused";
    public static final String BACKUP_NOW = "backup_now";
    static final String COMMAND = "simplebackups backup start";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MinecraftServer server;
    private boolean wasRunning;

    private BackupsCard(MinecraftServer server) {
        this.server = server;
    }

    public static void init() {
        MainframeCards.register(KIND, () -> IntegrationConfig.get(IntegrationConfig.BACKUP_CARD), mainframe -> new BackupsCard(mainframe.getLevel().getServer()));
        ai.ezfox.cccloud.compat.Integrations.test("simplebackups_card", () -> SimpleBackupsGameTests::card);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Backups";
    }

    @Override
    public List<Widget> widgets() {
        return new CardWidgets()
            .label(LAST, "Last backup", "span", 2, "icon", "shield")
            .label(SIZE, "Backups on disk")
            .indicator(RUNNING, "Backup running", "onColor", "yellow", "offColor", "gray")
            .indicator(PAUSED, "Automatic backups paused", "onColor", "yellow", "offColor", "gray")
            .button(BACKUP_NOW, "Backup now", "confirm", true, "color", "yellow")
            .build();
    }

    String levelId() {
        var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return root == null ? "world" : root.toString();
    }

    /** True while Simple Backups' backup thread is alive (it names it "SimpleBackups"). */
    static boolean running() {
        for (var thread : Thread.getAllStackTraces().keySet()) {
            if ("SimpleBackups".equals(thread.getName()) && thread.isAlive()) return true;
        }
        return false;
    }

    @Override
    public void refresh(CardOutput out) {
        var manager = BackupChainManager.get(levelId());
        var latest = manager.getLatestChain();
        if (latest == null) {
            out.text(LAST, "No backup yet");
        } else {
            long millis = latest.getLastUpdated();
            long ago = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
            out.text(LAST, TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())) + " (" + Fmt.duration(ago) + " ago)");
        }
        out.text(SIZE, Fmt.si(manager.getFileSize()) + "B");
        boolean running = running();
        out.bool(RUNNING, running);
        out.bool(PAUSED, BackupData.get(server).isPaused());
        if (wasRunning && !running) out.alert("Backup finished", "info");
        wasRunning = running;
    }

    @Override
    public @Nullable String action(String widgetId, JsonElement value, String user) {
        if (!BACKUP_NOW.equals(widgetId)) return "not_interactive";
        if (running()) return "busy";
        CCCloud.LOGGER.info("[cccloud] {} started a backup (Simple Backups)", user);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), COMMAND);
        return null;
    }
}
