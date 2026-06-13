package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.compat.create.BoilerSteamPort;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class DirectBoilerSteamHandler implements IFluidHandler {
    private final FullSteamDirectBoilerSource source;
    private final BoilerSteamPort port;
    private final IFluidHandler fallback;

    public DirectBoilerSteamHandler(FullSteamDirectBoilerSource source, BoilerSteamPort port, IFluidHandler fallback) {
        this.source = source;
        this.port = port;
        this.fallback = fallback;
    }

    @Override
    public int getTanks() {
        return Math.max(1, fallback == null ? 0 : fallback.getTanks());
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        int stored = source.fullSteamAhead$getDirectStoredSteamMb(port);
        if (stored > 0) {
            return new FluidStack(ModFluids.STEAM.get(), stored);
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Math.max(FullSteamConfig.steamBufferCapMb(), fallback == null ? 0 : fallback.getTankCapacity(tank));
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        if (stack.is(ModFluids.STEAM.get())) {
            return true;
        }
        return fallback != null && fallback.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.is(ModFluids.STEAM.get())) {
            return 0;
        }
        return fallback == null ? 0 : fallback.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.is(ModFluids.STEAM.get())) {
            int drained = action.simulate()
                    ? Math.min(resource.getAmount(), source.fullSteamAhead$getDirectStoredSteamMb(port))
                    : source.fullSteamAhead$drainDirectSteam(port, resource.getAmount(), true);
            return drained <= 0 ? FluidStack.EMPTY : new FluidStack(ModFluids.STEAM.get(), drained);
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        int stored = source.fullSteamAhead$getDirectStoredSteamMb(port);
        if (stored > 0) {
            int drained = action.simulate()
                    ? Math.min(maxDrain, stored)
                    : source.fullSteamAhead$drainDirectSteam(port, maxDrain, true);
            return drained <= 0 ? FluidStack.EMPTY : new FluidStack(ModFluids.STEAM.get(), drained);
        }
        return FluidStack.EMPTY;
    }
}
