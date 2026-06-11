package dev.gustavo.fullsteamahead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.steam.SteamReliefValveBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamReliefValveBlockEntity;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SteamReliefValveRenderer extends SafeBlockEntityRenderer<SteamReliefValveBlockEntity> {
    private static final float CAP_LIFT_BLOCKS = 1.5F / 16.0F;

    public SteamReliefValveRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
            SteamReliefValveBlockEntity valve,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        BlockState state = valve.getBlockState();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.cutoutMipped());

        float open = valve.getRenderedOpen(partialTicks);
        Level level = valve.getLevel();
        long gameTime = level == null ? 0L : level.getGameTime();
        float jitter = valve.isVenting()
                ? Mth.sin((gameTime + partialTicks) * 1.7F) * 0.012F
                : 0.0F;
        float lift = open * CAP_LIFT_BLOCKS + jitter;
        SuperByteBuffer cap = CachedBuffers.partial(FullSteamPartialModels.steamReliefValveCap(), state);
        cap.translate(0.0F, lift, 0.0F);
        orient(cap, state)
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);

        SuperByteBuffer handwheel = CachedBuffers.partial(FullSteamPartialModels.steamReliefValveHandwheel(), state);
        orient(handwheel, state)
                .translate(8.0F / 16.0F, 7.5F / 16.0F, 1.75F / 16.0F)
                .rotate(Direction.SOUTH.getAxis(), valve.getWheelAngle(partialTicks))
                .translate(-8.0F / 16.0F, -7.5F / 16.0F, -1.75F / 16.0F)
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, vertexConsumer);
    }

    private static SuperByteBuffer orient(SuperByteBuffer buffer, BlockState state) {
        Direction attachedFace = SteamReliefValveBlock.getAttachedFace(state);
        Direction visualFacing = state.getValue(SteamReliefValveBlock.FACING);
        float yRotation = AngleHelper.horizontalAngle(attachedFace == Direction.UP ? visualFacing : attachedFace);
        buffer.rotateCentered((yRotation / 180.0F) * (float) Math.PI, Direction.UP);
        if (attachedFace != Direction.UP) {
            buffer.rotateCentered((90.0F / 180.0F) * (float) Math.PI, Direction.EAST);
        }
        return buffer;
    }
}
