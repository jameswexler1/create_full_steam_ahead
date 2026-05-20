package dev.gustavo.fullsteamahead.mixin;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.compat.create.FullSteamBoilerIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoilerData.class)
public abstract class BoilerDataMixin {
    @Shadow
    public int attachedEngines;

    @Shadow
    public boolean needsHeatLevelUpdate;

    @Unique
    private int fullSteamAhead$lastAttachedEngines;

    @Unique
    private boolean fullSteamAhead$compactBoiler;

    @Inject(method = "evaluate", at = @At("RETURN"), cancellable = true)
    private void fullSteamAhead$countFullSteamEngines(
            FluidTankBlockEntity boiler,
            CallbackInfoReturnable<Boolean> cir
    ) {
        int fullSteamEngines = FullSteamBoilerIntegration.countAttachedEngines(boiler);
        fullSteamAhead$compactBoiler = fullSteamEngines > 0;

        if (fullSteamEngines > 0) {
            attachedEngines += fullSteamEngines;
            needsHeatLevelUpdate = true;
        }

        boolean changed = cir.getReturnValue() || fullSteamAhead$lastAttachedEngines != attachedEngines;
        fullSteamAhead$lastAttachedEngines = attachedEngines;
        if (changed) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getMaxHeatLevelForBoilerSize", at = @At("HEAD"), cancellable = true)
    private void fullSteamAhead$useCompactBoilerSizing(int size, CallbackInfoReturnable<Integer> cir) {
        if (fullSteamAhead$compactBoiler && size >= FullSteamBoilerIntegration.MIN_BOILER_TANKS) {
            cir.setReturnValue(FullSteamBoilerIntegration.compactBoilerHeatLimit(size));
        }
    }
}
