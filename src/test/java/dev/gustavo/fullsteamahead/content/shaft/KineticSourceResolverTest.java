package dev.gustavo.fullsteamahead.content.shaft;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class KineticSourceResolverTest {
    @Test
    void resolvesAnOrdinarySingleSource() {
        Object source = new Object();

        Object resolved = KineticSourceResolver.resolve(32.0F, List.of(
                new KineticSourceResolver.Candidate<>(source, true, true, 32.0F)
        ));

        assertSame(source, resolved);
    }

    @Test
    void selectsTheSameCoordinateIdentityThatActuallyConveysTheChildSpeed() {
        Object ordinaryIdentity = new Object();
        Object extraKineticIdentity = new Object();

        Object resolved = KineticSourceResolver.resolve(-256.0F, List.of(
                new KineticSourceResolver.Candidate<>(ordinaryIdentity, false, true, 0.0F),
                new KineticSourceResolver.Candidate<>(extraKineticIdentity, false, false, -256.0F)
        ));

        assertSame(extraKineticIdentity, resolved);
    }

    @Test
    void exactPositionIdentityBreaksAnOtherwiseEqualTie() {
        Object exactIdentity = new Object();
        Object coordinateTwin = new Object();

        Object resolved = KineticSourceResolver.resolve(48.0F, List.of(
                new KineticSourceResolver.Candidate<>(coordinateTwin, false, true, 48.0F),
                new KineticSourceResolver.Candidate<>(exactIdentity, true, true, 48.0F)
        ));

        assertSame(exactIdentity, resolved);
    }

    @Test
    void explicitPositionTypeBreaksAnOtherwiseEqualTie() {
        Object matchingType = new Object();
        Object otherType = new Object();

        Object resolved = KineticSourceResolver.resolve(16.0F, List.of(
                new KineticSourceResolver.Candidate<>(otherType, false, false, 16.0F),
                new KineticSourceResolver.Candidate<>(matchingType, false, true, 16.0F)
        ));

        assertSame(matchingType, resolved);
    }

    @Test
    void rejectsAnAmbiguousOrInvalidSourceSet() {
        Object first = new Object();
        Object second = new Object();

        assertNull(KineticSourceResolver.resolve(16.0F, List.of(
                new KineticSourceResolver.Candidate<>(first, false, true, 16.0F),
                new KineticSourceResolver.Candidate<>(second, false, true, 16.0F)
        )));
        assertNull(KineticSourceResolver.resolve(16.0F, List.of(
                new KineticSourceResolver.Candidate<>(first, true, true, Float.NaN)
        )));
    }
}
