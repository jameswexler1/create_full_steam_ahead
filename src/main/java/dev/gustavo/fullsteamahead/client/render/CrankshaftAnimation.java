package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.IRotate;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class CrankshaftAnimation {
    public static final int PISTON_BLOCKS = 2;

    private static final float STROKE_BLOCKS = 1.25F;
    private static final float HEAD_BASE_Y = -3.0F;
    private static final float PISTON_BASE_Y = -2.0F;

    public static State state(CrankshaftBlockEntity crankshaft) {
        boolean visible = crankshaft.isEngineAssembled();
        BlockState blockState = crankshaft.getBlockState();
        Direction.Axis axis = blockState.getBlock() instanceof IRotate rotating
                ? rotating.getRotationAxis(blockState)
                : Direction.Axis.X;
        float angle = crankshaft.isEngineRunning()
                ? KineticBlockEntityRenderer.getAngleForBe(crankshaft, crankshaft.getBlockPos(), axis)
                : 0;
        return state(visible, angle);
    }

    public static State state(boolean visible, float angle) {
        float pistonOffset = (1.0F - Mth.cos(angle)) * 0.5F * STROKE_BLOCKS;
        return new State(visible, angle, pistonOffset);
    }

    public record State(boolean visible, float angle, float pistonOffset) {
        public float pistonY(int blockIndex) {
            return PISTON_BASE_Y + blockIndex + pistonOffset;
        }

        public float headY() {
            return HEAD_BASE_Y + pistonOffset;
        }
    }

    private CrankshaftAnimation() {
    }
}
