package dev.gustavo.fullsteamahead.content.engine.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

public class EnginePartBlock extends Block {
    public static final MapCodec<EnginePartBlock> CODEC = simpleCodec(EnginePartBlock::new);

    public EnginePartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
