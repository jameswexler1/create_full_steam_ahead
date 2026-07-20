package dev.gustavo.fullsteamahead.content.shaft;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.gustavo.fullsteamahead.mixin.accessor.RotationPropagatorAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ActiveKineticNetworkRetimer {
    static NetworkUpdate resolveNetworkUpdate(FullSteamPoweredShaftBlockEntity source) {
        Coordination coordination = resolveCoordination(source);
        if (coordination == null) {
            return NetworkUpdate.DEFER_TO_CREATE;
        }

        return switch (decisionFor(coordination)) {
            case COORDINATE -> NetworkUpdate.coordinate(coordination.owner());
            case FORCE_STOP -> {
                stopNetwork(coordination);
                yield NetworkUpdate.STOPPED;
            }
            case DEFER_TO_CREATE -> NetworkUpdate.DEFER_TO_CREATE;
        };
    }

    private static void stopNetwork(Coordination coordination) {
        Map<KineticBlockEntity, Float> previousSpeeds = new IdentityHashMap<>();
        for (KineticBlockEntity blockEntity : coordination.networkEntities()) {
            previousSpeeds.put(blockEntity, blockEntity.getTheoreticalSpeed());
            blockEntity.setSpeed(0.0F);
            if (blockEntity instanceof FullSteamPoweredShaftBlockEntity fullSteamSource) {
                fullSteamSource.setCoordinatedOwnerSpeed(0.0F);
            }
        }

        // Create normally removes a source tree from its root. A stale all-FSA tree can instead
        // contain a closed source cycle, so no member is recognized as the root. Clear the entire
        // already-copied network in two passes so callbacks cannot observe another dead member as a
        // still-running source. Unknown or externally rooted graphs never enter this path.
        for (KineticBlockEntity blockEntity : coordination.networkEntities()) {
            coordination.network().sources.remove(blockEntity);
            blockEntity.source = null;
            blockEntity.sequenceContext = null;
        }
        for (KineticBlockEntity blockEntity : coordination.networkEntities()) {
            blockEntity.setNetwork(null);
        }
        for (KineticBlockEntity blockEntity : coordination.networkEntities()) {
            blockEntity.onSpeedChanged(previousSpeeds.get(blockEntity));
            blockEntity.sendData();
        }
    }

    static void prepareNetworkCommand(FullSteamPoweredShaftBlockEntity source) {
        Coordination coordination = resolveCoordination(source);
        if (coordination == null
                || decisionFor(coordination) != FsaKineticNetworkPolicy.Decision.COORDINATE) {
            clearNetworkCommand(source);
            return;
        }

        for (KineticBlockEntity blockEntity : coordination.networkEntities()) {
            if (blockEntity instanceof FullSteamPoweredShaftBlockEntity fullSteamSource) {
                fullSteamSource.setCoordinatedOwnerSpeed(
                        fullSteamSource == coordination.owner()
                                ? coordination.selection().ownerSpeed()
                                : 0.0F
                );
            }
        }
    }

    static boolean tryRetime(
            FullSteamPoweredShaftBlockEntity source,
            float previousSpeed
    ) {
        if (!KineticSpeedUpdatePolicy.canCoordinateActiveNetwork(previousSpeed, source.hasNetwork())) {
            return false;
        }

        Coordination coordination = resolveCoordination(source);
        if (coordination == null
                || decisionFor(coordination) != FsaKineticNetworkPolicy.Decision.COORDINATE) {
            // A real stop, reversal, or source conflict still needs Create's topology propagation.
            return false;
        }

        KineticNetwork network = coordination.network();
        Set<KineticBlockEntity> networkEntities = coordination.networkEntities();
        FullSteamPoweredShaftBlockEntity owner = coordination.owner();

        Map<KineticBlockEntity, Float> previousSpeeds = new IdentityHashMap<>();
        for (KineticBlockEntity blockEntity : networkEntities) {
            previousSpeeds.put(blockEntity, blockEntity.getTheoreticalSpeed());
        }
        Map<KineticBlockEntity, List<KineticBlockEntity>> childrenBySource = resolveChildrenBySource(
                networkEntities,
                previousSpeeds
        );
        if (childrenBySource == null) {
            return false;
        }

        Set<KineticBlockEntity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<KineticBlockEntity> changedDescendants = new ArrayList<>();
        ArrayDeque<KineticBlockEntity> pending = new ArrayDeque<>();
        float ownerTargetSpeed = coordination.selection().ownerSpeed();
        owner.setSpeed(ownerTargetSpeed);
        visited.add(owner);
        pending.add(owner);

        while (!pending.isEmpty()) {
            KineticBlockEntity parent = pending.removeFirst();
            List<KineticBlockEntity> children = childrenBySource.get(parent);
            if (children == null) {
                continue;
            }

            for (KineticBlockEntity child : children) {
                if (!visited.add(child)) {
                    continue;
                }

                float oldChildSpeed = previousSpeeds.getOrDefault(child, child.getTheoreticalSpeed());
                float newChildSpeed = RotationPropagatorAccessor.fullSteamAhead$getConveyedSpeed(parent, child);
                if (!Float.isFinite(newChildSpeed)
                        || (oldChildSpeed != 0.0F && newChildSpeed == 0.0F)) {
                    restoreSpeeds(previousSpeeds);
                    return false;
                }

                child.setSpeed(newChildSpeed);
                if (!KineticSpeedUpdatePolicy.sameSpeed(oldChildSpeed, newChildSpeed)) {
                    changedDescendants.add(child);
                }
                pending.addLast(child);
            }
        }

        if (visited.size() != networkEntities.size()) {
            restoreSpeeds(previousSpeeds);
            return false;
        }

        if (!KineticSpeedUpdatePolicy.sameSpeed(previousSpeeds.get(owner), ownerTargetSpeed)) {
            changedDescendants.add(owner);
        }

        if (source.getGeneratedSpeed() == 0.0F) {
            // updateGeneratedRotation() skips its normal capacity branch for a stopped source.
            network.updateCapacityFor(source, 0.0F);
        }
        network.updateStress();

        // Update callbacks run only after every speed has been changed, so downstream blocks observe
        // one coherent network state. Components behind a fixed speed controller are not notified at
        // all when their final speed is unchanged, avoiding a false pump stop/start cycle. The caller
        // receives its callback from GeneratingKineticBlockEntity.updateGeneratedRotation().
        for (KineticBlockEntity blockEntity : changedDescendants) {
            if (blockEntity == source) {
                continue;
            }
            blockEntity.onSpeedChanged(previousSpeeds.get(blockEntity));
            blockEntity.sendData();
        }
        return true;
    }

    private static Map<KineticBlockEntity, List<KineticBlockEntity>> resolveChildrenBySource(
            Set<KineticBlockEntity> networkEntities,
            Map<KineticBlockEntity, Float> currentSpeeds
    ) {
        Map<SourceCoordinate, List<KineticBlockEntity>> candidatesByCoordinate = new HashMap<>();
        for (KineticBlockEntity blockEntity : networkEntities) {
            candidatesByCoordinate
                    .computeIfAbsent(SourceCoordinate.of(blockEntity.getBlockPos()), ignored -> new ArrayList<>())
                    .add(blockEntity);
        }

        Map<KineticBlockEntity, List<KineticBlockEntity>> childrenBySource = new IdentityHashMap<>();
        for (KineticBlockEntity child : networkEntities) {
            if (child.source == null) {
                continue;
            }

            List<KineticBlockEntity> candidates = candidatesByCoordinate.get(SourceCoordinate.of(child.source));
            KineticBlockEntity parent = resolveSource(child, candidates, currentSpeeds.get(child));
            if (parent == null) {
                return null;
            }
            childrenBySource.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(child);
        }
        return childrenBySource;
    }

    private static KineticBlockEntity resolveSource(
            KineticBlockEntity child,
            List<KineticBlockEntity> possibleSources,
            float currentChildSpeed
    ) {
        if (possibleSources == null) {
            return null;
        }

        List<KineticSourceResolver.Candidate<KineticBlockEntity>> candidates = new ArrayList<>();
        for (KineticBlockEntity candidate : possibleSources) {
            if (candidate == child) {
                continue;
            }

            BlockPos candidatePos = candidate.getBlockPos();
            boolean explicitPositionType = child.source.getClass() != BlockPos.class
                    && candidatePos.getClass() == child.source.getClass();
            candidates.add(new KineticSourceResolver.Candidate<>(
                    candidate,
                    candidatePos == child.source,
                    explicitPositionType,
                    RotationPropagatorAccessor.fullSteamAhead$getConveyedSpeed(candidate, child)
            ));
        }
        return KineticSourceResolver.resolve(currentChildSpeed, candidates);
    }

    private record SourceCoordinate(int x, int y, int z) {
        private static SourceCoordinate of(BlockPos pos) {
            return new SourceCoordinate(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    private static Coordination resolveCoordination(FullSteamPoweredShaftBlockEntity source) {
        if (!source.hasNetwork()) {
            return null;
        }

        KineticNetwork network = source.getOrCreateNetwork();
        Set<KineticBlockEntity> networkEntities = networkEntities(source, network);
        FullSteamPoweredShaftBlockEntity owner = networkOwner(source, network);
        if (owner == null
                || !networkEntities.contains(owner)
                || !KineticSpeedUpdatePolicy.canCoordinateActiveNetwork(
                        owner.getTheoreticalSpeed(),
                        owner.hasNetwork()
                )) {
            return null;
        }

        List<SharedShaftSpeedPolicy.SourceSpeed> sourceSpeeds = new ArrayList<>();
        boolean hasExternalSource = false;
        for (KineticBlockEntity blockEntity : networkEntities) {
            if (blockEntity instanceof FullSteamPoweredShaftBlockEntity fullSteamSource) {
                sourceSpeeds.add(new SharedShaftSpeedPolicy.SourceSpeed(
                        fullSteamSource.getTheoreticalSpeed(),
                        fullSteamSource.getIndividualGeneratedSpeed()
                ));
            } else if (blockEntity.isSource()) {
                hasExternalSource = true;
            }
        }

        SharedShaftSpeedPolicy.Selection selection = SharedShaftSpeedPolicy.select(
                owner.getTheoreticalSpeed(),
                sourceSpeeds
        );
        return new Coordination(network, networkEntities, owner, selection, hasExternalSource);
    }

    private static Set<KineticBlockEntity> networkEntities(
            FullSteamPoweredShaftBlockEntity source,
            KineticNetwork network
    ) {
        Set<KineticBlockEntity> networkEntities = Collections.newSetFromMap(new IdentityHashMap<>());
        networkEntities.add(source);
        networkEntities.addAll(network.sources.keySet());
        networkEntities.addAll(network.members.keySet());
        networkEntities.removeIf(blockEntity -> blockEntity.isRemoved()
                || blockEntity.getLevel() != source.getLevel()
                || !Objects.equals(blockEntity.network, source.network));
        return networkEntities;
    }

    private static boolean hasClosedSourceGraph(Set<KineticBlockEntity> networkEntities) {
        Set<SourceCoordinate> memberCoordinates = new HashSet<>();
        for (KineticBlockEntity blockEntity : networkEntities) {
            memberCoordinates.add(SourceCoordinate.of(blockEntity.getBlockPos()));
        }
        for (KineticBlockEntity blockEntity : networkEntities) {
            if (blockEntity.source != null
                    && !memberCoordinates.contains(SourceCoordinate.of(blockEntity.source))) {
                return false;
            }
        }
        return true;
    }

    private static FsaKineticNetworkPolicy.Decision decisionFor(Coordination coordination) {
        return FsaKineticNetworkPolicy.decide(
                coordination.selection().compatible(),
                coordination.selection().active(),
                coordination.hasExternalSource(),
                hasClosedSourceGraph(coordination.networkEntities())
        );
    }

    private static void clearNetworkCommand(FullSteamPoweredShaftBlockEntity source) {
        source.setCoordinatedOwnerSpeed(0.0F);
        if (!source.hasNetwork()) {
            return;
        }
        KineticNetwork network = source.getOrCreateNetwork();
        for (KineticBlockEntity blockEntity : networkEntities(source, network)) {
            if (blockEntity instanceof FullSteamPoweredShaftBlockEntity fullSteamSource) {
                fullSteamSource.setCoordinatedOwnerSpeed(0.0F);
            }
        }
    }

    private static FullSteamPoweredShaftBlockEntity networkOwner(
            FullSteamPoweredShaftBlockEntity source,
            KineticNetwork network
    ) {
        if (network.id == null || source.getLevel() == null) {
            return null;
        }
        BlockPos ownerPos = BlockPos.of(network.id);
        if (!source.getLevel().isLoaded(ownerPos)) {
            return null;
        }
        BlockEntity blockEntity = source.getLevel().getBlockEntity(ownerPos);
        return blockEntity instanceof FullSteamPoweredShaftBlockEntity owner ? owner : null;
    }

    private static void restoreSpeeds(Map<KineticBlockEntity, Float> previousSpeeds) {
        previousSpeeds.forEach(KineticBlockEntity::setSpeed);
    }

    private record Coordination(
            KineticNetwork network,
            Set<KineticBlockEntity> networkEntities,
            FullSteamPoweredShaftBlockEntity owner,
            SharedShaftSpeedPolicy.Selection selection,
            boolean hasExternalSource
    ) {
    }

    record NetworkUpdate(boolean stopped, FullSteamPoweredShaftBlockEntity owner) {
        private static final NetworkUpdate STOPPED = new NetworkUpdate(true, null);
        private static final NetworkUpdate DEFER_TO_CREATE = new NetworkUpdate(false, null);

        private static NetworkUpdate coordinate(FullSteamPoweredShaftBlockEntity owner) {
            return new NetworkUpdate(false, owner);
        }
    }

    private ActiveKineticNetworkRetimer() {
    }
}
