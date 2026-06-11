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
    private static final int[] CONNECTING_ROD_LENGTH_UNITS = {
            14,
            31,
            44
    };
    private static final int CONNECTING_ROD_BASE_LENGTH_UNITS = 14;
    private static final int CONNECTING_ROD_SEGMENT_UNITS = 8;
    private static final float PISTON_WRIST_PIN_Y = 14.0F / 16.0F;
    private static final float HEAD_TO_PISTON_BODY_Y = 1.0F;
    private static final float HALF_PI = (float) (Math.PI / 2.0D);
    private static final float CRANK_DOWN_REST_OFFSET = HALF_PI;

    public static State state(PistonHeadBlockEntity engine) {
        boolean visible = engine.isEngineAssembled();
        FullSteamPoweredShaftBlockEntity shaft = engine.getShaft();
        Direction.Axis shaftAxis = engine.getShaftAxis();
        float angle = engine.isLinkageMoving() && shaft != null
                ? KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), shaftAxis)
                : 0;
        angle += engine.getAnimationPhaseOffset();
        return state(
                visible,
                angle,
                shaftAxis,
                engine.getStrokeDirection(),
                engine.getPistonBodyCount(),
                engine.getShaftGap(),
                engine.getShaftDistance()
        );
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
        int gap = EngineValidator.defaultShaftGapForPistonBodies(count);
        return state(
                visible,
                angle,
                shaftAxis,
                strokeDirection,
                count,
                gap,
                EngineValidator.shaftDistanceForPistonBodies(count, gap)
        );
    }

    public static State state(
            boolean visible,
            float angle,
            Direction.Axis shaftAxis,
            Direction strokeDirection,
            int pistonBodyCount,
            int shaftGap,
            int shaftDistance
    ) {
        int count = clampPistonBodyCount(pistonBodyCount);
        int gap = clampShaftGap(shaftGap);
        int distance = Math.max(
                EngineValidator.shaftDistanceForPistonBodies(count, EngineValidator.MIN_SHAFT_GAP),
                Math.min(
                        shaftDistance,
                        EngineValidator.shaftDistanceForPistonBodies(count, EngineValidator.MAX_SHAFT_GAP)
                )
        );
        float crankRadius = crankRadius(gap);
        float connectingRodLength = connectingRodLength(gap);
        float shaftBaseY = distance;
        float shaftCenterY = shaftBaseY + 0.5F;
        float linkageAngle = angle + CRANK_DOWN_REST_OFFSET;
        float crankDepth = crankRadius * Mth.cos(linkageAngle);
        float crankVertical = -crankRadius * Mth.sin(linkageAngle);
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
                linkageAngle,
                shaftAxis,
                strokeDirection,
                count,
                gap,
                distance,
                headY,
                pistonY,
                connectingRodY,
                connectingRodAngle
        );
    }

    public static int clampPistonBodyCount(int pistonBodyCount) {
        return Mth.clamp(pistonBodyCount, EngineValidator.MIN_PISTON_BODIES, EngineValidator.MAX_PISTON_BODIES);
    }

    public static int clampShaftGap(int shaftGap) {
        return EngineValidator.clampShaftGap(shaftGap);
    }

    public static float crankRadius(int shaftGap) {
        return CRANK_RADII[clampShaftGap(shaftGap) - 1];
    }

    public static float connectingRodLength(int shaftGap) {
        return connectingRodLengthUnits(shaftGap) / 16.0F;
    }

    public static int connectingRodMiddleSegmentsForGap(int shaftGap) {
        int extraUnits = connectingRodUpperOffsetUnits(shaftGap);
        return (int) Math.ceil(extraUnits / (float) CONNECTING_ROD_SEGMENT_UNITS) + 1;
    }

    public static int maxConnectingRodMiddleSegments() {
        return connectingRodMiddleSegmentsForGap(EngineValidator.MAX_SHAFT_GAP);
    }

    public static float connectingRodMiddleOffset(int segmentIndex) {
        return segmentIndex * CONNECTING_ROD_SEGMENT_UNITS / 16.0F;
    }

    public static float connectingRodUpperOffsetForGap(int shaftGap) {
        return connectingRodUpperOffsetUnits(shaftGap) / 16.0F;
    }

    private static int connectingRodUpperOffsetUnits(int shaftGap) {
        return connectingRodLengthUnits(shaftGap) - CONNECTING_ROD_BASE_LENGTH_UNITS;
    }

    private static int connectingRodLengthUnits(int shaftGap) {
        return CONNECTING_ROD_LENGTH_UNITS[clampShaftGap(shaftGap) - 1];
    }

    public record State(
            boolean visible,
            float angle,
            Direction.Axis shaftAxis,
            Direction strokeDirection,
            int pistonBodyCount,
            int shaftGap,
            int shaftDistance,
            float headY,
            float pistonY,
            float connectingRodY,
            float connectingRodAngle
    ) {
        public float pistonY(int blockIndex) {
            return pistonY + blockIndex;
        }

        public boolean isRodConnectionPiston(int blockIndex) {
            return blockIndex == pistonBodyCount - 1;
        }

        public int connectingRodMiddleSegments() {
            return PistonHeadAnimation.connectingRodMiddleSegmentsForGap(shaftGap);
        }

        public float connectingRodUpperOffset() {
            return PistonHeadAnimation.connectingRodUpperOffsetForGap(shaftGap);
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
