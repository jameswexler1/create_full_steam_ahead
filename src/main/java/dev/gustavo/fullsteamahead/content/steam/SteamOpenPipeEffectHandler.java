package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import com.simibubi.create.content.kinetics.steamEngine.SteamJetParticleData;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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

        for (int i = 0; i < 3; i++) {
            double x = center.x + (random.nextDouble() - 0.5D) * spread;
            double y = center.y + 0.2D + random.nextDouble() * 0.18D;
            double z = center.z + (random.nextDouble() - 0.5D) * spread;
            double xSpeed = (random.nextDouble() - 0.5D) * 0.09D;
            double ySpeed = 0.12D + random.nextDouble() * 0.08D;
            double zSpeed = (random.nextDouble() - 0.5D) * 0.09D;

            serverLevel.sendParticles(
                    new SteamJetParticleData(0.42F + random.nextFloat() * 0.22F),
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

        serverLevel.sendParticles(
                ParticleTypes.CLOUD,
                center.x,
                center.y + 0.25D,
                center.z,
                5,
                Math.max(0.08, area.getXsize() * 0.18D),
                0.16D,
                Math.max(0.08, area.getZsize() * 0.18D),
                0.025D
        );
    }

    private SteamOpenPipeEffectHandler() {
    }
}
