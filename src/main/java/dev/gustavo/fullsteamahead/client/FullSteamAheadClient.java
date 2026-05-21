package dev.gustavo.fullsteamahead.client;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = FullSteamAhead.MOD_ID, value = Dist.CLIENT)
public final class FullSteamAheadClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        FullSteamAhead.LOGGER.info("Initializing Create: Full Steam Ahead client");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    }

    private FullSteamAheadClient() {
    }
}
