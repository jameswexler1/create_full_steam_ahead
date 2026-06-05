package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.piston.EngineValidator;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class PistonHeadAnimation {
    public static final int MAX_PISTON_BLOCKS = EngineValidator.MAX_PISTON_BODIES;
    public static final float CONNECTING_ROD_SMALL_END_Y = 2.0F / 16.0F;

    private static final float[] CRANK_RADII = {
            7.5F / 16.0F,
            11.0F / 16.0F,
            14.0F / 16.0F
    };
    private static final float[] CONNECTING_ROD_LENGTHS = {
            14.0F / 16.0F,
            46.0F / 16.0F,
            78.0F / 16.0F
    };
    private static final int CONNECTING_ROD_BASE_LENGTH_UNITS = 14;
    private static final int CONNECTING_ROD_SEGMENT_UNITS = 8;
    private static final float PISTON_WRIST_PIN_Y = 14.0F / 16.0F;
    private static final float HEAD_TO_PISTON_BODY_Y = 1.0F;
    private static final float HALF_PI = (float) (Math.PI / 2.0D);

    public static State state(PistonHeadBlockEntity engine) {
        boolean visible = engine.isEngineAssembled();
        FullSteamPoweredShaftBlockEntity shaft = engine.getShaft();
        Direction.Axis shaftAxis = engine.getShaftAxis();
        float angle = engine.isLinkageMoving() && shaft != null
                ? KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), shaftAxis)
                : 0;
        angle += engine.getAnimationPhaseOffset();
        return state(visible, angle, shaftAxis, engine.getStrokeDirection(), engine.getPistonBodyCount());
    }

    public static State state(boolean visible, float angle) {
        return state(visible, angle, Direction.Axis.Z);
    }

    public static State state(boolean visible, float angle, Direction.Axis shaftAxis) {
        return state(visible, angle, shaftAxis, Direction.UP);
    }

    public static State state(boolean visible, float angle, Direction.Axis shaftAxis, Direction strokeDirection) {
        return state(visible, angle, shaftAxis, strokeDirection, EngineValidator.MIN_PISTON_BODIES);
    }

    public static State state(
            boolean visible,
            float angle,
            Direction.Axis shaftAxis,
            Direction strokeDirection,
            int pistonBodyCount
    ) {
        int count = clampPistonBodyCount(pistonBodyCount);
        float crankRadius = crankRadius(count);
        float connectingRodLength = connectingRodLength(count);
        float shaftBaseY = EngineValidator.shaftDistanceForPistonBodies(count);
        float shaftCenterY = shaftBaseY + 0.5F;
        float crankDepth = crankRadius * Mth.cos(angle);
        float crankVertical = -crankRadius * Mth.sin(angle);
        float rodVertical = Mth.sqrt(Math.max(
                0.0F,
                connectingRodLength * connectingRodLength - crankDepth * crankDepth
        ));
        float wristY = shaftCenterY + crankVertical - rodVertical;
        float pistonY = wristY - PISTON_WRIST_PIN_Y - (count - 1);
        float headY = pistonY - HEAD_TO_PISTON_BODY_Y;
        float connectingRodY = wristY - CONNECTING_ROD_SMALL_END_Y;
        float connectingRodAngle = (float) Math.asin(Mth.clamp(crankDepth / connectingRodLength, -1.0F, 1.0F));
        // The slider-crank is always solved in the upright frame. Inverted engines are
        // rendered by rigidly flipping the fully posed linkage 180 degrees about the head
        // block center (see orientForStroke), which keeps every joint connected.
        return new State(
                visible,
                angle,
                shaftAxis,
                strokeDirection,
                count,
                EngineValidator.shaftDistanceForPistonBodies(count),
                headY,
                pistonY,
                connectingRodY,
                connectingRodAngle
        );
    }

    public static int clampPistonBodyCount(int pistonBodyCount) {
        return Mth.clamp(pistonBodyCount, EngineValidator.MIN_PISTON_BODIES, EngineValidator.MAX_PISTON_BODIES);
    }

    public static float crankRadius(int pistonBodyCount) {
        return CRANK_RADII[clampPistonBodyCount(pistonBodyCount) - 1];
    }

    public static float connectingRodLength(int pistonBodyCount) {
        return CONNECTING_ROD_LENGTHS[clampPistonBodyCount(pistonBodyCount) - 1];
    }

    public static int connectingRodMiddleSegments(int pistonBodyCount) {
        int extraUnits = connectingRodUpperOffsetUnits(pistonBodyCount);
        return extraUnits / CONNECTING_ROD_SEGMENT_UNITS + 1;
    }

    public static int maxConnectingRodMiddleSegments() {
        return connectingRodMiddleSegments(EngineValidator.MAX_PISTON_BODIES);
    }

    public static float connectingRodMiddleOffset(int segmentIndex) {
        return segmentIndex * CONNECTING_ROD_SEGMENT_UNITS / 16.0F;
    }

    public static float connectingRodUpperOffset(int pistonBodyCount) {
        return connectingRodUpperOffsetUnits(pistonBodyCount) / 16.0F;
    }

    private static int connectingRodUpperOffsetUnits(int pistonBodyCount) {
        return Math.round(connectingRodLength(pistonBodyCount) * 16.0F) - CONNECTING_ROD_BASE_LENGTH_UNITS;
    }

    public record State(
            boolean visible,
            float angle,
            Direction.Axis shaftAxis,
            Direction strokeDirection,
            int pistonBodyCount,
            int shaftDistance,
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
            return shaftDistance;
        }

        public float crankRotation() {
            return angle - HALF_PI;
        }
    }

    private PistonHeadAnimation() {
    }
}
