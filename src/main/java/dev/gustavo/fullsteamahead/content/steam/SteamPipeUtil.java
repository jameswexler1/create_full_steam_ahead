package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SteamPipeUtil {
    public static boolean canSteamPassThrough(FluidTransportBehaviour pipe, BlockState state, Direction side) {
        return pipe.canHaveFlowToward(state, side)
                && pipe.canPullFluidFrom(new FluidStack(ModFluids.STEAM.get(), 1), state, side);
    }

    /**
     * Mechanical pumps only move fluid toward their front ({@link PumpBlock#FACING}) face, so steam
     * must not be able to cross one against that push. {@code flowDir} is the direction steam travels
     * across the edge between two adjacent blocks (current -> neighbour). Any non-pump block returns
     * {@code true}, so ordinary pipe networks are unaffected.
     */
    public static boolean pumpPassable(BlockState state, Direction flowDir) {
        return !(state.getBlock() instanceof PumpBlock)
                || state.getValue(PumpBlock.FACING) == flowDir;
    }

    private SteamPipeUtil() {
    }
}
