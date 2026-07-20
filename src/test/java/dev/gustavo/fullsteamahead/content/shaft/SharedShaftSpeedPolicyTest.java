package dev.gustavo.fullsteamahead.content.shaft;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedShaftSpeedPolicyTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void strongestEngineSetsOneSharedShaftSpeed() {
        SharedShaftSpeedPolicy.Selection selection = SharedShaftSpeedPolicy.select(16.0F, List.of(
                new SharedShaftSpeedPolicy.SourceSpeed(16.0F, 16.125F),
                new SharedShaftSpeedPolicy.SourceSpeed(16.0F, 16.5F),
                new SharedShaftSpeedPolicy.SourceSpeed(16.0F, 16.25F)
        ));

        assertTrue(selection.compatible());
        assertTrue(selection.active());
        assertEquals(16.5F, selection.ownerSpeed(), EPSILON);
    }

    @Test
    void activeFollowerKeepsBankRunningWhenOwnerLosesSteam() {
        SharedShaftSpeedPolicy.Selection selection = SharedShaftSpeedPolicy.select(32.0F, List.of(
                new SharedShaftSpeedPolicy.SourceSpeed(32.0F, 0.0F),
                new SharedShaftSpeedPolicy.SourceSpeed(32.0F, 31.75F)
        ));

        assertTrue(selection.compatible());
        assertTrue(selection.active());
        assertEquals(31.75F, selection.ownerSpeed(), EPSILON);
    }

    @Test
    void localGearRatiosConvertBackToOwnerSpeed() {
        SharedShaftSpeedPolicy.Selection selection = SharedShaftSpeedPolicy.select(20.0F, List.of(
                new SharedShaftSpeedPolicy.SourceSpeed(20.0F, 22.0F),
                new SharedShaftSpeedPolicy.SourceSpeed(-40.0F, -48.0F)
        ));

        assertTrue(selection.compatible());
        assertTrue(selection.active());
        assertEquals(24.0F, selection.ownerSpeed(), EPSILON);
    }

    @Test
    void allStoppedSourcesReportNoActiveFullSteamGenerator() {
        SharedShaftSpeedPolicy.Selection selection = SharedShaftSpeedPolicy.select(16.0F, List.of(
                new SharedShaftSpeedPolicy.SourceSpeed(16.0F, 0.0F),
                new SharedShaftSpeedPolicy.SourceSpeed(16.0F, 0.0F)
        ));

        assertTrue(selection.compatible());
        assertFalse(selection.active());
    }

    @Test
    void conflictingGeneratorDirectionIsNotCoordinated() {
        SharedShaftSpeedPolicy.Selection selection = SharedShaftSpeedPolicy.select(16.0F, List.of(
                new SharedShaftSpeedPolicy.SourceSpeed(16.0F, 20.0F),
                new SharedShaftSpeedPolicy.SourceSpeed(-16.0F, 20.0F)
        ));

        assertFalse(selection.compatible());
        assertFalse(selection.active());
    }

    @Test
    void activeSourceWithoutAUsableNetworkRatioIsRejected() {
        SharedShaftSpeedPolicy.Selection selection = SharedShaftSpeedPolicy.select(16.0F, List.of(
                new SharedShaftSpeedPolicy.SourceSpeed(0.0F, 20.0F)
        ));

        assertFalse(selection.compatible());
    }
}
