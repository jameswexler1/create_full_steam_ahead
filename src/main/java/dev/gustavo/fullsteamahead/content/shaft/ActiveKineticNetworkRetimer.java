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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ActiveKineticNetworkRetimer {
    static void prepareNetworkCommand(FullSteamPoweredShaftBlockEntity source) {
        Coordination coordination = resolveCoordination(source);
        if (coordination == null || !coordination.selection().compatible()) {
            clearNetworkCommand(source);
            return;
        }
        if (!coordination.selection().active()) {
            clearNetworkCommand(source);
            // If the final active follower stopped, its normal Create update only removes its own
            // capacity. Prompt the zero-output owner to perform the one real network stop now.
            if (coordination.owner() != source
                    && coordination.owner().getIndividualGeneratedSpeed() == 0.0F
                    && coordination.owner().getTheoreticalSpeed() != 0.0F) {
                coordination.owner().updateGeneratedRotation();
            }
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
                || !coordination.selection().compatible()
                || !coordination.selection().active()) {
            // A real stop, reversal, or source conflict still needs Create's topology propagation.
            return false;
        }

        KineticNetwork network = coordination.network();
        Set<KineticBlockEntity> networkEntities = coordination.networkEntities();
        FullSteamPoweredShaftBlockEntity owner = coordination.owner();

        Map<BlockPos, List<KineticBlockEntity>> childrenBySource = new HashMap<>();
        Map<KineticBlockEntity, Float> previousSpeeds = new IdentityHashMap<>();
        for (KineticBlockEntity blockEntity : networkEntities) {
            previousSpeeds.put(blockEntity, blockEntity.getTheoreticalSpeed());
            if (blockEntity.source != null) {
                childrenBySource.computeIfAbsent(blockEntity.source, ignored -> new ArrayList<>())
                        .add(blockEntity);
            }
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
            List<KineticBlockEntity> children = childrenBySource.get(parent.getBlockPos());
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
        for (KineticBlockEntity blockEntity : networkEntities) {
            if (blockEntity instanceof FullSteamPoweredShaftBlockEntity fullSteamSource) {
                sourceSpeeds.add(new SharedShaftSpeedPolicy.SourceSpeed(
                        fullSteamSource.getTheoreticalSpeed(),
                        fullSteamSource.getIndividualGeneratedSpeed()
                ));
            } else if (blockEntity.isSource()) {
                // Let Create resolve networks containing another mod's generator.
                return null;
            }
        }

        SharedShaftSpeedPolicy.Selection selection = SharedShaftSpeedPolicy.select(
                owner.getTheoreticalSpeed(),
                sourceSpeeds
        );
        return new Coordination(network, networkEntities, owner, selection);
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
            SharedShaftSpeedPolicy.Selection selection
    ) {
    }

    private ActiveKineticNetworkRetimer() {
    }
}
