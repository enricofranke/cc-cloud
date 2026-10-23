package ai.ezfox.cccloud.access;

import ai.ezfox.cccloud.CCCloud;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.jspecify.annotations.Nullable;

/**
 * The {@code cccloud.*} permission nodes, registered with NeoForge's PermissionAPI so LuckPerms (NeoForge) and FTB
 * Ranks control them without extra integration. Without a permission mod the defaults apply: {@code cccloud.use} is
 * true, the admin/command nodes need op level 2, {@code cccloud.bypass.limits} op level 4, and
 * {@code cccloud.limit.mainframes} is 3.
 */
public final class CloudPermissions implements ServerPermissions {
    private static final Map<Node, PermissionNode<Boolean>> NODES = new EnumMap<>(Node.class);
    public static final PermissionNode<Integer> LIMIT_MAINFRAMES = new PermissionNode<>(CCCloud.MODID, "limit.mainframes", PermissionTypes.INTEGER,
        (player, uuid, ctx) -> 3);

    static {
        for (var node : Node.values()) {
            NODES.put(node, new PermissionNode<>(CCCloud.MODID, node.path, PermissionTypes.BOOLEAN, (player, uuid, ctx) -> switch (node) {
                case USE -> true;
                case BYPASS_LIMITS -> hasLevel(player, uuid, Permissions.COMMANDS_OWNER);
                default -> hasLevel(player, uuid, Permissions.COMMANDS_GAMEMASTER);
            }));
        }
    }

    private final MinecraftServer server;

    private CloudPermissions(MinecraftServer server) {
        this.server = server;
    }

    public static CloudPermissions of(MinecraftServer server) {
        return new CloudPermissions(server);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class, event -> {
            event.addNodes(NODES.values().toArray(PermissionNode[]::new));
            event.addNodes(LIMIT_MAINFRAMES);
        });
    }

    public static PermissionNode<Boolean> node(Node node) {
        return NODES.get(node);
    }

    /** Default resolver: vanilla op level, also for offline players (op list is keyed by UUID). */
    private static boolean hasLevel(@Nullable ServerPlayer player, UUID uuid, Permission level) {
        if (player != null) return player.permissions().hasPermission(level);
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        return server.getProfilePermissions(new NameAndId(uuid, nameOf(server, uuid))).hasPermission(level);
    }

    /** Best-effort name of a player without network lookups (online players, then the server's profile cache). */
    public static String nameOf(MinecraftServer server, UUID uuid) {
        var online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().name();
        return server.services().nameToIdCache().get(uuid).map(NameAndId::name).orElse("");
    }

    @Override
    public boolean banned(UUID player) {
        return server.getPlayerList().getBans().isBanned(new NameAndId(player, nameOf(server, player)));
    }

    @Override
    public boolean has(UUID player, Node node) {
        var permissionNode = NODES.get(node);
        try {
            var online = server.getPlayerList().getPlayer(player);
            Boolean value = online != null ? PermissionAPI.getPermission(online, permissionNode) : PermissionAPI.getOfflinePermission(player, permissionNode);
            return value != null && value;
        } catch (RuntimeException e) {
            // Nodes not registered (should not happen) or a broken permission mod: fall back to the vanilla defaults.
            return permissionNode.getDefaultResolver().resolve(server.getPlayerList().getPlayer(player), player);
        }
    }

    @Override
    public int mainframeLimit(UUID player) {
        try {
            var online = server.getPlayerList().getPlayer(player);
            Integer value = online != null ? PermissionAPI.getPermission(online, LIMIT_MAINFRAMES) : PermissionAPI.getOfflinePermission(player, LIMIT_MAINFRAMES);
            return value == null ? 3 : Math.max(0, value);
        } catch (RuntimeException e) {
            return 3;
        }
    }
}
