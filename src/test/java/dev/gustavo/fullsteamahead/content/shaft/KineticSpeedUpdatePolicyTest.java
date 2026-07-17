package dev.gustavo.fullsteamahead.content.shaft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticSpeedUpdatePolicyTest {
    @Test
    void startsStopsAndReversalsRemainImmediate() {
        assertTrue(KineticSpeedUpdatePolicy.requiresImmediateUpdate(0.0F, 1.0F));
        assertTrue(KineticSpeedUpdatePolicy.requiresImmediateUpdate(32.0F, 0.0F));
        assertTrue(KineticSpeedUpdatePolicy.requiresImmediateUpdate(32.0F, -32.0F));
        assertFalse(KineticSpeedUpdatePolicy.requiresImmediateUpdate(16.0F, 32.0F));
    }

    @Test
    void activeRampWaitsForSharedUpdateWindow() {
        assertFalse(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                16.0F, 16.75F, 19L, 10L, 19L
        ));
        assertTrue(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                16.0F, 16.75F, 20L, 10L, 19L
        ));
    }

    @Test
    void consecutivePropagationKeepsACompleteBoilerSampleWindow() {
        assertFalse(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                16.0F, 17.0F, 20L, 18L, 20L
        ));
        assertTrue(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                16.0F, 17.0F, 30L, 18L, 20L
        ));
    }

    @Test
    void subDeadbandChangesAccumulateAgainstAppliedSpeed() {
        assertFalse(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                16.0F, 16.49F, 20L, 10L, 20L
        ));
        assertTrue(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                16.0F, 16.5F, 20L, 10L, 20L
        ));
    }

    @Test
    void stableSubDeadbandTargetIsEventuallyAppliedExactly() {
        assertFalse(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                16.0F, 16.25F, 20L, 10L, 10L
        ));
        assertTrue(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                16.0F, 16.25F, 30L, 20L, 10L
        ));
    }

    @Test
    void matchingSpeedNeverRequestsPropagation() {
        assertFalse(KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                32.0F, 32.0F, 100L, 0L, 0L
        ));
    }

    @Test
    void activeRootSpeedChangesCanRetimeWithoutNetworkDetachment() {
        assertTrue(KineticSpeedUpdatePolicy.canRetimeActiveNetworkInPlace(
                16.0F, 32.0F, false, true
        ));
        assertTrue(KineticSpeedUpdatePolicy.canRetimeActiveNetworkInPlace(
                -16.0F, -32.0F, false, true
        ));
    }

    @Test
    void topologyChangingTransitionsKeepCreateDefaultPropagation() {
        assertFalse(KineticSpeedUpdatePolicy.canRetimeActiveNetworkInPlace(
                0.0F, 16.0F, false, true
        ));
        assertFalse(KineticSpeedUpdatePolicy.canRetimeActiveNetworkInPlace(
                16.0F, 0.0F, false, true
        ));
        assertFalse(KineticSpeedUpdatePolicy.canRetimeActiveNetworkInPlace(
                16.0F, -16.0F, false, true
        ));
        assertFalse(KineticSpeedUpdatePolicy.canRetimeActiveNetworkInPlace(
                16.0F, 32.0F, true, true
        ));
        assertFalse(KineticSpeedUpdatePolicy.canRetimeActiveNetworkInPlace(
                16.0F, 32.0F, false, false
        ));
    }
}
