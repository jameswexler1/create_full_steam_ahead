package dev.gustavo.fullsteamahead.content.cylinder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlock;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.content.piston.SteamPistonBlock;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        Map<BlockPos, RingData> validRings = resolveSharedRings(
                level,
                findIndividuallyValidRings(level, candidates, ignoredRingMemberPos)
        );
        Map<BlockPos, List<RingData>> memberships = buildMemberships(validRings.values());
        Set<BlockPos> validRingPositions = new LinkedHashSet<>(memberships.keySet());

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

        assemble(level, validRings.values(), memberships);

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

                for (BlockPos ringOrigin : trackedRingOrigins(level, pos)) {
                    if (expanded.add(ringOrigin)) {
                        changed = true;
                    }
                }
            }
        } while (changed);

        return expanded;
    }

    private static Set<BlockPos> trackedRingOrigins(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamCylinderBlockEntity cylinder && cylinder.isCylinderAssembled()) {
            return cylinder.getRingOrigins();
        }
        if (blockEntity instanceof SteamInletBlockEntity inlet && inlet.isInletAssembled()) {
            BlockPos ringOrigin = inlet.getRingOrigin();
            return ringOrigin == null ? Set.of() : Set.of(ringOrigin);
        }
        return Set.of();
    }

    private static Map<BlockPos, RingData> findIndividuallyValidRings(
            Level level,
            Set<BlockPos> candidates,
            BlockPos ignoredRingMemberPos
    ) {
        Map<BlockPos, RingData> rings = new LinkedHashMap<>();
        for (BlockPos origin : candidates) {
            if (!isValidRing(level, origin, ignoredRingMemberPos)) {
                continue;
            }

            List<BlockPos> positions = ringPositions(origin);
            Direction facing = ringFacing(level, origin);
            BlockPos boilerPos = facing == Direction.UP ? findBoiler(level, origin).orElse(null) : null;
            BlockPos inletPos = findInlet(level, positions).orElse(null);
            rings.put(origin, new RingData(origin, positions, facing, boilerPos, inletPos));
        }
        return rings;
    }

    private static Map<BlockPos, RingData> resolveSharedRings(Level level, Map<BlockPos, RingData> rings) {
        Set<BlockPos> invalidOrigins = new LinkedHashSet<>();
        Map<BlockPos, List<RingData>> memberships = buildMemberships(rings.values());

        for (Map.Entry<BlockPos, List<RingData>> entry : memberships.entrySet()) {
            List<RingData> owners = entry.getValue();
            if (owners.size() <= 1) {
                continue;
            }

            if (owners.size() > 2 || !canShareWallAt(level, entry.getKey(), owners.get(0), owners.get(1))) {
                for (RingData owner : owners) {
                    invalidOrigins.add(owner.origin());
                }
            }
        }

        if (invalidOrigins.isEmpty()) {
            return rings;
        }

        Map<BlockPos, RingData> resolved = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, RingData> entry : rings.entrySet()) {
            if (!invalidOrigins.contains(entry.getKey())) {
                resolved.put(entry.getKey(), entry.getValue());
            }
        }
        return resolved;
    }

    private static Map<BlockPos, List<RingData>> buildMemberships(Collection<RingData> rings) {
        Map<BlockPos, List<RingData>> memberships = new LinkedHashMap<>();
        for (RingData ring : rings) {
            for (BlockPos pos : ring.positions()) {
                memberships.computeIfAbsent(pos, ignored -> new ArrayList<>(2)).add(ring);
            }
        }

        for (List<RingData> owners : memberships.values()) {
            owners.sort(Comparator.comparing(RingData::origin, ROOT_ORDER));
        }
        return memberships;
    }

    private static boolean canShareWallAt(Level level, BlockPos pos, RingData first, RingData second) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.STEAM_CYLINDER.get())) {
            return false;
        }

        return first.facing() == second.facing() && sharedBankAxis(first.origin(), second.origin()).isPresent();
    }

    private static Optional<Direction.Axis> sharedBankAxis(BlockPos first, BlockPos second) {
        int dx = Math.abs(first.getX() - second.getX());
        int dy = Math.abs(first.getY() - second.getY());
        int dz = Math.abs(first.getZ() - second.getZ());

        if (dy != 0) {
            return Optional.empty();
        }
        if (dx == 2 && dz == 0) {
            return Optional.of(Direction.Axis.X);
        }
        if (dz == 2 && dx == 0) {
            return Optional.of(Direction.Axis.Z);
        }
        return Optional.empty();
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
            // For blocks not part of a ring or inferred straight run, keep the player's chosen
            // orientation instead of snapping back to STANDALONE, so decorative walls stay put.
            CylinderWallShape wallShape = partialWallShapes.containsKey(pos)
                    ? partialWallShapes.get(pos)
                    : (section == CylinderSection.NONE ? currentWallShape(state) : CylinderWallShape.STANDALONE);
            withCylinderBlockEntity(level, pos, SteamCylinderBlockEntity::clearRingState);
            withInletBlockEntity(level, pos, SteamInletBlockEntity::clearRingState);
            setRingState(level, pos, state, false, section, wallShape, Direction.UP, CylinderSharedWall.NONE);
        }
    }

    private static void assemble(
            Level level,
            Collection<RingData> rings,
            Map<BlockPos, List<RingData>> memberships
    ) {
        Map<BlockPos, BlockPos> roots = new HashMap<>();
        for (RingData ring : rings) {
            roots.put(ring.origin(), rootFor(level, ring, memberships));
        }

        for (Map.Entry<BlockPos, List<RingData>> entry : memberships.entrySet()) {
            BlockPos pos = entry.getKey();
            List<RingData> owners = entry.getValue();
            RingData primary = owners.getFirst();
            RingData secondary = owners.size() > 1 ? owners.get(1) : null;
            BlockState state = level.getBlockState(pos);
            CylinderSection section = sectionFor(primary.origin(), pos);
            CylinderSharedWall sharedWall = sharedWallFor(owners);
            if (state.is(ModBlocks.STEAM_INLET.get())) {
                withInletBlockEntity(level, pos,
                        be -> be.applyRingState(primary.origin(), roots.get(primary.origin()), primary.boilerPos()));
                setRingState(
                        level,
                        pos,
                        state,
                        true,
                        section,
                        CylinderWallShape.STANDALONE,
                        primary.facing(),
                        CylinderSharedWall.NONE
                );
            } else {
                setRingState(
                        level,
                        pos,
                        state,
                        true,
                        section,
                        CylinderWallShape.STANDALONE,
                        primary.facing(),
                        sharedWall
                );
                withCylinderBlockEntity(level, pos,
                        be -> be.applyRingState(
                                primary.origin(),
                                secondary == null ? null : secondary.origin(),
                                roots.get(primary.origin()),
                                primary.boilerPos(),
                                primary.inletPos(),
                                pos.equals(roots.get(primary.origin()))
                        ));
            }
        }

        for (RingData ring : rings) {
            alignPistonColumn(level, ring.origin(), ring.facing());
        }
    }

    private static BlockPos rootFor(
            Level level,
            RingData ring,
            Map<BlockPos, List<RingData>> memberships
    ) {
        return ring.positions()
                .stream()
                .filter(pos -> level.getBlockState(pos).is(ModBlocks.STEAM_CYLINDER.get()))
                .filter(pos -> memberships.getOrDefault(pos, List.of()).size() == 1)
                .min(ROOT_ORDER)
                .orElseGet(() -> ring.positions()
                        .stream()
                        .filter(pos -> level.getBlockState(pos).is(ModBlocks.STEAM_CYLINDER.get()))
                        .min(ROOT_ORDER)
                        .orElse(ring.origin()));
    }

    private static CylinderSharedWall sharedWallFor(List<RingData> owners) {
        if (owners.size() != 2) {
            return CylinderSharedWall.NONE;
        }

        Optional<Direction.Axis> bankAxis = sharedBankAxis(owners.get(0).origin(), owners.get(1).origin());
        if (bankAxis.isEmpty()) {
            return CylinderSharedWall.NONE;
        }

        return bankAxis.get() == Direction.Axis.X ? CylinderSharedWall.STRIP_Z : CylinderSharedWall.STRIP_X;
    }

    private static Direction ringFacing(Level level, BlockPos origin) {
        BlockPos lowerCenter = origin.offset(1, 0, 1);
        BlockPos upperCenter = origin.offset(1, 1, 1);

        boolean lowerHead = isPistonHead(level, lowerCenter);
        boolean upperHead = isPistonHead(level, upperCenter);
        if (upperHead && !lowerHead) {
            return Direction.DOWN;
        }
        return Direction.UP;
    }

    private static boolean isPistonHead(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        return level.getBlockState(pos).is(ModBlocks.PISTON_HEAD.get());
    }

    private static void alignPistonColumn(Level level, BlockPos origin, Direction ringFacing) {
        BlockPos pistonHeadPos = origin.offset(1, ringFacing == Direction.DOWN ? 1 : 0, 1);
        BlockPos pistonPos = pistonHeadPos.relative(ringFacing);
        setFacing(level, pistonHeadPos, PistonHeadBlock.FACING, ringFacing);
        setFacing(level, pistonPos, SteamPistonBlock.FACING, ringFacing);
    }

    private static void setFacing(
            Level level,
            BlockPos pos,
            net.minecraft.world.level.block.state.properties.DirectionProperty property,
            Direction facing
    ) {
        if (!level.isLoaded(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(property)) {
            return;
        }

        BlockState newState = state.setValue(property, facing);
        if (newState != state) {
            level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
        }
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
            if (!canShowPartialRingVisuals(level, component) || !hasRingIntent(component)) {
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
            if (!canShowPartialRingVisuals(level, component)) {
                continue;
            }

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

    private static boolean canShowPartialRingVisuals(Level level, Set<BlockPos> component) {
        int inlets = 0;
        for (BlockPos pos : component) {
            if (level.getBlockState(pos).is(ModBlocks.STEAM_INLET.get()) && ++inlets > 1) {
                return false;
            }
        }

        return true;
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
            Direction facing,
            CylinderSharedWall sharedWall
    ) {
        if (state.is(ModBlocks.STEAM_CYLINDER.get())) {
            setCylinderRingState(level, pos, state, assembled, section, wallShape, facing, sharedWall);
        } else if (state.is(ModBlocks.STEAM_INLET.get())) {
            setInletRingState(
                    level,
                    pos,
                    state,
                    assembled,
                    section,
                    wallShape,
                    facing
            );
        }
    }

    private static void setCylinderRingState(
            Level level,
            BlockPos pos,
            BlockState state,
            boolean assembled,
            CylinderSection section,
            CylinderWallShape wallShape,
            Direction facing,
            CylinderSharedWall sharedWall
    ) {
        if (!state.hasProperty(SteamCylinderBlock.ASSEMBLED)
                || !state.hasProperty(SteamCylinderBlock.SECTION)
                || !state.hasProperty(SteamCylinderBlock.WALL_SHAPE)
                || !state.hasProperty(SteamCylinderBlock.SHARED_WALL)
                || !state.hasProperty(SteamCylinderBlock.FACING)) {
            return;
        }

        BlockState newState = state
                .setValue(SteamCylinderBlock.ASSEMBLED, assembled)
                .setValue(SteamCylinderBlock.SECTION, section)
                .setValue(SteamCylinderBlock.WALL_SHAPE, wallShape)
                .setValue(SteamCylinderBlock.SHARED_WALL, sharedWall)
                .setValue(SteamCylinderBlock.FACING, facing);
        if (newState == state) {
            return;
        }

        level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
    }

    private static void setInletRingState(
            Level level,
            BlockPos pos,
            BlockState state,
            boolean assembled,
            CylinderSection section,
            CylinderWallShape wallShape,
            Direction facing
    ) {
        if (!state.hasProperty(SteamInletBlock.ASSEMBLED)
                || !state.hasProperty(SteamInletBlock.SECTION)
                || !state.hasProperty(SteamInletBlock.WALL_SHAPE)
                || !state.hasProperty(SteamInletBlock.FACING)) {
            return;
        }

        BlockState newState = state
                .setValue(SteamInletBlock.ASSEMBLED, assembled)
                .setValue(SteamInletBlock.SECTION, section)
                .setValue(SteamInletBlock.WALL_SHAPE, wallShape)
                .setValue(SteamInletBlock.FACING, facing);
        if (newState == state) {
            return;
        }

        level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
    }

    private static boolean isRingMember(BlockState state) {
        return state.is(ModBlocks.STEAM_CYLINDER.get()) || state.is(ModBlocks.STEAM_INLET.get());
    }

    private static CylinderWallShape currentWallShape(BlockState state) {
        if (state.hasProperty(SteamCylinderBlock.WALL_SHAPE)) {
            return state.getValue(SteamCylinderBlock.WALL_SHAPE);
        }
        if (state.hasProperty(SteamInletBlock.WALL_SHAPE)) {
            return state.getValue(SteamInletBlock.WALL_SHAPE);
        }
        return CylinderWallShape.STANDALONE;
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

    private record RingData(
            BlockPos origin,
            List<BlockPos> positions,
            Direction facing,
            BlockPos boilerPos,
            BlockPos inletPos
    ) {
    }

    private CylinderConnectivity() {
    }
}
