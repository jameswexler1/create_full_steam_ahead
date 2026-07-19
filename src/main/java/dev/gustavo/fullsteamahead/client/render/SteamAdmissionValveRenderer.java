package dev.gustavo.fullsteamahead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.steam.SteamAdmissionControlMode;
import dev.gustavo.fullsteamahead.content.steam.SteamAdmissionValveBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamAdmissionValveBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class SteamAdmissionValveRenderer extends SmartBlockEntityRenderer<SteamAdmissionValveBlockEntity> {
    private static final float LEVER_TRAVEL = 4.0F / 16.0F;

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
        // LinkBehaviour renders the selected frequency items and value-box highlights here.
        super.renderSafe(valve, partialTicks, poseStack, bufferSource, light, overlay);

        BlockState state = valve.getBlockState();
        int controllerLight = valve.getLevel() == null
                ? light
                : LevelRenderer.getLightColor(
                        valve.getLevel(),
                        state.getValue(SteamAdmissionValveBlock.INVERTED)
                                ? valve.getBlockPos().below()
                                : valve.getBlockPos().above()
                );
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutoutMipped());

        if (valve.getControlMode() == SteamAdmissionControlMode.MANUAL) {
            render(FullSteamPartialModels.steamAdmissionValveManualMechanism(), state, 0.0F,
                    poseStack, consumer, controllerLight, overlay);
            float leverOffset = valve.getRenderedManualStrength(partialTicks) / 15.0F * LEVER_TRAVEL;
            render(FullSteamPartialModels.steamAdmissionValveManualLever(), state, leverOffset,
                    poseStack, consumer, controllerLight, overlay);
        } else {
            render(FullSteamPartialModels.steamAdmissionValveReceiverPanel(), state, 0.0F,
                    poseStack, consumer, controllerLight, overlay);
        }
    }

    @Override
    public AABB getRenderBoundingBox(SteamAdmissionValveBlockEntity valve) {
        AABB base = new AABB(valve.getBlockPos()).inflate(1.0D, 0.0D, 1.0D);
        return valve.getBlockState().getValue(SteamAdmissionValveBlock.INVERTED)
                ? base.expandTowards(0.0D, -2.0D, 0.0D)
                : base.expandTowards(0.0D, 2.0D, 0.0D);
    }

    private static void render(
            dev.engine_room.flywheel.lib.model.baked.PartialModel partial,
            BlockState state,
            float yOffset,
            PoseStack poseStack,
            VertexConsumer consumer,
            int light,
            int overlay
    ) {
        SuperByteBuffer buffer = CachedBuffers.partial(partial, state);
        orient(buffer, state);
        buffer.translate(0.0F, yOffset, 0.0F)
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, consumer);
    }

    private static void orient(SuperByteBuffer buffer, BlockState state) {
        if (state.getValue(SteamAdmissionValveBlock.INVERTED)) {
            buffer.center();
            buffer.rotateZ((float) Math.PI);
            buffer.uncenter();
        }
        Direction facing = state.getValue(SteamAdmissionValveBlock.FACING);
        float angle = switch (facing) {
            case EAST -> (float) (Math.PI * 1.5D);
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (Math.PI / 2.0D);
            default -> 0.0F;
        };
        buffer.rotateCentered(angle, Direction.UP);
    }
}
