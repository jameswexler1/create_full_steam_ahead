package dev.gustavo.fullsteamahead.mixin.aeronautics;

import com.simibubi.create.content.fluids.FluidPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlock", remap = false)
public abstract class SteamVentBlockMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void fullSteamAhead$surviveOnSteamPipe(
            BlockState state,
            LevelReader level,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (level instanceof Level realLevel && FluidPropagator.getPipe(realLevel, pos.relative(Direction.DOWN)) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "neighborChanged", at = @At("TAIL"))
    private void fullSteamAhead$dropWhenPipeSupportRemoved(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
            BlockPos fromPos,
            boolean moving,
            CallbackInfo ci
    ) {
        if (!level.isClientSide() && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }
}
