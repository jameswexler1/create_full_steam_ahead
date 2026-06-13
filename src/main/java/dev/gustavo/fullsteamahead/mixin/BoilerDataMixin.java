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
    public int attachedWhistles;

    @Shadow
    public boolean needsHeatLevelUpdate;

    @Unique
    private int fullSteamAhead$lastAttachedDevices;

    @Unique
    private boolean fullSteamAhead$compactBoiler;

    @Inject(method = "evaluate", at = @At("RETURN"), cancellable = true)
    private void fullSteamAhead$countFullSteamBoilerDevices(
            FluidTankBlockEntity boiler,
            CallbackInfoReturnable<Boolean> cir
    ) {
        int fullSteamDevices = FullSteamBoilerIntegration.countAttachedBoilerDevices(boiler);
        fullSteamAhead$compactBoiler = fullSteamDevices > 0;

        if (fullSteamDevices > 0) {
            attachedWhistles += fullSteamDevices;
            needsHeatLevelUpdate = true;
        }

        boolean changed = cir.getReturnValue() || fullSteamAhead$lastAttachedDevices != fullSteamDevices;
        fullSteamAhead$lastAttachedDevices = fullSteamDevices;
        if (changed) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getMaxHeatLevelForBoilerSize", at = @At("HEAD"), cancellable = true)
    private void fullSteamAhead$useCompactBoilerSizing(int size, CallbackInfoReturnable<Integer> cir) {
        if (fullSteamAhead$compactBoiler && size > 0) {
            cir.setReturnValue(FullSteamBoilerIntegration.compactBoilerHeatLimit(size));
        }
    }
}
