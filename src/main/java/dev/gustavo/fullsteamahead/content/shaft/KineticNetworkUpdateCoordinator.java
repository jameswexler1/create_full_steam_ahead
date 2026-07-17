package dev.gustavo.fullsteamahead.content.shaft;

import com.simibubi.create.content.kinetics.KineticNetwork;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Coalesces generator changes made by several engines during one block-entity tick pass.
 */
public final class KineticNetworkUpdateCoordinator {
    private static final Map<Level, Set<FullSteamPoweredShaftBlockEntity>> PENDING = new WeakHashMap<>();

    public static void register(IEventBus eventBus) {
        eventBus.addListener(KineticNetworkUpdateCoordinator::onLevelTick);
        eventBus.addListener(KineticNetworkUpdateCoordinator::onLevelUnload);
    }

    static void schedule(FullSteamPoweredShaftBlockEntity source) {
        Level level = source.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        PENDING.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(source);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        Set<FullSteamPoweredShaftBlockEntity> pending = PENDING.remove(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        List<FullSteamPoweredShaftBlockEntity> detached = new ArrayList<>();
        Map<Long, List<FullSteamPoweredShaftBlockEntity>> byNetwork = new HashMap<>();
        for (FullSteamPoweredShaftBlockEntity source : pending) {
            if (source.isRemoved() || source.getLevel() != level) {
                continue;
            }
            if (!source.hasNetwork() || source.network == null) {
                detached.add(source);
                continue;
            }
            byNetwork.computeIfAbsent(source.network, ignored -> new ArrayList<>()).add(source);
        }

        // Detached starts and topology-changing edge cases remain Create-owned.
        detached.forEach(FullSteamPoweredShaftBlockEntity::flushScheduledRotation);

        for (List<FullSteamPoweredShaftBlockEntity> changedSources : byNetwork.values()) {
            flushNetwork(changedSources);
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            PENDING.remove(level);
        }
    }

    private static void flushNetwork(List<FullSteamPoweredShaftBlockEntity> changedSources) {
        FullSteamPoweredShaftBlockEntity representative = changedSources.getFirst();
        FullSteamPoweredShaftBlockEntity owner = ActiveKineticNetworkRetimer.coordinatedOwner(representative);
        if (owner == null) {
            // Mixed-mod generators and malformed/stale topologies use Create's normal source rules.
            changedSources.forEach(FullSteamPoweredShaftBlockEntity::flushScheduledRotation);
            return;
        }

        owner.flushScheduledRotation();

        // updateGeneratedRotation() refreshes only the caller's capacity. Every other changed engine
        // remains an independent SU contributor, so refresh those values without another RPM pass.
        KineticNetwork network = owner.hasNetwork() ? owner.getOrCreateNetwork() : null;
        if (network == null) {
            return;
        }
        for (FullSteamPoweredShaftBlockEntity source : changedSources) {
            if (source == owner
                    || source.isRemoved()
                    || !source.hasNetwork()
                    || !source.network.equals(owner.network)) {
                continue;
            }
            source.refreshGeneratedCapacity(network);
        }
    }

    private KineticNetworkUpdateCoordinator() {
    }
}
