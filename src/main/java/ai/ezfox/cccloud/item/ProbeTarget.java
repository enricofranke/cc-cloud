package ai.ezfox.cccloud.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A machine picked with the Link Card for a Cloud Probe (sneak + right-click): where it is, the face that was clicked
 * (the probe reads it through that side, like an uplink stuck to it), who picked it and when. Only that player can hand
 * it to a probe, and only for {@link #VALID_MS}: picking it went through the normal interaction checks (claims, spawn
 * protection) for them, and those rights may be gone later (security review 2026-10-30).
 *
 * @param picked ms since epoch (0 = unknown: a card from before the pick time was stored, refused)
 */
public record ProbeTarget(GlobalPos pos, Direction side, UUID player, long picked) {
    /** A picked machine must be handed to a probe within 10 minutes. */
    public static final long VALID_MS = 10 * 60_000L;

    public static final Codec<ProbeTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
        GlobalPos.CODEC.fieldOf("pos").forGetter(ProbeTarget::pos),
        Direction.CODEC.fieldOf("side").forGetter(ProbeTarget::side),
        UUIDUtil.CODEC.fieldOf("player").forGetter(ProbeTarget::player),
        Codec.LONG.optionalFieldOf("picked", 0L).forGetter(ProbeTarget::picked)
    ).apply(i, ProbeTarget::new));

    public static final StreamCodec<ByteBuf, ProbeTarget> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC, ProbeTarget::pos,
        Direction.STREAM_CODEC, ProbeTarget::side,
        UUIDUtil.STREAM_CODEC, ProbeTarget::player,
        ByteBufCodecs.VAR_LONG, ProbeTarget::picked,
        ProbeTarget::new
    );

    /** Picked now. */
    public ProbeTarget(GlobalPos pos, Direction side, UUID player) {
        this(pos, side, player, System.currentTimeMillis());
    }

    /** Still valid at {@code now}: picked within {@link #VALID_MS} (and not in the future). */
    public boolean fresh(long now) {
        return picked > 0 && picked <= now + 60_000L && now - picked <= VALID_MS;
    }

    public String describe() {
        var p = pos.pos();
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
