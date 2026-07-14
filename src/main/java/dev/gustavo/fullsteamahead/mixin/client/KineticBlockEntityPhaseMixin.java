package dev.gustavo.fullsteamahead.mixin.client;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.gustavo.fullsteamahead.client.render.KineticPhaseContinuity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityPhaseMixin {
    @Inject(method = "getRotationAngleOffset", at = @At("RETURN"), cancellable = true)
    private void fullSteamAhead$preserveChangingSpeedPhase(
            Direction.Axis axis,
            CallbackInfoReturnable<Integer> cir
    ) {
        KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
        int correction = KineticPhaseContinuity.rotationOffsetDegrees(blockEntity);
        if (correction != 0) {
            cir.setReturnValue(cir.getReturnValue() + correction);
        }
    }
}
