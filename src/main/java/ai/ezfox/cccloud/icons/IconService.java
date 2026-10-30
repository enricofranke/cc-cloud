package ai.ezfox.cccloud.icons;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Item icons for the dashboard (docs/SPEC.md "Item icons (v0.3)"): a persistent {@link IconStore} with an icon for
 * every registered item, rendered once by game clients that have the mod (the singleplayer client too, through the
 * same packets) and re-rendered only for new or changed items after updates.
 * <p>
 * Flow: the server queues every item whose stored version differs from {@code namespace@modVersion}. Clients that
 * announced themselves ({@link ClientStatePayload}) get batches of up to {@link #BATCH} ids ({@link RequestPayload},
 * at most one outstanding batch and one request per second per client) and answer with {@link UploadPayload}s. Every
 * uploaded icon is checked: it must belong to that client's outstanding batch and pass {@link PngCodec}'s strict
 * validation (32×32 RGBA, ≤ 16 KB). An empty upload means "cannot render" and is remembered per version.
 * <p>
 * On a dedicated server, {@link ServerIconPass} draws the simple icons (flat items, plain cubes) right at start on one
 * low-priority background thread, so the dashboard has icons before anyone joins; client renders replace them later
 * (they stay queued for clients). Vanilla items need a client jar for that: {@code vanillaAssetsJar} or the opt-in
 * download ({@code downloadVanillaAssets}, {@link VanillaAssets}).
 * <p>
 * Without any stored icon, {@link #fallback} reads the item/block texture from the mod jar (or the vanilla source).
 */
public final class IconService {
    public static final int BATCH = 256;
    public static final int MAX_UPLOAD_ICONS = 64;
    /** Outstanding batches older than this go back to the queue. */
    private static final long BATCH_TIMEOUT_MS = 10 * 60 * 1000L;

    // ---- payloads ----------------------------------------------------------------------------------------------------

    /** Server → client: please render these items. */
    public record RequestPayload(List<String> ids) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "icon_request"));
        public static final StreamCodec<ByteBuf, RequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(BATCH)), RequestPayload::ids, RequestPayload::new);

        @Override
        public Type<RequestPayload> type() {
            return TYPE;
        }
    }

    public record Icon(String id, byte[] png) {
        public static final StreamCodec<ByteBuf, Icon> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), Icon::id, ByteBufCodecs.byteArray(PngCodec.MAX_BYTES), Icon::png, Icon::new);
    }

    /** Client → server: rendered icons (an empty png = cannot render). */
    public record UploadPayload(List<Icon> icons) implements CustomPacketPayload {
        public static final Type<UploadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "icon_upload"));
        public static final StreamCodec<ByteBuf, UploadPayload> CODEC = StreamCodec.composite(
            Icon.CODEC.apply(ByteBufCodecs.list(MAX_UPLOAD_ICONS)), UploadPayload::icons, UploadPayload::new);

        @Override
        public Type<UploadPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: whether this client renders icons (client config {@code shareIconRenders}). */
    public record ClientStatePayload(boolean available) implements CustomPacketPayload {
        public static final Type<ClientStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "icon_client_state"));
        public static final StreamCodec<ByteBuf, ClientStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClientStatePayload::available, ClientStatePayload::new);

        @Override
        public Type<ClientStatePayload> type() {
            return TYPE;
        }
    }

    /** Set by the client entry point. */
    public static volatile java.util.function.@Nullable Consumer<RequestPayload> clientRequestHandler;

    public record Status(int total, int done, int queued, int inFlight, int renderers, IconStore.Status store) {
    }

    /** Icons of the registered items by source; {@code missing} have none (the dashboard shows a letter tile). */
    public record Sources(int client, int server, int missing) {
    }

    /** Where the server renderer gets vanilla textures from. */
    public enum Vanilla { NOT_NEEDED, NONE, JAR, DOWNLOAD, FAILED }

    private record Assignment(Set<String> ids, long since) {
    }

    private static volatile @Nullable IconService current;

    private final MinecraftServer server;
    private final IconStore store;
    /** id → version for every registered item. */
    private final Map<String, String> wanted = new LinkedHashMap<>();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final Set<String> queued = new LinkedHashSet<>();
    private final Map<UUID, Assignment> assignments = new HashMap<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Set<UUID> renderers = new LinkedHashSet<>();
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "cccloud-icons");
        t.setDaemon(true);
        return t;
    });
    /** The server renderer: one low-priority thread, passes run one after the other. */
    private final java.util.concurrent.ExecutorService render = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "cccloud-icon-render");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private volatile @Nullable ServerIconPass pass;
    private volatile Vanilla vanilla = Vanilla.NOT_NEEDED;
    private volatile String vanillaError = "";
    private long accepted, rejected;
    /** L10: 4096 icons per player and hour; replacing a current icon only by players with a role. */
    final UploadGuard guard = new UploadGuard(4096, System::currentTimeMillis, uuid -> false);

    private IconService(MinecraftServer server, Path dir) {
        this.server = server;
        guard.setTrusted(uuid -> ai.ezfox.cccloud.access.Access.hasAnyRole(server, uuid));
        this.store = new IconStore(dir);
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            var key = BuiltInRegistries.ITEM.getKey(item);
            wanted.put(key.toString(), version(key.getNamespace()));
        }
        requeue(store.outdated(wanted));
        io.scheduleWithFixedDelay(this::flushQuietly, 60, 60, TimeUnit.SECONDS);
        CCCloud.LOGGER.info("[cccloud] Item icons: {} of {} rendered by game clients, {} queued for them ({})", wanted.size() - queue.size(),
            wanted.size(), queue.size(), dir);
        // Singleplayer and LAN: the host's game renders every icon with the real renderer and has all textures.
        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isDedicatedServer()) startServerPass(false, true);
    }

    public static @Nullable IconService current() {
        return current;
    }

    public IconStore store() {
        return store;
    }

    /** Public form of {@link #version} (block textures use the same versioning). */
    public static String versionOf(String namespace) {
        return version(namespace);
    }

    /** Players whose client renders (icons and block textures, client config {@code shareIconRenders}). */
    public static List<UUID> renderers() {
        var service = current;
        if (service == null) return List.of();
        synchronized (service) {
            return List.copyOf(service.renderers);
        }
    }

    /** Version key of a namespace: mod id + mod version (game version for minecraft), so updates re-render. */
    static String version(String namespace) {
        if (namespace.equals("minecraft")) return "minecraft@" + SharedConstants.getCurrentVersion().name();
        return ModList.get().getModContainerById(namespace).map(c -> namespace + "@" + c.getModInfo().getVersion()).orElse(namespace + "@?");
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    public static void init(IEventBus modBus) {
        modBus.addListener(IconService::register);
        NeoForge.EVENT_BUS.addListener(IconService::onStarted);
        NeoForge.EVENT_BUS.addListener(IconService::onStopping);
        NeoForge.EVENT_BUS.addListener(IconService::onTick);
        NeoForge.EVENT_BUS.addListener(IconService::onLogout);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(RequestPayload.TYPE, RequestPayload.CODEC, (payload, context) -> {
            var handler = clientRequestHandler;
            if (handler != null) handler.accept(payload);
        });
        registrar.playToServer(UploadPayload.TYPE, UploadPayload.CODEC, IconService::onUpload);
        registrar.playToServer(ClientStatePayload.TYPE, ClientStatePayload.CODEC, IconService::onClientState);
    }

    private static void onStarted(ServerStartedEvent event) {
        try {
            current = new IconService(event.getServer(), event.getServer().getWorldPath(LevelResource.ROOT).resolve("cccloud").resolve("icons"));
        } catch (RuntimeException e) {
            CCCloud.LOGGER.error("[cccloud] Item icon store could not be opened; icons fall back to textures/letters", e);
        }
    }

    private static void onStopping(ServerStoppingEvent event) {
        var service = current;
        current = null;
        if (service == null) return;
        var running = service.pass;
        if (running != null) running.cancel();
        service.render.shutdownNow();
        service.io.shutdownNow();
        service.flushQuietly();
        ModAssets.setVanilla(null);
        try {
            service.store.close();
        } catch (IOException ignored) {
        }
    }

    private void flushQuietly() {
        try {
            store.flush();
        } catch (IOException | RuntimeException e) {
            CCCloud.LOGGER.warn("[cccloud] Could not write the item icon store", e);
        }
    }

    // ---- queue -------------------------------------------------------------------------------------------------------

    private synchronized void requeue(Iterable<String> ids) {
        for (var id : ids) if (queued.add(id)) queue.addLast(id);
    }

    /** Server tick: hand out batches (at most one outstanding batch and one request per second per client). */
    private static void onTick(ServerTickEvent.Post event) {
        var service = current;
        if (service == null || event.getServer().getTickCount() % 20 != 0) return;
        service.assign();
    }

    private synchronized void assign() {
        long now = System.currentTimeMillis();
        for (var it = assignments.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue().since() > BATCH_TIMEOUT_MS) {
                it.remove();
                requeue(e.getValue().ids());
            }
        }
        if (queue.isEmpty()) return;
        for (var uuid : List.copyOf(renderers)) {
            if (queue.isEmpty()) break;
            if (assignments.containsKey(uuid) || now - lastRequest.getOrDefault(uuid, 0L) < 1000) continue;
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.connection.hasChannel(RequestPayload.TYPE)) {
                renderers.remove(uuid);
                continue;
            }
            var batch = new ArrayList<String>(BATCH);
            while (batch.size() < BATCH && !queue.isEmpty()) {
                var id = queue.pollFirst();
                queued.remove(id);
                batch.add(id);
            }
            assignments.put(uuid, new Assignment(new LinkedHashSet<>(batch), now));
            lastRequest.put(uuid, now);
            PacketDistributor.sendToPlayer(player, new RequestPayload(batch));
        }
    }

    private static void onClientState(ClientStatePayload payload, IPayloadContext context) {
        var service = current;
        if (service == null || !(context.player() instanceof ServerPlayer player)) return;
        synchronized (service) {
            if (payload.available()) {
                service.renderers.add(player.getUUID());
            } else {
                service.renderers.remove(player.getUUID());
                var assignment = service.assignments.remove(player.getUUID());
                if (assignment != null) service.requeue(assignment.ids());
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        var service = current;
        if (service == null) return;
        synchronized (service) {
            var uuid = event.getEntity().getUUID();
            service.renderers.remove(uuid);
            service.lastRequest.remove(uuid);
            var assignment = service.assignments.remove(uuid);
            if (assignment != null) service.requeue(assignment.ids());
        }
    }

    private static void onUpload(UploadPayload payload, IPayloadContext context) {
        var service = current;
        if (service == null || !(context.player() instanceof ServerPlayer player)) return;
        service.accept(player.getUUID(), payload.icons());
    }

    /**
     * Stores uploaded icons. Only ids from this client's outstanding batch are accepted; everything else is ignored.
     *
     * @return the number of accepted icons
     */
    synchronized int accept(UUID client, List<Icon> icons) {
        var assignment = assignments.get(client);
        if (assignment == null) {
            rejected += icons.size();
            return 0;
        }
        int ok = 0;
        for (var icon : icons) {
            if (!assignment.ids().remove(icon.id())) {
                rejected++;
                continue;
            }
            var version = wanted.get(icon.id());
            if (version == null) continue;
            if (!guard.take(client) || (store.isCurrent(icon.id(), version) && store.hasIcon(icon.id()) && !guard.mayReplace(client))) {
                rejected++;
                continue;
            }
            if (icon.png().length == 0) {
                store.markFailed(icon.id(), version);
            } else if (PngCodec.isValid(icon.png(), PngCodec.ICON_SIZE, PngCodec.ICON_SIZE)) {
                store.put(icon.id(), version, icon.png());
                accepted++;
                ok++;
            } else {
                rejected++;
                store.markFailed(icon.id(), version);
            }
        }
        if (assignment.ids().isEmpty()) assignments.remove(client);
        return ok;
    }

    // ---- admin -------------------------------------------------------------------------------------------------------

    public synchronized Status status() {
        int inFlight = 0;
        for (var a : assignments.values()) inFlight += a.ids().size();
        int done = wanted.size() - queue.size() - inFlight;
        return new Status(wanted.size(), Math.max(0, done), queue.size(), inFlight, renderers.size(), store.status());
    }

    /** Re-renders every item (server renderer and clients); old icons stay available until replaced. */
    public synchronized void rebuild() {
        assignments.clear();
        queue.clear();
        queued.clear();
        requeue(wanted.keySet());
        if (server != null && net.neoforged.fml.loading.FMLEnvironment.getDist().isDedicatedServer()) startServerPass(true, false);
    }

    // ---- server renderer ---------------------------------------------------------------------------------------------

    /**
     * Queues a server render pass on the low-priority render thread (a running one is cancelled first). The first
     * pass also opens the vanilla source ({@code vanillaAssetsJar}, else the opt-in download).
     */
    private void startServerPass(boolean force, boolean resolveVanilla) {
        var old = pass;
        if (old != null) old.cancel();
        if (!CloudConfig.get(CloudConfig.RENDER_ICONS_ON_SERVER)) {
            pass = null;
            if (resolveVanilla) render.execute(this::resolveVanilla); // still used by the texture fallback
            return;
        }
        Map<String, String> snapshot;
        synchronized (this) {
            snapshot = new LinkedHashMap<>(wanted);
        }
        var next = new ServerIconPass(snapshot, store, ModAssets::read, force);
        pass = next;
        render.execute(() -> {
            if (resolveVanilla) resolveVanilla();
            ModAssets.index();
            CCCloud.LOGGER.info("[cccloud] Item icons: drawing the simple ones on the server (one background thread) …");
            try {
                next.run();
            } catch (RuntimeException | LinkageError e) {
                CCCloud.LOGGER.warn("[cccloud] The server icon renderer stopped", e);
            }
            var p = next.progress();
            if (p.state() == ServerIconPass.State.DONE) {
                flushQuietly();
                CCCloud.LOGGER.info("[cccloud] Item icons drawn on the server: {} flat, {} cubes, {} left for game clients ({} items checked) in {} ms; "
                    + "vanilla textures: {}", p.flat(), p.cube(), p.skipped(), p.total(), p.millis(), vanilla.name().toLowerCase(java.util.Locale.ROOT));
                var reasons = next.topReasons(6);
                if (!reasons.isEmpty()) CCCloud.LOGGER.debug("[cccloud] Most common reasons an icon was left for game clients: {}", reasons);
            }
        });
    }

    /** Opens the vanilla source for {@code minecraft} assets, or logs how to get one. Render thread only. */
    private void resolveVanilla() {
        String configured = CloudConfig.get(CloudConfig.VANILLA_ASSETS_JAR).trim();
        if (!configured.isEmpty()) {
            try {
                ModAssets.setVanilla(VanillaAssets.open(Path.of(configured)));
                vanilla = Vanilla.JAR;
                CCCloud.LOGGER.info("[cccloud] Item icons: vanilla textures from {}", configured);
                return;
            } catch (IOException | RuntimeException e) {
                vanilla = Vanilla.FAILED;
                vanillaError = String.valueOf(e.getMessage());
                CCCloud.LOGGER.warn("[cccloud] vanillaAssetsJar = {} could not be read: {}", configured, e.getMessage());
            }
        }
        if (CloudConfig.get(CloudConfig.DOWNLOAD_VANILLA_ASSETS)) {
            String version = SharedConstants.getCurrentVersion().id();
            var cache = server.getServerDirectory().resolve("cccloud").resolve("cache");
            try {
                var jar = VanillaAssets.ensureClientJar(cache, version, VanillaAssets.https());
                ModAssets.setVanilla(VanillaAssets.open(jar));
                vanilla = Vanilla.DOWNLOAD;
                CCCloud.LOGGER.info("[cccloud] Item icons: vanilla textures from the official Minecraft {} client jar (downloadVanillaAssets = true, {})",
                    version, jar);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException | RuntimeException e) {
                vanilla = Vanilla.FAILED;
                vanillaError = String.valueOf(e.getMessage());
                CCCloud.LOGGER.warn("[cccloud] Item icons: could not get the Minecraft {} client jar from Mojang: {}", version, e.getMessage());
                return;
            }
        }
        if (vanilla == Vanilla.FAILED) return;
        if (ModAssets.read("assets/minecraft/textures/block/stone.png") != null) {
            vanilla = Vanilla.NOT_NEEDED; // dev environments have the client resources on the classpath
            return;
        }
        vanilla = Vanilla.NONE;
        CCCloud.LOGGER.info("[cccloud] Item icons: this server has no Minecraft textures, so vanilla items get their icons once a player with CC: Cloud "
            + "joins. To draw them at start, set downloadVanillaAssets = true in serverconfig/cccloud-server.toml (downloads the official client jar "
            + "from Mojang, read locally only) or vanillaAssetsJar = <path to a client jar you own>.");
    }

    public ServerIconPass.@Nullable Progress serverProgress() {
        var p = pass;
        return p == null ? null : p.progress();
    }

    public Vanilla vanilla() {
        return vanilla;
    }

    public String vanillaError() {
        return vanillaError;
    }

    /** Icons of the registered items by source (for {@code /cccloud icons status}; walks every item). */
    public Sources sources() {
        List<String> ids;
        synchronized (this) {
            ids = List.copyOf(wanted.keySet());
        }
        int client = 0, srv = 0;
        for (var id : ids) {
            var source = store.source(id);
            if (source == IconStore.Source.CLIENT) client++;
            else if (source == IconStore.Source.SERVER) srv++;
        }
        return new Sources(client, srv, ids.size() - client - srv);
    }

    /** Deletes the store and renders everything from scratch. */
    public synchronized void clear() throws IOException {
        store.clear();
        rebuild();
    }

    public void flushNow() {
        io.execute(this::flushQuietly);
    }

    // ---- fallbacks ---------------------------------------------------------------------------------------------------

    /**
     * The item's flat texture from the mod jar ({@code textures/item/<path>.png}, then {@code textures/block/<path>.png});
     * vanilla only with a vanilla source ({@code vanillaAssetsJar} or {@code downloadVanillaAssets}). Square PNGs ≤ 64 KB only.
     */
    public static byte @Nullable [] fallback(String namespace, String path) {
        // Item definition → model → texture, read from the jars that provide each namespace.
        byte[] resolved = IconFallback.resolve(namespace, path, IconService::readResource);
        if (resolved != null) return resolved;
        for (var dir : new String[]{"item", "block"}) {
            String file = "assets/" + namespace + "/textures/" + dir + "/" + path + ".png";
            byte[] bytes = ModAssets.read(file);
            if (bytes == null || bytes.length > 64 * 1024) continue;
            var size = PngCodec.size(bytes);
            if (size != null && size[0] == size[1] && size[0] > 0 && size[0] <= 512) return bytes;
        }
        return null;
    }

    /** A resource from the mods' jars (any namespace) or, for minecraft, the vanilla source (if any). */
    static byte @Nullable [] readResource(String file) {
        return ModAssets.read(file);
    }

    /** Test hook: a service over a given store without a server (unit tests of {@link #accept}). */
    static IconService forTest(Path dir, Map<String, String> items) {
        return new IconService(dir, items);
    }

    private IconService(Path dir, Map<String, String> items) {
        this.server = null;
        this.store = new IconStore(dir);
        wanted.putAll(items);
        requeue(store.outdated(wanted));
    }

    /** Test hook: assign a batch to a client as {@link #assign()} would. */
    synchronized List<String> assignForTest(UUID client, int max) {
        var batch = new ArrayList<String>();
        while (batch.size() < max && !queue.isEmpty()) {
            var id = queue.pollFirst();
            queued.remove(id);
            batch.add(id);
        }
        assignments.put(client, new Assignment(new LinkedHashSet<>(batch), System.currentTimeMillis()));
        return batch;
    }

    synchronized long rejectedCount() {
        return rejected;
    }
}
