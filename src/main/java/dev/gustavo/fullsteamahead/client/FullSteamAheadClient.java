package dev.gustavo.fullsteamahead.client;

import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.client.render.FullSteamPartialModels;
import dev.gustavo.fullsteamahead.client.render.PistonHeadRenderer;
import dev.gustavo.fullsteamahead.client.render.PistonHeadVisual;
import dev.gustavo.fullsteamahead.client.render.PoweredShaftRenderer;
import dev.gustavo.fullsteamahead.client.render.PoweredShaftVisual;
import dev.gustavo.fullsteamahead.client.render.SteppedLeverRenderer;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = FullSteamAhead.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FullSteamAheadClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        FullSteamAhead.LOGGER.info("Initializing Create: Full Steam Ahead client");
        event.enqueueWork(() -> {
            PistonHeadVisual.register();
            VisualizerRegistry.setVisualizer(
                    ModBlockEntities.POWERED_SHAFT.get(),
                    SimpleBlockEntityVisualizer.builder(ModBlockEntities.POWERED_SHAFT.get())
                            .factory(PoweredShaftVisual::new)
                            .apply()
            );
        });
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        FullSteamPartialModels.registerAdditional(event);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PISTON_HEAD.get(), PistonHeadRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.POWERED_SHAFT.get(), PoweredShaftRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STEPPED_LEVER.get(), SteppedLeverRenderer::new);
    }

    private FullSteamAheadClient() {
    }
}
