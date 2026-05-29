package dev.gustavo.fullsteamahead.content.cylinder;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SteamCylinderBlock extends Block implements IBE<SteamCylinderBlockEntity> {
    public static final MapCodec<SteamCylinderBlock> CODEC = simpleCodec(SteamCylinderBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final EnumProperty<CylinderSection> SECTION = EnumProperty.create("section", CylinderSection.class);
    public static final EnumProperty<CylinderWallShape> WALL_SHAPE =
            EnumProperty.create("wall_shape", CylinderWallShape.class);
    private static final VoxelShape STANDALONE_SHAPE = Shapes.or(
            Block.box(4, 0, 0, 12, 15, 16),
            Block.box(5, 15, 0, 11, 16, 16)
    );
    private static final VoxelShape STRAIGHT_X_SHAPE = Shapes.or(
            Block.box(0, 0, 4, 16, 15, 12),
            Block.box(0, 15, 5, 16, 16, 11)
    );

    public SteamCylinderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ASSEMBLED, false)
                .setValue(SECTION, CylinderSection.NONE)
                .setValue(WALL_SHAPE, CylinderWallShape.STANDALONE));
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
        if (blockEntity instanceof SteamCylinderBlockEntity cylinder && cylinder.isCylinderAssembled()) {
            BlockPos ringOrigin = cylinder.getRingOrigin();
            return ringOrigin != null && isInsideTrackedStructure(ringOrigin, neighborPos);
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.is(ModBlocks.STEAM_CYLINDER.get())
                || neighborState.is(ModBlocks.STEAM_INLET.get())
                || neighborState.is(ModBlocks.PISTON.get())) {
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
        if (state.getValue(SECTION) != CylinderSection.NONE) {
            return CylinderRingShapes.forSection(state.getValue(SECTION));
        }

        return state.getValue(WALL_SHAPE) == CylinderWallShape.STRAIGHT_X ? STRAIGHT_X_SHAPE : STANDALONE_SHAPE;
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
        builder.add(ASSEMBLED, SECTION, WALL_SHAPE);
    }

    @Override
    public Class<SteamCylinderBlockEntity> getBlockEntityClass() {
        return SteamCylinderBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamCylinderBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STEAM_CYLINDER.get();
    }
}
