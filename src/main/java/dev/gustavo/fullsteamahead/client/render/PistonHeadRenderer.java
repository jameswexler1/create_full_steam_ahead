package dev.gustavo.fullsteamahead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class PistonHeadRenderer extends SafeBlockEntityRenderer<PistonHeadBlockEntity> {
    public PistonHeadRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            PistonHeadBlockEntity engine,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        PistonHeadAnimation.State animation = PistonHeadAnimation.state(engine);
        if (!animation.visible()) {
            return;
        }

        VertexConsumer solid = bufferSource.getBuffer(RenderType.solid());
        BlockState state = engine.getBlockState();
        Level level = engine.getLevel();
        BlockPos headPos = engine.getBlockPos();
        int headLight = partLight(level, headPos, animation.headY(), light);
        int pistonLight = partLight(level, headPos, animation.pistonY(0), light);
        int shaftLight = level == null ? light : LevelRenderer.getLightColor(level, headPos.above(3));

        renderTranslated(FullSteamPartialModels.pistonHead(), state, poseStack, solid, headLight, overlay,
                animation.headY());
        for (int blockIndex = 0; blockIndex < PistonHeadAnimation.PISTON_BLOCKS; blockIndex++) {
            renderTranslated(FullSteamPartialModels.pistonBody(), state, poseStack, solid, pistonLight, overlay,
                    animation.pistonY(blockIndex));
        }
        renderCrankPin(state, poseStack, solid, shaftLight, overlay, animation.crankPinY(), animation.angle());
    }

    @Override
    public AABB getRenderBoundingBox(PistonHeadBlockEntity engine) {
        return new AABB(engine.getBlockPos()).inflate(2.0D, 5.0D, 2.0D);
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
            float y,
            float angle
    ) {
        SuperByteBuffer buffer = CachedBuffers.partial(FullSteamPartialModels.crankPinProxy(), state);
        buffer.translate(0, y, 0)
                .center()
                .rotateY(angle)
                .uncenter()
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static int partLight(Level level, BlockPos basePos, float y, int fallbackLight) {
        if (level == null) {
            return fallbackLight;
        }

        int blockOffset = Math.max(0, Math.min(3, Math.round(y)));
        return LevelRenderer.getLightColor(level, basePos.above(blockOffset));
    }
}
