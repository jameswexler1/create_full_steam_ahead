package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, FullSteamAhead.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STEAM_LEAK =
            PARTICLE_TYPES.register("steam_leak", () -> new SimpleParticleType(false));

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }

    private ModParticleTypes() {
    }
}
