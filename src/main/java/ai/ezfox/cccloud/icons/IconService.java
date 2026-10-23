package ai.ezfox.cccloud.icons;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.CloudConfig;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
import java.util.zip.ZipFile;
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
 * Without a rendered icon, {@link #fallback} reads the item/block texture from the mod jar (or from a Minecraft client
 * jar configured as {@code vanillaAssetsJar}); vanilla textures are not on a dedicated server otherwise.
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
    private long accepted, rejected;

    private IconService(MinecraftServer server, Path dir) {
        this.server = server;
        this.store = new IconStore(dir);
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            var key = BuiltInRegistries.ITEM.getKey(item);
            wanted.put(key.toString(), version(key.getNamespace()));
        }
        requeue(store.outdated(wanted));
        io.scheduleWithFixedDelay(this::flushQuietly, 60, 60, TimeUnit.SECONDS);
        CCCloud.LOGGER.info("[cccloud] Item icons: {} of {} stored, {} queued for rendering by game clients ({})", wanted.size() - queue.size(),
            wanted.size(), queue.size(), dir);
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
        service.io.shutdownNow();
        service.flushQuietly();
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

    /** Re-renders every item; old icons stay available until replaced. */
    public synchronized void rebuild() {
        assignments.clear();
        queue.clear();
        queued.clear();
        requeue(wanted.keySet());
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
     * vanilla only if {@code vanillaAssetsJar} points to a Minecraft client jar. Square PNGs ≤ 64 KB only.
     */
    public static byte @Nullable [] fallback(String namespace, String path) {
        // Item definition → model → texture, read from the jar that provides each namespace.
        byte[] resolved = IconFallback.resolve(namespace, path, IconService::readResource);
        if (resolved != null) return resolved;
        for (var dir : new String[]{"item", "block"}) {
            String file = "assets/" + namespace + "/textures/" + dir + "/" + path + ".png";
            byte[] bytes = namespace.equals("minecraft") ? fromVanillaJar(file) : fromModJar(namespace, file);
            if (bytes == null || bytes.length > 64 * 1024) continue;
            var size = PngCodec.size(bytes);
            if (size != null && size[0] == size[1] && size[0] > 0 && size[0] <= 512) return bytes;
        }
        return null;
    }

    /** namespace → mod file that has {@code assets/<namespace>/} (namespaces need not match mod ids). */
    private static final Map<String, java.util.Optional<net.neoforged.neoforgespi.locating.IModFile>> NAMESPACE_FILES = new java.util.concurrent.ConcurrentHashMap<>();

    /** A resource from the mods' jars (any namespace) or, for minecraft, the configured client jar. */
    static byte @Nullable [] readResource(String file) {
        String[] parts = file.split("/", 3);
        if (parts.length < 3 || !parts[0].equals("assets")) return null;
        String namespace = parts[1];
        if (namespace.equals("minecraft")) return fromVanillaJar(file);
        var modFile = NAMESPACE_FILES.computeIfAbsent(namespace, ns -> {
            try {
                for (var info : ModList.get().getModFiles()) {
                    var contents = info.getFile().getContents();
                    if (contents.findFile("assets/" + ns + "/").isPresent() || contents.findFile("assets/" + ns + "/lang/en_us.json").isPresent()
                        || contents.findFile("assets/" + ns + "/models").isPresent() || contents.findFile("assets/" + ns + "/textures").isPresent()) {
                        return java.util.Optional.of(info.getFile());
                    }
                }
            } catch (RuntimeException ignored) {
            }
            return java.util.Optional.empty();
        });
        if (modFile.isEmpty()) return null;
        try {
            var contents = modFile.get().getContents();
            if (contents.findFile(file).isEmpty()) return null;
            try (InputStream in = contents.openFile(file)) {
                return in == null ? null : in.readNBytes(256 * 1024 + 1);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte @Nullable [] fromModJar(String namespace, String file) {
        try {
            var info = ModList.get().getModFileById(namespace);
            if (info == null) return null;
            var contents = info.getFile().getContents();
            if (contents.findFile(file).isEmpty()) return null;
            try (InputStream in = contents.openFile(file)) {
                return in == null ? null : in.readNBytes(64 * 1024 + 1);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte @Nullable [] fromVanillaJar(String file) {
        String jar = CloudConfig.get(CloudConfig.VANILLA_ASSETS_JAR).trim();
        if (jar.isEmpty() || !Files.isRegularFile(Path.of(jar))) return null;
        try (var zip = new ZipFile(jar)) {
            var entry = zip.getEntry(file);
            if (entry == null) return null;
            try (var in = zip.getInputStream(entry)) {
                return in.readNBytes(64 * 1024 + 1);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
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
