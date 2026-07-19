package dev.gustavo.fullsteamahead.mixin;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import dev.gustavo.fullsteamahead.content.steam.SteamAdmissionValveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity$StandardPipeFluidTransportBehaviour")
public abstract class FluidPipeAttachmentMixin {
    @Inject(method = "getRenderedRimAttachment", at = @At("HEAD"), cancellable = true)
    private void fullSteamAhead$suppressAuthoredAdmissionValveInletAttachment(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction direction,
            CallbackInfoReturnable<FluidTransportBehaviour.AttachmentTypes> cir
    ) {
        // The authored flange replaces Create's nearly identical drain cuboid on this face.
        if (state.getBlock() instanceof SteamAdmissionValveBlock
                && direction == state.getValue(SteamAdmissionValveBlock.FACING)) {
            cir.setReturnValue(FluidTransportBehaviour.AttachmentTypes.NONE);
        }
    }
}
