package dev.gustavo.fullsteamahead;

import com.mojang.logging.LogUtils;
import dev.gustavo.fullsteamahead.compat.create.CreateMovementCompat;
import dev.gustavo.fullsteamahead.compat.simulated.SimulatedMovementCompat;
import dev.gustavo.fullsteamahead.content.steam.SteamOpenPipeEffectHandler;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import dev.gustavo.fullsteamahead.registry.ModCapabilities;
import dev.gustavo.fullsteamahead.registry.ModCreativeTabs;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import dev.gustavo.fullsteamahead.registry.ModItems;
import dev.gustavo.fullsteamahead.registry.ModParticleTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(ModCapabilities::register);
        modEventBus.addListener(this::commonSetup);

        LOGGER.info("Initializing Create: Full Steam Ahead");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SteamOpenPipeEffectHandler.register();
            CreateMovementCompat.register();
            SimulatedMovementCompat.registerIfPresent();
        });
    }
}
