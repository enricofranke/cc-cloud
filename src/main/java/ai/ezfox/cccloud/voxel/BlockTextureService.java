package ai.ezfox.cccloud.voxel;

import ai.ezfox.cccloud.CCCloud;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
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
 * Voxel view stage 2 (docs/SPEC.md "Voxel view – stage 2"): block textures rendered by game clients, the same way as
 * item icons. Blocks that appear in voxel sections without textures are queued; clients that render icons get batches
 * ({@link RequestPayload}, ≤ {@link #BATCH} ids, one outstanding batch and one request per second each) and answer
 * with the face sprites ({@link UploadPayload}). Every upload is checked: only ids of the client's batch, valid ids,
 * validated 16×16 PNGs, sprites referenced by a block must be known or in the same upload. Stored in
 * {@code <world>/cccloud/block_textures.json}, versioned per mod version.
 */
public final class BlockTextureService {
    public static final int BATCH = 64;
    public static final int MAX_SPRITES_PER_UPLOAD = 192;
    private static final long BATCH_TIMEOUT_MS = 5 * 60 * 1000L;

    public record Sprite(String name, byte[] png) {
        public static final StreamCodec<ByteBuf, Sprite> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(200), Sprite::name, ByteBufCodecs.byteArray(BlockTextureStore.MAX_PNG), Sprite::png, Sprite::new);
    }

    /** faces: 6 sprite names, "" = no texture; tint bits per face. Empty faces list = cannot render. */
    public record BlockFaces(String id, List<String> faces, int tint) {
        public static final StreamCodec<ByteBuf, BlockFaces> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(200), BlockFaces::id, ByteBufCodecs.stringUtf8(200).apply(ByteBufCodecs.list(6)), BlockFaces::faces,
            ByteBufCodecs.VAR_INT, BlockFaces::tint, BlockFaces::new);
    }

    /** Server → client: please read the textures of these blocks. */
    public record RequestPayload(List<String> ids) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "block_texture_request"));
        public static final StreamCodec<ByteBuf, RequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(200).apply(ByteBufCodecs.list(BATCH)), RequestPayload::ids, RequestPayload::new);

        @Override
        public Type<RequestPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: blocks and the sprites they use. */
    public record UploadPayload(List<BlockFaces> blocks, List<Sprite> sprites) implements CustomPacketPayload {
        public static final Type<UploadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "block_texture_upload"));
        public static final StreamCodec<ByteBuf, UploadPayload> CODEC = StreamCodec.composite(
            BlockFaces.CODEC.apply(ByteBufCodecs.list(BATCH)), UploadPayload::blocks,
            Sprite.CODEC.apply(ByteBufCodecs.list(MAX_SPRITES_PER_UPLOAD)), UploadPayload::sprites, UploadPayload::new);

        @Override
        public Type<UploadPayload> type() {
            return TYPE;
        }
    }

    /** Set by the client entry point. */
    public static volatile @Nullable Consumer<RequestPayload> clientRequestHandler;

    private record Assignment(Set<String> ids, long since) {
    }

    private static volatile @Nullable BlockTextureService current;
    private static final BlockTextureStore EMPTY = new BlockTextureStore();

    private final @Nullable MinecraftServer server;
    private final BlockTextureStore store;
    private final @Nullable Path file;
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final Set<String> queued = new LinkedHashSet<>();
    private final Map<UUID, Assignment> assignments = new HashMap<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    long accepted, rejected;
    /** L10: 512 blocks per player and hour; replacing a stored sprite only by players with a role. */
    final ai.ezfox.cccloud.icons.UploadGuard guard = new ai.ezfox.cccloud.icons.UploadGuard(512, System::currentTimeMillis, uuid -> false);

    private BlockTextureService(@Nullable MinecraftServer server, BlockTextureStore store, @Nullable Path file) {
        this.server = server;
        this.store = store;
        this.file = file;
        if (server != null) guard.setTrusted(uuid -> ai.ezfox.cccloud.access.Access.hasAnyRole(server, uuid));
    }

    public static BlockTextureStore store() {
        var s = current;
        return s == null ? EMPTY : s.store;
    }

    /** Version key of a block id's namespace (mod version / game version). */
    static String version(String blockId) {
        return ai.ezfox.cccloud.icons.IconService.versionOf(blockId.substring(0, blockId.indexOf(':')));
    }

    /** Adds the textures to a voxel response (a copy is changed) and queues blocks without textures. */
    public static void decorate(com.google.gson.JsonObject voxels) {
        var service = current;
        if (service == null) return;
        var missing = service.store.decorate(voxels);
        if (!missing.isEmpty()) service.request(missing);
    }

    synchronized void request(List<String> ids) {
        for (var id : ids) {
            if (!BlockTextureStore.ID.matcher(id).matches() || store.isCurrent(id, version(id))) continue;
            boolean assigned = false;
            for (var a : assignments.values()) assigned |= a.ids().contains(id);
            if (!assigned && queued.add(id)) queue.addLast(id);
        }
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    public static void init(IEventBus modBus) {
        modBus.addListener(BlockTextureService::register);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> {
            var path = e.getServer().getWorldPath(LevelResource.ROOT).resolve("cccloud").resolve("block_textures.json");
            current = new BlockTextureService(e.getServer(), BlockTextureStore.load(path), path);
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> {
            var service = current;
            current = null;
            if (service != null) service.save();
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            var service = current;
            if (service == null || e.getServer().getTickCount() % 20 != 0) return;
            service.assign();
            if (e.getServer().getTickCount() % 1200 == 0) service.save();
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            var service = current;
            if (service == null) return;
            synchronized (service) {
                var a = service.assignments.remove(e.getEntity().getUUID());
                service.lastRequest.remove(e.getEntity().getUUID());
                if (a != null) service.request(List.copyOf(a.ids()));
            }
        });
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(RequestPayload.TYPE, RequestPayload.CODEC, (payload, context) -> {
            var handler = clientRequestHandler;
            if (handler != null) handler.accept(payload);
        });
        registrar.playToServer(UploadPayload.TYPE, UploadPayload.CODEC, (payload, context) -> {
            var service = current;
            if (service != null && context.player() instanceof ServerPlayer player) service.accept(player.getUUID(), payload);
        });
    }

    private void save() {
        if (file == null) return;
        try {
            store.save(file);
        } catch (IOException | RuntimeException e) {
            CCCloud.LOGGER.warn("[cccloud] Could not write the block textures", e);
        }
    }

    /** Hands out batches to clients that render icons (same opt-in, client config {@code shareIconRenders}). */
    private synchronized void assign() {
        long now = System.currentTimeMillis();
        for (var it = assignments.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue().since() > BATCH_TIMEOUT_MS) {
                it.remove();
                for (var id : e.getValue().ids()) if (queued.add(id)) queue.addLast(id);
            }
        }
        if (queue.isEmpty() || server == null) return;
        for (var uuid : ai.ezfox.cccloud.icons.IconService.renderers()) {
            if (queue.isEmpty()) break;
            if (assignments.containsKey(uuid) || now - lastRequest.getOrDefault(uuid, 0L) < 1000) continue;
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.connection.hasChannel(RequestPayload.TYPE)) continue;
            // Never more than the player's remaining upload quota, so nothing it renders is thrown away.
            int size = Math.min(BATCH, guard.remaining(uuid));
            if (size <= 0) continue;
            var batch = new ArrayList<String>(size);
            while (batch.size() < size && !queue.isEmpty()) {
                var id = queue.pollFirst();
                queued.remove(id);
                batch.add(id);
            }
            assignments.put(uuid, new Assignment(new LinkedHashSet<>(batch), now));
            lastRequest.put(uuid, now);
            PacketDistributor.sendToPlayer(player, new RequestPayload(batch));
        }
    }

    /** Stores an upload; returns the number of accepted blocks. Only ids of the client's outstanding batch count. */
    synchronized int accept(UUID client, UploadPayload upload) {
        var assignment = assignments.get(client);
        if (assignment == null) {
            rejected += upload.blocks().size();
            return 0;
        }
        // sprites first, but only those referenced by an accepted block of this upload
        var referenced = new java.util.HashSet<String>();
        for (var b : upload.blocks()) if (assignment.ids().contains(b.id())) referenced.addAll(b.faces());
        for (var s : upload.sprites()) {
            if (!referenced.contains(s.name())) continue;
            if (store.hasSprite(s.name()) && !guard.mayReplace(client)) continue; // first writer wins (L10)
            if (!store.putSprite(s.name(), s.png())) rejected++;
        }
        int ok = 0;
        for (var b : upload.blocks()) {
            if (!assignment.ids().remove(b.id())) {
                rejected++;
                continue;
            }
            if (!guard.take(client)) {
                rejected++;
                if (queued.add(b.id())) queue.addLast(b.id()); // quota used up: back to the queue, not lost
                continue;
            }
            if (server != null && BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(b.id())).isEmpty()) continue;
            String version = server == null ? "test@1" : version(b.id());
            if (b.faces().size() != 6) {
                store.markFailed(b.id(), version);
                continue;
            }
            var faces = new String[6];
            for (int i = 0; i < 6; i++) faces[i] = b.faces().get(i).isEmpty() ? null : b.faces().get(i);
            if (store.putBlock(b.id(), version, faces, b.tint())) {
                ok++;
                accepted++;
            } else {
                rejected++;
                store.markFailed(b.id(), version);
            }
        }
        if (assignment.ids().isEmpty()) assignments.remove(client);
        return ok;
    }

    /** Test hook: a service without a server over a given store, with a batch assigned to a client. */
    static BlockTextureService forTest(BlockTextureStore store, UUID client, List<String> batch) {
        var service = new BlockTextureService(null, store, null);
        service.assignments.put(client, new Assignment(new LinkedHashSet<>(batch), System.currentTimeMillis()));
        return service;
    }

    /** GameTests: install a service over the running server (normally done at server start). */
    public static @Nullable BlockTextureService current() {
        return current;
    }
}
