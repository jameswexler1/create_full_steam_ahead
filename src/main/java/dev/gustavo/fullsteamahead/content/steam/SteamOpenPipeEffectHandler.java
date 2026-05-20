package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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

        double x = area.getCenter().x;
        double y = area.getCenter().y + 0.25;
        double z = area.getCenter().z;
        serverLevel.sendParticles(
                ParticleTypes.CLOUD,
                x,
                y,
                z,
                8,
                Math.max(0.08, area.getXsize() * 0.25),
                0.18,
                Math.max(0.08, area.getZsize() * 0.25),
                0.035
        );
    }

    private SteamOpenPipeEffectHandler() {
    }
}
