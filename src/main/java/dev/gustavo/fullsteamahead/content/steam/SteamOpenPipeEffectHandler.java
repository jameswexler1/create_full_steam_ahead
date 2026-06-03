package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.registry.ModDamageTypes;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import dev.gustavo.fullsteamahead.registry.ModParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SteamOpenPipeEffectHandler implements OpenPipeEffectHandler {
    public static final SteamOpenPipeEffectHandler INSTANCE = new SteamOpenPipeEffectHandler();

    public static void register() {
        OpenPipeEffectHandler.REGISTRY.register(ModFluids.STEAM.get(), INSTANCE);
    }

    @Override
    public void apply(Level level, AABB area, FluidStack stack) {
        if (!(level instanceof ServerLevel serverLevel) || stack.isEmpty()) {
            return;
        }

        RandomSource random = serverLevel.random;
        Vec3 center = area.getCenter();
        double spread = Math.max(0.08, Math.max(area.getXsize(), area.getZsize()) * 0.16);

        for (int i = 0; i < 10; i++) {
            double x = center.x + (random.nextDouble() - 0.5D) * spread;
            double y = center.y + 0.15D + random.nextDouble() * 0.24D;
            double z = center.z + (random.nextDouble() - 0.5D) * spread;
            double xSpeed = (random.nextDouble() - 0.5D) * 0.055D;
            double ySpeed = 0.045D + random.nextDouble() * 0.045D;
            double zSpeed = (random.nextDouble() - 0.5D) * 0.055D;

            serverLevel.sendParticles(
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

        scaldEntities(serverLevel, area, stack);
    }

    // Steam venting from an open pipe end scalds entities standing in the cloud. The cadence is gated
    // by game time (like Create's LavaEffectHandler) since this runs on every fluid push, and the
    // damage scales with how much steam is escaping.
    private void scaldEntities(ServerLevel level, AABB area, FluidStack stack) {
        if (!FullSteamConfig.steamLeakDamageEnabled()) {
            return;
        }
        if (level.getGameTime() % FullSteamConfig.steamLeakDamageInterval() != 0L) {
            return;
        }

        // Every leak deals at least the base damage; larger leaks scale up from there to the cap.
        double base = FullSteamConfig.steamLeakBaseDamage();
        double ratio = Math.max(1.0D, (double) stack.getAmount() / FullSteamConfig.steamLeakDamageReferenceMb());
        float damage = (float) Mth.clamp(base * ratio, base, FullSteamConfig.steamLeakMaxDamage());
        if (damage <= 0.0F) {
            return;
        }

        AABB damageArea = area.inflate(FullSteamConfig.steamLeakDamageRadius());
        DamageSource source = ModDamageTypes.steam(level);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, damageArea, LivingEntity::isAlive)) {
            entity.hurt(source, damage);
        }
    }

    private SteamOpenPipeEffectHandler() {
    }
}
