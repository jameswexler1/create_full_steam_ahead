package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.compat.create.BoilerSteamPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public interface FullSteamDirectBoilerSource extends SteamNetworkReadout {
    List<BoilerSteamPort> fullSteamAhead$getActiveDirectSteamPorts();

    IFluidHandler fullSteamAhead$getDirectSteamFluidHandler(BlockPos tankPos, Direction side, IFluidHandler fallback);

    int fullSteamAhead$getDirectStoredSteamMb(BoilerSteamPort port);

    int fullSteamAhead$getDirectProductionMb(BoilerSteamPort port);

    int fullSteamAhead$getDirectBoilerVolume(BoilerSteamPort port);

    double fullSteamAhead$getDirectTemperatureK(BoilerSteamPort port);

    double fullSteamAhead$getDirectNetworkPressurePn(BoilerSteamPort port);

    int fullSteamAhead$drainDirectSteam(BoilerSteamPort port, int amount, boolean externallyDrained);

    int fullSteamAhead$getBoilerStoredSteamMb();

    int fullSteamAhead$getBoilerProductionMb();

    int fullSteamAhead$getBoilerVolume();

    double fullSteamAhead$getBoilerTemperatureK();

    double fullSteamAhead$getBoilerNetworkPressurePn();

    int fullSteamAhead$drainBoilerSteam(int amount);

    void fullSteamAhead$applyDirectNetworkState(
            BoilerSteamPort port,
            double pressurePn,
            boolean venting,
            boolean warn,
            int production,
            int networkVolume,
            int engines,
            int consumed
    );

    void fullSteamAhead$applyBoilerNetworkState(
            double pressurePn,
            boolean venting,
            boolean warn,
            int production,
            int networkVolume,
            int engines,
            int consumed
    );

    void fullSteamAhead$clearDirectEffectivePressure();

    void fullSteamAhead$burstDirectBoiler(double networkVolumeM3, double pressurePn);
}
