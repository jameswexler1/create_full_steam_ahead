package dev.gustavo.fullsteamahead.content.cylinder;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class SteamCylinderBlock extends Block implements IBE<SteamCylinderBlockEntity> {
    public static final MapCodec<SteamCylinderBlock> CODEC = simpleCodec(SteamCylinderBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    public SteamCylinderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ASSEMBLED, false));
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
        if (!level.isClientSide()) {
            CylinderConnectivity.refreshFrom(level, pos);
        }
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED);
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
