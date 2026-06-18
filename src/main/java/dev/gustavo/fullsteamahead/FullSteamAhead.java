package dev.gustavo.fullsteamahead;

import com.mojang.logging.LogUtils;
import dev.gustavo.fullsteamahead.compat.cbc.CbcProjectileDamageCompat;
import dev.gustavo.fullsteamahead.compat.create.CreateMovementCompat;
import dev.gustavo.fullsteamahead.compat.simulated.SimulatedMovementCompat;
import dev.gustavo.fullsteamahead.config.FullSteamClientConfig;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.piston.EngineShaftEventHandler;
import dev.gustavo.fullsteamahead.content.steam.SteamNetworkManager;
import dev.gustavo.fullsteamahead.content.steam.SteamOpenPipeEffectHandler;
import dev.gustavo.fullsteamahead.network.ModPackets;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import dev.gustavo.fullsteamahead.registry.ModCapabilities;
import dev.gustavo.fullsteamahead.registry.ModCreativeTabs;
import dev.gustavo.fullsteamahead.registry.ModDataComponents;
import dev.gustavo.fullsteamahead.registry.ModDisplaySources;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import dev.gustavo.fullsteamahead.registry.ModItems;
import dev.gustavo.fullsteamahead.registry.ModParticleTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(FullSteamAhead.MOD_ID)
public final class FullSteamAhead {
    public static final String MOD_ID = "full_steam_ahead";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FullSteamAhead(IEventBus modEventBus, ModContainer modContainer) {
        ModFluids.register(modEventBus);
        ModParticleTypes.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModDisplaySources.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(ModCapabilities::register);
        modEventBus.addListener(ModPackets::register);
        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.SERVER, FullSteamConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, FullSteamClientConfig.SPEC);

        LOGGER.info("Initializing Create: Full Steam Ahead");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            EngineShaftEventHandler.register(NeoForge.EVENT_BUS);
            SteamNetworkManager.register(NeoForge.EVENT_BUS);
            SteamOpenPipeEffectHandler.register();
            ModDisplaySources.registerAssociations();
            CbcProjectileDamageCompat.registerIfPresent(NeoForge.EVENT_BUS);
            CreateMovementCompat.register();
            SimulatedMovementCompat.registerIfPresent();
        });
    }
}
