package dev.gustavo.fullsteamahead.client.render;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.resources.ResourceLocation;

public final class FullSteamPartialModels {
    public static final PartialModel PISTON_ROD_PROXY = partial("piston_rod_proxy");
    public static final PartialModel PISTON_HEAD_PROXY = partial("piston_head_proxy");
    public static final PartialModel CRANK_PIN_PROXY = partial("crank_pin_proxy");

    public static void init() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                FullSteamAhead.MOD_ID,
                "block/partial/" + path
        ));
    }

    private FullSteamPartialModels() {
    }
}
