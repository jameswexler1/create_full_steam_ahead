package dev.gustavo.fullsteamahead.client.render;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class FullSteamPartialModels {
    private static final ResourceLocation PISTON_ROD_PROXY_LOCATION = location("piston_rod_proxy");
    private static final ResourceLocation PISTON_HEAD_PROXY_LOCATION = location("piston_head_proxy");
    private static final ResourceLocation CRANK_PIN_PROXY_LOCATION = location("crank_pin_proxy");

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(PISTON_ROD_PROXY_LOCATION));
        event.register(ModelResourceLocation.standalone(PISTON_HEAD_PROXY_LOCATION));
        event.register(ModelResourceLocation.standalone(CRANK_PIN_PROXY_LOCATION));
    }

    public static PartialModel pistonRodProxy() {
        return PartialModel.of(PISTON_ROD_PROXY_LOCATION);
    }

    public static PartialModel pistonHeadProxy() {
        return PartialModel.of(PISTON_HEAD_PROXY_LOCATION);
    }

    public static PartialModel crankPinProxy() {
        return PartialModel.of(CRANK_PIN_PROXY_LOCATION);
    }

    private static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                FullSteamAhead.MOD_ID,
                "block/partial/" + path
        );
    }

    private FullSteamPartialModels() {
    }
}
