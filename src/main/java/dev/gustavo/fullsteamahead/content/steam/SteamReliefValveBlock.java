package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.content.common.FullSteamWrenchable;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class SteamReliefValveBlock extends Block implements IBE<SteamReliefValveBlockEntity>, FullSteamWrenchable {
    public static final MapCodec<SteamReliefValveBlock> CODEC = simpleCodec(SteamReliefValveBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final DirectionProperty ATTACHED_FACE =
            DirectionProperty.create("attached_face", direction -> direction != Direction.DOWN);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final Box[] NORTH_BOXES = new Box[] {
            new Box(2, 0, 2, 14, 2, 14),
            new Box(5, 1.5, 5, 11, 4.5, 11),
            new Box(4, 4, 4, 12, 11, 12),
            new Box(5, 10.75, 5, 11, 12.75, 11),
            new Box(3.5, 13, 3.5, 12.5, 15, 12.5),
            new Box(7, 14.5, 7, 9, 18, 9),
            new Box(6.5, 17.5, 6.5, 9.5, 18.5, 9.5),
            new Box(5, 4.5, 1.25, 6, 10.5, 2.25),
            new Box(10, 4.5, 1.25, 11, 10.5, 2.25),
            new Box(6, 9.5, 1.25, 10, 10.5, 2.25),
            new Box(6, 4.5, 1.25, 10, 5.5, 2.25),
            new Box(5.5, 7, 1.4, 10.5, 8, 2.1),
            new Box(7.5, 5, 1.4, 8.5, 10, 2.1),
            new Box(7.25, 6.75, 1.25, 8.75, 8.25, 4.5)
    };
    private static final Map<Direction, VoxelShape> TOP_SHAPES = buildTopShapes();
    private static final Map<Direction, VoxelShape> SIDE_SHAPES = buildSideShapes();

    public SteamReliefValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ATTACHED_FACE, Direction.UP)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction attachedFace = context.getClickedFace();
        if (attachedFace == Direction.DOWN) {
            return null;
        }

        Direction facing = FullSteamWrenchable.flipIfShifted(
                context,
                context.getHorizontalDirection().getOpposite()
        );
        return defaultBlockState()
                .setValue(FACING, normalizeVisualFacing(facing, attachedFace))
                .setValue(ATTACHED_FACE, attachedFace)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockEntity(getAttachedTankPos(pos, state)) instanceof FluidTankBlockEntity;
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        Direction attachedFace = getAttachedFace(state);
        Direction facing = state.getValue(FACING);
        do {
            facing = facing.getClockWise();
        } while (!canUseVisualFacing(facing, attachedFace));
        return state.setValue(FACING, facing);
    }

    @Override
    public void onAfterWrench(Level level, BlockPos pos) {
        withBlockEntityDo(level, pos, SteamReliefValveBlockEntity::refreshBoilerState);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            withBlockEntityDo(level, pos, SteamReliefValveBlockEntity::refreshBoilerState);
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
        if (level.isClientSide()) {
            return;
        }
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }

        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
        if (neighborPos.equals(getAttachedTankPos(pos, state))) {
            withBlockEntityDo(level, pos, SteamReliefValveBlockEntity::refreshBoilerState);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                SteamNetworkManager.unregisterReliefValve(level, pos);
                withBlockEntityDo(level, pos, SteamReliefValveBlockEntity::clearBoilerState);
            }
            IBE.onRemove(state, level, pos, newState);
        }
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getInteractionShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getInteractionShape(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ATTACHED_FACE, POWERED);
    }

    public static Direction getFacing(BlockState state) {
        return state.getValue(FACING);
    }

    public static Direction getAttachedFace(BlockState state) {
        return state.hasProperty(ATTACHED_FACE) ? state.getValue(ATTACHED_FACE) : Direction.UP;
    }

    public static BlockPos getAttachedTankPos(BlockPos pos, BlockState state) {
        return pos.relative(getAttachedFace(state).getOpposite());
    }

    private static VoxelShape getInteractionShape(BlockState state) {
        // The authored model's visible front is opposite the logical horizontal facing used by
        // blockstate/model rotation. Mirror the model-derived hitbox to match the rendered valve.
        Direction attachedFace = getAttachedFace(state);
        if (attachedFace == Direction.UP) {
            return TOP_SHAPES.get(state.getValue(FACING).getOpposite());
        }
        return SIDE_SHAPES.get(attachedFace);
    }

    @Override
    public Class<SteamReliefValveBlockEntity> getBlockEntityClass() {
        return SteamReliefValveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamReliefValveBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STEAM_RELIEF_VALVE.get();
    }

    private static Map<Direction, VoxelShape> buildTopShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (Box box : NORTH_BOXES) {
                shape = Shapes.or(shape, rotate(box, direction).shape());
            }
            shapes.put(direction, shape.optimize());
        }
        return shapes;
    }

    private static Map<Direction, VoxelShape> buildSideShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (Box box : NORTH_BOXES) {
                shape = Shapes.or(shape, rotate(tiltUpToNorthWithHandleUp(box), direction).shape());
            }
            shapes.put(direction, shape.optimize());
        }
        return shapes;
    }

    private static Box rotate(Box box, Direction facing) {
        return switch (facing) {
            case SOUTH -> new Box(16 - box.maxX, box.minY, 16 - box.maxZ,
                    16 - box.minX, box.maxY, 16 - box.minZ);
            case EAST -> new Box(16 - box.maxZ, box.minY, box.minX,
                    16 - box.minZ, box.maxY, box.maxX);
            case WEST -> new Box(box.minZ, box.minY, 16 - box.maxX,
                    box.maxZ, box.maxY, 16 - box.minX);
            default -> box;
        };
    }

    private static Box tiltUpToNorthWithHandleUp(Box box) {
        return new Box(box.minX, 16 - box.maxZ, 16 - box.maxY,
                box.maxX, 16 - box.minZ, 16 - box.minY);
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private VoxelShape shape() {
            return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static boolean canUseVisualFacing(Direction facing, Direction attachedFace) {
        return attachedFace == Direction.UP || facing.getAxis() != attachedFace.getAxis();
    }

    private static Direction normalizeVisualFacing(Direction facing, Direction attachedFace) {
        if (facing.getAxis().isVertical()) {
            facing = Direction.NORTH;
        }
        if (canUseVisualFacing(facing, attachedFace)) {
            return facing;
        }
        return attachedFace.getClockWise();
    }
}
