package ai.ezfox.cccloud.cable;

import com.mojang.serialization.MapCodec;

/**
 * Fiber Junction: a 12×12×12 box for splits and wall pass-through. Same connections and side properties as the
 * {@link FiberCableBlock}; the model shows a short stub per connected side.
 */
public class FiberJunctionBlock extends FiberCableBlock {
    public static final MapCodec<FiberJunctionBlock> CODEC = simpleCodec(FiberJunctionBlock::new);

    public FiberJunctionBlock(Properties properties) {
        super(properties, 2, 14);
    }

    @Override
    protected MapCodec<? extends FiberJunctionBlock> codec() {
        return CODEC;
    }
}
