package dev.gustavo.fullsteamahead.content.shaft;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;

public class FullSteamPoweredGirderEncasedShaftBlock extends GirderEncasedShaftBlock {
    public FullSteamPoweredGirderEncasedShaftBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return ModBlockEntities.POWERED_SHAFT.get();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!FullSteamPoweredShaftBlock.stillValid(state, level, pos)) {
            level.setBlock(pos, asRegularShaft(state), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return FullSteamPoweredShaftBlock.stillValid(state, level, pos);
    }

    @Override
    public ItemStack getCloneItemStack(
            BlockState state,
            HitResult target,
            LevelReader level,
            BlockPos pos,
            Player player
    ) {
        return AllBlocks.METAL_GIRDER.asStack();
    }

    public static BlockState equivalentOf(BlockState shaftState) {
        return ModBlocks.POWERED_GIRDER_ENCASED_SHAFT.get()
                .defaultBlockState()
                .setValue(HORIZONTAL_AXIS, shaftState.getValue(HORIZONTAL_AXIS))
                .setValue(TOP, shaftState.getValue(TOP))
                .setValue(BOTTOM, shaftState.getValue(BOTTOM))
                .setValue(BlockStateProperties.WATERLOGGED, shaftState.getValue(BlockStateProperties.WATERLOGGED));
    }

    public static BlockState asRegularShaft(BlockState poweredState) {
        return AllBlocks.METAL_GIRDER_ENCASED_SHAFT.getDefaultState()
                .setValue(HORIZONTAL_AXIS, poweredState.getValue(HORIZONTAL_AXIS))
                .setValue(TOP, poweredState.getValue(TOP))
                .setValue(BOTTOM, poweredState.getValue(BOTTOM))
                .setValue(BlockStateProperties.WATERLOGGED, poweredState.getValue(BlockStateProperties.WATERLOGGED));
    }
}
