package dev.gustavo.fullsteamahead.content.redstone;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public class SteppedLeverBlock extends FaceAttachedHorizontalDirectionalBlock implements IBE<SteppedLeverBlockEntity> {
    public static final MapCodec<SteppedLeverBlock> CODEC = simpleCodec(SteppedLeverBlock::new);

    private static final VoxelShape FLOOR_Z = Block.box(3, 0, 0, 13, 12, 16);
    private static final VoxelShape FLOOR_X = Block.box(0, 0, 3, 16, 12, 13);
    private static final VoxelShape CEILING_Z = Block.box(3, 4, 0, 13, 16, 16);
    private static final VoxelShape CEILING_X = Block.box(0, 4, 3, 16, 16, 13);
    private static final VoxelShape WALL_NORTH = Block.box(3, 0, 4, 13, 16, 16);
    private static final VoxelShape WALL_SOUTH = Block.box(3, 0, 0, 13, 16, 12);
    private static final VoxelShape WALL_EAST = Block.box(0, 0, 3, 12, 16, 13);
    private static final VoxelShape WALL_WEST = Block.box(4, 0, 3, 16, 16, 13);

    public SteppedLeverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected @NotNull MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            addParticles(state, level, pos, 1.0F);
            return InteractionResult.SUCCESS;
        }

        return onBlockEntityUse(level, pos, lever -> {
            lever.changeState(player.isShiftKeyDown());
            float pitch = 0.25F + ((lever.getState() + 5) / 15.0F) * 0.5F;
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.2F, pitch);
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getBlockEntityOptional(level, pos)
                .map(SteppedLeverBlockEntity::getState)
                .orElse(0);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getConnectedDirection(state) == side ? getSignal(state, level, pos, side) : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        withBlockEntityDo(level, pos, lever -> {
            if (lever.getState() != 0 && random.nextFloat() < 0.25F) {
                addParticles(state, level, pos, 0.5F);
            }
        });
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                withBlockEntityDo(level, pos, lever -> {
                    if (lever.getState() != 0) {
                        updateNeighbors(state, level, pos);
                    }
                });
            }
            IBE.onRemove(state, level, pos, newState);
        }
    }

    static void updateNeighbors(BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), state.getBlock());
    }

    private static void addParticles(BlockState state, LevelAccessor level, BlockPos pos, float alpha) {
        Direction facingDirection = state.getValue(FACING).getOpposite();
        Direction connectedDirection = getConnectedDirection(state).getOpposite();
        double x = pos.getX() + 0.5D + 0.1D * facingDirection.getStepX() + 0.2D * connectedDirection.getStepX();
        double y = pos.getY() + 0.5D + 0.1D * facingDirection.getStepY() + 0.2D * connectedDirection.getStepY();
        double z = pos.getZ() + 0.5D + 0.1D * facingDirection.getStepZ() + 0.2D * connectedDirection.getStepZ();
        level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), alpha), x, y, z, 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected @NotNull VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        AttachFace face = state.getValue(FACE);
        Direction facing = state.getValue(FACING);

        if (face == AttachFace.FLOOR) {
            return facing.getAxis() == Direction.Axis.X ? FLOOR_X : FLOOR_Z;
        }
        if (face == AttachFace.CEILING) {
            return facing.getAxis() == Direction.Axis.X ? CEILING_X : CEILING_Z;
        }

        return switch (facing) {
            case SOUTH -> WALL_SOUTH;
            case EAST -> WALL_EAST;
            case WEST -> WALL_WEST;
            default -> WALL_NORTH;
        };
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Class<SteppedLeverBlockEntity> getBlockEntityClass() {
        return SteppedLeverBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteppedLeverBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STEPPED_LEVER.get();
    }
}
