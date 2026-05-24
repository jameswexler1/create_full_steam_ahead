package dev.gustavo.fullsteamahead.content.telegraph;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EngineTelegraphBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<EngineTelegraphBlock> CODEC =
            simpleCodec(EngineTelegraphBlock::new);

    private static final Box[] NORTH_BOXES = {
            new Box(4, 0, 3, 12, 1, 12),
            new Box(7, 1, 6, 9, 5, 9),
            new Box(5, 5, 4, 11, 6, 11),
            new Box(5, 6, 10, 11, 7, 13),
            new Box(5, 6, 2, 11, 7, 5),
            new Box(5, 7, 13, 11, 9, 14),
            new Box(5, 7, 1, 11, 9, 2),
            new Box(5, 8, 0, 11, 14, 1),
            new Box(5, 8, 14, 11, 14, 15),
            new Box(5, 13, 1, 11, 15, 2),
            new Box(5, 13, 13, 11, 15, 14),
            new Box(5, 15, 2, 11, 16, 5),
            new Box(5, 15, 10, 11, 16, 13),
            new Box(5, 16, 4, 11, 17, 11),
            new Box(6, 6, 5, 10, 16, 10),
            new Box(6, 7, 2, 10, 15, 5),
            new Box(6, 7, 10, 10, 15, 13),
            new Box(6, 9, 1, 10, 13, 2),
            new Box(6, 9, 13, 10, 13, 14),
            new Box(4, 9, 6, 6, 10, 8),
            new Box(10, 9, 6, 12, 10, 8),
            new Box(4, 10, 8, 6, 13, 9),
            new Box(4, 13, 9, 5, 17, 10),
            new Box(4, 18, 6, 5, 22, 8),
            new Box(11, 18, 6, 12, 22, 8),
            new Box(4, 13, 4, 5, 17, 5),
            new Box(4, 17, 5, 5, 18, 9),
            new Box(4, 10, 5, 6, 13, 6),
            new Box(11, 17, 5, 12, 18, 9),
            new Box(11, 13, 4, 12, 17, 5),
            new Box(10, 10, 5, 12, 13, 6),
            new Box(11, 13, 9, 12, 17, 10),
            new Box(10, 10, 8, 12, 13, 9)
    };

    private static final VoxelShape SHAPE_NORTH = createShape(Direction.NORTH);
    private static final VoxelShape SHAPE_SOUTH = createShape(Direction.SOUTH);
    private static final VoxelShape SHAPE_EAST = createShape(Direction.EAST);
    private static final VoxelShape SHAPE_WEST = createShape(Direction.WEST);

    public EngineTelegraphBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Faces the player when placed
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level,
                               BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
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

    private static VoxelShape createShape(Direction facing) {
        VoxelShape shape = Shapes.empty();
        for (Box box : NORTH_BOXES) {
            shape = Shapes.or(shape, rotateBox(box, facing));
        }
        return shape.optimize();
    }

    private static VoxelShape rotateBox(Box box, Direction facing) {
        return switch (facing) {
            case SOUTH -> Block.box(
                    16 - box.maxX, box.minY, 16 - box.maxZ,
                    16 - box.minX, box.maxY, 16 - box.minZ
            );
            case EAST -> Block.box(
                    16 - box.maxZ, box.minY, box.minX,
                    16 - box.minZ, box.maxY, box.maxX
            );
            case WEST -> Block.box(
                    box.minZ, box.minY, 16 - box.maxX,
                    box.maxZ, box.maxY, 16 - box.minX
            );
            default -> Block.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        };
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }
}
