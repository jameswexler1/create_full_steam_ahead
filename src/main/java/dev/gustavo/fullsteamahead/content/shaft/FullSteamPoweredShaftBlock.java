package dev.gustavo.fullsteamahead.content.shaft;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import dev.gustavo.fullsteamahead.content.piston.EngineValidator;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FullSteamPoweredShaftBlock extends PoweredShaftBlock {
    public static final MapCodec<FullSteamPoweredShaftBlock> CODEC = simpleCodec(FullSteamPoweredShaftBlock::new);

    public FullSteamPoweredShaftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntityType<? extends FullSteamPoweredShaftBlockEntity> getBlockEntityType() {
        return ModBlockEntities.POWERED_SHAFT.get();
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return AllShapes.EIGHT_VOXEL_POLE.get(state.getValue(AXIS));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
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
        if (!stillValid(state, level, pos)) {
            level.setBlock(pos, asRegularShaft(state), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return stillValid(state, level, pos);
    }

    @Override
    public ItemStack getCloneItemStack(
            BlockState state,
            HitResult target,
            LevelReader level,
            BlockPos pos,
            Player player
    ) {
        return AllBlocks.SHAFT.asStack();
    }

    public static boolean stillValid(BlockState state, LevelReader level, BlockPos shaftPos) {
        int maxDistance = EngineValidator.shaftDistanceForPistonBodies(
                EngineValidator.MAX_PISTON_BODIES,
                EngineValidator.MAX_SHAFT_GAP
        );
        for (int dy = -maxDistance; dy <= maxDistance; dy++) {
            BlockPos headPos = shaftPos.offset(0, dy, 0);
            if (level instanceof Level loadedLevel && !loadedLevel.isLoaded(headPos)) {
                continue;
            }
            if (!level.getBlockState(headPos).is(ModBlocks.PISTON_HEAD.get())) {
                continue;
            }
            if (level instanceof Level loadedLevel) {
                EngineValidator.Result result = EngineValidator.validate(loadedLevel, headPos);
                if (result.valid() && shaftPos.equals(result.shaft())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isPoweredShaft(BlockState state) {
        return state.is(ModBlocks.POWERED_SHAFT.get())
                || state.is(ModBlocks.POWERED_GIRDER_ENCASED_SHAFT.get());
    }

    public static boolean isClaimableShaft(BlockState state) {
        return AllBlocks.SHAFT.has(state) || AllBlocks.METAL_GIRDER_ENCASED_SHAFT.has(state);
    }

    public static boolean isRecognizedShaft(BlockState state) {
        return isClaimableShaft(state) || isPoweredShaft(state);
    }

    public static Direction.Axis axisOf(BlockState state) {
        if (state.getBlock() instanceof AbstractShaftBlock shaft) {
            return shaft.getRotationAxis(state);
        }
        if (state.hasProperty(HorizontalAxisKineticBlock.HORIZONTAL_AXIS)) {
            return state.getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS);
        }
        return Direction.Axis.X;
    }

    public static BlockState equivalentOf(BlockState shaftState) {
        if (AllBlocks.METAL_GIRDER_ENCASED_SHAFT.has(shaftState)) {
            return FullSteamPoweredGirderEncasedShaftBlock.equivalentOf(shaftState);
        }
        return ModBlocks.POWERED_SHAFT.get()
                .defaultBlockState()
                .setValue(AXIS, shaftState.getValue(ShaftBlock.AXIS))
                .setValue(WATERLOGGED, shaftState.getValue(WATERLOGGED));
    }

    public static BlockState asRegularShaft(BlockState poweredState) {
        if (poweredState.is(ModBlocks.POWERED_GIRDER_ENCASED_SHAFT.get())) {
            return FullSteamPoweredGirderEncasedShaftBlock.asRegularShaft(poweredState);
        }
        return AllBlocks.SHAFT.getDefaultState()
                .setValue(ShaftBlock.AXIS, poweredState.getValue(AXIS))
                .setValue(WATERLOGGED, poweredState.getValue(WATERLOGGED));
    }
}
