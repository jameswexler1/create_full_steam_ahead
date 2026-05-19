package dev.gustavo.fullsteamahead.content.engine.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.RotatedPillarBlock;

public class AxialEnginePartBlock extends RotatedPillarBlock {
    public static final MapCodec<AxialEnginePartBlock> CODEC = simpleCodec(AxialEnginePartBlock::new);

    public AxialEnginePartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }
}
