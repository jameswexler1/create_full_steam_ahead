package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import dev.gustavo.fullsteamahead.compat.create.BoilerSteamPort;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class DirectBoilerPipeTransfer {
    private static final float PRESSURE_PER_MB = 2.0F;

    public static Result push(Level level, FullSteamDirectBoilerSource source, BoilerSteamPort port, int maxAmount) {
        if (level == null || level.isClientSide() || port.type() != BoilerSteamPort.Type.DIRECT_PIPE || maxAmount <= 0) {
            return Result.NONE;
        }

        BlockPos startPos = port.pipePos();
        if (!level.isLoaded(startPos)) {
            return Result.BLOCKED;
        }

        FluidTransportBehaviour startPipe = FluidPropagator.getPipe(level, startPos);
        if (startPipe == null) {
            return Result.BLOCKED;
        }

        Direction sourceSide = port.direction().getOpposite();
        BlockState startState = level.getBlockState(startPos);
        if (!SteamPipeUtil.canSteamPassThrough(startPipe, startState, sourceSide)
                || !SteamPipeUtil.pumpPassable(startState, sourceSide.getOpposite())) {
            return Result.BLOCKED;
        }

        PipePressureResult pressure = applyPipePressure(level, port, startPos, sourceSide, maxAmount);
        int fallbackMoved = 0;
        if (pressure.hasEndpoint()) {
            fallbackMoved = tryFillTargetsThroughPipes(level, source, port, startPos, sourceSide, fallbackBudget(source, port));
        }
        boolean blockedOnly = pressure.blocked() && fallbackMoved == 0 && !pressure.openEnd();
        return new Result(fallbackMoved, pressure.openEnd(), blockedOnly);
    }

    private static int fallbackBudget(FullSteamDirectBoilerSource source, BoilerSteamPort port) {
        int stored = source.fullSteamAhead$getDirectStoredSteamMb(port);
        if (stored <= 0) {
            return 0;
        }

        int flowReserve = Math.max(
                source.fullSteamAhead$getDirectProductionMb(port),
                FullSteamConfig.maxPipedSteamPerTick()
        );
        return Math.max(0, stored - flowReserve);
    }

    private static PipePressureResult applyPipePressure(
            Level level,
            BoilerSteamPort port,
            BlockPos startPos,
            Direction sourceSide,
            int maxAmount
    ) {
        float pressure = Math.max(2.0F, maxAmount * PRESSURE_PER_MB);
        Set<BlockPos> visited = new HashSet<>();
        Queue<PipeNode> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(new PipeNode(startPos, sourceSide, 0));
        boolean hasEndpoint = false;
        boolean openEnd = false;
        boolean blocked = false;

        while (!queue.isEmpty()) {
            PipeNode node = queue.remove();
            if (!level.isLoaded(node.pos())) {
                continue;
            }

            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, node.pos());
            if (pipe == null) {
                continue;
            }
            BlockState pipeState = level.getBlockState(node.pos());
            pipe.addPressure(node.incomingSide(), true, pressure);

            for (Direction direction : FluidPropagator.getPipeConnections(pipeState, pipe)) {
                if (direction == node.incomingSide()) {
                    continue;
                }
                if (!SteamPipeUtil.canSteamPassThrough(pipe, pipeState, direction)
                        || !SteamPipeUtil.pumpPassable(pipeState, direction)) {
                    blocked = true;
                    continue;
                }

                BlockPos next = node.pos().relative(direction);
                if (next.equals(port.pos()) || !level.isLoaded(next)) {
                    continue;
                }

                FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, next);
                if (nextPipe != null) {
                    BlockState nextState = level.getBlockState(next);
                    if (!SteamPipeUtil.canSteamPassThrough(nextPipe, nextState, direction.getOpposite())
                            || !SteamPipeUtil.pumpPassable(nextState, direction)) {
                        blocked = true;
                        continue;
                    }
                    pipe.addPressure(direction, false, pressure);
                    if (node.distance() + 1 <= FullSteamConfig.boilerOutletPressureRange() && visited.add(next)) {
                        queue.add(new PipeNode(next, direction.getOpposite(), node.distance() + 1));
                    }
                    continue;
                }

                IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, next, direction.getOpposite());
                if (target != null) {
                    pipe.addPressure(direction, false, pressure);
                    hasEndpoint = true;
                    continue;
                }

                if (FluidPropagator.isOpenEnd(level, node.pos(), direction)) {
                    pipe.addPressure(direction, false, pressure);
                    hasEndpoint = true;
                    openEnd = true;
                }
            }
        }

        return new PipePressureResult(hasEndpoint, openEnd, blocked);
    }

    private static int tryFillTargetsThroughPipes(
            Level level,
            FullSteamDirectBoilerSource source,
            BoilerSteamPort port,
            BlockPos startPos,
            Direction sourceSide,
            int maxAmount
    ) {
        int remaining = Math.min(maxAmount, source.fullSteamAhead$getDirectStoredSteamMb(port));
        if (remaining <= 0) {
            return 0;
        }

        List<FillTarget> targets = collectFillTargetsThroughPipes(level, port, startPos, sourceSide);
        if (targets.isEmpty()) {
            return 0;
        }

        List<FillTarget> steamInlets = targets.stream()
                .filter(FillTarget::steamInlet)
                .toList();
        if (steamInlets.isEmpty()) {
            return fillTargetsEvenly(level, source, port, targets, remaining);
        }

        int moved = fillTargetsEvenly(level, source, port, steamInlets, remaining);
        remaining -= moved;
        if (remaining <= 0) {
            return moved;
        }

        List<FillTarget> passiveTargets = targets.stream()
                .filter(target -> !target.steamInlet())
                .toList();
        if (!passiveTargets.isEmpty()) {
            moved += fillTargetsEvenly(level, source, port, passiveTargets, remaining);
        }
        return moved;
    }

    private static List<FillTarget> collectFillTargetsThroughPipes(
            Level level,
            BoilerSteamPort port,
            BlockPos startPos,
            Direction sourceSide
    ) {
        List<FillTarget> targets = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> seenTargets = new HashSet<>();
        Queue<PipeNode> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(new PipeNode(startPos, sourceSide, 0));

        while (!queue.isEmpty()) {
            PipeNode node = queue.remove();
            if (!level.isLoaded(node.pos())) {
                continue;
            }

            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, node.pos());
            if (pipe == null) {
                continue;
            }
            BlockState pipeState = level.getBlockState(node.pos());

            for (Direction direction : FluidPropagator.getPipeConnections(pipeState, pipe)) {
                if (direction == node.incomingSide()
                        || !SteamPipeUtil.canSteamPassThrough(pipe, pipeState, direction)
                        || !SteamPipeUtil.pumpPassable(pipeState, direction)) {
                    continue;
                }

                BlockPos next = node.pos().relative(direction);
                if (next.equals(port.pos()) || !level.isLoaded(next)) {
                    continue;
                }

                FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, next);
                if (nextPipe != null) {
                    BlockState nextState = level.getBlockState(next);
                    if (SteamPipeUtil.canSteamPassThrough(nextPipe, nextState, direction.getOpposite())
                            && SteamPipeUtil.pumpPassable(nextState, direction)
                            && node.distance() + 1 <= FullSteamConfig.boilerOutletPressureRange()
                            && visited.add(next)) {
                        queue.add(new PipeNode(next, direction.getOpposite(), node.distance() + 1));
                    }
                    continue;
                }

                IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, next, direction.getOpposite());
                if (target == null || !seenTargets.add(next)) {
                    continue;
                }

                boolean steamInlet = level.getBlockEntity(next) instanceof SteamInletBlockEntity inlet
                        && inlet.isInletAssembled();
                int maxFillPerTick = steamInlet ? FullSteamConfig.maxPipedSteamPerTick() : Integer.MAX_VALUE;
                targets.add(new FillTarget(next, direction.getOpposite(), steamInlet, maxFillPerTick));
            }
        }

        targets.sort(Comparator
                .comparing((FillTarget target) -> !target.steamInlet())
                .thenComparingInt(target -> target.pos().getY())
                .thenComparingInt(target -> target.pos().getX())
                .thenComparingInt(target -> target.pos().getZ())
                .thenComparingInt(target -> target.side().ordinal()));
        return targets;
    }

    private static int fillTargetsEvenly(
            Level level,
            FullSteamDirectBoilerSource source,
            BoilerSteamPort port,
            List<FillTarget> targets,
            int maxAmount
    ) {
        int remaining = Math.min(maxAmount, source.fullSteamAhead$getDirectStoredSteamMb(port));
        int moved = 0;
        int[] delivered = new int[targets.size()];
        List<Integer> openTargetIndexes = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            openTargetIndexes.add(i);
        }
        int offset = targetOrderOffset(port, openTargetIndexes.size());

        while (remaining > 0 && !openTargetIndexes.isEmpty()) {
            int targetCount = openTargetIndexes.size();
            int share = remaining / targetCount;
            int remainder = remaining % targetCount;
            if (share <= 0 && remainder <= 0) {
                break;
            }

            List<Integer> nextOpenTargetIndexes = new ArrayList<>();
            int movedThisPass = 0;
            for (int i = 0; i < targetCount && remaining > 0; i++) {
                int requested = share + (i < remainder ? 1 : 0);
                if (requested <= 0) {
                    continue;
                }

                int targetIndex = openTargetIndexes.get(Math.floorMod(i + offset, targetCount));
                FillTarget target = targets.get(targetIndex);
                int targetRemaining = target.maxFillPerTick() - delivered[targetIndex];
                if (targetRemaining <= 0) {
                    continue;
                }

                int allowance = Math.min(requested, targetRemaining);
                int filled = tryFillTarget(level, source, port, target.pos(), target.side(), allowance);
                moved += filled;
                movedThisPass += filled;
                remaining -= filled;
                delivered[targetIndex] += filled;
                if (filled == allowance && delivered[targetIndex] < target.maxFillPerTick()) {
                    nextOpenTargetIndexes.add(targetIndex);
                }
            }

            if (movedThisPass <= 0) {
                break;
            }
            openTargetIndexes = nextOpenTargetIndexes;
            offset = 0;
        }

        return moved;
    }

    private static int targetOrderOffset(BoilerSteamPort port, int targetCount) {
        if (targetCount <= 1) {
            return 0;
        }
        BlockPos pos = port.pos();
        int hash = pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13 + port.direction().ordinal();
        return Math.floorMod(hash, targetCount);
    }

    private static int tryFillTarget(
            Level level,
            FullSteamDirectBoilerSource source,
            BoilerSteamPort port,
            BlockPos targetPos,
            Direction side,
            int maxAmount
    ) {
        if (maxAmount <= 0 || targetPos.equals(port.pos()) || !level.isLoaded(targetPos)) {
            return 0;
        }

        IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, side);
        if (target == null) {
            return 0;
        }

        FluidStack simulatedStack = new FluidStack(ModFluids.STEAM.get(), maxAmount);
        int accepted = target.fill(simulatedStack, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return 0;
        }

        int available = Math.min(accepted, source.fullSteamAhead$getDirectStoredSteamMb(port));
        if (available <= 0) {
            return 0;
        }

        int filled = target.fill(new FluidStack(ModFluids.STEAM.get(), available), IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            source.fullSteamAhead$drainDirectSteam(port, filled, false);
        }
        return filled;
    }

    public record Result(int moved, boolean venting, boolean blocked) {
        public static final Result NONE = new Result(0, false, false);
        public static final Result BLOCKED = new Result(0, false, true);
    }

    private record PipeNode(BlockPos pos, Direction incomingSide, int distance) {
    }

    private record FillTarget(BlockPos pos, Direction side, boolean steamInlet, int maxFillPerTick) {
    }

    private record PipePressureResult(boolean hasEndpoint, boolean openEnd, boolean blocked) {
    }

    private DirectBoilerPipeTransfer() {
    }
}
