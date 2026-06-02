package dev.gustavo.fullsteamahead.content.piston;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.content.common.FullSteamWrenchable;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderConnectivity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PistonHeadBlock extends Block implements IBE<PistonHeadBlockEntity>, FullSteamWrenchable {
    public static final MapCodec<PistonHeadBlock> CODEC = simpleCodec(PistonHeadBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.UP, Direction.DOWN);
    private static final VoxelShape UP_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(2, 3, 2, 14, 5, 14),
            Block.box(4, 5, 4, 12, 7, 12),
            Block.box(5, 7, 5, 11, 16, 11)
    );
    private static final VoxelShape DOWN_SHAPE = Shapes.or(
            Block.box(0, 13, 0, 16, 16, 16),
            Block.box(2, 11, 2, 14, 13, 14),
            Block.box(4, 9, 4, 12, 11, 12),
            Block.box(5, 0, 5, 11, 9, 11)
    );

    public PistonHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ASSEMBLED, false)
                .setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing;
        if (FullSteamWrenchable.isPlacingShifted(context)) {
            // Sneaking flips the placement orientation and ignores neighbour auto-connect, matching Create.
            facing = placementFacing(context).getOpposite();
        } else {
            Direction inferredFacing = adjacentPistonFacing(context.getLevel(), context.getClickedPos());
            facing = inferredFacing == null ? placementFacing(context) : inferredFacing;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return state.setValue(FACING, state.getValue(FACING).getOpposite());
    }

    @Override
    public void onAfterWrench(Level level, BlockPos pos) {
        CylinderConnectivity.refreshFrom(level, pos);
        withBlockEntityDo(level, pos, PistonHeadBlockEntity::revalidateStructure);
        PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
    }

    public static Direction placementFacing(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.DOWN) {
            return Direction.DOWN;
        }
        if (clickedFace != Direction.UP && context.getClickLocation().y - context.getClickedPos().getY() > 0.5D) {
            return Direction.DOWN;
        }
        return Direction.UP;
    }

    private static Direction adjacentPistonFacing(Level level, BlockPos pos) {
        if (level.isLoaded(pos.below()) && level.getBlockState(pos.below()).is(ModBlocks.PISTON.get())) {
            return Direction.DOWN;
        }
        if (level.isLoaded(pos.above()) && level.getBlockState(pos.above()).is(ModBlocks.PISTON.get())) {
            return Direction.UP;
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return state.getValue(FACING) == Direction.DOWN ? DOWN_SHAPE : UP_SHAPE;
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            CylinderConnectivity.refreshFrom(level, pos);
            withBlockEntityDo(level, pos, PistonHeadBlockEntity::revalidateStructure);
            PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                withBlockEntityDo(level, pos, PistonHeadBlockEntity::clearAssembly);
                CylinderConnectivity.refreshFromRemoval(level, pos);
            }
            IBE.onRemove(state, level, pos, newState);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED, FACING);
    }

    @Override
    public Class<PistonHeadBlockEntity> getBlockEntityClass() {
        return PistonHeadBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PistonHeadBlockEntity> getBlockEntityType() {
        return ModBlockEntities.PISTON_HEAD.get();
    }
}
