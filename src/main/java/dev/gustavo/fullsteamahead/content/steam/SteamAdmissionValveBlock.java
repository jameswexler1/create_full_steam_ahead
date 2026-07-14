package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class SteamAdmissionValveBlock extends FluidPipeBlock {
    public static final MapCodec<SteamAdmissionValveBlock> CODEC = simpleCodec(SteamAdmissionValveBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<SteamAdmissionValveMode> MODE =
            EnumProperty.create("mode", SteamAdmissionValveMode.class);

    private static final Box[] BASE_BOXES = new Box[] {
            new Box(4, 3, 4, 12, 13, 12),
            new Box(3, 4, 4, 4, 12, 12),
            new Box(12, 4, 4, 13, 12, 12),
            new Box(4, 4, 3, 12, 12, 4),
            new Box(4, 4, 12, 12, 12, 13),
            new Box(4, 4, 0, 12, 12, 3),
            new Box(3, 3, 0, 13, 13, 2),
            new Box(3.5, 3.5, 2, 12.5, 12.5, 3),
            new Box(6.5, 11, 1.5, 9.5, 13, 3.5),
            new Box(3.25, 12.5, 2.2, 6.5, 12.75, 2.8),
            new Box(9.5, 12.5, 2.2, 12.75, 12.75, 2.8),
            new Box(3.25, 3.25, 2.2, 12.75, 3.5, 2.8),
            new Box(3.25, 3.5, 2.2, 3.5, 12.5, 2.8),
            new Box(12.5, 3.5, 2.2, 12.75, 12.5, 2.8),
            new Box(4.5, 13, 5, 11.5, 14, 11),
            new Box(3, 14, 4, 13, 15, 12),
            new Box(3.75, 15, 5, 7.25, 15.25, 9),
            new Box(8.75, 15, 5, 12.25, 15.25, 9),
            new Box(4.75, 15.25, 9.25, 6.25, 15.5, 9.5),
            new Box(9.75, 15.25, 9.25, 11.25, 15.5, 9.5),
            new Box(7, 12, 1, 9, 14, 3),
            new Box(6.5, 13.5, 0.75, 9.5, 14.5, 3.25),
            new Box(7.25, 12.5, 0.75, 8.75, 13.5, 1)
    };
    private static final Box[] STRAIGHT_BOXES = new Box[] {
            new Box(4, 4, 13, 12, 12, 16),
            new Box(3, 3, 14, 13, 13, 16)
    };
    private static final Box[] CLOCKWISE_BOXES = new Box[] {
            new Box(13, 4, 4, 16, 12, 12),
            new Box(14, 3, 3, 16, 13, 13)
    };
    private static final Box[] COUNTERCLOCKWISE_BOXES = new Box[] {
            new Box(0, 4, 4, 3, 12, 12),
            new Box(0, 3, 3, 2, 13, 13)
    };
    private static final Box[] THROUGH_BOXES = new Box[] {
            CLOCKWISE_BOXES[0],
            CLOCKWISE_BOXES[1],
            COUNTERCLOCKWISE_BOXES[0],
            COUNTERCLOCKWISE_BOXES[1]
    };
    private static final Map<SteamAdmissionValveMode, Map<Direction, VoxelShape>> SHAPES = buildShapes();

    public SteamAdmissionValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(MODE, SteamAdmissionValveMode.UNLINKED));
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }

        state = state.setValue(FACING, context.getHorizontalDirection().getOpposite());
        return resolveTopology(context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        return resolveTopology(level, pos, super.updateShape(state, direction, neighborState, level, pos, neighborPos));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = super.rotate(state, rotation);
        return rotated.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = rotate(state, mirror.getRotation(state.getValue(FACING)));
        if (mirror != Mirror.NONE) {
            mirrored = mirrored.setValue(MODE, mirrored.getValue(MODE).mirrored());
        }
        return mirrored;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getValveShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getValveShape(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, MODE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Class<FluidPipeBlockEntity> getBlockEntityClass() {
        return (Class) SteamAdmissionValveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STEAM_ADMISSION_VALVE.get();
    }

    private static BlockState resolveTopology(BlockGetter level, BlockPos pos, BlockState state) {
        Direction inletDirection = null;
        int inletCount = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(direction)).is(ModBlocks.STEAM_INLET.get())) {
                inletDirection = direction;
                inletCount++;
            }
        }

        if (inletCount != 1) {
            return state.setValue(MODE, SteamAdmissionValveMode.UNLINKED);
        }

        Direction facing = inletDirection;
        Direction back = facing.getOpposite();
        Direction clockwise = facing.getClockWise();
        Direction counterClockwise = facing.getCounterClockWise();
        boolean hasBack = hasPipe(level, pos, back);
        boolean hasClockwise = hasPipe(level, pos, clockwise);
        boolean hasCounterClockwise = hasPipe(level, pos, counterClockwise);

        SteamAdmissionValveMode mode = SteamAdmissionValveMode.UNLINKED;
        if (hasClockwise && hasCounterClockwise && !hasBack) {
            mode = SteamAdmissionValveMode.THROUGH_BRANCH;
        } else if (hasBack && !hasClockwise && !hasCounterClockwise) {
            mode = SteamAdmissionValveMode.TERMINAL_STRAIGHT;
        } else if (!hasBack && hasClockwise && !hasCounterClockwise) {
            mode = SteamAdmissionValveMode.TERMINAL_CLOCKWISE;
        } else if (!hasBack && !hasClockwise && hasCounterClockwise) {
            mode = SteamAdmissionValveMode.TERMINAL_COUNTERCLOCKWISE;
        }

        return state.setValue(FACING, facing).setValue(MODE, mode);
    }

    private static boolean hasPipe(BlockGetter level, BlockPos pos, Direction direction) {
        return FluidPipeBlock.isPipe(level.getBlockState(pos.relative(direction)));
    }

    private static VoxelShape getValveShape(BlockState state) {
        return SHAPES.get(state.getValue(MODE)).get(state.getValue(FACING));
    }

    private static Map<SteamAdmissionValveMode, Map<Direction, VoxelShape>> buildShapes() {
        Map<SteamAdmissionValveMode, Map<Direction, VoxelShape>> shapes =
                new EnumMap<>(SteamAdmissionValveMode.class);
        for (SteamAdmissionValveMode mode : SteamAdmissionValveMode.values()) {
            Map<Direction, VoxelShape> byDirection = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = Shapes.empty();
                for (Box box : BASE_BOXES) {
                    shape = Shapes.or(shape, rotate(box, direction).shape());
                }
                for (Box box : boxesFor(mode)) {
                    shape = Shapes.or(shape, rotate(box, direction).shape());
                }
                byDirection.put(direction, shape.optimize());
            }
            shapes.put(mode, byDirection);
        }
        return shapes;
    }

    private static Box[] boxesFor(SteamAdmissionValveMode mode) {
        return switch (mode) {
            case TERMINAL_STRAIGHT -> STRAIGHT_BOXES;
            case TERMINAL_CLOCKWISE -> CLOCKWISE_BOXES;
            case TERMINAL_COUNTERCLOCKWISE -> COUNTERCLOCKWISE_BOXES;
            case THROUGH_BRANCH -> THROUGH_BOXES;
            case UNLINKED -> new Box[0];
        };
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

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private VoxelShape shape() {
            return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
