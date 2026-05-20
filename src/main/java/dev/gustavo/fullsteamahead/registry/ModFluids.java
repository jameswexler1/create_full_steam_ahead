package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.steam.SteamFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, FullSteamAhead.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, FullSteamAhead.MOD_ID);

    public static final DeferredHolder<FluidType, SteamFluidType> STEAM_TYPE =
            FLUID_TYPES.register("steam", SteamFluidType::new);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM =
            FLUIDS.register("steam", () -> new BaseFlowingFluid.Source(steamProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_STEAM =
            FLUIDS.register("flowing_steam", () -> new BaseFlowingFluid.Flowing(steamProperties()));

    private static BaseFlowingFluid.Properties steamProperties() {
        return new BaseFlowingFluid.Properties(STEAM_TYPE, STEAM, FLOWING_STEAM)
                .slopeFindDistance(1)
                .levelDecreasePerBlock(2)
                .tickRate(5);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    private ModFluids() {
    }
}
