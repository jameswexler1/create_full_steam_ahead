package dev.gustavo.fullsteamahead.content.steam;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import dev.gustavo.fullsteamahead.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class SteamAdmissionValveBlock extends FluidPipeBlock {
    public static final MapCodec<SteamAdmissionValveBlock> CODEC = simpleCodec(SteamAdmissionValveBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");
    private static final Box[] BODY_BOXES = new Box[] {
            new Box(1, 3, 3, 15, 5, 13),
            new Box(1, 5, 3, 15, 11, 13),
            new Box(1, 11, 3, 15, 13, 13),
            new Box(4, 4, 2, 12, 12, 3),
            new Box(3, 3, -1, 13, 13, 2),
            new Box(2, 13, 4, 14, 17, 12),
            new Box(2, 17, 4, 14, 18, 12),
            new Box(4, 19, 5, 12, 27, 10),
            new Box(3, 18, 5, 4, 28, 10),
            new Box(12, 18, 5, 13, 28, 10),
            new Box(4, 18, 5, 12, 19, 10),
            new Box(4, 27, 5, 12, 28, 10),
            new Box(2.5, 28, 4.5, 13.5, 29, 11.5),
            new Box(4, 29, 5.5, 12, 29.5, 10.5),
            new Box(4, 19, 4.5, 12, 27, 5),
            new Box(5, 20, 4.125, 11, 26, 4.5),
            new Box(3, 18, 10, 5, 28, 11),
            new Box(11, 18, 10, 13, 28, 11),
            new Box(6, 18, 10, 10, 28, 11),
            new Box(5, 18, 10, 6, 20, 11),
            new Box(10, 18, 10, 11, 20, 11),
            new Box(5, 26, 10, 6, 28, 11),
            new Box(10, 26, 10, 11, 28, 11),
            // Full travel envelope of the moving manual lever.
            new Box(5.125, 20.125, 10.125, 10.875, 25.875, 13),
    };
    private static final Map<Direction, VoxelShape> BODY_SHAPES = buildBodyShapes(false, ShapeSection.FULL);
    private static final Map<Direction, VoxelShape> INVERTED_BODY_SHAPES =
            buildBodyShapes(true, ShapeSection.FULL);
    private static final Map<Direction, VoxelShape> BASE_COLLISION_SHAPES =
            buildBodyShapes(false, ShapeSection.BASE);
    private static final Map<Direction, VoxelShape> INVERTED_BASE_COLLISION_SHAPES =
            buildBodyShapes(true, ShapeSection.BASE);
    private static final Map<Direction, VoxelShape> CONTROLLER_SHAPES =
            buildBodyShapes(false, ShapeSection.CONTROLLER);
    private static final Map<Direction, VoxelShape> INVERTED_CONTROLLER_SHAPES =
            buildBodyShapes(true, ShapeSection.CONTROLLER);
    private static final Map<Direction, VoxelShape> CONNECTION_SHAPES = buildConnectionShapes();
    private static final Map<Direction, VoxelShape> RIM_SHAPES = buildRimShapes();

    public SteamAdmissionValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(INVERTED, false));
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

        Direction facing = context.getHorizontalDirection().getOpposite();
        state = state.setValue(FACING, facing);
        state = enforceHorizontalConnections(state, facing);
        state = resolveTopology(context.getLevel(), context.getClickedPos(), state);
        return SteamAdmissionValveController.canOccupy(
                context.getLevel(), context.getClickedPos(), state, context) ? state : null;
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
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        updated = enforceHorizontalConnections(updated, state.getValue(FACING));
        BlockState resolved = resolveTopology(level, pos, updated);
        if (resolved.getValue(INVERTED) != state.getValue(INVERTED)
                && !SteamAdmissionValveController.canOccupy(level, pos, resolved)) {
            resolved = resolved.setValue(INVERTED, state.getValue(INVERTED));
        }
        return resolved;
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        SteamAdmissionValveController.sync(level, pos, state);
    }

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())) {
            SteamAdmissionValveController.removeOwned(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof SteamAdmissionValveBlockEntity valve)) {
            return InteractionResult.PASS;
        }
        SteamAdmissionControlMode next = valve.cycleControlMode();
        IWrenchable.playRotateSound(level, pos);
        Player player = context.getPlayer();
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    next == SteamAdmissionControlMode.MANUAL
                            ? "message.full_steam_ahead.admission_mode_manual"
                            : "message.full_steam_ahead.admission_mode_redstone_link"), true);
        }
        return InteractionResult.SUCCESS;
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
        if (stack.getItem() instanceof WrenchItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.getBlockEntity(pos) instanceof SteamAdmissionValveBlockEntity valve
                && valve.isManualMode()
                && stack.is(ModBlocks.STEPPED_LEVER.get().asItem())) {
            if (!level.isClientSide) {
                linkWithTelegraphItem(valve, stack, player);
            }
            return level.isClientSide ? ItemInteractionResult.SUCCESS : ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!(level.getBlockEntity(pos) instanceof SteamAdmissionValveBlockEntity valve)
                || !valve.isManualMode()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (valve.changeManualStrength(player.isShiftKeyDown())) {
            float pitch = 0.45F + valve.getManualStrength() / 15.0F * 0.5F;
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.25F, pitch);
        }
        return InteractionResult.SUCCESS;
    }

    private static void linkWithTelegraphItem(
            SteamAdmissionValveBlockEntity valve,
            ItemStack stack,
            Player player
    ) {
        String message;
        if (player.isShiftKeyDown()) {
            valve.setTelegraphLinkId(null);
            message = "message.full_steam_ahead.telegraph_unlinked";
        } else if (valve.getLinkId() != null) {
            stack.set(ModDataComponents.TELEGRAPH_LINK.get(), valve.getLinkId());
            message = "message.full_steam_ahead.telegraph_copied";
        } else if (stack.has(ModDataComponents.TELEGRAPH_LINK.get())) {
            valve.setTelegraphLinkId(stack.get(ModDataComponents.TELEGRAPH_LINK.get()));
            message = "message.full_steam_ahead.telegraph_bound";
        } else {
            UUID id = UUID.randomUUID();
            valve.setTelegraphLinkId(id);
            stack.set(ModDataComponents.TELEGRAPH_LINK.get(), id);
            message = "message.full_steam_ahead.telegraph_started";
        }
        player.displayClientMessage(Component.translatable(message), true);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = super.rotate(state, rotation);
        return rotated.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getValveShape(state, level, pos);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getValveCollisionShape(state, level, pos);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, INVERTED);
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

    static BlockState resolveTopology(BlockGetter level, BlockPos pos, BlockState state) {
        Direction inletDirection = null;
        boolean inletInverted = false;
        int inletCount = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState inletState = level.getBlockState(pos.relative(direction));
            if (inletState.is(ModBlocks.STEAM_INLET.get())
                    && SteamInletBlock.getPortFacing(inletState) == direction.getOpposite()) {
                inletDirection = direction;
                inletInverted = inletState.getValue(SteamInletBlock.FACING) == Direction.DOWN;
                inletCount++;
            }
        }

        if (inletCount != 1) {
            return alignFacingToPipeAxis(state).setValue(INVERTED, false);
        }

        return state.setValue(FACING, inletDirection).setValue(INVERTED, inletInverted);
    }

    private static BlockState alignFacingToPipeAxis(BlockState state) {
        boolean north = state.getValue(PROPERTY_BY_DIRECTION.get(Direction.NORTH));
        boolean south = state.getValue(PROPERTY_BY_DIRECTION.get(Direction.SOUTH));
        boolean east = state.getValue(PROPERTY_BY_DIRECTION.get(Direction.EAST));
        boolean west = state.getValue(PROPERTY_BY_DIRECTION.get(Direction.WEST));
        boolean northSouthStraight = north && south;
        boolean eastWestStraight = east && west;

        Direction.Axis axis;
        if (northSouthStraight != eastWestStraight) {
            axis = northSouthStraight ? Direction.Axis.Z : Direction.Axis.X;
        } else {
            boolean hasNorthSouth = north || south;
            boolean hasEastWest = east || west;
            if (hasNorthSouth == hasEastWest) {
                return state;
            }
            axis = hasNorthSouth ? Direction.Axis.Z : Direction.Axis.X;
        }

        Direction currentFacing = state.getValue(FACING);
        if (currentFacing.getAxis() == axis) {
            return state;
        }

        Direction alignedFacing = axis == Direction.Axis.Z
                ? (north && !south ? Direction.NORTH : Direction.SOUTH)
                : (east && !west ? Direction.EAST : Direction.WEST);
        return state.setValue(FACING, alignedFacing);
    }

    private static VoxelShape getValveShape(BlockState state, BlockGetter level, BlockPos pos) {
        Map<Direction, VoxelShape> bodyShapes = state.getValue(INVERTED)
                ? INVERTED_BODY_SHAPES
                : BODY_SHAPES;
        VoxelShape shape = bodyShapes.get(state.getValue(FACING));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
                continue;
            }

            shape = Shapes.or(shape, CONNECTION_SHAPES.get(direction));
            if (level instanceof BlockAndTintGetter tintGetter
                    && FluidPipeBlock.shouldDrawRim(tintGetter, pos, state, direction)) {
                shape = Shapes.or(shape, RIM_SHAPES.get(direction));
            }
        }
        return shape.optimize();
    }

    private static VoxelShape getValveCollisionShape(BlockState state, BlockGetter level, BlockPos pos) {
        Map<Direction, VoxelShape> bodyShapes = state.getValue(INVERTED)
                ? INVERTED_BASE_COLLISION_SHAPES
                : BASE_COLLISION_SHAPES;
        VoxelShape shape = bodyShapes.get(state.getValue(FACING));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
                continue;
            }

            shape = Shapes.or(shape, CONNECTION_SHAPES.get(direction));
            if (level instanceof BlockAndTintGetter tintGetter
                    && FluidPipeBlock.shouldDrawRim(tintGetter, pos, state, direction)) {
                shape = Shapes.or(shape, RIM_SHAPES.get(direction));
            }
        }
        return shape.optimize();
    }

    static VoxelShape getControllerShape(BlockState baseState) {
        Map<Direction, VoxelShape> shapes = baseState.getValue(INVERTED)
                ? INVERTED_CONTROLLER_SHAPES
                : CONTROLLER_SHAPES;
        return shapes.get(baseState.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> buildBodyShapes(
            boolean inverted,
            ShapeSection section
    ) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (Box box : BODY_BOXES) {
                Box oriented = inverted ? invertAroundLocalInletAxis(box) : box;
                Box sectionBox = section.clip(oriented, inverted);
                if (sectionBox != null) {
                    shape = Shapes.or(shape, rotate(sectionBox, direction).shape());
                }
            }
            shapes.put(direction, shape.optimize());
        }
        return shapes;
    }

    private static Box invertAroundLocalInletAxis(Box box) {
        return new Box(
                16 - box.maxX,
                16 - box.maxY,
                box.minZ,
                16 - box.minX,
                16 - box.minY,
                box.maxZ
        );
    }

    private static Map<Direction, VoxelShape> buildConnectionShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.NORTH, Block.box(4, 4, 0, 12, 12, 4));
        shapes.put(Direction.SOUTH, Block.box(4, 4, 12, 12, 12, 16));
        shapes.put(Direction.EAST, Block.box(12, 4, 4, 16, 12, 12));
        shapes.put(Direction.WEST, Block.box(0, 4, 4, 4, 12, 12));
        shapes.put(Direction.UP, Block.box(4, 12, 4, 12, 16, 12));
        shapes.put(Direction.DOWN, Block.box(4, 0, 4, 12, 4, 12));
        return shapes;
    }

    private static Map<Direction, VoxelShape> buildRimShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.NORTH, Block.box(3, 3, 0, 13, 13, 2));
        shapes.put(Direction.SOUTH, Block.box(3, 3, 14, 13, 13, 16));
        shapes.put(Direction.EAST, Block.box(14, 3, 3, 16, 13, 13));
        shapes.put(Direction.WEST, Block.box(0, 3, 3, 2, 13, 13));
        shapes.put(Direction.UP, Block.box(3, 14, 3, 13, 16, 13));
        shapes.put(Direction.DOWN, Block.box(3, 0, 3, 13, 2, 13));
        return shapes;
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

    private static BlockState enforceHorizontalConnections(BlockState state, Direction fallbackFacing) {
        state = state
                .setValue(PROPERTY_BY_DIRECTION.get(Direction.UP), false)
                .setValue(PROPERTY_BY_DIRECTION.get(Direction.DOWN), false);

        boolean hasHorizontalConnection = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            hasHorizontalConnection |= state.getValue(PROPERTY_BY_DIRECTION.get(direction));
        }
        if (hasHorizontalConnection) {
            return state;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(
                    PROPERTY_BY_DIRECTION.get(direction),
                    direction == fallbackFacing.getOpposite()
            );
        }
        return state;
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private VoxelShape shape() {
            return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private enum ShapeSection {
        FULL {
            @Override
            Box clip(Box box, boolean inverted) {
                return box;
            }
        },
        BASE {
            @Override
            Box clip(Box box, boolean inverted) {
                return clipY(box, 0.0D, 16.0D, 0.0D);
            }
        },
        CONTROLLER {
            @Override
            Box clip(Box box, boolean inverted) {
                return inverted
                        ? clipY(box, -16.0D, 0.0D, 16.0D)
                        : clipY(box, 16.0D, 32.0D, -16.0D);
            }
        };

        abstract Box clip(Box box, boolean inverted);

        private static Box clipY(Box box, double cellMinY, double cellMaxY, double offsetY) {
            double minY = Math.max(box.minY, cellMinY);
            double maxY = Math.min(box.maxY, cellMaxY);
            if (minY >= maxY) {
                return null;
            }
            return new Box(
                    box.minX,
                    minY + offsetY,
                    box.minZ,
                    box.maxX,
                    maxY + offsetY,
                    box.maxZ
            );
        }
    }
}
