package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderConnectivity;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderRingShapes;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderSection;
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
import net.minecraft.world.phys.shapes.VoxelShape;

public class SteamInletBlock extends Block implements IBE<SteamInletBlockEntity> {
    public static final MapCodec<SteamInletBlock> CODEC = simpleCodec(SteamInletBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final EnumProperty<CylinderSection> SECTION = EnumProperty.create("section", CylinderSection.class);

    public SteamInletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ASSEMBLED, false)
                .setValue(SECTION, CylinderSection.NONE));
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
        return state.getValue(SECTION) != CylinderSection.NONE
                ? CylinderRingShapes.forSection(state.getValue(SECTION))
                : super.getShape(state, level, pos, context);
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
        builder.add(ASSEMBLED, SECTION);
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
