package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class PoweredShaftVisual extends SingleAxisRotatingVisual<FullSteamPoweredShaftBlockEntity> {
    public PoweredShaftVisual(
            VisualizationContext context,
            FullSteamPoweredShaftBlockEntity blockEntity,
            float partialTick
    ) {
        super(context, blockEntity, partialTick, Models.block(verticalPoweredShaft(blockEntity)));
    }

    private static BlockState verticalPoweredShaft(FullSteamPoweredShaftBlockEntity blockEntity) {
        return blockEntity.getBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    }
}
