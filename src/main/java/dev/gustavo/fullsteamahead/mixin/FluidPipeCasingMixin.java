package dev.gustavo.fullsteamahead.mixin;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamAdmissionValveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidPipeBlock.class)
public abstract class FluidPipeCasingMixin {
    @Inject(method = "shouldDrawCasing", at = @At("HEAD"), cancellable = true)
    private static void fullSteamAhead$suppressAdmissionValveJunctionCasing(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (state.getBlock() instanceof SteamAdmissionValveBlock) {
            cir.setReturnValue(false);
        }
    }
}
