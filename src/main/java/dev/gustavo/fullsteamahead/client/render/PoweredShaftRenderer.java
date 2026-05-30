package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class PoweredShaftRenderer extends KineticBlockEntityRenderer<FullSteamPoweredShaftBlockEntity> {
    public PoweredShaftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
}
