package dev.gustavo.fullsteamahead.content.shaft;

final class FsaKineticNetworkPolicy {
    static Decision decide(
            boolean compatible,
            boolean hasActiveFullSteamSource,
            boolean hasExternalSource,
            boolean hasClosedSourceGraph
    ) {
        if (!compatible || hasExternalSource) {
            return Decision.DEFER_TO_CREATE;
        }
        if (hasActiveFullSteamSource) {
            return Decision.COORDINATE;
        }
        return hasClosedSourceGraph ? Decision.FORCE_STOP : Decision.DEFER_TO_CREATE;
    }

    enum Decision {
        COORDINATE,
        FORCE_STOP,
        DEFER_TO_CREATE
    }

    private FsaKineticNetworkPolicy() {
    }
}
