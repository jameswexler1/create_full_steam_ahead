package dev.gustavo.fullsteamahead.compat.aeronautics;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public interface FullSteamAeronauticsSteamVent {
    boolean fullSteamAhead$isPipeFedSteamVent();

    IFluidHandler fullSteamAhead$getSteamFluidHandler(Direction side);

    int fullSteamAhead$getStoredSteamMb();

    int fullSteamAhead$getDisplayConsumedSteamMb();

    int fullSteamAhead$getRequestedSteamMb(double pressurePn);

    void fullSteamAhead$applyNetworkState(double pressurePn, int drawCap);

    int fullSteamAhead$drainSteam(int amount);
}
