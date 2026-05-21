package dev.gustavo.fullsteamahead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class CrankshaftRenderer extends SafeBlockEntityRenderer<CrankshaftBlockEntity> {
    public CrankshaftRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            CrankshaftBlockEntity crankshaft,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        VertexConsumer solid = bufferSource.getBuffer(RenderType.solid());
        BlockState state = crankshaft.getBlockState();
        KineticBlockEntityRenderer.renderRotatingKineticBlock(crankshaft, state, poseStack, solid, light);

        CrankshaftAnimation.State animation = CrankshaftAnimation.state(crankshaft);
        if (!animation.visible()) {
            return;
        }

        for (int segment = 0; segment < CrankshaftAnimation.ROD_SEGMENTS; segment++) {
            renderTranslated(FullSteamPartialModels.PISTON_ROD_PROXY, state, poseStack, solid, light, overlay,
                    animation.rodY(segment));
        }
        renderTranslated(FullSteamPartialModels.PISTON_HEAD_PROXY, state, poseStack, solid, light, overlay,
                animation.headY());
        renderCrankPin(state, poseStack, solid, light, overlay, animation.angle());
    }

    @Override
    public AABB getRenderBoundingBox(CrankshaftBlockEntity crankshaft) {
        return new AABB(crankshaft.getBlockPos()).inflate(2.0D, 5.0D, 2.0D);
    }

    private static void renderTranslated(
            PartialModel partial,
            BlockState state,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            float y
    ) {
        CachedBuffers.partial(partial, state)
                .translate(0, y, 0)
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static void renderCrankPin(
            BlockState state,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            float angle
    ) {
        SuperByteBuffer buffer = CachedBuffers.partial(FullSteamPartialModels.CRANK_PIN_PROXY, state);
        buffer.center()
                .rotateY(angle)
                .uncenter()
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }
}
