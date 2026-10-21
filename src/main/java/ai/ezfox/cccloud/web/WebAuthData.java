package ai.ezfox.cccloud.web;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

/** World SavedData ({@code data/cccloud/web.dat}) holding the {@link AuthStore}. Pending approvals are memory-only. */
public final class WebAuthData extends SavedData {
    private static final Codec<AuthStore.PairingCode> CODE_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("code").forGetter(AuthStore.PairingCode::code),
        Codec.LONG.fieldOf("expires").forGetter(AuthStore.PairingCode::expiresAt),
        UUIDUtil.STRING_CODEC.optionalFieldOf("issuer").forGetter(c -> Optional.ofNullable(c.issuer()))
    ).apply(i, (code, expires, issuer) -> new AuthStore.PairingCode(code, expires, issuer.orElse(null))));

    private static final Codec<AuthStore.Device> DEVICE_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("hash").forGetter(AuthStore.Device::tokenHash),
        UUIDUtil.STRING_CODEC.fieldOf("mainframe").forGetter(AuthStore.Device::mainframeId),
        Codec.STRING.fieldOf("name").forGetter(AuthStore.Device::name),
        Codec.BOOL.optionalFieldOf("can_control", true).forGetter(AuthStore.Device::canControl),
        Codec.LONG.fieldOf("created").forGetter(AuthStore.Device::created),
        Codec.LONG.fieldOf("last_used").forGetter(AuthStore.Device::lastUsed),
        UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(AuthStore.Device::player),
        Codec.STRING.optionalFieldOf("player_name", "").forGetter(AuthStore.Device::playerName)
    ).apply(i, AuthStore.Device::new));

    private static final Codec<AuthStore.Acl> ACL_CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(a -> Optional.ofNullable(a.owner())),
        UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("trusted", List.of()).forGetter(a -> List.copyOf(a.trusted()))
    ).apply(i, (owner, trusted) -> new AuthStore.Acl(owner.orElse(null), new HashSet<>(trusted))));

    private static final Codec<AuthStore.PinHash> PIN_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("salt").forGetter(AuthStore.PinHash::salt),
        Codec.STRING.fieldOf("hash").forGetter(AuthStore.PinHash::hash),
        Codec.INT.fieldOf("iterations").forGetter(AuthStore.PinHash::iterations)
    ).apply(i, AuthStore.PinHash::new));

    private static final Codec<WebAuthData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, CODE_CODEC).optionalFieldOf("pairing_codes", Map.of()).forGetter(d -> Map.copyOf(d.store.codes())),
        DEVICE_CODEC.listOf().optionalFieldOf("devices", List.of()).forGetter(d -> List.copyOf(d.store.deviceMap().values())),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, ACL_CODEC).optionalFieldOf("acls", Map.of()).forGetter(d -> Map.copyOf(d.store.acls())),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, PIN_CODEC).optionalFieldOf("pins", Map.of()).forGetter(d -> Map.copyOf(d.store.pins()))
    ).apply(i, WebAuthData::new));

    public static final SavedDataType<WebAuthData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(CCCloud.MODID, "web"), WebAuthData::new, CODEC
    );

    private final AuthStore store = new AuthStore(System::currentTimeMillis, this::setDirty);

    public WebAuthData() {
    }

    private WebAuthData(Map<UUID, AuthStore.PairingCode> codes, List<AuthStore.Device> devices, Map<UUID, AuthStore.Acl> acls, Map<UUID, AuthStore.PinHash> pins) {
        store.load(codes, devices, acls, pins);
    }

    public static AuthStore get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE).store;
    }
}
