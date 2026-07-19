package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Owns the admission valve's internal second block cell. */
public final class SteamAdmissionValveController {
    static Direction controllerDirection(boolean inverted) {
        return inverted ? Direction.DOWN : Direction.UP;
    }

    static Direction baseDirection(boolean inverted) {
        return controllerDirection(inverted).getOpposite();
    }

    public static BlockPos controllerPos(BlockPos basePos, BlockState baseState) {
        return basePos.relative(controllerDirection(baseState.getValue(SteamAdmissionValveBlock.INVERTED)));
    }

    public static BlockPos basePos(BlockPos controllerPos, BlockState controllerState) {
        return controllerPos.relative(controllerState.getValue(
                SteamAdmissionValveControllerBlock.BASE_DIRECTION));
    }

    static boolean canOccupy(BlockGetter level, BlockPos basePos, BlockState baseState) {
        BlockPos controllerPos = controllerPos(basePos, baseState);
        if (level.isOutsideBuildHeight(controllerPos)) {
            return false;
        }

        BlockState occupant = level.getBlockState(controllerPos);
        return occupant.canBeReplaced() || isOwnedController(occupant, controllerPos, basePos);
    }

    static boolean canOccupy(
            BlockGetter level,
            BlockPos basePos,
            BlockState baseState,
            BlockPlaceContext context
    ) {
        BlockPos controllerPos = controllerPos(basePos, baseState);
        if (level.isOutsideBuildHeight(controllerPos)) {
            return false;
        }

        BlockState occupant = level.getBlockState(controllerPos);
        return occupant.canBeReplaced(context) || isOwnedController(occupant, controllerPos, basePos);
    }

    public static boolean sync(Level level, BlockPos basePos, BlockState baseState) {
        if (level.isClientSide || !(baseState.getBlock() instanceof SteamAdmissionValveBlock)) {
            return false;
        }

        BlockPos expectedPos = controllerPos(basePos, baseState);
        if (!canOccupy(level, basePos, baseState)) {
            return false;
        }

        Direction directionToBase = baseDirection(baseState.getValue(SteamAdmissionValveBlock.INVERTED));
        BlockState expectedState = ModBlocks.STEAM_ADMISSION_VALVE_CONTROLLER.get()
                .defaultBlockState()
                .setValue(SteamAdmissionValveControllerBlock.BASE_DIRECTION, directionToBase);
        BlockState current = level.getBlockState(expectedPos);
        if (!current.equals(expectedState)) {
            level.setBlock(expectedPos, expectedState, net.minecraft.world.level.block.Block.UPDATE_ALL);
        }

        BlockPos stalePos = basePos.relative(controllerDirection(
                !baseState.getValue(SteamAdmissionValveBlock.INVERTED)));
        removeOwnedAt(level, stalePos, basePos);
        return true;
    }

    public static void removeOwned(Level level, BlockPos basePos) {
        if (level.isClientSide) {
            return;
        }
        removeOwnedAt(level, basePos.above(), basePos);
        removeOwnedAt(level, basePos.below(), basePos);
    }

    public static boolean isOwnedController(
            BlockState controllerState,
            BlockPos controllerPos,
            BlockPos basePos
    ) {
        return controllerState.is(ModBlocks.STEAM_ADMISSION_VALVE_CONTROLLER.get())
                && basePos(controllerPos, controllerState).equals(basePos);
    }

    private static void removeOwnedAt(Level level, BlockPos controllerPos, BlockPos basePos) {
        BlockState state = level.getBlockState(controllerPos);
        if (isOwnedController(state, controllerPos, basePos)) {
            level.removeBlock(controllerPos, false);
        }
    }

    private SteamAdmissionValveController() {
    }
}
