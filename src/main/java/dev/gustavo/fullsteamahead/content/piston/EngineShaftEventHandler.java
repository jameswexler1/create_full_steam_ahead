package dev.gustavo.fullsteamahead.content.piston;

import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class EngineShaftEventHandler {
    public static void register(IEventBus eventBus) {
        eventBus.addListener(EngineShaftEventHandler::onBlockPlaced);
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!isHorizontalShaft(event.getPlacedBlock())) {
            return;
        }

        revalidateNearby(event.getLevel(), event.getPos());
    }

    private static void revalidateNearby(LevelAccessor levelAccessor, BlockPos pos) {
        if (levelAccessor instanceof Level level && !level.isClientSide()) {
            PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
        }
    }

    private static boolean isHorizontalShaft(BlockState state) {
        if (!state.is(ModBlocks.POWERED_SHAFT.get()) && !(state.getBlock() instanceof AbstractShaftBlock)) {
            return false;
        }
        if (state.getBlock() instanceof AbstractShaftBlock shaft) {
            return shaft.getRotationAxis(state).isHorizontal();
        }
        return true;
    }

    private EngineShaftEventHandler() {
    }
}
