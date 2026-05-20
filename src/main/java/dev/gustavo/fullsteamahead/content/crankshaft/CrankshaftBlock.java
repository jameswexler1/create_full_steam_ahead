package dev.gustavo.fullsteamahead.content.crankshaft;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CrankshaftBlock extends KineticBlock implements IBE<CrankshaftBlockEntity> {
    public static final MapCodec<CrankshaftBlock> CODEC = simpleCodec(CrankshaftBlock::new);

    public CrankshaftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis().isHorizontal();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            withBlockEntityDo(level, pos, CrankshaftBlockEntity::revalidateStructure);
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        if (!level.isClientSide()) {
            withBlockEntityDo(level, pos, CrankshaftBlockEntity::revalidateStructure);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                withBlockEntityDo(level, pos, CrankshaftBlockEntity::clearAssembly);
            }
            IBE.onRemove(state, level, pos, newState);
        }
    }

    @Override
    public Class<CrankshaftBlockEntity> getBlockEntityClass() {
        return CrankshaftBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CrankshaftBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CRANKSHAFT.get();
    }
}
