package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlock;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class PoweredShaftRenderer extends KineticBlockEntityRenderer<FullSteamPoweredShaftBlockEntity> {
    public PoweredShaftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected BlockState getRenderedBlockState(FullSteamPoweredShaftBlockEntity blockEntity) {
        BlockState state = blockEntity.getBlockState();
        if (state.is(ModBlocks.POWERED_GIRDER_ENCASED_SHAFT.get())) {
            return shaft(FullSteamPoweredShaftBlock.axisOf(state));
        }
        return super.getRenderedBlockState(blockEntity);
    }
}
