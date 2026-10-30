package ai.ezfox.cccloud.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

/**
 * A machine picked with the Link Card for a Cloud Probe (sneak + right-click): where it is, the face that was clicked
 * (the probe reads it through that side, like an uplink stuck to it) and who picked it. Only that player can hand it to
 * a probe: picking it went through the normal interaction checks (claims, spawn protection) for them.
 */
public record ProbeTarget(GlobalPos pos, Direction side, UUID player) {
    public static final Codec<ProbeTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
        GlobalPos.CODEC.fieldOf("pos").forGetter(ProbeTarget::pos),
        Direction.CODEC.fieldOf("side").forGetter(ProbeTarget::side),
        UUIDUtil.CODEC.fieldOf("player").forGetter(ProbeTarget::player)
    ).apply(i, ProbeTarget::new));

    public static final StreamCodec<ByteBuf, ProbeTarget> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC, ProbeTarget::pos,
        Direction.STREAM_CODEC, ProbeTarget::side,
        UUIDUtil.STREAM_CODEC, ProbeTarget::player,
        ProbeTarget::new
    );

    public String describe() {
        var p = pos.pos();
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
