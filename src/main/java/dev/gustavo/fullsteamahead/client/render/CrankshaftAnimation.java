package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.crankshaft.CrankshaftBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class CrankshaftAnimation {
    public static final int ROD_SEGMENTS = 4;

    private static final float STROKE_BLOCKS = 1.25F;
    private static final float ROD_BASE_Y = -4.0F;

    public static State state(CrankshaftBlockEntity crankshaft) {
        boolean visible = crankshaft.isEngineRunning();
        float angle = visible
                ? KineticBlockEntityRenderer.getAngleForBe(crankshaft, crankshaft.getBlockPos(), Direction.Axis.Y)
                : 0;
        return state(visible, angle);
    }

    public static State state(boolean visible, float angle) {
        float pistonOffset = (1.0F - Mth.cos(angle)) * 0.5F * STROKE_BLOCKS;
        return new State(visible, angle, pistonOffset);
    }

    public record State(boolean visible, float angle, float pistonOffset) {
        public float rodY(int segment) {
            return ROD_BASE_Y + segment + pistonOffset;
        }

        public float headY() {
            return ROD_BASE_Y + pistonOffset;
        }
    }

    private CrankshaftAnimation() {
    }
}
