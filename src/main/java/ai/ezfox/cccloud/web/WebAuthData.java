package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.access.AccessStore;
import ai.ezfox.cccloud.access.MainframeAccess;
import ai.ezfox.cccloud.access.Role;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World SavedData ({@code data/cccloud/web.dat}) holding the {@link AuthStore} (codes, devices, PINs) and the
 * {@link AccessStore} (roles, teams, share links, audit log). Pending approvals and account codes are memory-only.
 * <p>
 * Migration from v0.3: the old {@code acls} (owner + trusted) become access entries with trusted players as admins;
 * old device tokens keep their mainframe and become account tokens limited to it.
 */
public final class WebAuthData extends SavedData {
    private static final Codec<AuthStore.PairingCode> CODE_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("code").forGetter(AuthStore.PairingCode::code),
        Codec.LONG.fieldOf("expires").forGetter(AuthStore.PairingCode::expiresAt),
        UUIDUtil.STRING_CODEC.optionalFieldOf("issuer").forGetter(c -> Optional.ofNullable(c.issuer()))
    ).apply(i, (code, expires, issuer) -> new AuthStore.PairingCode(code, expires, issuer.orElse(null))));

    private static final Codec<AuthStore.Device> DEVICE_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("hash").forGetter(AuthStore.Device::tokenHash),
        UUIDUtil.STRING_CODEC.optionalFieldOf("mainframe").forGetter(d -> Optional.ofNullable(d.mainframeId())),
        Codec.STRING.fieldOf("name").forGetter(AuthStore.Device::name),
        Codec.BOOL.optionalFieldOf("can_control", true).forGetter(AuthStore.Device::canControl),
        Codec.LONG.fieldOf("created").forGetter(AuthStore.Device::created),
        Codec.LONG.fieldOf("last_used").forGetter(AuthStore.Device::lastUsed),
        UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(AuthStore.Device::player),
        Codec.STRING.optionalFieldOf("player_name", "").forGetter(AuthStore.Device::playerName),
        Codec.STRING.optionalFieldOf("type", "other").forGetter(AuthStore.Device::type),
        Codec.STRING.optionalFieldOf("last_ip", "").forGetter(AuthStore.Device::lastIp)
    ).apply(i, (hash, mainframe, name, control, created, lastUsed, player, playerName, type, ip) ->
        new AuthStore.Device(hash, mainframe.orElse(null), name, control, created, lastUsed, player, playerName, type, ip)));

    /** v0.3 only (read for the migration, never written). */
    private static final Codec<AuthStore.Acl> ACL_CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(a -> Optional.ofNullable(a.owner())),
        UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("trusted", List.of()).forGetter(a -> List.copyOf(a.trusted()))
    ).apply(i, (owner, trusted) -> new AuthStore.Acl(owner.orElse(null), new HashSet<>(trusted))));

    private static final Codec<AuthStore.PinHash> PIN_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("salt").forGetter(AuthStore.PinHash::salt),
        Codec.STRING.fieldOf("hash").forGetter(AuthStore.PinHash::hash),
        Codec.INT.fieldOf("iterations").forGetter(AuthStore.PinHash::iterations)
    ).apply(i, AuthStore.PinHash::new));

    private static final Codec<Role> ROLE_CODEC = Codec.STRING.comapFlatMap(s -> {
        var role = Role.parse(s);
        return role == null || role == Role.OWNER ? DataResult.error(() -> "Unknown role " + s) : DataResult.success(role);
    }, r -> r.id);

    private static final Codec<MainframeAccess.Grant> GRANT_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("name", "").forGetter(MainframeAccess.Grant::name),
        ROLE_CODEC.fieldOf("role").forGetter(MainframeAccess.Grant::role)
    ).apply(i, MainframeAccess.Grant::new));

    private static final Codec<MainframeAccess.TeamBinding> TEAM_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("provider").forGetter(MainframeAccess.TeamBinding::provider),
        Codec.STRING.fieldOf("team").forGetter(MainframeAccess.TeamBinding::teamId),
        Codec.unboundedMap(Codec.STRING, ROLE_CODEC).optionalFieldOf("mapping", Map.of()).forGetter(MainframeAccess.TeamBinding::mapping)
    ).apply(i, MainframeAccess.TeamBinding::new));

    private static final Codec<MainframeAccess> ACCESS_CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(a -> Optional.ofNullable(a.owner())),
        Codec.STRING.optionalFieldOf("owner_name", "").forGetter(MainframeAccess::ownerName),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, GRANT_CODEC).optionalFieldOf("grants", Map.of()).forGetter(MainframeAccess::grants),
        TEAM_CODEC.optionalFieldOf("team").forGetter(a -> Optional.ofNullable(a.team()))
    ).apply(i, (owner, name, grants, team) -> new MainframeAccess(owner.orElse(null), name, grants, team.orElse(null))));

    private static final Codec<AccessStore.Share> SHARE_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(AccessStore.Share::id),
        UUIDUtil.STRING_CODEC.fieldOf("mainframe").forGetter(AccessStore.Share::mainframeId),
        Codec.STRING.fieldOf("hash").forGetter(AccessStore.Share::secretHash),
        Codec.STRING.optionalFieldOf("label", "").forGetter(AccessStore.Share::label),
        Codec.STRING.optionalFieldOf("created_by", "").forGetter(AccessStore.Share::createdBy),
        Codec.LONG.fieldOf("created").forGetter(AccessStore.Share::created),
        Codec.LONG.optionalFieldOf("expires", 0L).forGetter(AccessStore.Share::expires),
        Codec.INT.optionalFieldOf("max_viewers", 5).forGetter(AccessStore.Share::maxViewers),
        Codec.STRING.listOf().optionalFieldOf("pages", List.of()).forGetter(AccessStore.Share::pages),
        Codec.STRING.listOf().optionalFieldOf("cards", List.of()).forGetter(AccessStore.Share::cards),
        Codec.BOOL.optionalFieldOf("hide_numbers", false).forGetter(AccessStore.Share::hideNumbers)
    ).apply(i, AccessStore.Share::new));

    private static final Codec<AccessStore.AuditEntry> AUDIT_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.fieldOf("time").forGetter(AccessStore.AuditEntry::time),
        Codec.STRING.fieldOf("text").forGetter(AccessStore.AuditEntry::text)
    ).apply(i, AccessStore.AuditEntry::new));

    private static final Codec<WebAuthData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, CODE_CODEC).optionalFieldOf("pairing_codes", Map.of()).forGetter(d -> Map.copyOf(d.store.codes())),
        DEVICE_CODEC.listOf().optionalFieldOf("devices", List.of()).forGetter(d -> List.copyOf(d.store.deviceMap().values())),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, ACL_CODEC).optionalFieldOf("acls", Map.of()).forGetter(d -> Map.of()),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, PIN_CODEC).optionalFieldOf("pins", Map.of()).forGetter(d -> Map.copyOf(d.store.pins())),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, ACCESS_CODEC).optionalFieldOf("access", Map.of()).forGetter(d -> Map.copyOf(d.access.accessMap())),
        SHARE_CODEC.listOf().optionalFieldOf("shares", List.of()).forGetter(d -> List.copyOf(d.access.shareMap().values())),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, AUDIT_CODEC.listOf()).optionalFieldOf("audit", Map.of()).forGetter(d -> Map.copyOf(d.access.auditMap()))
    ).apply(i, WebAuthData::new));

    public static final SavedDataType<WebAuthData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(CCCloud.MODID, "web"), WebAuthData::new, CODEC
    );

    private final AuthStore store = new AuthStore(System::currentTimeMillis, this::setDirty);
    private final AccessStore access = new AccessStore(System::currentTimeMillis, this::setDirty);

    public WebAuthData() {
    }

    private WebAuthData(Map<UUID, AuthStore.PairingCode> codes, List<AuthStore.Device> devices, Map<UUID, AuthStore.Acl> acls,
                        Map<UUID, AuthStore.PinHash> pins, Map<UUID, MainframeAccess> accessMap, List<AccessStore.Share> shares,
                        Map<UUID, List<AccessStore.AuditEntry>> audit) {
        store.load(codes, devices, pins);
        access.load(accessMap, shares, audit);
        if (migrate(access, acls) > 0) setDirty();
    }

    /** v0.3 → v0.6: owner + trusted list → access entries (trusted = admin). Names are filled in when the block loads. */
    static int migrate(AccessStore access, Map<UUID, AuthStore.Acl> acls) {
        int migrated = 0;
        for (var e : acls.entrySet()) {
            var trusted = new HashMap<UUID, String>();
            for (var t : e.getValue().trusted()) trusted.put(t, "");
            if (access.migrateLegacy(e.getKey(), e.getValue().owner(), "", trusted)) migrated++;
        }
        if (migrated > 0) CCCloud.LOGGER.info("[cccloud] Migrated the owner/trusted lists of {} mainframe(s) to v0.6 roles (trusted players are admins)", migrated);
        return migrated;
    }

    public static AuthStore get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE).store;
    }

    public static AccessStore access(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE).access;
    }
}
