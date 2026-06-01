package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderConnectivity;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderSection;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderWallShape;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SteamInletBlock extends Block implements IBE<SteamInletBlockEntity> {
    public static final MapCodec<SteamInletBlock> CODEC = simpleCodec(SteamInletBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final EnumProperty<CylinderSection> SECTION = EnumProperty.create("section", CylinderSection.class);
    public static final EnumProperty<CylinderWallShape> WALL_SHAPE =
            EnumProperty.create("wall_shape", CylinderWallShape.class);
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.UP, Direction.DOWN);
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(0, 0, 7, 16, 16, 16),
            Block.box(2, 2, 5, 14, 14, 7),
            Block.box(3, 3, 3, 13, 13, 5),
            Block.box(4, 4, 0, 12, 12, 3)
    );
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 9, 16, 16),
            Block.box(9, 2, 2, 11, 14, 14),
            Block.box(11, 3, 3, 13, 13, 13),
            Block.box(13, 4, 4, 16, 12, 12)
    );
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 16, 9),
            Block.box(2, 2, 9, 14, 14, 11),
            Block.box(3, 3, 11, 13, 13, 13),
            Block.box(4, 4, 13, 12, 12, 16)
    );
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(7, 0, 0, 16, 16, 16),
            Block.box(5, 2, 2, 7, 14, 14),
            Block.box(3, 3, 3, 5, 13, 13),
            Block.box(0, 4, 4, 3, 12, 12)
    );

    public SteamInletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ASSEMBLED, false)
                .setValue(SECTION, CylinderSection.NONE)
                .setValue(WALL_SHAPE, CylinderWallShape.STANDALONE)
                .setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            CylinderConnectivity.refreshFrom(level, pos);
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
        if (!level.isClientSide() && shouldRefreshFromNeighbor(level, pos, neighborPos)) {
            CylinderConnectivity.refreshFrom(level, pos);
        }
    }

    private boolean shouldRefreshFromNeighbor(Level level, BlockPos pos, BlockPos neighborPos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamInletBlockEntity inlet && inlet.isInletAssembled()) {
            BlockPos ringOrigin = inlet.getRingOrigin();
            return ringOrigin != null && isInsideTrackedStructure(ringOrigin, neighborPos);
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.is(ModBlocks.STEAM_CYLINDER.get())
                || neighborState.is(ModBlocks.STEAM_INLET.get())
                || neighborState.is(ModBlocks.PISTON.get())
                || neighborState.is(ModBlocks.PISTON_HEAD.get())) {
            return true;
        }

        return level.getBlockEntity(neighborPos) instanceof FluidTankBlockEntity;
    }

    private boolean isInsideTrackedStructure(BlockPos ringOrigin, BlockPos neighborPos) {
        int dx = neighborPos.getX() - ringOrigin.getX();
        int dy = neighborPos.getY() - ringOrigin.getY();
        int dz = neighborPos.getZ() - ringOrigin.getZ();

        if (dx < 0 || dx > 2 || dz < 0 || dz > 2) {
            return false;
        }
        return dy >= -1 && dy <= 1;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                CylinderConnectivity.refreshFromRemoval(level, pos);
            }
            IBE.onRemove(state, level, pos, newState);
        }
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            net.minecraft.world.level.BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeForSection(state.getValue(SECTION));
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            net.minecraft.world.level.BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED, SECTION, WALL_SHAPE, FACING);
    }

    private VoxelShape shapeForSection(CylinderSection section) {
        return switch (facingForSection(section)) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    private Direction facingForSection(CylinderSection section) {
        if (section == null || section == CylinderSection.NONE) {
            return Direction.NORTH;
        }
        if (section.zOffset() == 0) {
            return Direction.NORTH;
        }
        if (section.zOffset() == 2) {
            return Direction.SOUTH;
        }
        return section.xOffset() == 0 ? Direction.WEST : Direction.EAST;
    }

    @Override
    public Class<SteamInletBlockEntity> getBlockEntityClass() {
        return SteamInletBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamInletBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STEAM_INLET.get();
    }
}
