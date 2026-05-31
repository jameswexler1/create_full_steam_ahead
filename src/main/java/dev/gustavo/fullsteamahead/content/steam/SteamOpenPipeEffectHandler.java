package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import dev.gustavo.fullsteamahead.registry.ModParticleTypes;
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
    }

    private SteamOpenPipeEffectHandler() {
    }
}
