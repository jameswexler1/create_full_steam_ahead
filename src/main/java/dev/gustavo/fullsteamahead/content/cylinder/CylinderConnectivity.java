package dev.gustavo.fullsteamahead.content.cylinder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.piston.EngineValidator;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CylinderConnectivity {
    private static final Comparator<BlockPos> ROOT_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getX())
            .thenComparingInt(pos -> pos.getZ());

    public static void refreshFrom(Level level, BlockPos changedPos) {
        refresh(level, changedPos, null);
    }

    public static void refreshFromRemoval(Level level, BlockPos removedPos) {
        refresh(level, removedPos, removedPos);
    }

    private static void refresh(Level level, BlockPos changedPos, BlockPos ignoredRingMemberPos) {
        if (level.isClientSide()) {
            return;
        }

        Set<BlockPos> candidates = expandWithTrackedRingOrigins(
                level,
                candidateOrigins(changedPos),
                ignoredRingMemberPos
        );
        Set<BlockPos> validOrigins = new LinkedHashSet<>();
        Set<BlockPos> validRingPositions = new LinkedHashSet<>();

        for (BlockPos origin : candidates) {
            if (isValidRing(level, origin, ignoredRingMemberPos)) {
                validOrigins.add(origin);
                validRingPositions.addAll(ringPositions(origin));
            }
        }

        Map<BlockPos, CylinderSection> partialSections =
                inferPartialSections(level, candidates, validRingPositions, ignoredRingMemberPos);
        Map<BlockPos, CylinderWallShape> partialWallShapes =
                inferPartialWallShapes(level, candidates, validRingPositions, ignoredRingMemberPos, partialSections);

        clearOrApplyPartialSections(
                level,
                candidateRingPositions(candidates),
                validRingPositions,
                partialSections,
                partialWallShapes
        );

        for (BlockPos origin : validOrigins) {
            assemble(level, origin);
        }

        notifyEngines(level, candidates);
    }

    private static Set<BlockPos> candidateOrigins(BlockPos pos) {
        Set<BlockPos> origins = new LinkedHashSet<>();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = 0; dz <= 2; dz++) {
                    origins.add(pos.offset(-dx, -dy, -dz));
                }
            }
        }
        return origins;
    }

    private static Set<BlockPos> expandWithTrackedRingOrigins(
            Level level,
            Set<BlockPos> origins,
            BlockPos ignoredRingMemberPos
    ) {
        Set<BlockPos> expanded = new LinkedHashSet<>(origins);
        boolean changed;

        do {
            changed = false;
            for (BlockPos pos : candidateRingPositions(expanded)) {
                if (pos.equals(ignoredRingMemberPos) || !level.isLoaded(pos)) {
                    continue;
                }

                BlockPos ringOrigin = trackedRingOrigin(level, pos);
                if (ringOrigin != null && expanded.add(ringOrigin)) {
                    changed = true;
                }
            }
        } while (changed);

        return expanded;
    }

    private static BlockPos trackedRingOrigin(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamCylinderBlockEntity cylinder && cylinder.isCylinderAssembled()) {
            return cylinder.getRingOrigin();
        }
        if (blockEntity instanceof SteamInletBlockEntity inlet && inlet.isInletAssembled()) {
            return inlet.getRingOrigin();
        }
        return null;
    }

    private static Set<BlockPos> candidateRingPositions(Set<BlockPos> candidateOrigins) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (BlockPos origin : candidateOrigins) {
            positions.addAll(ringPositions(origin));
        }
        return positions;
    }

    private static boolean isValidRing(Level level, BlockPos origin, BlockPos ignoredRingMemberPos) {
        int inlets = 0;

        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.isLoaded(pos)) {
                        return false;
                    }

                    if (isCenter(x, z)) {
                        BlockState centerState = level.getBlockState(pos);
                        if (!centerState.isAir()
                                && !centerState.is(ModBlocks.PISTON.get())
                                && !centerState.is(ModBlocks.PISTON_HEAD.get())) {
                            return false;
                        }
                        continue;
                    }

                    RingMember member = getRingMemberAt(level, pos, ignoredRingMemberPos);
                    if (member == RingMember.NONE) {
                        return false;
                    }
                    if (member == RingMember.INLET && ++inlets > 1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static RingMember getRingMemberAt(Level level, BlockPos pos, BlockPos ignoredRingMemberPos) {
        if (pos.equals(ignoredRingMemberPos)) {
            return RingMember.NONE;
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.STEAM_CYLINDER.get())) {
            return RingMember.CYLINDER;
        }
        if (state.is(ModBlocks.STEAM_INLET.get())) {
            return RingMember.INLET;
        }
        return RingMember.NONE;
    }

    private static boolean isCenter(int x, int z) {
        return x == 1 && z == 1;
    }

    private static List<BlockPos> ringPositions(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>(16);
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    if (!isCenter(x, z)) {
                        positions.add(origin.offset(x, y, z));
                    }
                }
            }
        }
        return positions;
    }

    private static void clearOrApplyPartialSections(
            Level level,
            Set<BlockPos> positions,
            Set<BlockPos> protectedPositions,
            Map<BlockPos, CylinderSection> partialSections,
            Map<BlockPos, CylinderWallShape> partialWallShapes
    ) {
        for (BlockPos pos : positions) {
            if (protectedPositions.contains(pos) || !level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!isRingMember(state)) {
                continue;
            }

            CylinderSection section = partialSections.getOrDefault(pos, CylinderSection.NONE);
            CylinderWallShape wallShape = partialWallShapes.getOrDefault(pos, CylinderWallShape.STANDALONE);
            withCylinderBlockEntity(level, pos, SteamCylinderBlockEntity::clearRingState);
            withInletBlockEntity(level, pos, SteamInletBlockEntity::clearRingState);
            setRingState(level, pos, state, false, section, wallShape, Direction.UP);
        }
    }

    private static void assemble(Level level, BlockPos origin) {
        List<BlockPos> positions = ringPositions(origin);
        BlockPos root = positions.stream()
                .filter(pos -> level.getBlockState(pos).is(ModBlocks.STEAM_CYLINDER.get()))
                .min(ROOT_ORDER)
                .orElse(origin);
        Direction ringFacing = ringFacing(level, origin);
        BlockPos boilerPos = ringFacing == Direction.UP ? findBoiler(level, origin).orElse(null) : null;
        BlockPos inletPos = findInlet(level, positions).orElse(null);

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            CylinderSection section = sectionFor(origin, pos);
            if (state.is(ModBlocks.STEAM_INLET.get())) {
                withInletBlockEntity(level, pos,
                        be -> be.applyRingState(origin, root, boilerPos));
                setRingState(level, pos, state, true, section, CylinderWallShape.STANDALONE, ringFacing);
            } else {
                setRingState(level, pos, state, true, section, CylinderWallShape.STANDALONE, ringFacing);
                withCylinderBlockEntity(level, pos,
                        be -> be.applyRingState(origin, root, boilerPos, inletPos, pos.equals(root)));
            }
        }
    }

    private static Direction ringFacing(Level level, BlockPos origin) {
        BlockPos lowerCenter = origin.offset(1, 0, 1);
        if (isPistonHeadFacing(level, lowerCenter, Direction.UP)) {
            return Direction.UP;
        }

        BlockPos upperCenter = origin.offset(1, 1, 1);
        if (isPistonHeadFacing(level, upperCenter, Direction.DOWN)) {
            return Direction.DOWN;
        }

        return Direction.UP;
    }

    private static boolean isPistonHeadFacing(Level level, BlockPos pos, Direction facing) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.PISTON_HEAD.get())
                && EngineValidator.pistonHeadFacing(state) == facing;
    }

    private static Optional<BlockPos> findInlet(Level level, List<BlockPos> positions) {
        return positions.stream()
                .filter(pos -> level.getBlockState(pos).is(ModBlocks.STEAM_INLET.get()))
                .findFirst();
    }

    private static Optional<BlockPos> findBoiler(Level level, BlockPos origin) {
        BlockPos linkedBoiler = null;

        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if (isCenter(x, z)) {
                    continue;
                }

                BlockPos tankPos = origin.offset(x, -1, z);
                if (!level.isLoaded(tankPos)) {
                    return Optional.empty();
                }

                BlockEntity blockEntity = level.getBlockEntity(tankPos);
                if (!(blockEntity instanceof FluidTankBlockEntity tank)) {
                    return Optional.empty();
                }

                if (linkedBoiler == null) {
                    FluidTankBlockEntity controller = tank.getControllerBE();
                    linkedBoiler = controller == null ? tankPos : controller.getBlockPos();
                }
            }
        }

        return Optional.ofNullable(linkedBoiler);
    }

    private static CylinderSection sectionFor(BlockPos origin, BlockPos pos) {
        return CylinderSection.fromOffsets(
                pos.getX() - origin.getX(),
                pos.getY() - origin.getY(),
                pos.getZ() - origin.getZ()
        );
    }

    private static Map<BlockPos, CylinderSection> inferPartialSections(
            Level level,
            Set<BlockPos> candidateOrigins,
            Set<BlockPos> protectedPositions,
            BlockPos ignoredRingMemberPos
    ) {
        Set<BlockPos> members = new LinkedHashSet<>();
        for (BlockPos pos : candidateRingPositions(candidateOrigins)) {
            if (!protectedPositions.contains(pos)
                    && level.isLoaded(pos)
                    && getRingMemberAt(level, pos, ignoredRingMemberPos) != RingMember.NONE) {
                members.add(pos);
            }
        }

        Map<BlockPos, CylinderSection> assignments = new HashMap<>();
        for (Set<BlockPos> component : connectedComponents(members)) {
            if (!hasRingIntent(component)) {
                continue;
            }

            Optional<BlockPos> bestOrigin = bestVisualOrigin(component);
            if (bestOrigin.isEmpty()) {
                continue;
            }

            for (BlockPos pos : component) {
                CylinderSection section = sectionFor(bestOrigin.get(), pos);
                if (section != CylinderSection.NONE) {
                    assignments.put(pos, section);
                }
            }
        }

        return assignments;
    }

    private static Map<BlockPos, CylinderWallShape> inferPartialWallShapes(
            Level level,
            Set<BlockPos> candidateOrigins,
            Set<BlockPos> protectedPositions,
            BlockPos ignoredRingMemberPos,
            Map<BlockPos, CylinderSection> partialSections
    ) {
        Set<BlockPos> members = new LinkedHashSet<>();
        for (BlockPos pos : candidateRingPositions(candidateOrigins)) {
            if (!protectedPositions.contains(pos)
                    && !partialSections.containsKey(pos)
                    && level.isLoaded(pos)
                    && getRingMemberAt(level, pos, ignoredRingMemberPos) != RingMember.NONE) {
                members.add(pos);
            }
        }

        Map<BlockPos, CylinderWallShape> assignments = new HashMap<>();
        for (Set<BlockPos> component : connectedComponents(members)) {
            CylinderWallShape shape = straightWallShape(component);
            if (shape == CylinderWallShape.STANDALONE) {
                continue;
            }

            for (BlockPos pos : component) {
                assignments.put(pos, shape);
            }
        }

        return assignments;
    }

    private static List<Set<BlockPos>> connectedComponents(Set<BlockPos> members) {
        List<Set<BlockPos>> components = new ArrayList<>();
        Set<BlockPos> unvisited = new LinkedHashSet<>(members);

        while (!unvisited.isEmpty()) {
            BlockPos start = unvisited.iterator().next();
            Set<BlockPos> component = new LinkedHashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            unvisited.remove(start);

            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                component.add(pos);

                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = pos.relative(direction);
                    if (unvisited.remove(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            components.add(component);
        }

        return components;
    }

    private static boolean hasRingIntent(Set<BlockPos> component) {
        return component.size() >= 3 && hasHorizontalAxis(component, Direction.Axis.X)
                && hasHorizontalAxis(component, Direction.Axis.Z);
    }

    private static CylinderWallShape straightWallShape(Set<BlockPos> component) {
        boolean hasX = hasHorizontalAxis(component, Direction.Axis.X);
        boolean hasZ = hasHorizontalAxis(component, Direction.Axis.Z);

        if (hasX == hasZ) {
            return CylinderWallShape.STANDALONE;
        }

        return hasX ? CylinderWallShape.STRAIGHT_X : CylinderWallShape.STRAIGHT_Z;
    }

    private static boolean hasHorizontalAxis(Set<BlockPos> component, Direction.Axis axis) {
        for (BlockPos pos : component) {
            Direction negative = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
            Direction positive = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            if (component.contains(pos.relative(negative)) || component.contains(pos.relative(positive))) {
                return true;
            }
        }

        return false;
    }

    private static Optional<BlockPos> bestVisualOrigin(Set<BlockPos> component) {
        Set<BlockPos> origins = new LinkedHashSet<>();
        for (BlockPos pos : component) {
            origins.addAll(candidateOrigins(pos));
        }

        return origins.stream()
                .filter(origin -> visualScore(origin, component) >= 2)
                .max(Comparator
                        .comparingInt((BlockPos origin) -> visualScore(origin, component))
                        .thenComparing(ROOT_ORDER.reversed()));
    }

    private static int visualScore(BlockPos origin, Set<BlockPos> component) {
        int score = 0;
        for (BlockPos pos : component) {
            if (sectionFor(origin, pos) != CylinderSection.NONE) {
                score++;
            }
        }
        return score;
    }

    private static void setRingState(
            Level level,
            BlockPos pos,
            BlockState state,
            boolean assembled,
            CylinderSection section,
            CylinderWallShape wallShape,
            Direction facing
    ) {
        if (state.is(ModBlocks.STEAM_CYLINDER.get())) {
            setRingState(
                    level,
                    pos,
                    state,
                    SteamCylinderBlock.ASSEMBLED,
                    SteamCylinderBlock.SECTION,
                    SteamCylinderBlock.WALL_SHAPE,
                    SteamCylinderBlock.FACING,
                    assembled,
                    section,
                    wallShape,
                    facing
            );
        } else if (state.is(ModBlocks.STEAM_INLET.get())) {
            setRingState(
                    level,
                    pos,
                    state,
                    SteamInletBlock.ASSEMBLED,
                    SteamInletBlock.SECTION,
                    SteamInletBlock.WALL_SHAPE,
                    SteamInletBlock.FACING,
                    assembled,
                    section,
                    wallShape,
                    facing
            );
        }
    }

    private static void setRingState(
            Level level,
            BlockPos pos,
            BlockState state,
            net.minecraft.world.level.block.state.properties.BooleanProperty property,
            net.minecraft.world.level.block.state.properties.EnumProperty<CylinderSection> sectionProperty,
            net.minecraft.world.level.block.state.properties.EnumProperty<CylinderWallShape> wallShapeProperty,
            net.minecraft.world.level.block.state.properties.DirectionProperty facingProperty,
            boolean assembled,
            CylinderSection section,
            CylinderWallShape wallShape,
            Direction facing
    ) {
        if (!state.hasProperty(property)
                || !state.hasProperty(sectionProperty)
                || !state.hasProperty(wallShapeProperty)
                || !state.hasProperty(facingProperty)) {
            return;
        }

        BlockState newState = state
                .setValue(property, assembled)
                .setValue(sectionProperty, section)
                .setValue(wallShapeProperty, wallShape)
                .setValue(facingProperty, facing);
        if (newState == state) {
            return;
        }

        level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
    }

    private static boolean isRingMember(BlockState state) {
        return state.is(ModBlocks.STEAM_CYLINDER.get()) || state.is(ModBlocks.STEAM_INLET.get());
    }

    private static void notifyEngines(Level level, Set<BlockPos> candidateOrigins) {
        Set<BlockPos> notified = new LinkedHashSet<>();
        for (BlockPos origin : candidateOrigins) {
            for (int y = 0; y <= 1; y++) {
                BlockPos pistonHeadPos = origin.offset(1, y, 1);
                if (notified.add(pistonHeadPos)) {
                    PistonHeadBlockEntity.revalidateAt(level, pistonHeadPos);
                }
            }
        }
    }

    private static void withCylinderBlockEntity(
            Level level,
            BlockPos pos,
            java.util.function.Consumer<SteamCylinderBlockEntity> action
    ) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamCylinderBlockEntity cylinder) {
            action.accept(cylinder);
        }
    }

    private static void withInletBlockEntity(
            Level level,
            BlockPos pos,
            java.util.function.Consumer<SteamInletBlockEntity> action
    ) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamInletBlockEntity inlet) {
            action.accept(inlet);
        }
    }

    private enum RingMember {
        NONE,
        CYLINDER,
        INLET
    }

    private CylinderConnectivity() {
    }
}
