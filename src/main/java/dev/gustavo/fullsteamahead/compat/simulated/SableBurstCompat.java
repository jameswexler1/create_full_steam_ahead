package dev.gustavo.fullsteamahead.compat.simulated;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional Sable integration for boiler bursts on simulated sublevels.
 *
 * <p>Sable projects vanilla explosions out of sublevels, which is correct for world/entity effects
 * but leaves the moving contraption's stored blocks under-damaged. This class keeps Sable optional
 * and adds the missing local block-damage pass only when a burst originates inside a sublevel.</p>
 */
public final class SableBurstCompat {
    private static final double MAX_LOCAL_DAMAGE_RADIUS = 48.0D;
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

        int radius = Mth.ceil(Mth.clamp(power * 1.5D, 2.0D, MAX_LOCAL_DAMAGE_RADIUS));
        Vec3 center = context.localCenter();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int minX = Mth.floor(center.x - radius);
        int minY = Mth.floor(center.y - radius);
        int minZ = Mth.floor(center.z - radius);
        int maxX = Mth.floor(center.x + radius);
        int maxY = Mth.floor(center.y + radius);
        int maxZ = Mth.floor(center.z + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    if (!level.isLoaded(mutable) || !shouldDamage(level, mutable, center, radius, seed)) {
                        continue;
                    }
                    BlockPos pos = mutable.immutable();
                    level.destroyBlock(pos, shouldDrop(pos, center, radius, seed));
                }
            }
        }
    }

    private static boolean shouldDamage(ServerLevel level, BlockPos pos, Vec3 center, int radius, long seed) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        VoxelShape collision = state.getCollisionShape(level, pos);
        if (!state.getFluidState().isEmpty() && collision.isEmpty()) {
            return false;
        }

        double dx = pos.getX() + 0.5D - center.x;
        double dy = pos.getY() + 0.5D - center.y;
        double dz = pos.getZ() + 0.5D - center.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double noise = randomUnit(seed, pos);
        double irregularRadius = radius * (0.72D + noise * 0.36D);
        if (distance > irregularRadius) {
            return false;
        }

        double normalized = distance / Math.max(1.0D, radius);
        double chance = Mth.clamp(1.05D - normalized * 0.92D, 0.08D, 1.0D);
        return randomUnit(seed ^ 0x9E3779B97F4A7C15L, pos) <= chance;
    }

    private static boolean shouldDrop(BlockPos pos, Vec3 center, int radius, long seed) {
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
