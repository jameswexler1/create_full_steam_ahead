package dev.gustavo.fullsteamahead.mixin.accessor;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RotationPropagator.class)
public interface RotationPropagatorAccessor {
    @Invoker("getConveyedSpeed")
    static float fullSteamAhead$getConveyedSpeed(
            KineticBlockEntity source,
            KineticBlockEntity target
    ) {
        throw new AssertionError("RotationPropagator accessor was not applied");
    }
}
