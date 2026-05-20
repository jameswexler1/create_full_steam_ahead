package dev.gustavo.fullsteamahead.mixin;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamPipePressureCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FluidPipeBlock.class, GlassFluidPipeBlock.class})
public abstract class FluidPipeWrenchMixin {
    @Inject(method = "onWrenched", at = @At("RETURN"))
    private void fullSteamAhead$refreshSteamPressureAfterPipeConversion(
            BlockState state,
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (cir.getReturnValue() != InteractionResult.SUCCESS) {
            return;
        }

        Level level = context.getLevel();
        if (level.isClientSide()) {
            return;
        }

        BlockPos pos = context.getClickedPos();
        SteamPipePressureCoordinator.refreshSteamPressureNear(level, pos);
    }
}
