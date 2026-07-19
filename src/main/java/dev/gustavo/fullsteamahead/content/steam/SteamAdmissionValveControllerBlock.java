package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible occupancy for the admission valve's controller tower. The adjacent valve base owns all
 * rendering, interaction state, fluid transport, block-entity data, drops, and restoration.
 */
public class SteamAdmissionValveControllerBlock extends Block {
    public static final MapCodec<SteamAdmissionValveControllerBlock> CODEC =
            simpleCodec(SteamAdmissionValveControllerBlock::new);
    public static final DirectionProperty BASE_DIRECTION =
            DirectionProperty.create("base_direction", Direction.UP, Direction.DOWN);

    public SteamAdmissionValveControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BASE_DIRECTION, Direction.DOWN));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        BlockState baseState = getOwningValve(level, pos, state);
        return baseState == null ? Shapes.empty() : SteamAdmissionValveBlock.getControllerShape(baseState);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == state.getValue(BASE_DIRECTION)
                && !(neighborState.getBlock() instanceof SteamAdmissionValveBlock)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    public static BlockPos getBasePos(BlockPos controllerPos, BlockState controllerState) {
        return SteamAdmissionValveController.basePos(controllerPos, controllerState);
    }

    public static BlockState getOwningValve(
            BlockGetter level,
            BlockPos controllerPos,
            BlockState controllerState
    ) {
        BlockPos basePos = getBasePos(controllerPos, controllerState);
        BlockState baseState = level.getBlockState(basePos);
        if (!(baseState.getBlock() instanceof SteamAdmissionValveBlock)
                || !SteamAdmissionValveController.controllerPos(basePos, baseState).equals(controllerPos)) {
            return null;
        }
        return baseState;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BASE_DIRECTION);
    }
}
