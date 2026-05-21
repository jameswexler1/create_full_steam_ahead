package dev.gustavo.fullsteamahead.client;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.client.render.CrankshaftRenderer;
import dev.gustavo.fullsteamahead.client.render.CrankshaftVisual;
import dev.gustavo.fullsteamahead.client.render.FullSteamPartialModels;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = FullSteamAhead.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FullSteamAheadClient {
    static {
        FullSteamPartialModels.init();
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        FullSteamAhead.LOGGER.info("Initializing Create: Full Steam Ahead client");
        event.enqueueWork(CrankshaftVisual::register);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CRANKSHAFT.get(), CrankshaftRenderer::new);
    }

    private FullSteamAheadClient() {
    }
}
