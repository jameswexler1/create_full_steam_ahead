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
        Set<BlockPos> candidatePositions = candidateRingPositions(candidates);
        Set<BlockPos> protectedOrigins = assembledRingOrigins(level, candidatePositions, ignoredRingMemberPos);
        Set<BlockPos> trackedPartialOrigins = trackedPartialRingOrigins(level, candidatePositions, ignoredRingMemberPos);
        Set<BlockPos> protectedBorePositions = combine(
                borePositions(combine(protectedOrigins, trackedPartialOrigins)),
                interiorRingMemberPositions(level, candidatePositions, ignoredRingMemberPos)
        );
        Map<BlockPos, RingData> validRings = resolveSharedRings(
                level,
                findIndividuallyValidRings(level, candidates, ignoredRingMemberPos),
                protectedOrigins
        );
        Map<BlockPos, List<RingData>> memberships = buildMemberships(validRings.values());
        Set<BlockPos> validRingPositions = new LinkedHashSet<>(memberships.keySet());
        Map<BlockPos, List<RingData>> visualPartialMemberships = buildMemberships(
                findPartialRings(level, collectRingMembers(
                        level,
                        candidatePositions,
                        protectedBorePositions,
                        ignoredRingMemberPos
                )).values()
        );

        Map<BlockPos, PartialVisual> partialVisuals =
                inferPartialVisuals(
                        level,
                        candidatePositions,
                        combine(validRingPositions, protectedBorePositions),
                        ignoredRingMemberPos
                );

        clearOrApplyPartialVisuals(
                level,
                candidatePositions,
                validRingPositions,
                partialVisuals
        );

        assemble(level, validRings.values(), memberships, visualPartialMemberships);

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
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SteamCylinderBlockEntity cylinder && cylinder.isCylinderAssembled()) {
            Set<BlockPos> origins = new LinkedHashSet<>(cylinder.getRingOrigins());
            origins.addAll(trackedPartialRingOrigins(state, pos));
            return origins;
        }
        if (blockEntity instanceof SteamInletBlockEntity inlet && inlet.isInletAssembled()) {
            BlockPos ringOrigin = inlet.getRingOrigin();
            return ringOrigin == null ? Set.of() : Set.of(ringOrigin);
        }
        return trackedPartialRingOrigins(state, pos);
    }

    private static Set<BlockPos> assembledRingOrigins(
            Level level,
            Set<BlockPos> positions,
            BlockPos ignoredRingMemberPos
    ) {
        Set<BlockPos> origins = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (pos.equals(ignoredRingMemberPos) || !level.isLoaded(pos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SteamCylinderBlockEntity cylinder && cylinder.isCylinderAssembled()) {
                origins.addAll(cylinder.getRingOrigins());
            } else if (blockEntity instanceof SteamInletBlockEntity inlet && inlet.isInletAssembled()) {
                BlockPos ringOrigin = inlet.getRingOrigin();
                if (ringOrigin != null) {
                    origins.add(ringOrigin);
                }
            }
        }
        return origins;
    }

    private static Set<BlockPos> borePositions(Set<BlockPos> origins) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (BlockPos origin : origins) {
            positions.add(origin.offset(1, 0, 1));
            positions.add(origin.offset(1, 1, 1));
        }
        return positions;
    }

    private static Set<BlockPos> trackedPartialRingOrigins(
            Level level,
            Set<BlockPos> positions,
            BlockPos ignoredRingMemberPos
    ) {
        Set<BlockPos> origins = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (pos.equals(ignoredRingMemberPos) || !level.isLoaded(pos)) {
                continue;
            }

            origins.addAll(trackedPartialRingOrigins(level.getBlockState(pos), pos));
        }
        return origins;
    }

    private static Set<BlockPos> interiorRingMemberPositions(
            Level level,
            Set<BlockPos> positions,
            BlockPos ignoredRingMemberPos
    ) {
        Set<BlockPos> members = collectRingMembers(level, positions, Set.of(), ignoredRingMemberPos);
        Set<BlockPos> interiors = new LinkedHashSet<>();
        for (BlockPos pos : members) {
            boolean east = members.contains(pos.east());
            boolean west = members.contains(pos.west());
            boolean north = members.contains(pos.north());
            boolean south = members.contains(pos.south());

            if ((east && west && (north || south)) || (north && south && (east || west))) {
                interiors.add(pos);
            }
        }
        return interiors;
    }

    private static Set<BlockPos> combine(Set<BlockPos> first, Set<BlockPos> second) {
        Set<BlockPos> combined = new LinkedHashSet<>(first);
        combined.addAll(second);
        return combined;
    }

    private static Set<BlockPos> trackedPartialRingOrigins(BlockState state, BlockPos pos) {
        CylinderSection section = currentSection(state);
        if (section == CylinderSection.NONE) {
            return Set.of();
        }

        BlockPos primaryOrigin = pos.offset(-section.xOffset(), -section.yOffset(), -section.zOffset());
        if (!state.is(ModBlocks.STEAM_CYLINDER.get()) || !state.hasProperty(SteamCylinderBlock.SHARED_WALL)) {
            return Set.of(primaryOrigin);
        }

        CylinderSharedWall sharedWall = state.getValue(SteamCylinderBlock.SHARED_WALL);
        if (sharedWall == CylinderSharedWall.NONE) {
            return Set.of(primaryOrigin);
        }
        if (sharedWall == CylinderSharedWall.STRIP_Z) {
            if (section.xOffset() == 2) {
                return Set.of(primaryOrigin, primaryOrigin.east(2));
            }
            if (section.xOffset() == 0) {
                return Set.of(primaryOrigin.west(2), primaryOrigin);
            }
        }
        if (sharedWall == CylinderSharedWall.STRIP_X) {
            if (section.zOffset() == 2) {
                return Set.of(primaryOrigin, primaryOrigin.south(2));
            }
            if (section.zOffset() == 0) {
                return Set.of(primaryOrigin.north(2), primaryOrigin);
            }
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

    private static Map<BlockPos, RingData> resolveSharedRings(
            Level level,
            Map<BlockPos, RingData> rings,
            Set<BlockPos> protectedOrigins
    ) {
        Set<BlockPos> invalidOrigins = new LinkedHashSet<>();
        Map<BlockPos, List<RingData>> memberships = buildMemberships(rings.values());

        for (Map.Entry<BlockPos, List<RingData>> entry : memberships.entrySet()) {
            List<RingData> owners = entry.getValue();
            if (owners.size() <= 1) {
                continue;
            }

            if (owners.size() > 2 || !canShareWallAt(level, entry.getKey(), owners.get(0), owners.get(1))) {
                boolean hasProtectedOwner = owners.stream().anyMatch(owner -> protectedOrigins.contains(owner.origin()));
                for (RingData owner : owners) {
                    if (!hasProtectedOwner || !protectedOrigins.contains(owner.origin())) {
                        invalidOrigins.add(owner.origin());
                    }
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

    private static void clearOrApplyPartialVisuals(
            Level level,
            Set<BlockPos> positions,
            Set<BlockPos> protectedPositions,
            Map<BlockPos, PartialVisual> partialVisuals
    ) {
        for (BlockPos pos : positions) {
            if (protectedPositions.contains(pos) || !level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!isRingMember(state)) {
                continue;
            }

            PartialVisual partial = partialVisuals.get(pos);
            CylinderSection section = partial == null ? CylinderSection.NONE : partial.section();
            CylinderWallShape wallShape = partial == null ? currentStandaloneShape(state) : partial.wallShape();
            CylinderSharedWall sharedWall = partial == null ? CylinderSharedWall.NONE : partial.sharedWall();
            withCylinderBlockEntity(level, pos, SteamCylinderBlockEntity::clearRingState);
            withInletBlockEntity(level, pos, SteamInletBlockEntity::clearRingState);
            setRingState(level, pos, state, false, section, wallShape, Direction.UP, sharedWall);
        }
    }

    private static void assemble(
            Level level,
            Collection<RingData> rings,
            Map<BlockPos, List<RingData>> memberships,
            Map<BlockPos, List<RingData>> visualPartialMemberships
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
                RingVisual visual = visualFor(level, pos, owners, visualPartialMemberships);
                setRingState(
                        level,
                        pos,
                        state,
                        true,
                        visual.section(),
                        CylinderWallShape.STANDALONE,
                        primary.facing(),
                        visual.sharedWall()
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

    private static RingVisual visualFor(
            Level level,
            BlockPos pos,
            List<RingData> mechanicalOwners,
            Map<BlockPos, List<RingData>> visualPartialMemberships
    ) {
        List<RingData> visualOwners = visualOwnersFor(level, pos, mechanicalOwners, visualPartialMemberships);
        RingData visualPrimary = visualOwners.getFirst();
        return new RingVisual(sectionFor(visualPrimary.origin(), pos), sharedWallFor(visualOwners));
    }

    private static List<RingData> visualOwnersFor(
            Level level,
            BlockPos pos,
            List<RingData> mechanicalOwners,
            Map<BlockPos, List<RingData>> visualPartialMemberships
    ) {
        if (mechanicalOwners.size() != 1 || sharedWallFor(mechanicalOwners) != CylinderSharedWall.NONE) {
            return mechanicalOwners;
        }

        RingData mechanicalOwner = mechanicalOwners.getFirst();
        CylinderSharedWall existingSharedWall = currentSharedWall(level.getBlockState(pos));
        List<RingData> visualOwners = null;
        int bestScore = 0;
        for (RingData partialOwner : visualPartialMemberships.getOrDefault(pos, List.of())) {
            if (partialOwner.origin().equals(mechanicalOwner.origin())) {
                continue;
            }
            if (!canShareWallAt(level, pos, mechanicalOwner, partialOwner)) {
                continue;
            }

            List<RingData> candidateOwners = orderedSharedOwners(mechanicalOwner, partialOwner);
            CylinderSharedWall candidateSharedWall = sharedWallFor(candidateOwners);
            if (!isMechanicalBoundaryForSharedCandidate(mechanicalOwner, partialOwner, pos, candidateSharedWall)) {
                continue;
            }
            if (!hasSharedModelVariant(candidateOwners.getFirst().origin(), pos, candidateSharedWall)) {
                continue;
            }
            int candidateScore = visualOwnerScore(mechanicalOwner, partialOwner, pos, candidateSharedWall);
            if (candidateScore == 0) {
                continue;
            }
            if (candidateSharedWall == existingSharedWall) {
                candidateScore++;
            }
            if (candidateScore == bestScore) {
                return mechanicalOwners;
            }
            if (candidateScore < bestScore) {
                continue;
            }
            bestScore = candidateScore;
            visualOwners = candidateOwners;
        }

        return visualOwners == null ? mechanicalOwners : visualOwners;
    }

    private static boolean isMechanicalBoundaryForSharedCandidate(
            RingData mechanicalOwner,
            RingData partialOwner,
            BlockPos pos,
            CylinderSharedWall sharedWall
    ) {
        CylinderSection mechanicalSection = sectionFor(mechanicalOwner.origin(), pos);
        if (mechanicalSection == CylinderSection.NONE || sharedWall == CylinderSharedWall.NONE) {
            return false;
        }

        int dx = partialOwner.origin().getX() - mechanicalOwner.origin().getX();
        int dz = partialOwner.origin().getZ() - mechanicalOwner.origin().getZ();
        if (sharedWall == CylinderSharedWall.STRIP_Z) {
            if (dx == -2 && dz == 0) {
                return mechanicalSection.xOffset() == 0;
            }
            if (dx == 2 && dz == 0) {
                return mechanicalSection.xOffset() == 2;
            }
        }
        if (sharedWall == CylinderSharedWall.STRIP_X) {
            if (dz == -2 && dx == 0) {
                return mechanicalSection.zOffset() == 0;
            }
            if (dz == 2 && dx == 0) {
                return mechanicalSection.zOffset() == 2;
            }
        }
        return false;
    }

    private static List<RingData> orderedSharedOwners(RingData first, RingData second) {
        List<RingData> owners = new ArrayList<>(List.of(first, second));
        owners.sort(Comparator.comparing(RingData::origin, ROOT_ORDER));
        return owners;
    }

    private static CylinderSharedWall currentSharedWall(BlockState state) {
        if (!state.is(ModBlocks.STEAM_CYLINDER.get()) || !state.hasProperty(SteamCylinderBlock.SHARED_WALL)) {
            return CylinderSharedWall.NONE;
        }
        return state.getValue(SteamCylinderBlock.SHARED_WALL);
    }

    private static boolean hasSharedModelVariant(
            BlockPos visualOrigin,
            BlockPos pos,
            CylinderSharedWall sharedWall
    ) {
        CylinderSection section = sectionFor(visualOrigin, pos);
        if (section == CylinderSection.NONE || sharedWall == CylinderSharedWall.NONE) {
            return false;
        }
        if (sharedWall == CylinderSharedWall.STRIP_Z) {
            return section.xOffset() == 2;
        }
        if (sharedWall == CylinderSharedWall.STRIP_X) {
            return section.zOffset() == 2;
        }
        return false;
    }

    private static int visualOwnerScore(
            RingData mechanicalOwner,
            RingData partialOwner,
            BlockPos pos,
            CylinderSharedWall sharedWall
    ) {
        int outsideBlocks = outsidePartialBlockCount(mechanicalOwner, partialOwner);
        int sharedStripBlocks = sharedStripBlockCount(mechanicalOwner, partialOwner, pos, sharedWall);
        if (outsideBlocks == 0 || sharedStripBlocks < 2) {
            return 0;
        }
        return outsideBlocks * 100 + sharedStripBlocks * 10;
    }

    private static int outsidePartialBlockCount(RingData mechanicalOwner, RingData partialOwner) {
        Set<BlockPos> mechanicalPositions = new LinkedHashSet<>(mechanicalOwner.positions());
        int count = 0;
        for (BlockPos partialPos : partialOwner.positions()) {
            if (!mechanicalPositions.contains(partialPos)) {
                count++;
            }
        }
        return count;
    }

    private static int sharedStripBlockCount(
            RingData mechanicalOwner,
            RingData partialOwner,
            BlockPos pos,
            CylinderSharedWall sharedWall
    ) {
        List<RingData> owners = orderedSharedOwners(mechanicalOwner, partialOwner);
        BlockPos origin = owners.getFirst().origin();
        int yOffset = pos.getY() - origin.getY();
        if (yOffset < 0 || yOffset > 1) {
            return 0;
        }

        Set<BlockPos> mechanicalPositions = new LinkedHashSet<>(mechanicalOwner.positions());
        Set<BlockPos> partialPositions = new LinkedHashSet<>(partialOwner.positions());
        int sharedBlocks = 0;
        for (int offset = 0; offset <= 2; offset++) {
            BlockPos sharedPos = sharedWall == CylinderSharedWall.STRIP_Z
                    ? origin.offset(2, yOffset, offset)
                    : origin.offset(offset, yOffset, 2);
            if (mechanicalPositions.contains(sharedPos) && partialPositions.contains(sharedPos)) {
                sharedBlocks++;
            }
        }
        return sharedBlocks;
    }

    private static Direction ringFacing(Level level, BlockPos origin) {
        BlockPos lowerCenter = origin.offset(1, 0, 1);
        BlockPos upperCenter = origin.offset(1, 1, 1);

        boolean lowerHead = isPistonHead(level, lowerCenter);
        boolean upperHead = isPistonHead(level, upperCenter);
        if (upperHead && !lowerHead) {
            return Direction.DOWN;
        }
        if (lowerHead && !upperHead) {
            return Direction.UP;
        }
        return currentRingFacing(level, origin).orElse(Direction.UP);
    }

    private static Optional<Direction> currentRingFacing(Level level, BlockPos origin) {
        int up = 0;
        int down = 0;
        for (BlockPos pos : ringPositions(origin)) {
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(SteamCylinderBlock.FACING)) {
                if (state.getValue(SteamCylinderBlock.FACING) == Direction.DOWN) {
                    down++;
                } else {
                    up++;
                }
            } else if (state.hasProperty(SteamInletBlock.FACING)) {
                if (state.getValue(SteamInletBlock.FACING) == Direction.DOWN) {
                    down++;
                } else {
                    up++;
                }
            }
        }

        if (down > up) {
            return Optional.of(Direction.DOWN);
        }
        if (up > down) {
            return Optional.of(Direction.UP);
        }
        return Optional.empty();
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

    private static Map<BlockPos, PartialVisual> inferPartialVisuals(
            Level level,
            Set<BlockPos> positions,
            Set<BlockPos> protectedPositions,
            BlockPos ignoredRingMemberPos
    ) {
        Set<BlockPos> members = collectRingMembers(level, positions, protectedPositions, ignoredRingMemberPos);

        Map<BlockPos, RingData> partialRings = findPartialRings(level, members);
        Map<BlockPos, List<RingData>> memberships = buildMemberships(partialRings.values());
        Map<BlockPos, PartialVisual> assignments = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, List<RingData>> entry : memberships.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos) || !isRingMember(level.getBlockState(pos))) {
                continue;
            }

            List<RingData> owners = entry.getValue();
            RingData primary = owners.getFirst();
            CylinderSection section = sectionFor(primary.origin(), pos);
            if (section == CylinderSection.NONE) {
                continue;
            }

            CylinderSharedWall sharedWall = level.getBlockState(pos).is(ModBlocks.STEAM_CYLINDER.get())
                    ? sharedWallFor(owners)
                    : CylinderSharedWall.NONE;
            assignments.put(pos, new PartialVisual(section, CylinderWallShape.STANDALONE, sharedWall));
        }
        for (BlockPos pos : members) {
            if (!assignments.containsKey(pos) && level.getBlockState(pos).is(ModBlocks.STEAM_CYLINDER.get())) {
                assignments.put(pos, new PartialVisual(
                        CylinderSection.NONE,
                        localStraightWallShape(level, pos, members),
                        CylinderSharedWall.NONE
                ));
            }
        }
        return assignments;
    }

    private static Set<BlockPos> collectRingMembers(
            Level level,
            Set<BlockPos> positions,
            Set<BlockPos> excludedPositions,
            BlockPos ignoredRingMemberPos
    ) {
        Set<BlockPos> members = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (!excludedPositions.contains(pos)
                    && level.isLoaded(pos)
                    && getRingMemberAt(level, pos, ignoredRingMemberPos) != RingMember.NONE) {
                members.add(pos);
            }
        }
        return members;
    }

    private static Map<BlockPos, RingData> findPartialRings(
            Level level,
            Set<BlockPos> members
    ) {
        Map<BlockPos, PartialRingCandidate> candidates = new LinkedHashMap<>();
        for (BlockPos pos : members) {
            for (BlockPos origin : partialCandidateOrigins(level, members, pos)) {
                List<BlockPos> positions = partialRingPositions(origin, members);
                if (positions.size() < 3 || hasTooManyInlets(level, positions)) {
                    continue;
                }
                int score = partialRingScore(level, origin, positions, members);
                candidates.merge(
                        origin,
                        new PartialRingCandidate(new RingData(origin, positions, Direction.UP, null, null), score),
                        (previous, current) -> previous.score() >= current.score() ? previous : current
                );
            }
        }

        List<PartialRingGroup> sorted = partialRingGroups(level, candidates.values());
        sorted.sort((first, second) -> {
            int scoreCompare = Integer.compare(second.score(), first.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }

            int sizeCompare = Integer.compare(second.rings().size(), first.rings().size());
            if (sizeCompare != 0) {
                return sizeCompare;
            }

            int firstOriginCompare = ROOT_ORDER.compare(first.rings().getFirst().origin(), second.rings().getFirst().origin());
            if (firstOriginCompare != 0) {
                return firstOriginCompare;
            }

            return ROOT_ORDER.compare(first.rings().getLast().origin(), second.rings().getLast().origin());
        });

        Map<BlockPos, RingData> selected = new LinkedHashMap<>();
        Map<BlockPos, List<RingData>> memberships = new LinkedHashMap<>();
        for (PartialRingGroup group : sorted) {
            List<RingData> ringsToAdd = unselectedRings(group, selected);
            if (ringsToAdd.isEmpty() || !canAddPartialGroup(level, ringsToAdd, memberships)) {
                continue;
            }

            for (RingData ring : ringsToAdd) {
                selected.put(ring.origin(), ring);
                addRingMembership(memberships, ring);
            }
        }
        return selected;
    }

    private static Set<BlockPos> partialCandidateOrigins(Level level, Set<BlockPos> members, BlockPos pos) {
        Set<BlockPos> origins = new LinkedHashSet<>(partialOriginsFromLocalCorners(members, pos));
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.STEAM_CYLINDER.get())
                && state.hasProperty(SteamCylinderBlock.SHARED_WALL)
                && state.getValue(SteamCylinderBlock.SHARED_WALL) != CylinderSharedWall.NONE) {
            origins.addAll(trackedPartialRingOrigins(state, pos));
        }
        return origins;
    }

    private static List<PartialRingGroup> partialRingGroups(
            Level level,
            Collection<PartialRingCandidate> candidates
    ) {
        List<PartialRingCandidate> sortedCandidates = new ArrayList<>(candidates);
        sortedCandidates.sort(Comparator.comparing(candidate -> candidate.ring().origin(), ROOT_ORDER));

        List<PartialRingGroup> groups = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < sortedCandidates.size(); firstIndex++) {
            PartialRingCandidate first = sortedCandidates.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < sortedCandidates.size(); secondIndex++) {
                PartialRingCandidate second = sortedCandidates.get(secondIndex);
                int sharedBlocks = sharedPartialWallBlockCount(level, first.ring(), second.ring());
                if (sharedBlocks == 0) {
                    continue;
                }

                groups.add(new PartialRingGroup(
                        List.of(first.ring(), second.ring()),
                        first.score()
                                + second.score()
                                + 500
                                + sharedBlocks * 50
                                + existingSharedWallBlockCount(level, first.ring(), second.ring()) * 300
                ));
            }
        }

        for (PartialRingCandidate candidate : sortedCandidates) {
            groups.add(new PartialRingGroup(List.of(candidate.ring()), candidate.score()));
        }
        return groups;
    }

    private static List<RingData> unselectedRings(PartialRingGroup group, Map<BlockPos, RingData> selected) {
        List<RingData> rings = new ArrayList<>(group.rings().size());
        for (RingData ring : group.rings()) {
            if (!selected.containsKey(ring.origin())) {
                rings.add(ring);
            }
        }
        return rings;
    }

    private static Set<BlockPos> partialOriginsFromLocalCorners(Set<BlockPos> members, BlockPos pos) {
        Set<BlockPos> origins = new LinkedHashSet<>();
        boolean north = hasNeighbor(members, pos, Direction.NORTH);
        boolean east = hasNeighbor(members, pos, Direction.EAST);
        boolean south = hasNeighbor(members, pos, Direction.SOUTH);
        boolean west = hasNeighbor(members, pos, Direction.WEST);

        for (int verticalOffset : partialOriginVerticalOffsets(members, pos)) {
            if (east && south) {
                origins.add(pos.offset(0, verticalOffset, 0));
            }
            if (south && west) {
                origins.add(pos.offset(-2, verticalOffset, 0));
            }
            if (west && north) {
                origins.add(pos.offset(-2, verticalOffset, -2));
            }
            if (north && east) {
                origins.add(pos.offset(0, verticalOffset, -2));
            }
        }
        return origins;
    }

    private static List<Integer> partialOriginVerticalOffsets(Set<BlockPos> members, BlockPos pos) {
        List<Integer> offsets = new ArrayList<>(2);
        if (members.contains(pos.below())) {
            offsets.add(-1);
        }
        if (members.contains(pos.above()) || offsets.isEmpty()) {
            offsets.add(0);
        }
        return offsets;
    }

    private static List<BlockPos> partialRingPositions(BlockPos origin, Set<BlockPos> members) {
        List<BlockPos> positions = new ArrayList<>(16);
        for (BlockPos ringPos : ringPositions(origin)) {
            if (members.contains(ringPos)) {
                positions.add(ringPos);
            }
        }
        return positions;
    }

    private static boolean hasTooManyInlets(Level level, List<BlockPos> positions) {
        int inlets = 0;
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).is(ModBlocks.STEAM_INLET.get()) && ++inlets > 1) {
                return true;
            }
        }
        return false;
    }

    private static int partialRingScore(
            Level level,
            BlockPos origin,
            List<BlockPos> positions,
            Set<BlockPos> members
    ) {
        int score = positions.size() * 10;
        score += cornerEvidenceCount(origin, members) * 100;
        score += edgeAdjacencyCount(positions) * 5;
        score += existingSectionMatches(level, origin, positions) * 25;
        return score;
    }

    private static int cornerEvidenceCount(BlockPos origin, Set<BlockPos> members) {
        int evidence = 0;
        if (hasCorner(members, origin, Direction.EAST, Direction.SOUTH)) {
            evidence++;
        }
        if (hasCorner(members, origin.offset(2, 0, 0), Direction.WEST, Direction.SOUTH)) {
            evidence++;
        }
        if (hasCorner(members, origin.offset(2, 0, 2), Direction.WEST, Direction.NORTH)) {
            evidence++;
        }
        if (hasCorner(members, origin.offset(0, 0, 2), Direction.EAST, Direction.NORTH)) {
            evidence++;
        }
        if (hasCorner(members, origin.above(), Direction.EAST, Direction.SOUTH)) {
            evidence++;
        }
        if (hasCorner(members, origin.offset(2, 1, 0), Direction.WEST, Direction.SOUTH)) {
            evidence++;
        }
        if (hasCorner(members, origin.offset(2, 1, 2), Direction.WEST, Direction.NORTH)) {
            evidence++;
        }
        if (hasCorner(members, origin.offset(0, 1, 2), Direction.EAST, Direction.NORTH)) {
            evidence++;
        }
        return evidence;
    }

    private static boolean hasCorner(Set<BlockPos> members, BlockPos pos, Direction first, Direction second) {
        return members.contains(pos) && hasNeighbor(members, pos, first) && hasNeighbor(members, pos, second);
    }

    private static int edgeAdjacencyCount(List<BlockPos> positions) {
        Set<BlockPos> positionSet = new LinkedHashSet<>(positions);
        int edges = 0;
        for (BlockPos pos : positions) {
            if (positionSet.contains(pos.east())) {
                edges++;
            }
            if (positionSet.contains(pos.south())) {
                edges++;
            }
            if (positionSet.contains(pos.above())) {
                edges++;
            }
        }
        return edges;
    }

    private static int existingSectionMatches(Level level, BlockPos origin, List<BlockPos> positions) {
        int matches = 0;
        for (BlockPos pos : positions) {
            CylinderSection current = currentSection(level.getBlockState(pos));
            if (current != CylinderSection.NONE && current == sectionFor(origin, pos)) {
                matches++;
            }
        }
        return matches;
    }

    private static CylinderSection currentSection(BlockState state) {
        if (state.hasProperty(SteamCylinderBlock.SECTION)) {
            return state.getValue(SteamCylinderBlock.SECTION);
        }
        if (state.hasProperty(SteamInletBlock.SECTION)) {
            return state.getValue(SteamInletBlock.SECTION);
        }
        return CylinderSection.NONE;
    }

    private static boolean canAddPartialRing(
            Level level,
            RingData ring,
            Map<BlockPos, List<RingData>> selectedMemberships
    ) {
        for (BlockPos pos : ring.positions()) {
            List<RingData> owners = selectedMemberships.get(pos);
            if (owners == null) {
                continue;
            }
            if (owners.size() >= 2) {
                return false;
            }
            if (!canShareWallAt(level, pos, owners.getFirst(), ring)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canAddPartialGroup(
            Level level,
            List<RingData> rings,
            Map<BlockPos, List<RingData>> selectedMemberships
    ) {
        Map<BlockPos, List<RingData>> trialMemberships = copyMemberships(selectedMemberships);
        for (RingData ring : rings) {
            if (!canAddPartialRing(level, ring, trialMemberships)) {
                return false;
            }
            addRingMembership(trialMemberships, ring);
        }
        return true;
    }

    private static Map<BlockPos, List<RingData>> copyMemberships(Map<BlockPos, List<RingData>> memberships) {
        Map<BlockPos, List<RingData>> copy = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, List<RingData>> entry : memberships.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private static void addRingMembership(Map<BlockPos, List<RingData>> memberships, RingData ring) {
        for (BlockPos pos : ring.positions()) {
            memberships.computeIfAbsent(pos, ignored -> new ArrayList<>(2)).add(ring);
        }
    }

    private static int sharedPartialWallBlockCount(Level level, RingData first, RingData second) {
        if (sharedBankAxis(first.origin(), second.origin()).isEmpty()) {
            return 0;
        }

        Set<BlockPos> firstPositions = new LinkedHashSet<>(first.positions());
        int sharedBlocks = 0;
        for (BlockPos pos : second.positions()) {
            if (!firstPositions.contains(pos)) {
                continue;
            }
            if (!canShareWallAt(level, pos, first, second)) {
                return 0;
            }
            sharedBlocks++;
        }
        return sharedBlocks;
    }

    private static int existingSharedWallBlockCount(Level level, RingData first, RingData second) {
        CylinderSharedWall expectedSharedWall = sharedWallFor(List.of(first, second));
        if (expectedSharedWall == CylinderSharedWall.NONE) {
            return 0;
        }

        Set<BlockPos> firstPositions = new LinkedHashSet<>(first.positions());
        int matches = 0;
        for (BlockPos pos : second.positions()) {
            if (!firstPositions.contains(pos) || !level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.STEAM_CYLINDER.get())
                    && state.hasProperty(SteamCylinderBlock.SHARED_WALL)
                    && state.getValue(SteamCylinderBlock.SHARED_WALL) == expectedSharedWall) {
                matches++;
            }
        }
        return matches;
    }

    private static CylinderWallShape localStraightWallShape(Level level, BlockPos pos, Set<BlockPos> members) {
        boolean east = hasNeighbor(members, pos, Direction.EAST);
        boolean west = hasNeighbor(members, pos, Direction.WEST);
        boolean north = hasNeighbor(members, pos, Direction.NORTH);
        boolean south = hasNeighbor(members, pos, Direction.SOUTH);

        if (east || west) {
            return CylinderWallShape.STRAIGHT_X;
        }
        if (north || south) {
            return CylinderWallShape.STRAIGHT_Z;
        }
        return currentStandaloneShape(level.getBlockState(pos));
    }

    private static CylinderWallShape currentStandaloneShape(BlockState state) {
        if (state.hasProperty(SteamCylinderBlock.WALL_SHAPE)) {
            CylinderWallShape shape = state.getValue(SteamCylinderBlock.WALL_SHAPE);
            return switch (shape) {
                case STRAIGHT_X, SHARED_STRIP_X -> CylinderWallShape.STRAIGHT_X;
                case STRAIGHT_Z, SHARED_STRIP_Z -> CylinderWallShape.STRAIGHT_Z;
                default -> CylinderWallShape.STANDALONE;
            };
        }
        return CylinderWallShape.STANDALONE;
    }

    private static boolean hasNeighbor(Set<BlockPos> members, BlockPos pos, Direction direction) {
        return members.contains(pos.relative(direction));
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

    private record PartialVisual(
            CylinderSection section,
            CylinderWallShape wallShape,
            CylinderSharedWall sharedWall
    ) {
    }

    private record RingVisual(
            CylinderSection section,
            CylinderSharedWall sharedWall
    ) {
    }

    private record PartialRingCandidate(
            RingData ring,
            int score
    ) {
    }

    private record PartialRingGroup(
            List<RingData> rings,
            int score
    ) {
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
