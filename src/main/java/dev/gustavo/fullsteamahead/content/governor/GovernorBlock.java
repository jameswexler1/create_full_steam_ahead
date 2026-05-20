package dev.gustavo.fullsteamahead.content.governor;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

public class GovernorBlock extends Block {
    public static final MapCodec<GovernorBlock> CODEC = simpleCodec(GovernorBlock::new);

    public GovernorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
