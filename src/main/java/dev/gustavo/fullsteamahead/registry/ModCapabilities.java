package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.compat.aeronautics.AeronauticsSteamVentCompat;
import dev.gustavo.fullsteamahead.compat.aeronautics.FullSteamAeronauticsSteamVent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.BOILER_OUTLET.get(),
                (blockEntity, side) -> blockEntity.getFluidHandler(side)
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.STEAM_INLET.get(),
                (blockEntity, side) -> blockEntity.getFluidHandler(side)
        );

        BuiltInRegistries.BLOCK.getOptional(AeronauticsSteamVentCompat.STEAM_VENT_ID)
                .ifPresent(block -> registerAeronauticsSteamVent(event, block));
    }

    private static void registerAeronauticsSteamVent(RegisterCapabilitiesEvent event, Block block) {
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    if (blockEntity instanceof FullSteamAeronauticsSteamVent vent) {
                        return vent.fullSteamAhead$getSteamFluidHandler(side);
                    }
                    return null;
                },
                block
        );
    }

    private ModCapabilities() {
    }
}
