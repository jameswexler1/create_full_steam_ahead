package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import net.minecraft.util.Mth;

public final class PistonHeadAnimation {
    public static final int PISTON_BLOCKS = 1;

    private static final float STROKE_BLOCKS = 1.25F;
    private static final float HEAD_BASE_Y = 0.0F;
    private static final float PISTON_BASE_Y = 1.0F;
    private static final float CRANK_PIN_BASE_Y = 3.0F;

    public static State state(PistonHeadBlockEntity engine) {
        boolean visible = engine.isEngineAssembled();
        FullSteamPoweredShaftBlockEntity shaft = engine.getShaft();
        float angle = engine.isEngineRunning() && shaft != null
                ? KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), engine.getShaftAxis())
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

        public float crankPinY() {
            return CRANK_PIN_BASE_Y;
        }
    }

    private PistonHeadAnimation() {
    }
}
