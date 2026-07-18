package dev.gustavo.fullsteamahead.compat.movement;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.content.cylinder.SteamCylinderBlock;
import dev.gustavo.fullsteamahead.content.piston.EngineValidator;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlock;
import dev.gustavo.fullsteamahead.content.steam.BoilerOutletBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlock;
import dev.gustavo.fullsteamahead.content.steam.SteamReliefValveBlock;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FullSteamMovementRules {
    public static BlockMovementChecks.CheckResult isMovementNecessary(BlockState state, Level level, BlockPos pos) {
        return isFullSteamEngineBlock(state)
                ? BlockMovementChecks.CheckResult.SUCCESS
                : BlockMovementChecks.CheckResult.PASS;
    }

    public static BlockMovementChecks.CheckResult isMovementAllowed(BlockState state, Level level, BlockPos pos) {
        return isFullSteamEngineBlock(state)
                ? BlockMovementChecks.CheckResult.SUCCESS
                : BlockMovementChecks.CheckResult.PASS;
    }

    public static BlockMovementChecks.CheckResult isBrittle(BlockState state) {
        return isFullSteamEngineBlock(state)
                ? BlockMovementChecks.CheckResult.FAIL
                : BlockMovementChecks.CheckResult.PASS;
    }

    public static BlockMovementChecks.CheckResult isNotSupportive(BlockState state, Direction direction) {
        return isFullSteamEngineBlock(state)
                ? BlockMovementChecks.CheckResult.FAIL
                : BlockMovementChecks.CheckResult.PASS;
    }

    public static BlockMovementChecks.CheckResult isBlockAttachedTowards(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction direction
    ) {
        return isBlockAttachedTowards(state, level, pos, BlockPos.ZERO.relative(direction));
    }

    public static BlockMovementChecks.CheckResult isBlockAttachedTowards(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockPos offset
    ) {
        if (offset.equals(BlockPos.ZERO)) {
            return BlockMovementChecks.CheckResult.PASS;
        }

        if (!isFullSteamEngineBlock(state)) {
            return isExternalBlockAttachedToFullSteamBlock(level, pos, offset);
        }

        BlockPos neighborPos = pos.offset(offset);
        if (!level.isLoaded(neighborPos)) {
            return BlockMovementChecks.CheckResult.PASS;
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        if (isFullSteamEngineBlock(neighborState)) {
            return BlockMovementChecks.CheckResult.SUCCESS;
        }

        Direction direction = unitDirection(offset);
        if (direction == null) {
            return BlockMovementChecks.CheckResult.PASS;
        }

        if (attachesToCompactBoiler(state, level, neighborPos, direction)
                || attachesToBoilerOutletConnection(state, level, neighborPos, direction)
                || attachesToReliefValveBoiler(state, level, neighborPos, direction)
                || attachesToSteamPipe(state, level, neighborPos)) {
            return BlockMovementChecks.CheckResult.SUCCESS;
        }

        return BlockMovementChecks.CheckResult.PASS;
    }

    private static BlockMovementChecks.CheckResult isExternalBlockAttachedToFullSteamBlock(
            Level level,
            BlockPos pos,
            BlockPos offset
    ) {
        Direction direction = unitDirection(offset);
        if (direction == null) {
            return BlockMovementChecks.CheckResult.PASS;
        }

        BlockPos neighborPos = pos.offset(offset);
        if (!level.isLoaded(neighborPos)) {
            return BlockMovementChecks.CheckResult.PASS;
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        if (isCompactBoilerAttachedToCylinder(level, pos, neighborState, direction)
                || isBoilerTankAttachedToOutlet(level, pos, neighborPos, neighborState)
                || isBoilerTankAttachedToReliefValve(level, pos, neighborPos, neighborState)
                || isPipeAttachedToInletOrOutlet(level, pos, neighborPos, neighborState)) {
            return BlockMovementChecks.CheckResult.SUCCESS;
        }

        return BlockMovementChecks.CheckResult.PASS;
    }

    public static Iterable<BlockPos> addAdditionalBlocks(
            BlockState state,
            Level level,
            BlockPos pos,
            Set<BlockPos> visited
    ) {
        List<BlockPos> additional = new ArrayList<>();
        if (!isFullSteamEngineBlock(state)) {
            return additional;
        }

        if (state.is(ModBlocks.PISTON_HEAD.get())) {
            EngineValidator.Result result = EngineValidator.validate(level, pos);
            if (result.valid() && !visited.contains(result.shaft())) {
                additional.add(result.shaft());
            }
        } else if (FullSteamPoweredShaftBlock.isPoweredShaft(state)) {
            for (BlockPos headPos : EngineValidator.candidatePistonHeadsNear(pos)) {
                if (visited.contains(headPos) || !level.isLoaded(headPos)) {
                    continue;
                }

                EngineValidator.Result result = EngineValidator.validate(level, headPos);
                if (result.valid() && pos.equals(result.shaft())) {
                    additional.add(headPos);
                }
            }
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            if (visited.contains(neighborPos)) {
                continue;
            }

            if (isBlockAttachedTowards(state, level, pos, direction) == BlockMovementChecks.CheckResult.SUCCESS) {
                additional.add(neighborPos);
            }
        }

        return additional;
    }

    public static boolean isFullSteamEngineBlock(BlockState state) {
        return state.is(ModBlocks.STEAM_CYLINDER.get())
                || state.is(ModBlocks.STEAM_INLET.get())
                || state.is(ModBlocks.PISTON.get())
                || state.is(ModBlocks.PISTON_HEAD.get())
                || state.is(ModBlocks.ENGINE_LINKAGE.get())
                || FullSteamPoweredShaftBlock.isPoweredShaft(state)
                || state.is(ModBlocks.BOILER_OUTLET.get())
                || state.is(ModBlocks.STEAM_RELIEF_VALVE.get())
                || state.is(ModBlocks.STEAM_PRESSURE_GAUGE.get());
    }

    private static boolean attachesToCompactBoiler(
            BlockState state,
            Level level,
            BlockPos neighborPos,
            Direction direction
    ) {
        if (direction != Direction.DOWN || !isCylinderShellBlock(state) || isCylinderShellFacingDown(state)) {
            return false;
        }

        return level.getBlockEntity(neighborPos) instanceof FluidTankBlockEntity;
    }

    private static boolean attachesToBoilerOutletConnection(
            BlockState state,
            Level level,
            BlockPos neighborPos,
            Direction direction
    ) {
        if (!state.is(ModBlocks.BOILER_OUTLET.get())) {
            return false;
        }

        Direction facing = BoilerOutletBlock.getFacing(state);
        if (direction == facing.getOpposite()) {
            return level.getBlockEntity(neighborPos) instanceof FluidTankBlockEntity;
        }

        return direction == facing && FluidPropagator.getPipe(level, neighborPos) != null;
    }

    private static boolean attachesToSteamPipe(BlockState state, Level level, BlockPos neighborPos) {
        return state.is(ModBlocks.STEAM_INLET.get()) && FluidPropagator.getPipe(level, neighborPos) != null;
    }

    private static boolean attachesToReliefValveBoiler(
            BlockState state,
            Level level,
            BlockPos neighborPos,
            Direction direction
    ) {
        return state.is(ModBlocks.STEAM_RELIEF_VALVE.get())
                && direction == SteamReliefValveBlock.getAttachedFace(state).getOpposite()
                && level.getBlockEntity(neighborPos) instanceof FluidTankBlockEntity;
    }

    private static boolean isCompactBoilerAttachedToCylinder(
            Level level,
            BlockPos pos,
            BlockState neighborState,
            Direction direction
    ) {
        return direction == Direction.UP
                && isCylinderShellBlock(neighborState)
                && !isCylinderShellFacingDown(neighborState)
                && level.getBlockEntity(pos) instanceof FluidTankBlockEntity;
    }

    private static boolean isBoilerTankAttachedToOutlet(
            Level level,
            BlockPos pos,
            BlockPos outletPos,
            BlockState outletState
    ) {
        return outletState.is(ModBlocks.BOILER_OUTLET.get())
                && BoilerOutletBlock.getAttachedTankPos(outletPos, outletState).equals(pos)
                && level.getBlockEntity(pos) instanceof FluidTankBlockEntity;
    }

    private static boolean isBoilerTankAttachedToReliefValve(
            Level level,
            BlockPos pos,
            BlockPos valvePos,
            BlockState valveState
    ) {
        return valveState.is(ModBlocks.STEAM_RELIEF_VALVE.get())
                && SteamReliefValveBlock.getAttachedTankPos(valvePos, valveState).equals(pos)
                && level.getBlockEntity(pos) instanceof FluidTankBlockEntity;
    }

    private static boolean isPipeAttachedToInletOrOutlet(
            Level level,
            BlockPos pos,
            BlockPos neighborPos,
            BlockState neighborState
    ) {
        if (FluidPropagator.getPipe(level, pos) == null) {
            return false;
        }

        return neighborState.is(ModBlocks.STEAM_INLET.get())
                || neighborState.is(ModBlocks.BOILER_OUTLET.get())
                && BoilerOutletBlock.getOutputPipePos(neighborPos, neighborState).equals(pos);
    }

    private static boolean isCylinderShellBlock(BlockState state) {
        return state.is(ModBlocks.STEAM_CYLINDER.get()) || state.is(ModBlocks.STEAM_INLET.get());
    }

    private static boolean isCylinderShellFacingDown(BlockState state) {
        if (state.hasProperty(SteamCylinderBlock.FACING)) {
            return state.getValue(SteamCylinderBlock.FACING) == Direction.DOWN;
        }
        if (state.hasProperty(SteamInletBlock.FACING)) {
            return state.getValue(SteamInletBlock.FACING) == Direction.DOWN;
        }
        return false;
    }

    private static Direction unitDirection(BlockPos offset) {
        for (Direction direction : Direction.values()) {
            if (offset.equals(BlockPos.ZERO.relative(direction))) {
                return direction;
            }
        }

        return null;
    }

    private FullSteamMovementRules() {
    }
}
