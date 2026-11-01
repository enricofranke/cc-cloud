package ai.ezfox.cccloud.reports;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import ai.ezfox.cccloud.access.CloudPermissions;
import ai.ezfox.cccloud.block.entity.CloudUplinkBlockEntity;
import ai.ezfox.cccloud.command.InternetCommand;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.network.CloudNetwork;
import ai.ezfox.cccloud.reports.ReportConsent.Channel;
import ai.ezfox.cccloud.reports.ReportConsent.Setting;
import ai.ezfox.cccloud.reports.ReportConsent.State;
import ai.ezfox.cccloud.reports.ReportPayload.Env;
import ai.ezfox.cccloud.reports.ReportPayload.Where;
import ai.ezfox.cccloud.web.AccessApi;
import ai.ezfox.cccloud.web.AuthStore;
import ai.ezfox.cccloud.web.CloudWebServer;
import ai.ezfox.cccloud.web.Router;
import ai.ezfox.cccloud.web.WebAuthData;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Reports in the game (docs/PRIVACY.md): keeps the store and the {@link ReportEngine} of the running server, reads the
 * consent from the config, schedules the daily stats and looks for crash files at start. The rules themselves are in the
 * pure classes of this package.
 */
public final class ReportService {
    /** How often the stats are looked at, and the first look after a start (5 minutes in ticks). */
    private static final int STATS_TICKS = 6000;
    /** How often the scrubber learns the names of the server while a channel is on (1 minute). */
    private static final int NAMES_TICKS = 1200;
    /** Reports waiting for the worker; more are dropped. */
    private static final int QUEUE = 8;

    private static volatile @Nullable MinecraftServer server;
    private static volatile @Nullable ReportStore store;
    private static volatile @Nullable ReportEngine engine;
    private static volatile @Nullable ThreadPoolExecutor pool;
    private static volatile @Nullable ReportHttp http;
    private static volatile ReportScrubber scrubber = ReportScrubber.EMPTY;
    private static volatile ReportEngine.@Nullable Transport testTransport;
    private static final FeedbackLimits FEEDBACK = new FeedbackLimits();
    private static int ticks;

    private ReportService() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ServerStartedEvent.class, e -> start(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> stop());
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> learnNames());
    }

    private static void start(MinecraftServer srv) {
        server = srv;
        store = new ReportStore(srv.getServerDirectory().resolve("cccloud").resolve("reports").resolve("state.json"));
        var newEngine = newEngine();
        engine = newEngine;
        ReportCapture.engine(newEngine);
        learnNames();
        newEngine.scanCrashes(srv.getServerDirectory().resolve("crash-reports"));
    }

    private static ReportEngine newEngine() {
        // one thread at most, and only while there is work: nothing runs for a server that said no
        var worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE), r -> {
            var t = new Thread(r, "cccloud-reports");
            t.setDaemon(true);
            return t;
        });
        worker.allowCoreThreadTimeOut(true);
        pool = worker;
        var transport = testTransport;
        if (transport == null) http = new ReportHttp();
        return new ReportEngine(ReportService::state, store, () -> scrubber, ReportService::env,
            (channel, body) -> send(channel, body), worker, System::currentTimeMillis);
    }

    private static boolean send(String channel, byte[] body) {
        var transport = testTransport;
        if (transport != null) return transport.post(channel, body);
        var client = http;
        return client != null && client.post(channel, body);
    }

    private static void stop() {
        ReportCapture.engine(null);
        engine = null;
        var worker = pool;
        if (worker != null) worker.shutdownNow();
        pool = null;
        var client = http;
        if (client != null) client.shutdown();
        http = null;
        store = null;
        server = null;
        scrubber = ReportScrubber.EMPTY;
        ticks = 0;
    }

    private static void tick() {
        ticks++;
        if (ticks % NAMES_TICKS == 0) learnNames();
        if (ticks % STATS_TICKS != 0) return;
        var srv = server;
        var current = engine;
        if (srv == null || current == null || !current.statsDue()) return;
        var snapshot = statsPayload(srv);
        if (snapshot != null) current.stats(snapshot);
    }

    // ---- consent --------------------------------------------------------------------------------------------------

    private static ModConfigSpec.ConfigValue<String> setting(Channel channel) {
        return channel == Channel.CRASH ? CloudConfig.CRASH_REPORTS : CloudConfig.USAGE_STATS;
    }

    /** The channel's state right now; without a running server nobody decided anything. */
    public static State state(Channel channel) {
        var current = store;
        if (current == null) return State.ASK;
        return ReportConsent.state(Setting.parse(CloudConfig.get(setting(channel))), CloudConfig.get(CloudConfig.CONSENT_VERSION), current.decision(channel), ReportConsent.VERSION);
    }

    public static boolean promptDue() {
        var current = store;
        return current != null && ReportConsent.prompt(state(Channel.CRASH), state(Channel.STATS), current.dismissedVersion(), ReportConsent.VERSION);
    }

    /** The decision belongs to the server owner: op level 4, or the player whose game hosts a singleplayer world. */
    public static boolean owner(MinecraftServer srv, UUID player) {
        var profile = new NameAndId(player, CloudPermissions.nameOf(srv, player));
        return srv.isSingleplayerOwner(profile) || srv.getProfilePermissions(profile).hasPermission(Permissions.COMMANDS_OWNER);
    }

    /** Records the owner's answer and stops or starts the channel at once. False if it could not be saved. */
    public static boolean decide(Channel channel, boolean yes, String who) {
        var current = store;
        if (current == null) return false;
        try {
            current.decide(channel, yes, ReportConsent.VERSION, System.currentTimeMillis());
            setting(channel).set(yes ? Setting.ON.id() : Setting.OFF.id());
            CloudConfig.CONSENT_VERSION.set(ReportConsent.VERSION);
            CloudConfig.SPEC.save();
        } catch (RuntimeException e) {
            // the store alone already keeps a no; a yes without the config stays undecided
            CCCloud.LOGGER.error("[cccloud] Could not save the reports setting to the server config", e);
            return false;
        }
        CCCloud.LOGGER.info("[cccloud] {} set {} = {}", who, channel == Channel.CRASH ? "crashReports" : "usageStats", yes ? "on" : "off");
        learnNames();
        return true;
    }

    /** The prompt was seen and put aside: it does not come back for this version, and nothing is sent. */
    public static void dismissPrompt() {
        var current = store;
        if (current != null) current.dismiss(ReportConsent.VERSION);
    }

    // ---- what is known about this server ------------------------------------------------------------------------

    private static @Nullable String versionOf(String mod) {
        return ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString()).orElse(null);
    }

    private static Env env() {
        String mod = versionOf(CCCloud.MODID), neoforge = versionOf("neoforge");
        return new Env(mod == null ? "unknown" : mod, neoforge == null ? "unknown" : neoforge, SharedConstants.getCurrentVersion().name(), Runtime.version().feature(),
            ReportPayload.osFamily(System.getProperty("os.name")));
    }

    /** Refreshes the names the scrubber knows (server thread). Only while reports can use them. */
    private static void learnNames() {
        var srv = server;
        if (srv != null && (state(Channel.CRASH) == State.ACTIVE)) scrubber = scrubberFor(srv);
    }

    /** Everything on this server that is a name: players, the world, addresses, mainframes, the system account. */
    static ReportScrubber scrubberFor(MinecraftServer srv) {
        var players = new ArrayList<String>();
        for (var player : srv.getPlayerList().getPlayers()) players.add(player.getGameProfile().name());
        players.addAll(List.of(srv.getPlayerList().getWhiteListNames()));
        players.addAll(List.of(srv.getPlayerList().getOpNames()));
        var access = WebAuthData.access(srv);
        var literals = new ArrayList<String>();
        for (var id : access.mainframes()) {
            var entry = access.access(id);
            if (entry != null) players.add(entry.ownerName());
        }
        for (var mainframe : CloudNetwork.mainframes()) literals.add(mainframe.getDisplayName());
        literals.add(srv.getWorldData().getLevelName());
        var folder = srv.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        if (folder != null) literals.add(folder.toString());
        literals.add(srv.getMotd());
        literals.add(srv.getLocalIp());
        literals.add(CloudConfig.get(CloudConfig.PUBLIC_URL));
        literals.add(CloudConfig.get(CloudConfig.INTERNET_DOMAIN));
        literals.add(CloudConfig.get(CloudConfig.CONNECT_NAME));
        players.add(System.getProperty("user.name"));
        return new ReportScrubber(players, literals);
    }

    /** The stats snapshot as it would go out (server thread). */
    public static @Nullable JsonObject statsPayload(MinecraftServer srv) {
        return ReportPayload.failClosed(() -> {
            int uplinks = 0, cards = 0;
            for (var uplink : CloudNetwork.uplinks()) if (!uplink.isRemoved()) uplinks++;
            for (var section : CloudNetwork.sections()) {
                if (!(section instanceof CloudUplinkBlockEntity uplink) || uplink.deviceCard() != null) cards++;
            }
            var integrations = new ArrayList<>(Integrations.active());
            for (String mod : List.of("ae2", "refinedstorage")) if (ModList.get().isLoaded(mod)) integrations.add(mod);
            var counts = new ReportPayload.Counts(InternetCommand.standingMainframes(srv).size(), uplinks, cards);
            return ReportPayload.stats(env(), counts, CloudWebServer.internetMode().id(), integrations);
        });
    }

    /** An exception that never happened, through the real pipeline, for the crash preview. */
    private static ReportStack exampleStack() {
        var failure = new IllegalStateException("Example: messages are cleaned like this one, a path /home/steve/world/level.dat and 192.168.1.20 are gone");
        failure.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("ai.ezfox.cccloud.compat.card.DeviceCards", "build", "DeviceCards.java", 99),
            new StackTraceElement("ai.ezfox.cccloud.uplink.CloudUplinkBlockEntity", "tick", "CloudUplinkBlockEntity.java", 412),
            new StackTraceElement("net.minecraft.server.MinecraftServer", "tickServer", "MinecraftServer.java", 1010)});
        return ReportStack.of(failure);
    }

    /** The exact payloads: stats as they would go out now, crash the last one sent or a marked example. */
    public static JsonObject preview(MinecraftServer srv) {
        var json = new JsonObject();
        var crash = new JsonObject();
        var current = engine;
        var last = current == null ? null : current.lastCrash();
        crash.addProperty("example", last == null);
        var payload = last != null ? last : ReportPayload.crash(env(), Where.EXAMPLE, exampleStack(), scrubberFor(srv));
        if (payload != null) crash.add("payload", payload);
        json.add("crash", crash);
        var stats = new JsonObject();
        var snapshot = statsPayload(srv);
        if (snapshot != null) stats.add("payload", snapshot);
        json.add("stats", stats);
        return json;
    }

    public static @Nullable JsonObject feedbackPayload(MinecraftServer srv, String category, String text, @Nullable String contact, boolean technical) {
        return ReportPayload.feedback(category, text, contact, technical ? env() : null, scrubberFor(srv));
    }

    /** Sends one confirmed message unless the limits refuse it; {@code done} gets the result on the worker. */
    public static boolean sendFeedback(String sender, JsonObject payload, @Nullable Consumer<Boolean> done) {
        var current = engine;
        if (current == null || !FEEDBACK.allow(sender, System.currentTimeMillis())) return false;
        current.feedback(payload, done);
        return true;
    }

    /** The dashboard's side, for one request (server thread). */
    public static ReportApi.Hooks hooks(MinecraftServer srv, String who) {
        return new ReportApi.Hooks() {
            @Override
            public State state(Channel channel) {
                return ReportService.state(channel);
            }

            @Override
            public boolean promptDue() {
                return ReportService.promptDue();
            }

            @Override
            public void decide(Channel channel, boolean yes) {
                ReportService.decide(channel, yes, who);
            }

            @Override
            public void dismissPrompt() {
                ReportService.dismissPrompt();
            }

            @Override
            public JsonObject preview() {
                return ReportService.preview(srv);
            }

            @Override
            public @Nullable JsonObject feedbackPayload(String category, String text, @Nullable String contact, boolean technical) {
                return ReportService.feedbackPayload(srv, category, text, contact, technical);
            }

            @Override
            public boolean sendFeedback(String sender, JsonObject payload) {
                return ReportService.sendFeedback(sender, payload, null);
            }
        };
    }

    /** {@code /api/mainframes/<id>/reports…} and {@code /feedback} for a device that passed the route's checks. */
    public static AccessApi.Response api(MinecraftServer srv, Router.Id route, AuthStore.Device device, String body) {
        var caller = new ReportApi.Caller(owner(srv, device.player()), device.canControl(), device.player().toString());
        var hooks = hooks(srv, device.playerName() + " (" + device.name() + ")");
        return switch (route) {
            case REPORTS_GET -> ReportApi.get(caller, hooks);
            case REPORTS_PREVIEW -> ReportApi.preview(caller, hooks);
            case REPORTS_POST -> ReportApi.post(caller, body, hooks);
            case FEEDBACK_POST -> ReportApi.feedback(caller, body, hooks);
            default -> AccessApi.error(404, "not_found");
        };
    }

    // ---- the mainframe screen ---------------------------------------------------------------------------------------

    /** What the screen's prompt needs, or null when this player has nothing to answer. {@code preview}: with the payloads. */
    public static @Nullable JsonObject screenPrompt(ServerPlayer player, boolean preview) {
        var srv = player.level().getServer();
        if (srv == null || !promptDue() || !owner(srv, player.getUUID())) return null;
        var json = new JsonObject();
        for (var channel : Channel.values()) json.addProperty(channel.id, state(channel).id());
        if (preview) {
            var payloads = preview(srv);
            for (var channel : Channel.values()) {
                var entry = payloads.getAsJsonObject(channel.id);
                if (entry.has("payload")) json.addProperty(channel.id + "Payload", entry.get("payload").toString());
            }
        }
        return json;
    }

    // ---- tests --------------------------------------------------------------------------------------------------------

    /** GameTests: every send goes here instead of the network. Null restores the real client. */
    public static void transportForTest(ReportEngine.@Nullable Transport transport) {
        testTransport = transport;
    }

    /** GameTests: looks for crash files now, as a start does. */
    public static void scanForTest(Path folder) {
        var current = engine;
        if (current != null) current.scanCrashes(folder);
    }

    /** GameTests: one stats check, as the timer does. */
    public static void statsForTest() {
        ticks = STATS_TICKS - 1;
        tick();
    }

    /** GameTests: forgets the decisions of a running server. */
    public static void resetForTest() {
        var srv = server;
        if (srv == null) return;
        stop();
        start(srv);
    }
}
