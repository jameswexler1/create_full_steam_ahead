package dev.gustavo.fullsteamahead.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KineticNetworkPhaseStateTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    void delayedComponentsReceiveTheSameNetworkCorrection() {
        KineticNetworkPhaseState phase = new KineticNetworkPhaseState(16.0F, 0.0D);

        phase.observeReferenceSpeed(32.0F, 100.0F);
        int firstEngineCorrection = phase.roundedCorrectionForRatio(1.0F);

        phase.observeReferenceSpeed(32.0F, 140.0F);
        int laterEngineCorrection = phase.roundedCorrectionForRatio(1.0F);

        assertEquals(firstEngineCorrection, laterEngineCorrection);
    }

    @Test
    void speedChangePreservesTheReferenceAngle() {
        KineticNetworkPhaseState phase = new KineticNetworkPhaseState(16.0F, 0.0D);
        double oldAngle = angleAt(100.0D, 16.0D, 0.0D);

        phase.observeReferenceSpeed(32.0F, 100.0F);
        double newAngle = angleAt(100.0D, 32.0D, phase.referenceCorrectionDegrees());

        assertEquals(oldAngle, newAngle, EPSILON);
    }

    @Test
    void signedSpeedRatiosPreserveConnectedComponentAngles() {
        KineticNetworkPhaseState phase = new KineticNetworkPhaseState(16.0F, 0.0D);
        double oldFastAngle = angleAt(75.0D, -32.0D, 0.0D);

        phase.observeReferenceSpeed(40.0F, 75.0F);
        double fastCorrection = phase.correctionForRatio(-2.0F);
        double newFastAngle = angleAt(75.0D, -80.0D, fastCorrection);

        assertEquals(oldFastAngle, newFastAngle, EPSILON);
    }

    @Test
    void fractionalRatiosUseTheUnwrappedNetworkCorrection() {
        KineticNetworkPhaseState phase = new KineticNetworkPhaseState(16.0F, 0.0D);

        phase.observeReferenceSpeed(32.0F, 1000.0F);

        assertEquals(120, phase.roundedCorrectionForRatio(0.5F));
    }

    @Test
    void equalSpeedEngineBankKeepsAlternatingPhaseInputs() {
        KineticNetworkPhaseState phase = new KineticNetworkPhaseState(20.0F, 0.0D);
        phase.observeReferenceSpeed(37.5F, 240.0F);

        double first = phase.correctionForRatio(1.0F);
        double second = phase.correctionForRatio(1.0F) + 180.0D;
        double third = phase.correctionForRatio(1.0F) + 360.0D;

        assertEquals(180.0D, normalize(second - first), EPSILON);
        assertEquals(0.0D, normalize(third - first), EPSILON);
    }

    private static double angleAt(double renderTime, double speed, double correction) {
        return renderTime * speed * KineticNetworkPhaseState.DEGREES_PER_TICK_PER_RPM + correction;
    }

    private static double normalize(double degrees) {
        double result = degrees % 360.0D;
        return result < 0.0D ? result + 360.0D : result;
    }
}
