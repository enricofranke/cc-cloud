package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.computer.ComputerRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component of a Cloud Computer item that came from a broken computer: its id and (for the tooltip and the item
 * name) its label. Placing it restores that computer with its files when {@link ComputerAccess#mayRestore} allows it;
 * the label shown in game always comes from the server, never from the item.
 */
public record ComputerItemData(int id, String label) {
    public static final Codec<ComputerItemData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("id").forGetter(ComputerItemData::id),
        Codec.string(0, ComputerRegistry.MAX_LABEL * 2).optionalFieldOf("label", "").forGetter(ComputerItemData::label)
    ).apply(i, ComputerItemData::new));

    public static final StreamCodec<ByteBuf, ComputerItemData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ComputerItemData::id,
        ByteBufCodecs.stringUtf8(ComputerRegistry.MAX_LABEL * 4), ComputerItemData::label,
        ComputerItemData::new
    );
}
