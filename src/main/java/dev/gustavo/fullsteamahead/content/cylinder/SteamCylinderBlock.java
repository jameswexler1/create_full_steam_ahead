package dev.gustavo.fullsteamahead.content.cylinder;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class SteamCylinderBlock extends Block {
    public static final MapCodec<SteamCylinderBlock> CODEC = simpleCodec(SteamCylinderBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    public SteamCylinderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ASSEMBLED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED);
    }
}
