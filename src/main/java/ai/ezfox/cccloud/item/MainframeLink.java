package ai.ezfox.cccloud.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

/** Reference to a mainframe: where it is and which id it has. Used by the link card and by uplinks. */
public record MainframeLink(GlobalPos pos, UUID id) {
    public static final Codec<MainframeLink> CODEC = RecordCodecBuilder.create(i -> i.group(
        GlobalPos.CODEC.fieldOf("pos").forGetter(MainframeLink::pos),
        UUIDUtil.CODEC.fieldOf("id").forGetter(MainframeLink::id)
    ).apply(i, MainframeLink::new));

    public static final StreamCodec<ByteBuf, MainframeLink> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC, MainframeLink::pos,
        UUIDUtil.STREAM_CODEC, MainframeLink::id,
        MainframeLink::new
    );

    public String describe() {
        var p = pos.pos();
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
