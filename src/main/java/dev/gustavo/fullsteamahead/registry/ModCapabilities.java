package dev.gustavo.fullsteamahead.registry;

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
    }

    private ModCapabilities() {
    }
}
