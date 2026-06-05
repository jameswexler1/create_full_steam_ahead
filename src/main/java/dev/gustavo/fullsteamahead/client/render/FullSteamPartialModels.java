package dev.gustavo.fullsteamahead.client.render;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class FullSteamPartialModels {
    private static final ResourceLocation PISTON_BODY_LOCATION = location("piston_body");
    private static final ResourceLocation PISTON_HEAD_LOCATION = location("piston_head");
    private static final ResourceLocation CONNECTING_ROD_LOCATION = location("connecting_rod");
    private static final ResourceLocation CONNECTING_ROD_2_LOCATION = location("connecting_rod_2");
    private static final ResourceLocation CONNECTING_ROD_3_LOCATION = location("connecting_rod_3");
    private static final ResourceLocation CRANK_LOCATION = location("crank");
    private static final ResourceLocation CRANK_2_LOCATION = location("crank_2");
    private static final ResourceLocation CRANK_3_LOCATION = location("crank_3");
    private static final ResourceLocation STEPPED_LEVER_HANDLE_LOCATION = location("stepped_lever_handle");

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(PISTON_BODY_LOCATION));
        event.register(ModelResourceLocation.standalone(PISTON_HEAD_LOCATION));
        event.register(ModelResourceLocation.standalone(CONNECTING_ROD_LOCATION));
        event.register(ModelResourceLocation.standalone(CONNECTING_ROD_2_LOCATION));
        event.register(ModelResourceLocation.standalone(CONNECTING_ROD_3_LOCATION));
        event.register(ModelResourceLocation.standalone(CRANK_LOCATION));
        event.register(ModelResourceLocation.standalone(CRANK_2_LOCATION));
        event.register(ModelResourceLocation.standalone(CRANK_3_LOCATION));
        event.register(ModelResourceLocation.standalone(STEPPED_LEVER_HANDLE_LOCATION));
    }

    public static PartialModel pistonBody() {
        return PartialModel.of(PISTON_BODY_LOCATION);
    }

    public static PartialModel pistonHead() {
        return PartialModel.of(PISTON_HEAD_LOCATION);
    }

    public static PartialModel connectingRod() {
        return PartialModel.of(CONNECTING_ROD_LOCATION);
    }

    public static PartialModel connectingRod(int pistonBodyCount) {
        return PartialModel.of(switch (PistonHeadAnimation.clampPistonBodyCount(pistonBodyCount)) {
            case 2 -> CONNECTING_ROD_2_LOCATION;
            case 3 -> CONNECTING_ROD_3_LOCATION;
            default -> CONNECTING_ROD_LOCATION;
        });
    }

    public static PartialModel crank() {
        return PartialModel.of(CRANK_LOCATION);
    }

    public static PartialModel crank(int pistonBodyCount) {
        return PartialModel.of(switch (PistonHeadAnimation.clampPistonBodyCount(pistonBodyCount)) {
            case 2 -> CRANK_2_LOCATION;
            case 3 -> CRANK_3_LOCATION;
            default -> CRANK_LOCATION;
        });
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
