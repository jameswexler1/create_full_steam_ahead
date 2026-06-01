package dev.gustavo.fullsteamahead.content.piston;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlock;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Predicate;

public class SteamPistonBlock extends Block {
    public static final MapCodec<SteamPistonBlock> CODEC = simpleCodec(SteamPistonBlock::new);
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final EnumProperty<PistonSection> PISTON_SECTION =
            EnumProperty.create("piston_section", PistonSection.class);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.UP, Direction.DOWN);
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new ShaftPlacementHelper());
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

    public SteamPistonBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ASSEMBLED, false)
                .setValue(PISTON_SECTION, PistonSection.INSIDE_LOW)
                .setValue(AXIS, Direction.Axis.X)
                .setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = adjacentPistonHeadFacing(context.getLevel(), context.getClickedPos());
        if (facing == null) {
            facing = PistonHeadBlock.placementFacing(context);
        }
        return defaultBlockState()
                .setValue(AXIS, context.getHorizontalDirection().getAxis())
                .setValue(FACING, facing);
    }

    private Direction adjacentPistonHeadFacing(Level level, BlockPos pos) {
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            BlockPos headPos = pos.relative(direction.getOpposite());
            if (!level.isLoaded(headPos)) {
                continue;
            }

            BlockState headState = level.getBlockState(headPos);
            if (headState.is(ModBlocks.PISTON_HEAD.get())) {
                return direction;
            }
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        IPlacementHelper placementHelper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (placementHelper.matchesItem(stack) && stack.getItem() instanceof BlockItem blockItem) {
            return placementHelper.getOffset(player, level, state, pos, hit)
                    .placeInWorld(level, blockItem, player, hand, hit);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            PistonHeadBlockEntity.invalidateNearbyEngines(level, pos, "Piston column changed", pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED, PISTON_SECTION, AXIS, FACING);
    }

    private static class ShaftPlacementHelper implements IPlacementHelper {
        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return AllBlocks.SHAFT::isIn;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.is(ModBlocks.PISTON.get());
        }

        @Override
        public PlacementOffset getOffset(
                Player player,
                Level level,
                BlockState state,
                BlockPos pos,
                BlockHitResult ray
        ) {
            if (!EngineValidator.isReadyForShaftPlacement(level, pos)) {
                return PlacementOffset.fail();
            }

            Direction.Axis axis = state.getValue(AXIS);
            BlockPos shaftPos = EngineValidator.pistonPositionsFromBody(level, pos).shaft();
            return PlacementOffset.success(shaftPos, placedState -> {
                BlockState shaftState = AllBlocks.SHAFT.getDefaultState()
                        .setValue(ShaftBlock.AXIS, axis);
                if (level.isClientSide()) {
                    return shaftState;
                }
                return FullSteamPoweredShaftBlock.equivalentOf(shaftState);
            });
        }
    }
}
