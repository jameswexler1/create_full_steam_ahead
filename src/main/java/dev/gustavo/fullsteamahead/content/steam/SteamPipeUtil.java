package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SteamPipeUtil {
    public static boolean canSteamPassThrough(FluidTransportBehaviour pipe, BlockState state, Direction side) {
        return pipe.canHaveFlowToward(state, side)
                && pipe.canPullFluidFrom(new FluidStack(ModFluids.STEAM.get(), 1), state, side);
    }

    private SteamPipeUtil() {
    }
}
