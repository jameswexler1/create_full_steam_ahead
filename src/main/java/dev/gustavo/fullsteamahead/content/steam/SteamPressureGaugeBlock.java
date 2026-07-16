package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.foundation.block.IBE;
import dev.gustavo.fullsteamahead.content.common.FullSteamWrenchable;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class SteamPressureGaugeBlock extends HorizontalDirectionalBlock
        implements IBE<SteamPressureGaugeBlockEntity>, FullSteamWrenchable {
    public static final MapCodec<SteamPressureGaugeBlock> CODEC = simpleCodec(SteamPressureGaugeBlock::new);
    // The source model faces north, with its rear flush against the opposite (south) block face.
    private static final Box[] NORTH_BOXES = new Box[] {
            new Box(5, 15, 12.8, 11, 16, 15.95),
            new Box(3, 14, 12.8, 13, 15, 15.95),
            new Box(1, 4, 12.8, 15, 14, 15.95),
            new Box(3, 3, 12.8, 13, 4, 15.95),
            new Box(5, 2, 12.8, 11, 3, 15.95),
            new Box(7, 1.75, 13.95, 9, 3.25, 15.45),
            new Box(6.5, 0.75, 13.45, 9.5, 1.75, 15.95),
            new Box(7, 0, 13.95, 9, 0.75, 15.45)
    };
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public SteamPressureGaugeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction facing = clickedFace.getAxis() == Direction.Axis.Y
                ? FullSteamWrenchable.flipIfShifted(
                        context,
                        context.getHorizontalDirection().getOpposite()
                )
                : clickedFace;
        return defaultBlockState().setValue(
                FACING,
                facing
        );
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return state.setValue(FACING, state.getValue(FACING).getClockWise());
    }

    @Override
    public void onAfterWrench(Level level, BlockPos pos) {
        withBlockEntityDo(level, pos, SteamPressureGaugeBlockEntity::rebaseFacingAfterWrench);
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
        if (stack.getItem() instanceof WrenchItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(asItem())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            getBlockEntityOptional(level, pos).ifPresent(gauge -> linkWith(gauge, stack, player));
        }
        return level.isClientSide ? ItemInteractionResult.SUCCESS : ItemInteractionResult.CONSUME;
    }

    private static void linkWith(SteamPressureGaugeBlockEntity gauge, ItemStack stack, Player player) {
        GlobalPos heldSource = stack.get(ModDataComponents.STEAM_GAUGE_SOURCE.get());
        if (heldSource != null) {
            if (!gauge.bindToSource(heldSource)) {
                player.displayClientMessage(
                        Component.translatable("message.full_steam_ahead.steam_gauge_wrong_dimension"), true);
                return;
            }
            player.displayClientMessage(
                    Component.translatable("message.full_steam_ahead.steam_gauge_bound"), true);
            return;
        }

        GlobalPos gaugeSource = gauge.getLinkedSource();
        if (gaugeSource != null) {
            stack.set(ModDataComponents.STEAM_GAUGE_SOURCE.get(), gaugeSource);
            player.displayClientMessage(
                    Component.translatable("message.full_steam_ahead.steam_gauge_copied"), true);
        } else {
            player.displayClientMessage(
                    Component.translatable("message.full_steam_ahead.steam_gauge_unlinked_block"), true);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        GlobalPos source = stack.get(ModDataComponents.STEAM_GAUGE_SOURCE.get());
        if (source == null) {
            return;
        }
        getBlockEntityOptional(level, pos).ifPresent(gauge -> {
            if (!gauge.bindToSource(source) && placer instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable("message.full_steam_ahead.steam_gauge_wrong_dimension"), true);
            }
        });
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            IBE.onRemove(state, level, pos, newState);
        }
    }

    @Override
    public Class<SteamPressureGaugeBlockEntity> getBlockEntityClass() {
        return SteamPressureGaugeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamPressureGaugeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STEAM_PRESSURE_GAUGE.get();
    }

    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
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
            case SOUTH -> new Box(
                    16 - box.maxX, box.minY, 16 - box.maxZ,
                    16 - box.minX, box.maxY, 16 - box.minZ
            );
            case EAST -> new Box(
                    16 - box.maxZ, box.minY, box.minX,
                    16 - box.minZ, box.maxY, box.maxX
            );
            case WEST -> new Box(
                    box.minZ, box.minY, 16 - box.maxX,
                    box.maxZ, box.maxY, 16 - box.minX
            );
            default -> box;
        };
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private VoxelShape shape() {
            return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
