package dev.gustavo.fullsteamahead.compat.simulated;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional Sable integration for boiler bursts on simulated sublevels.
 *
 * <p>Sable projects vanilla explosions out of sublevels and already lets their rays damage the
 * contraption's stored blocks (resistance-aware; one world-space ray step can cross several rotated
 * sublevel blocks, so dense engine rooms absorb rays quickly and the blast core often survives).
 * This class keeps Sable optional and adds a bounded local crater pass around the boiler, run
 * AFTER the projected explosion so its rays meet intact armor first (parity with ground bursts).
 * Unbreakable and blast-proof blocks survive, mirroring vanilla explosion rules.</p>
 */
public final class SableBurstCompat {
    private static boolean initialized;
    private static boolean available;
    private static Object helper;
    private static Method getContainingBlockEntity;
    private static Method projectOutOfSubLevel;

    public static BurstContext resolve(BlockEntity source, Vec3 localCenter) {
        if (source == null || source.getLevel() == null || !isInSubLevel(source)) {
            return BurstContext.normal(localCenter);
        }

        try {
            Vec3 projected = (Vec3) projectOutOfSubLevel.invoke(helper, source.getLevel(), localCenter);
            return new BurstContext(localCenter, projected, true);
        } catch (ReflectiveOperationException | LinkageError exception) {
            FullSteamAhead.LOGGER.warn("Unable to project Sable boiler burst out of sublevel", exception);
            return BurstContext.normal(localCenter);
        }
    }

    public static void damageSubLevelBlocks(ServerLevel level, BurstContext context, float power, long seed) {
        if (!context.inSubLevel() || power <= 0.0F) {
            return;
        }
        double radius = FullSteamConfig.overpressureSublevelDamageRadius();
        if (radius <= 0.0D) {
            return;
        }

        int r = Mth.ceil(radius);
        double r2 = radius * radius;
        Vec3 center = context.localCenter();
        int cx = Mth.floor(center.x), cy = Mth.floor(center.y), cz = Mth.floor(center.z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        // Iterate only the bounding cube, but sphere-cull (cheap integer test) BEFORE any world access,
        // so a contraption burst never scans/queries an enormous volume.
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r2) {
                        continue;
                    }
                    m.set(cx + dx, cy + dy, cz + dz);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(m);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (state.getDestroySpeed(level, m) < 0.0F) {            // bedrock, barriers, ...
                        continue;
                    }
                    BlockPos pos = m.immutable();
                    double dist = Math.sqrt(d2);
                    double noise = randomUnit(seed, pos);
                    if (dist > radius * (0.72D + noise * 0.36D)) {           // irregular edge
                        continue;
                    }
                    // Vanilla-style decay: blast power fades toward the carve edge and each block's
                    // explosion resistance can absorb it, so obsidian-grade armor survives intact.
                    double effectivePower = power * (1.0D - dist / radius);
                    if ((state.getBlock().getExplosionResistance() + 0.3D) * 0.3D > effectivePower) {
                        continue;
                    }
                    double chance = Mth.clamp(1.05D - (dist / radius) * 0.92D, 0.08D, 1.0D);
                    if (randomUnit(seed ^ 0x9E3779B97F4A7C15L, pos) > chance) {
                        continue;
                    }
                    removeBlockQuiet(level, pos, state, shouldDrop(pos, center, radius, seed));
                }
            }
        }
    }

    /**
     * Removes a block without the per-block break event/particles/sound (the burst sends one client
     * effect; the old lag came from the unbounded scan and per-block break events). Neighbor and
     * shape updates stay on so attached blocks pop and pipe/kinetic neighbors re-evaluate.
     */
    private static void removeBlockQuiet(ServerLevel level, BlockPos pos, BlockState state, boolean drop) {
        if (drop) {
            Block.dropResources(state, level, pos);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static boolean shouldDrop(BlockPos pos, Vec3 center, double radius, long seed) {
        double dx = pos.getX() + 0.5D - center.x;
        double dy = pos.getY() + 0.5D - center.y;
        double dz = pos.getZ() + 0.5D - center.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double normalized = distance / Math.max(1.0D, radius);

        // Mimic explosion decay: the core is vaporized, while edge damage may occasionally drop salvage.
        double chance = Mth.clamp((normalized - 0.35D) * 0.24D, 0.0D, 0.12D);
        return randomUnit(seed ^ 0xD1B54A32D192ED03L, pos) <= chance;
    }

    private static double randomUnit(long seed, BlockPos pos) {
        long value = seed;
        value ^= (long) pos.getX() * 0x9E3779B97F4A7C15L;
        value ^= (long) pos.getY() * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) pos.getZ() * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static boolean isInSubLevel(BlockEntity source) {
        if (!init()) {
            return false;
        }

        try {
            return getContainingBlockEntity.invoke(helper, source) != null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            FullSteamAhead.LOGGER.warn("Unable to check whether boiler burst is in a Sable sublevel", exception);
            return false;
        }
    }

    private static boolean init() {
        if (initialized) {
            return available;
        }
        initialized = true;
        if (!ModList.get().isLoaded("sable")) {
            return false;
        }

        try {
            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            Field helperField = sableClass.getField("HELPER");
            helper = helperField.get(null);
            Class<?> helperClass = helper.getClass();
            getContainingBlockEntity = helperClass.getMethod("getContaining", BlockEntity.class);
            projectOutOfSubLevel = helperClass.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
            available = true;
            FullSteamAhead.LOGGER.info("Registered Full Steam Ahead optional Sable boiler burst compatibility");
        } catch (ReflectiveOperationException | LinkageError exception) {
            FullSteamAhead.LOGGER.warn("Unable to initialize optional Sable boiler burst compatibility", exception);
            available = false;
        }
        return available;
    }

    public record BurstContext(Vec3 localCenter, Vec3 worldCenter, boolean inSubLevel) {
        private static BurstContext normal(Vec3 center) {
            return new BurstContext(center, center, false);
        }
    }

    private SableBurstCompat() {
    }
}
