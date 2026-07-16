package dev.gustavo.fullsteamahead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.steam.SteamPressureGaugeBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamPressureGaugeBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class SteamPressureGaugeRenderer extends SafeBlockEntityRenderer<SteamPressureGaugeBlockEntity> {
    private static final float NEEDLE_X = 8.0F / 16.0F;
    private static final float NEEDLE_Y = 9.0F / 16.0F;
    private static final float NEEDLE_Z = 13.12F / 16.0F;
    // Viewed from the dial front: lower-left, clockwise through the top, then lower-right.
    private static final float SWEEP_RADIANS = (float) Math.toRadians(270.0D);
    private static final float START_RADIANS = (float) Math.toRadians(-135.0D);

    public SteamPressureGaugeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            SteamPressureGaugeBlockEntity gauge,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        BlockState state = gauge.getBlockState();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.cutoutMipped());
        float angle = START_RADIANS + gauge.getRenderedPressure(partialTicks) * SWEEP_RADIANS;

        SuperByteBuffer needle = CachedBuffers.partial(FullSteamPartialModels.steamPressureGaugeNeedle(), state);
        orient(needle, state)
                .translate(NEEDLE_X, NEEDLE_Y, NEEDLE_Z)
                .rotate(Direction.Axis.Z, angle)
                .translate(-NEEDLE_X, -NEEDLE_Y, -NEEDLE_Z)
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static SuperByteBuffer orient(SuperByteBuffer buffer, BlockState state) {
        Direction facing = state.getValue(SteamPressureGaugeBlock.FACING);
        // Runtime JOML rotations use the opposite sign from baked blockstate Y rotations.
        float angle = switch (facing) {
            case EAST -> (float) (Math.PI * 1.5D);
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (Math.PI / 2.0D);
            default -> 0.0F;
        };
        return buffer.rotateCentered(angle, Direction.UP);
    }
}
