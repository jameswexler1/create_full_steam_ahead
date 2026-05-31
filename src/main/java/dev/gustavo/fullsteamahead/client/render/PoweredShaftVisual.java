package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;

public class PoweredShaftVisual extends SingleAxisRotatingVisual<FullSteamPoweredShaftBlockEntity> {
    public PoweredShaftVisual(
            VisualizationContext context,
            FullSteamPoweredShaftBlockEntity blockEntity,
            float partialTick
    ) {
        super(context, blockEntity, partialTick, Models.partial(AllPartialModels.POWERED_SHAFT));
    }
}
