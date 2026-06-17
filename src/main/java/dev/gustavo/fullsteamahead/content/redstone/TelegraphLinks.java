package dev.gustavo.fullsteamahead.content.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-side registry pairing {@link SteppedLeverBlockEntity Engine Order Telegraphs} that share a
 * link id, modelled on Create's {@code RedstoneLinkNetworkHandler}: a per-level map keyed by the
 * shared {@link UUID}. Keyed by the live {@link LevelAccessor} instance (via {@link WeakHashMap}) so
 * a reloaded world gets a fresh map and unloaded levels are garbage-collected — no cross-save leak.
 *
 * <p>The map only tracks block positions; partner block entities are resolved lazily and only when
 * their chunk is loaded, so the registry never forces chunk loading. Stale loaded positions (a
 * telegraph that was replaced by another block, or relinked) are pruned on lookup, so the map
 * self-heals even if an unregister is missed.
 */
public final class TelegraphLinks {
    private static final Map<LevelAccessor, Map<UUID, Set<BlockPos>>> NETWORKS = new WeakHashMap<>();

    private TelegraphLinks() {
    }

    public static void add(LevelAccessor level, UUID linkId, BlockPos pos) {
        if (linkId == null) {
            return;
        }
        NETWORKS.computeIfAbsent(level, l -> new HashMap<>())
                .computeIfAbsent(linkId, id -> new HashSet<>())
                .add(pos.immutable());
    }

    public static void remove(LevelAccessor level, UUID linkId, BlockPos pos) {
        if (linkId == null) {
            return;
        }
        Map<UUID, Set<BlockPos>> byId = NETWORKS.get(level);
        if (byId == null) {
            return;
        }
        Set<BlockPos> positions = byId.get(linkId);
        if (positions == null) {
            return;
        }
        positions.remove(pos);
        if (positions.isEmpty()) {
            byId.remove(linkId);
        }
    }

    /**
     * Returns the loaded telegraphs sharing {@code linkId}, excluding {@code self}. Skips positions in
     * unloaded chunks (without loading them) and prunes loaded positions that no longer hold a
     * matching telegraph.
     */
    public static List<SteppedLeverBlockEntity> partners(Level level, UUID linkId, BlockPos self) {
        List<SteppedLeverBlockEntity> result = new ArrayList<>();
        if (linkId == null) {
            return result;
        }
        Map<UUID, Set<BlockPos>> byId = NETWORKS.get(level);
        if (byId == null) {
            return result;
        }
        Set<BlockPos> positions = byId.get(linkId);
        if (positions == null) {
            return result;
        }
        Iterator<BlockPos> it = positions.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (pos.equals(self)) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof SteppedLeverBlockEntity lever
                    && linkId.equals(lever.getLinkId())) {
                result.add(lever);
            } else {
                it.remove();
            }
        }
        return result;
    }
}
