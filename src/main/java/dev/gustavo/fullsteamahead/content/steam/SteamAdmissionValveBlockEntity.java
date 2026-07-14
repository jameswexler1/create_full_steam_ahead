package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class SteamAdmissionValveBlockEntity extends FluidPipeBlockEntity {
    public SteamAdmissionValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_ADMISSION_VALVE.get(), pos, state);
    }
}
