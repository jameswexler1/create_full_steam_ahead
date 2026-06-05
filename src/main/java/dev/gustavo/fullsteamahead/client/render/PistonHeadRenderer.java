package dev.gustavo.fullsteamahead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gustavo.fullsteamahead.content.piston.EngineValidator;
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
        Direction strokeDirection = animation.strokeDirection();
        int headLight = partLight(level, headPos, animation.headY(), strokeDirection, light);
        int rodLight = partLight(level, headPos, animation.connectingRodY(), strokeDirection, light);
        int shaftLight = level == null
                ? light
                : LevelRenderer.getLightColor(level, headPos.relative(animation.strokeDirection(), animation.shaftDistance()));

        renderTranslated(FullSteamPartialModels.pistonHead(), state, poseStack, solid, headLight, overlay, animation,
                animation.headY());
        for (int blockIndex = 0; blockIndex < animation.pistonBodyCount(); blockIndex++) {
            int pistonLight = partLight(level, headPos, animation.pistonY(blockIndex), strokeDirection, light);
            renderPistonBody(state, poseStack, solid, pistonLight, overlay, animation, blockIndex);
        }
        renderConnectingRod(state, poseStack, solid, rodLight, overlay, animation);
        renderCrank(state, poseStack, solid, shaftLight, overlay, animation);
    }

    @Override
    public AABB getRenderBoundingBox(PistonHeadBlockEntity engine) {
        return new AABB(engine.getBlockPos())
                .minmax(new AABB(engine.getBlockPos().relative(engine.getStrokeDirection(), engine.getShaftDistance())))
                .inflate(2.0D);
    }

    private static void renderTranslated(
            PartialModel partial,
            BlockState state,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            PistonHeadAnimation.State animation,
            float y
    ) {
        SuperByteBuffer buffer = CachedBuffers.partial(partial, state);
        orientForStroke(buffer, animation);
        buffer.translate(0, y, 0)
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
        PartialModel partial = blockIndex == animation.pistonBodyCount() - 1
                ? FullSteamPartialModels.pistonBody()
                : FullSteamPartialModels.pistonBodyIntermediate();
        SuperByteBuffer buffer = CachedBuffers.partial(partial, state);
        orientForStroke(buffer, animation);
        rotatePistonBody(
                buffer.translate(0, animation.pistonY(blockIndex), 0),
                animation.shaftAxis()
        )
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
        renderConnectingRodPart(
                FullSteamPartialModels.connectingRodLower(),
                state,
                poseStack,
                vertexConsumer,
                light,
                overlay,
                animation,
                0
        );
        for (int segmentIndex = 0;
             segmentIndex < PistonHeadAnimation.connectingRodMiddleSegments(animation.pistonBodyCount());
             segmentIndex++) {
            renderConnectingRodPart(
                    FullSteamPartialModels.connectingRodMiddle(),
                    state,
                    poseStack,
                    vertexConsumer,
                    light,
                    overlay,
                    animation,
                    PistonHeadAnimation.connectingRodMiddleOffset(segmentIndex)
            );
        }
        renderConnectingRodPart(
                FullSteamPartialModels.connectingRodUpper(),
                state,
                poseStack,
                vertexConsumer,
                light,
                overlay,
                animation,
                PistonHeadAnimation.connectingRodUpperOffset(animation.pistonBodyCount())
        );
    }

    private static void renderConnectingRodPart(
            PartialModel partial,
            BlockState state,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int light,
            int overlay,
            PistonHeadAnimation.State animation,
            float localYOffset
    ) {
        SuperByteBuffer buffer = CachedBuffers.partial(partial, state);
        orientForStroke(buffer, animation);
        rotateConnectingRod(
                buffer.translate(0, animation.connectingRodY(), 0),
                animation,
                localYOffset
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
        SuperByteBuffer buffer = CachedBuffers.partial(FullSteamPartialModels.crank(animation.pistonBodyCount()), state);
        orientForStroke(buffer, animation);
        rotateCrank(
                buffer.translate(0, animation.crankY(), 0),
                animation
        )
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static SuperByteBuffer rotateConnectingRod(
            SuperByteBuffer buffer,
            PistonHeadAnimation.State animation,
            float localYOffset
    ) {
        buffer.center();
        yawLinkageFrame(buffer, animation.shaftAxis());
        buffer.uncenter();
        buffer.translate(0.5F, PistonHeadAnimation.CONNECTING_ROD_SMALL_END_Y, 0.5F);
        buffer.rotateX(animation.connectingRodRotation());
        return buffer
                .translate(-0.5F, -PistonHeadAnimation.CONNECTING_ROD_SMALL_END_Y, -0.5F)
                .translate(0, localYOffset, 0);
    }

    private static SuperByteBuffer rotateCrank(
            SuperByteBuffer buffer,
            PistonHeadAnimation.State animation
    ) {
        buffer.center();
        yawLinkageFrame(buffer, animation.shaftAxis());
        buffer.rotateX(animation.crankRotation());
        return buffer.uncenter();
    }

    private static SuperByteBuffer rotatePistonBody(SuperByteBuffer buffer, Direction.Axis axis) {
        buffer.center();
        yawPistonBodyFrame(buffer, axis);
        return buffer.uncenter();
    }

    // Must be applied before the per-part positioning so it stays the outermost transform:
    // the upright-posed linkage is rigidly rotated 180 degrees about the head block center,
    // flipping head, piston, rod, and crank together while keeping every joint connected.
    private static SuperByteBuffer orientForStroke(
            SuperByteBuffer buffer,
            PistonHeadAnimation.State animation
    ) {
        if (animation.strokeDirection() == Direction.DOWN) {
            buffer.center();
            buffer.rotateX((float) Math.PI);
            buffer.uncenter();
        }
        return buffer;
    }

    private static SuperByteBuffer yawPistonBodyFrame(SuperByteBuffer buffer, Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            buffer.rotateY((float) (-Math.PI / 2.0D));
        }
        return buffer;
    }

    private static SuperByteBuffer yawLinkageFrame(SuperByteBuffer buffer, Direction.Axis axis) {
        if (axis == Direction.Axis.Z) {
            buffer.rotateY((float) (-Math.PI / 2.0D));
        }
        return buffer;
    }

    private static int partLight(Level level, BlockPos basePos, float y, Direction strokeDirection, int fallbackLight) {
        if (level == null) {
            return fallbackLight;
        }

        int maxDistance = EngineValidator.shaftDistanceForPistonBodies(EngineValidator.MAX_PISTON_BODIES);
        int blockOffset = Math.max(0, Math.min(maxDistance, Math.round(Math.abs(y))));
        return LevelRenderer.getLightColor(level, basePos.relative(strokeDirection, blockOffset));
    }
}
