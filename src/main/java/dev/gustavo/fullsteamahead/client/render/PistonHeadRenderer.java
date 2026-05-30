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
import net.minecraft.core.Direction;
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
        int rodLight = partLight(level, headPos, animation.connectingRodY(), light);
        int shaftLight = level == null ? light : LevelRenderer.getLightColor(level, headPos.above(3));

        renderTranslated(FullSteamPartialModels.pistonHead(), state, poseStack, solid, headLight, overlay,
                animation.headY());
        for (int blockIndex = 0; blockIndex < PistonHeadAnimation.PISTON_BLOCKS; blockIndex++) {
            renderPistonBody(state, poseStack, solid, pistonLight, overlay, animation, blockIndex);
        }
        renderConnectingRod(state, poseStack, solid, rodLight, overlay, animation);
        renderCrank(state, poseStack, solid, shaftLight, overlay, animation);
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

    private static void renderPistonBody(
            BlockState state,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            PistonHeadAnimation.State animation,
            int blockIndex
    ) {
        SuperByteBuffer buffer = CachedBuffers.partial(FullSteamPartialModels.pistonBody(), state);
        orientForShaft(buffer.translate(0, animation.pistonY(blockIndex), 0), animation.shaftAxis())
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static void renderConnectingRod(
            BlockState state,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            PistonHeadAnimation.State animation
    ) {
        SuperByteBuffer buffer = CachedBuffers.partial(FullSteamPartialModels.connectingRod(), state);
        rotateConnectingRod(
                orientForShaft(buffer.translate(0, animation.connectingRodY(), 0), animation.shaftAxis()),
                animation
        )
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static void renderCrank(
            BlockState state,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            PistonHeadAnimation.State animation
    ) {
        SuperByteBuffer buffer = CachedBuffers.partial(FullSteamPartialModels.crank(), state);
        rotateCrank(
                orientForShaft(buffer.translate(0, animation.crankY(), 0), animation.shaftAxis()),
                animation
        )
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static SuperByteBuffer orientForShaft(SuperByteBuffer buffer, Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            buffer.center()
                    .rotateY((float) (Math.PI / 2.0D))
                    .uncenter();
        }
        return buffer;
    }

    private static SuperByteBuffer rotateConnectingRod(
            SuperByteBuffer buffer,
            PistonHeadAnimation.State animation
    ) {
        buffer.translate(0.5F, PistonHeadAnimation.CONNECTING_ROD_SMALL_END_Y, 0.5F);
        if (animation.shaftAxis() == Direction.Axis.X) {
            buffer.rotateX(animation.connectingRodRotation());
        } else {
            buffer.rotateZ(animation.connectingRodRotation());
        }
        return buffer.translate(-0.5F, -PistonHeadAnimation.CONNECTING_ROD_SMALL_END_Y, -0.5F);
    }

    private static SuperByteBuffer rotateCrank(
            SuperByteBuffer buffer,
            PistonHeadAnimation.State animation
    ) {
        buffer.center();
        if (animation.shaftAxis() == Direction.Axis.X) {
            buffer.rotateX(animation.crankRotation());
        } else {
            buffer.rotateZ(animation.crankRotation());
        }
        return buffer.uncenter();
    }

    private static int partLight(Level level, BlockPos basePos, float y, int fallbackLight) {
        if (level == null) {
            return fallbackLight;
        }

        int blockOffset = Math.max(0, Math.min(3, Math.round(y)));
        return LevelRenderer.getLightColor(level, basePos.above(blockOffset));
    }
}
