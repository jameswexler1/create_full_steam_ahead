package dev.gustavo.fullsteamahead.content.shaft;

import org.junit.jupiter.api.Test;

import static dev.gustavo.fullsteamahead.content.shaft.FsaKineticNetworkPolicy.Decision.COORDINATE;
import static dev.gustavo.fullsteamahead.content.shaft.FsaKineticNetworkPolicy.Decision.DEFER_TO_CREATE;
import static dev.gustavo.fullsteamahead.content.shaft.FsaKineticNetworkPolicy.Decision.FORCE_STOP;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FsaKineticNetworkPolicyTest {
    @Test
    void activeFullSteamBankUsesCoordinatedSpeed() {
        assertEquals(COORDINATE, FsaKineticNetworkPolicy.decide(true, true, false, true));
    }

    @Test
    void closedAllZeroFullSteamGraphIsForceStopped() {
        assertEquals(FORCE_STOP, FsaKineticNetworkPolicy.decide(true, false, false, true));
    }

    @Test
    void externalGeneratorPreservesCreatePassiveDrive() {
        assertEquals(DEFER_TO_CREATE, FsaKineticNetworkPolicy.decide(true, false, true, true));
    }

    @Test
    void unresolvedSourceEdgeCannotBeForceStopped() {
        assertEquals(DEFER_TO_CREATE, FsaKineticNetworkPolicy.decide(true, false, false, false));
    }

    @Test
    void incompatibleTopologyRemainsCreateOwned() {
        assertEquals(DEFER_TO_CREATE, FsaKineticNetworkPolicy.decide(false, false, false, true));
    }
}
