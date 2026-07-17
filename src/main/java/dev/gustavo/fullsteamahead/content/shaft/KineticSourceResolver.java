package dev.gustavo.fullsteamahead.content.shaft;

import java.util.List;
import java.util.Objects;

final class KineticSourceResolver {
    private static final float CONVEYED_SPEED_EPSILON = 1.0E-4F;

    static <T> T resolve(float currentChildSpeed, List<Candidate<T>> candidates) {
        if (!Float.isFinite(currentChildSpeed)) {
            return null;
        }

        Candidate<T> selected = null;
        int selectedRank = -1;
        boolean ambiguous = false;
        for (Candidate<T> candidate : candidates) {
            if (!matchesCurrentSpeed(currentChildSpeed, candidate.conveyedSpeed())) {
                continue;
            }

            int rank = candidate.exactPositionInstance()
                    ? 2
                    : candidate.matchingPositionType() ? 1 : 0;
            if (rank > selectedRank) {
                selected = candidate;
                selectedRank = rank;
                ambiguous = false;
            } else if (rank == selectedRank) {
                ambiguous = true;
            }
        }

        return selected == null || ambiguous ? null : selected.source();
    }

    private static boolean matchesCurrentSpeed(float currentSpeed, float conveyedSpeed) {
        return Float.isFinite(conveyedSpeed)
                && Math.abs(currentSpeed - conveyedSpeed) <= CONVEYED_SPEED_EPSILON;
    }

    record Candidate<T>(
            T source,
            boolean exactPositionInstance,
            boolean matchingPositionType,
            float conveyedSpeed
    ) {
        Candidate {
            Objects.requireNonNull(source);
        }
    }

    private KineticSourceResolver() {
    }
}
