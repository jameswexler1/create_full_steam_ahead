package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class BoilerOutletBlock extends Block implements IBE<BoilerOutletBlockEntity> {
    public static final MapCodec<BoilerOutletBlock> CODEC = simpleCodec(BoilerOutletBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public BoilerOutletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            withBlockEntityDo(level, pos, BoilerOutletBlockEntity::refreshBoilerState);
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
            withBlockEntityDo(level, pos, BoilerOutletBlockEntity::refreshBoilerState);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                withBlockEntityDo(level, pos, BoilerOutletBlockEntity::clearBoilerState);
            }
            IBE.onRemove(state, level, pos, newState);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public static Direction getFacing(BlockState state) {
        return state.getValue(FACING);
    }

    public static BlockPos getAttachedTankPos(BlockPos outletPos, BlockState state) {
        return outletPos.relative(getFacing(state).getOpposite());
    }

    public static boolean isBackAttachedTo(LevelReader level, BlockPos outletPos, BlockState state, BlockPos tankPos) {
        return getAttachedTankPos(outletPos, state).equals(tankPos);
    }

    @Override
    public Class<BoilerOutletBlockEntity> getBlockEntityClass() {
        return BoilerOutletBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BoilerOutletBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BOILER_OUTLET.get();
    }
}
