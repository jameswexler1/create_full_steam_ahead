package dev.gustavo.fullsteamahead.client.render;

final class KineticNetworkPhaseState {
    static final double DEGREES_PER_TICK_PER_RPM = 0.3D;
    private static final float SPEED_EPSILON = 1.0E-5F;

    private float referenceSpeed;
    private double referenceCorrectionDegrees;

    KineticNetworkPhaseState(float referenceSpeed, double referenceCorrectionDegrees) {
        this.referenceSpeed = finiteOrZero(referenceSpeed);
        this.referenceCorrectionDegrees = finiteOrZero(referenceCorrectionDegrees);
    }

    void observeReferenceSpeed(float speed, float renderTime) {
        float nextSpeed = finiteOrZero(speed);
        if (sameSpeed(referenceSpeed, nextSpeed)) {
            return;
        }

        referenceCorrectionDegrees += (double) renderTime
                * (referenceSpeed - nextSpeed)
                * DEGREES_PER_TICK_PER_RPM;
        referenceSpeed = nextSpeed;
    }

    double correctionForRatio(float speedRatio) {
        return referenceCorrectionDegrees * finiteOrZero(speedRatio);
    }

    int roundedCorrectionForRatio(float speedRatio) {
        return (int) Math.round(wrapDegrees(correctionForRatio(speedRatio)));
    }

    double referenceCorrectionDegrees() {
        return referenceCorrectionDegrees;
    }

    private static boolean sameSpeed(float first, float second) {
        return Math.abs(first - second) < SPEED_EPSILON;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
