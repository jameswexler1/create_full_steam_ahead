package dev.gustavo.fullsteamahead.compat.create;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import dev.gustavo.fullsteamahead.FullSteamAhead;
import dev.gustavo.fullsteamahead.compat.movement.FullSteamMovementRules;

public final class CreateMovementCompat {
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        BlockMovementChecks.registerMovementNecessaryCheck(FullSteamMovementRules::isMovementNecessary);
        BlockMovementChecks.registerMovementAllowedCheck(FullSteamMovementRules::isMovementAllowed);
        BlockMovementChecks.registerBrittleCheck(FullSteamMovementRules::isBrittle);
        BlockMovementChecks.registerNotSupportiveCheck(FullSteamMovementRules::isNotSupportive);
        BlockMovementChecks.registerAttachedCheck(FullSteamMovementRules::isBlockAttachedTowards);
        FullSteamAhead.LOGGER.info("Registered Full Steam Ahead Create movement checks");
    }

    private CreateMovementCompat() {
    }
}
