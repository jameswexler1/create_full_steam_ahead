package dev.gustavo.fullsteamahead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.redstone.SteppedLeverBlock;
import dev.gustavo.fullsteamahead.content.redstone.SteppedLeverBlockEntity;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class SteppedLeverRenderer extends SafeBlockEntityRenderer<SteppedLeverBlockEntity> {
    public SteppedLeverRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            SteppedLeverBlockEntity lever,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        BlockState state = lever.getBlockState();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.cutoutMipped());

        // The partner's commanded position ("order") is drawn first as a red needle sharing the
        // pivot; while the bell is unanswered it glows full-bright. The player's own handle (brass)
        // is drawn on top, so a matched order reads as the brass handle covering the red one.
        if (lever.isLinked()) {
            int orderLight = lever.isRinging() ? LightTexture.FULL_BRIGHT : light;
            renderHandle(lever.getRenderedOrder(partialTicks), state, poseStack, vertexConsumer,
                    orderLight, overlay, 255, 60, 55);
        }
        renderHandle(lever.getRenderedState(partialTicks), state, poseStack, vertexConsumer,
                light, overlay, 255, 255, 255);
    }

    private static void renderHandle(
            float signal,
            BlockState state,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            int red,
            int green,
            int blue
    ) {
        float angle = (((signal / 15.0F * 135.0F) - 22.5F) / 180.0F) * (float) Math.PI;
        SuperByteBuffer handle = CachedBuffers.partial(FullSteamPartialModels.steppedLeverHandle(), state);
        transform(handle, state)
                .translate(0.5F, 0.25F, 0.5F)
                .rotate(Direction.EAST.getAxis(), angle)
                .translate(-0.5F, -0.25F, -0.5F)
                .color(red, green, blue, 255)
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static SuperByteBuffer transform(SuperByteBuffer buffer, BlockState state) {
        AttachFace face = state.getValue(SteppedLeverBlock.FACE);
        float xRotation = face == AttachFace.FLOOR ? 0.0F : face == AttachFace.WALL ? 90.0F : 180.0F;
        float yRotation = AngleHelper.horizontalAngle(state.getValue(SteppedLeverBlock.FACING));
        return buffer
                .rotateCentered((yRotation / 180.0F) * (float) Math.PI, Direction.UP)
                .rotateCentered((xRotation / 180.0F) * (float) Math.PI, Direction.EAST);
    }
}
