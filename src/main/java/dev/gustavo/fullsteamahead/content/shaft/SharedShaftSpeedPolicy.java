package dev.gustavo.fullsteamahead.content.shaft;

import java.util.List;

/**
 * Converts local generator commands into one command in the kinetic network owner's speed frame.
 */
final class SharedShaftSpeedPolicy {
    private static final float SPEED_EPSILON = 1.0E-5F;

    static Selection select(float ownerSpeed, List<SourceSpeed> sources) {
        if (!isUsableSpeed(ownerSpeed)) {
            return Selection.incompatible();
        }

        float selectedOwnerSpeed = 0.0F;
        boolean hasActiveSource = false;
        for (SourceSpeed source : sources) {
            float generatedSpeed = source.generatedSpeed();
            if (!Float.isFinite(generatedSpeed)) {
                return Selection.incompatible();
            }
            if (Math.abs(generatedSpeed) < SPEED_EPSILON) {
                continue;
            }

            float actualSpeed = source.actualSpeed();
            if (!isUsableSpeed(actualSpeed)) {
                return Selection.incompatible();
            }

            float equivalentOwnerSpeed = generatedSpeed * ownerSpeed / actualSpeed;
            if (!isUsableSpeed(equivalentOwnerSpeed)
                    || Math.signum(equivalentOwnerSpeed) != Math.signum(ownerSpeed)) {
                return Selection.incompatible();
            }

            if (!hasActiveSource || Math.abs(equivalentOwnerSpeed) > Math.abs(selectedOwnerSpeed)) {
                selectedOwnerSpeed = equivalentOwnerSpeed;
                hasActiveSource = true;
            }
        }

        return hasActiveSource
                ? Selection.active(selectedOwnerSpeed)
                : Selection.noActiveSource();
    }

    private static boolean isUsableSpeed(float speed) {
        return Float.isFinite(speed) && Math.abs(speed) >= SPEED_EPSILON;
    }

    record SourceSpeed(float actualSpeed, float generatedSpeed) {
    }

    record Selection(boolean compatible, boolean active, float ownerSpeed) {
        private static Selection incompatible() {
            return new Selection(false, false, 0.0F);
        }

        private static Selection noActiveSource() {
            return new Selection(true, false, 0.0F);
        }

        private static Selection active(float ownerSpeed) {
            return new Selection(true, true, ownerSpeed);
        }
    }

    private SharedShaftSpeedPolicy() {
    }
}
