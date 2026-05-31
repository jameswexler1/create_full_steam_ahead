package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class BoilerOutletBlock extends Block implements IBE<BoilerOutletBlockEntity> {
    public static final MapCodec<BoilerOutletBlock> CODEC = simpleCodec(BoilerOutletBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final Box[] NORTH_BOXES = new Box[] {
            new Box(2, 2, 5, 14, 14, 7),
            new Box(3, 3, 3, 13, 13, 5),
            new Box(4, 4, 0, 12, 12, 3),
            new Box(0, 0, 7, 16, 16, 16)
    };
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

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
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPES.get(state.getValue(FACING));
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
        if (!level.isClientSide() && getAttachedTankPos(pos, state).equals(neighborPos)) {
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

    public static BlockPos getOutputPipePos(BlockPos outletPos, BlockState state) {
        return outletPos.relative(getFacing(state));
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

    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (Box box : NORTH_BOXES) {
                shape = Shapes.or(shape, rotate(box, direction).shape());
            }
            shapes.put(direction, shape.optimize());
        }
        return shapes;
    }

    private static Box rotate(Box box, Direction facing) {
        return switch (facing) {
            case NORTH -> box;
            case SOUTH -> new Box(16 - box.maxX, box.minY, 16 - box.maxZ,
                    16 - box.minX, box.maxY, 16 - box.minZ);
            case EAST -> new Box(16 - box.maxZ, box.minY, box.minX,
                    16 - box.minZ, box.maxY, box.maxX);
            case WEST -> new Box(box.minZ, box.minY, 16 - box.maxX,
                    box.maxZ, box.maxY, 16 - box.minX);
            case UP -> new Box(box.minX, 16 - box.maxZ, box.minY,
                    box.maxX, 16 - box.minZ, box.maxY);
            case DOWN -> new Box(box.minX, box.minZ, 16 - box.maxY,
                    box.maxX, box.maxZ, 16 - box.minY);
        };
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private VoxelShape shape() {
            return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
