package dev.gustavo.fullsteamahead.registry;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.content.steam.SteamNetworkDisplaySource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDisplaySources {
    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateBuiltInRegistries.DISPLAY_SOURCE, FullSteamAhead.MOD_ID);

    public static final DeferredHolder<DisplaySource, SteamNetworkDisplaySource> STEAM_NETWORK =
            DISPLAY_SOURCES.register("steam_network", SteamNetworkDisplaySource::new);

    public static void register(IEventBus modEventBus) {
        DISPLAY_SOURCES.register(modEventBus);
    }

    public static void registerAssociations() {
        DisplaySource.BY_BLOCK_ENTITY.add(ModBlockEntities.BOILER_OUTLET.get(), STEAM_NETWORK.get());
    }

    private ModDisplaySources() {
    }
}
