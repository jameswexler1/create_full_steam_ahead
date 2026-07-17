package dev.gustavo.fullsteamahead.content.shaft;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.gustavo.fullsteamahead.mixin.accessor.RotationPropagatorAccessor;
import net.minecraft.core.BlockPos;

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
    static boolean tryRetime(
            FullSteamPoweredShaftBlockEntity source,
            float previousSpeed,
            float targetSpeed
    ) {
        if (!KineticSpeedUpdatePolicy.canRetimeActiveNetworkInPlace(
                previousSpeed,
                targetSpeed,
                source.hasSource(),
                source.hasNetwork()
        )) {
            return false;
        }

        KineticNetwork network = source.getOrCreateNetwork();
        Set<KineticBlockEntity> networkEntities = Collections.newSetFromMap(new IdentityHashMap<>());
        networkEntities.add(source);
        networkEntities.addAll(network.sources.keySet());
        networkEntities.addAll(network.members.keySet());
        networkEntities.removeIf(blockEntity -> blockEntity.getLevel() != source.getLevel()
                || !Objects.equals(blockEntity.network, source.network));

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
        source.setSpeed(targetSpeed);
        visited.add(source);
        pending.add(source);

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

        // Update callbacks run only after every speed has been changed, so downstream blocks observe
        // one coherent network state. Components behind a fixed speed controller are not notified at
        // all when their final speed is unchanged, avoiding a false pump stop/start cycle.
        for (KineticBlockEntity blockEntity : changedDescendants) {
            blockEntity.onSpeedChanged(previousSpeeds.get(blockEntity));
            blockEntity.sendData();
        }
        return true;
    }

    private static void restoreSpeeds(Map<KineticBlockEntity, Float> previousSpeeds) {
        previousSpeeds.forEach(KineticBlockEntity::setSpeed);
    }

    private ActiveKineticNetworkRetimer() {
    }
}
