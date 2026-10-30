package ai.ezfox.cccloud.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component of a Cloud Mainframe item that came from a broken mainframe: which mainframe it is, and (for the
 * tooltip only) its owner's name. Placing it restores that mainframe while its data is kept on the server
 * ({@code mainframeRetentionDays}); ownership always comes from the server, never from the item.
 */
public record MainframeIdentity(UUID id, String ownerName) {
    public static final Codec<MainframeIdentity> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(MainframeIdentity::id),
        Codec.string(0, 32).optionalFieldOf("owner", "").forGetter(MainframeIdentity::ownerName)
    ).apply(i, MainframeIdentity::new));

    public static final StreamCodec<ByteBuf, MainframeIdentity> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, MainframeIdentity::id,
        ByteBufCodecs.stringUtf8(32), MainframeIdentity::ownerName,
        MainframeIdentity::new
    );

    /** The first 8 characters of the id, as the dashboard and the commands show it. */
    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
