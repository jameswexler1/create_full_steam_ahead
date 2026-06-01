package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class PistonHeadAnimation {
    public static final int PISTON_BLOCKS = 1;
    public static final float CONNECTING_ROD_SMALL_END_Y = 2.0F / 16.0F;

    private static final float CRANK_RADIUS = 7.5F / 16.0F;
    private static final float CONNECTING_ROD_LENGTH = 14.0F / 16.0F;
    private static final float PISTON_WRIST_PIN_Y = 14.0F / 16.0F;
    private static final float HEAD_TO_PISTON_BODY_Y = 1.0F;
    private static final float SHAFT_BASE_Y = 3.0F;
    private static final float SHAFT_CENTER_Y = SHAFT_BASE_Y + 0.5F;
    private static final float HALF_PI = (float) (Math.PI / 2.0D);

    public static State state(PistonHeadBlockEntity engine) {
        boolean visible = engine.isEngineAssembled();
        FullSteamPoweredShaftBlockEntity shaft = engine.getShaft();
        Direction.Axis shaftAxis = engine.getShaftAxis();
        float angle = engine.isLinkageMoving() && shaft != null
                ? KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), shaftAxis)
                : 0;
        angle += engine.getAnimationPhaseOffset();
        return state(visible, angle, shaftAxis, engine.getStrokeDirection());
    }

    public static State state(boolean visible, float angle) {
        return state(visible, angle, Direction.Axis.Z);
    }

    public static State state(boolean visible, float angle, Direction.Axis shaftAxis) {
        return state(visible, angle, shaftAxis, Direction.UP);
    }

    public static State state(boolean visible, float angle, Direction.Axis shaftAxis, Direction strokeDirection) {
        float crankDepth = CRANK_RADIUS * Mth.cos(angle);
        float crankVertical = -CRANK_RADIUS * Mth.sin(angle);
        float rodVertical = Mth.sqrt(Math.max(
                0.0F,
                CONNECTING_ROD_LENGTH * CONNECTING_ROD_LENGTH - crankDepth * crankDepth
        ));
        float wristY = SHAFT_CENTER_Y + crankVertical - rodVertical;
        float pistonY = wristY - PISTON_WRIST_PIN_Y;
        float headY = pistonY - HEAD_TO_PISTON_BODY_Y;
        float connectingRodY = wristY - CONNECTING_ROD_SMALL_END_Y;
        float connectingRodAngle = (float) Math.asin(Mth.clamp(crankDepth / CONNECTING_ROD_LENGTH, -1.0F, 1.0F));
        float strokeSign = strokeDirection == Direction.DOWN ? -1.0F : 1.0F;
        return new State(
                visible,
                angle,
                shaftAxis,
                strokeDirection,
                headY * strokeSign,
                pistonY * strokeSign,
                connectingRodY * strokeSign,
                connectingRodAngle * strokeSign
        );
    }

    public record State(
            boolean visible,
            float angle,
            Direction.Axis shaftAxis,
            Direction strokeDirection,
            float headY,
            float pistonY,
            float connectingRodY,
            float connectingRodAngle
    ) {
        public float pistonY(int blockIndex) {
            return pistonY + blockIndex;
        }

        public float connectingRodRotation() {
            return connectingRodAngle;
        }

        public float crankY() {
            return strokeDirection == Direction.DOWN ? -SHAFT_BASE_Y : SHAFT_BASE_Y;
        }

        public float crankRotation() {
            return (strokeDirection == Direction.DOWN ? -angle : angle) - HALF_PI;
        }
    }

    private PistonHeadAnimation() {
    }
}
