package dev.gustavo.fullsteamahead.content.piston;

import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class EngineShaftEventHandler {
    public static void register(IEventBus eventBus) {
        eventBus.addListener(EngineShaftEventHandler::onBlockPlaced);
        eventBus.addListener(EngineShaftEventHandler::onNeighborNotify);
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!isHorizontalShaft(event.getPlacedBlock())) {
            return;
        }

        revalidateNearby(event.getLevel(), event.getPos());
    }

    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof Level level) || level.isClientSide()) {
            return;
        }

        BlockPos pos = event.getPos();
        revalidateIfHorizontalShaft(level, pos);
        for (Direction direction : event.getNotifiedSides()) {
            revalidateIfHorizontalShaft(level, pos.relative(direction));
        }
    }

    private static void revalidateNearby(LevelAccessor levelAccessor, BlockPos pos) {
        if (levelAccessor instanceof Level level && !level.isClientSide()) {
            PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
        }
    }

    private static void revalidateIfHorizontalShaft(Level level, BlockPos pos) {
        if (level.isLoaded(pos) && isHorizontalShaft(level.getBlockState(pos))) {
            PistonHeadBlockEntity.revalidateNearbyEngines(level, pos);
        }
    }

    private static boolean isHorizontalShaft(BlockState state) {
        if (!FullSteamPoweredShaftBlock.isRecognizedShaft(state)) {
            return false;
        }
        return FullSteamPoweredShaftBlock.axisOf(state).isHorizontal();
    }

    private EngineShaftEventHandler() {
    }
}
