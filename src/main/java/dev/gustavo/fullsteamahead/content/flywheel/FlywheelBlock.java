package dev.gustavo.fullsteamahead.content.flywheel;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

public class FlywheelBlock extends Block {
    public static final MapCodec<FlywheelBlock> CODEC = simpleCodec(FlywheelBlock::new);

    public FlywheelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
