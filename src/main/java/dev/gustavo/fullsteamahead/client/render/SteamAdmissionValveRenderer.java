package dev.gustavo.fullsteamahead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.steam.SteamAdmissionValveBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class SteamAdmissionValveRenderer extends SmartBlockEntityRenderer<SteamAdmissionValveBlockEntity> {
    private static final float ACTUATOR_LIFT = 1.25F / 16.0F;

    public SteamAdmissionValveRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(
            SteamAdmissionValveBlockEntity valve,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        super.renderSafe(valve, partialTicks, poseStack, bufferSource, light, overlay);

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.cutoutMipped());
        SuperByteBuffer actuator = CachedBuffers.partial(
                FullSteamPartialModels.steamAdmissionValveActuator(),
                valve.getBlockState()
        );
        actuator.translate(0.0F, valve.getRenderedAdmission(partialTicks) * ACTUATOR_LIFT, 0.0F)
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }
}
