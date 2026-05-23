package dev.gustavo.fullsteamahead.content.piston;

import com.mojang.serialization.MapCodec;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class PistonHeadBlock extends Block {
    public static final MapCodec<PistonHeadBlock> CODEC = simpleCodec(PistonHeadBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    public PistonHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ASSEMBLED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            CrankshaftBlockEntity.revalidateNearbyCrankshafts(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            CrankshaftBlockEntity.invalidateNearbyCrankshafts(level, pos, "Piston head changed", pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED);
    }
}
