package ai.ezfox.cccloud.publicapi;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World SavedData ({@code data/cccloud/apikeys.dat}): the {@link ApiKeys} (hashes only, never a key itself). */
public final class ApiKeyData extends SavedData {
    private static final Codec<ApiKeys.Key> KEY_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(ApiKeys.Key::id),
        Codec.STRING.fieldOf("hash").forGetter(ApiKeys.Key::hash),
        Codec.STRING.fieldOf("name").forGetter(ApiKeys.Key::name),
        UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(ApiKeys.Key::player),
        Codec.STRING.optionalFieldOf("player_name", "").forGetter(ApiKeys.Key::playerName),
        UUIDUtil.STRING_CODEC.fieldOf("mainframe").forGetter(ApiKeys.Key::mainframeId),
        Codec.STRING.listOf().fieldOf("scopes").forGetter(k -> k.scopes().stream().map(s -> s.id).toList()),
        Codec.LONG.fieldOf("created").forGetter(ApiKeys.Key::created),
        Codec.LONG.optionalFieldOf("expires", 0L).forGetter(ApiKeys.Key::expires),
        Codec.STRING.listOf().optionalFieldOf("ips", List.of()).forGetter(ApiKeys.Key::ips),
        Codec.LONG.optionalFieldOf("last_used", 0L).forGetter(ApiKeys.Key::lastUsed),
        Codec.STRING.optionalFieldOf("last_ip", "").forGetter(ApiKeys.Key::lastIp)
    ).apply(i, (id, hash, name, player, playerName, mainframe, scopes, created, expires, ips, lastUsed, lastIp) -> {
        var set = EnumSet.noneOf(ApiScope.class);
        for (var s : scopes) {
            var scope = ApiScope.byId(s);
            if (scope != null) set.add(scope);
        }
        return new ApiKeys.Key(id, hash, name, player, playerName, mainframe, set, created, expires, ips, lastUsed, lastIp);
    }));

    private static final Codec<ApiKeyData> CODEC = RecordCodecBuilder.create(i -> i.group(
        KEY_CODEC.listOf().optionalFieldOf("keys", List.of()).forGetter(d -> new ArrayList<>(d.keys.all()))
    ).apply(i, ApiKeyData::new));

    public static final SavedDataType<ApiKeyData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "apikeys"), ApiKeyData::new, CODEC);

    private final ApiKeys keys = new ApiKeys(System::currentTimeMillis, this::setDirty);

    public ApiKeyData() {
    }

    private ApiKeyData(List<ApiKeys.Key> stored) {
        keys.load(stored);
    }

    public static ApiKeyData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public ApiKeys keys() {
        return keys;
    }
}
