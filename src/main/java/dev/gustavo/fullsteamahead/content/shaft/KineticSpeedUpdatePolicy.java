package dev.gustavo.fullsteamahead.content.shaft;

final class KineticSpeedUpdatePolicy {
    static final int UPDATE_INTERVAL_TICKS = 10;
    static final int MINIMUM_QUIET_TICKS = 5;
    static final int SETTLE_DELAY_TICKS = 20;
    static final float UPDATE_DEADBAND_RPM = 0.5F;

    static boolean requiresImmediateUpdate(float appliedSpeed, float targetSpeed) {
        if (!Float.isFinite(appliedSpeed) || !Float.isFinite(targetSpeed)) {
            return true;
        }
        if (appliedSpeed == 0.0F || targetSpeed == 0.0F) {
            return appliedSpeed != targetSpeed;
        }
        return Math.signum(appliedSpeed) != Math.signum(targetSpeed);
    }

    static boolean shouldApplyDeferredUpdate(
            float appliedSpeed,
            float targetSpeed,
            long gameTime,
            long lastAppliedGameTime,
            long lastTargetChangeGameTime
    ) {
        if (sameSpeed(appliedSpeed, targetSpeed) || requiresImmediateUpdate(appliedSpeed, targetSpeed)) {
            return false;
        }
        if (Math.floorMod(gameTime, UPDATE_INTERVAL_TICKS) != 0L
                || !elapsedAtLeast(gameTime, lastAppliedGameTime, MINIMUM_QUIET_TICKS)) {
            return false;
        }

        if (Math.abs(appliedSpeed - targetSpeed) >= UPDATE_DEADBAND_RPM) {
            return true;
        }
        return elapsedAtLeast(gameTime, lastTargetChangeGameTime, SETTLE_DELAY_TICKS);
    }

    static boolean sameSpeed(float first, float second) {
        return Float.floatToIntBits(first) == Float.floatToIntBits(second);
    }

    static boolean canRetimeActiveNetworkInPlace(
            float appliedSpeed,
            float targetSpeed,
            boolean hasSource,
            boolean hasNetwork
    ) {
        return hasNetwork
                && !hasSource
                && !sameSpeed(appliedSpeed, targetSpeed)
                && !requiresImmediateUpdate(appliedSpeed, targetSpeed);
    }

    private static boolean elapsedAtLeast(long gameTime, long previousGameTime, int requiredTicks) {
        return previousGameTime == Long.MIN_VALUE
                || gameTime >= previousGameTime && gameTime - previousGameTime >= requiredTicks;
    }

    private KineticSpeedUpdatePolicy() {
    }
}
