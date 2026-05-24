package dev.gustavo.fullsteamahead.client.render;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class FullSteamPartialModels {
    private static final ResourceLocation PISTON_BODY_LOCATION = location("piston_body");
    private static final ResourceLocation PISTON_HEAD_LOCATION = location("piston_head");
    private static final ResourceLocation CRANK_PIN_PROXY_LOCATION = location("crank_pin_proxy");
    private static final ResourceLocation STEPPED_LEVER_HANDLE_LOCATION = location("stepped_lever_handle");

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(PISTON_BODY_LOCATION));
        event.register(ModelResourceLocation.standalone(PISTON_HEAD_LOCATION));
        event.register(ModelResourceLocation.standalone(CRANK_PIN_PROXY_LOCATION));
        event.register(ModelResourceLocation.standalone(STEPPED_LEVER_HANDLE_LOCATION));
    }

    public static PartialModel pistonBody() {
        return PartialModel.of(PISTON_BODY_LOCATION);
    }

    public static PartialModel pistonHead() {
        return PartialModel.of(PISTON_HEAD_LOCATION);
    }

    public static PartialModel crankPinProxy() {
        return PartialModel.of(CRANK_PIN_PROXY_LOCATION);
    }

    public static PartialModel steppedLeverHandle() {
        return PartialModel.of(STEPPED_LEVER_HANDLE_LOCATION);
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
