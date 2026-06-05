package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.registry.ModDamageTypes;
import dev.gustavo.fullsteamahead.registry.ModParticleTypes;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SteamCloudEffects {
    private static final double ENGINE_EXHAUST_DAMAGE_BOX = 0.35D;

    public static void emitOpenPipe(ServerLevel level, AABB area, int amount) {
        if (amount <= 0) {
            return;
        }

        RandomSource random = level.random;
        Vec3 center = area.getCenter();
        double spread = Math.max(0.08D, Math.max(area.getXsize(), area.getZsize()) * 0.16D);

        for (int i = 0; i < 10; i++) {
            double x = center.x + (random.nextDouble() - 0.5D) * spread;
            double y = center.y + 0.15D + random.nextDouble() * 0.24D;
            double z = center.z + (random.nextDouble() - 0.5D) * spread;
            double xSpeed = (random.nextDouble() - 0.5D) * 0.055D;
            double ySpeed = 0.045D + random.nextDouble() * 0.045D;
            double zSpeed = (random.nextDouble() - 0.5D) * 0.055D;
            sendSteamParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }

        scaldEntities(level, area, amount, 1.0D, true);
    }

    public static void emitEngineExhaust(ServerLevel level, Vec3 origin, Direction direction, int amount) {
        if (amount <= 0) {
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
        RandomSource random = level.random;
        double intensity = Mth.clamp(
                amount / (double) FullSteamConfig.maxPipedSteamPerTick(),
                0.35D,
                1.0D
        );
        int particles = 8 + (int) Math.round(intensity * 10.0D);
        double speed = 0.075D + intensity * 0.08D;

        for (int i = 0; i < particles; i++) {
            double jitterX = (random.nextDouble() - 0.5D) * 0.16D;
            double jitterY = (random.nextDouble() - 0.5D) * 0.16D;
            double jitterZ = (random.nextDouble() - 0.5D) * 0.16D;
            double x = origin.x + jitterX;
            double y = origin.y + jitterY;
            double z = origin.z + jitterZ;
            double xSpeed = normal.x * speed + jitterX * 0.45D;
            double ySpeed = normal.y * speed + 0.025D + jitterY * 0.45D;
            double zSpeed = normal.z * speed + jitterZ * 0.45D;
            sendSteamParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }

        Vec3 damageCenter = origin.add(normal.scale(0.35D));
        AABB damageArea = AABB.ofSize(
                damageCenter,
                ENGINE_EXHAUST_DAMAGE_BOX,
                ENGINE_EXHAUST_DAMAGE_BOX,
                ENGINE_EXHAUST_DAMAGE_BOX
        );
        scaldEntities(level, damageArea, amount, FullSteamConfig.engineExhaustDamageScale(), false);
    }

    private static void sendSteamParticle(
            ServerLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        level.sendParticles(
                ModParticleTypes.STEAM_LEAK.get(),
                x,
                y,
                z,
                0,
                xSpeed,
                ySpeed,
                zSpeed,
                1.0D
        );
    }

    private static void scaldEntities(
            ServerLevel level,
            AABB area,
            int amount,
            double damageScale,
            boolean respectInterval
    ) {
        if (!FullSteamConfig.steamLeakDamageEnabled() || damageScale <= 0.0D) {
            return;
        }
        if (respectInterval && level.getGameTime() % FullSteamConfig.steamLeakDamageInterval() != 0L) {
            return;
        }

        double base = FullSteamConfig.steamLeakBaseDamage();
        double ratio = Math.max(1.0D, amount / (double) FullSteamConfig.steamLeakDamageReferenceMb());
        float damage = (float) (Mth.clamp(base * ratio, base, FullSteamConfig.steamLeakMaxDamage()) * damageScale);
        if (damage <= 0.0F) {
            return;
        }

        AABB damageArea = area.inflate(FullSteamConfig.steamLeakDamageRadius());
        DamageSource source = ModDamageTypes.steam(level);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, damageArea, LivingEntity::isAlive)) {
            entity.hurt(source, damage);
        }
    }

    private SteamCloudEffects() {
    }
}
