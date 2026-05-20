package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class SteamPipePressureCoordinator {
    public static void refreshSteamPressureNear(Level level, BlockPos changedPipePos) {
        if (level.isClientSide() || !level.isLoaded(changedPipePos)) {
            return;
        }

        FluidTransportBehaviour startPipe = FluidPropagator.getPipe(level, changedPipePos);
        if (startPipe == null) {
            return;
        }

        Set<BlockPos> visited = new HashSet<>();
        Queue<PipeNode> queue = new ArrayDeque<>();
        visited.add(changedPipePos);
        queue.add(new PipeNode(changedPipePos, 0));

        while (!queue.isEmpty()) {
            PipeNode node = queue.remove();
            if (!level.isLoaded(node.pos())) {
                continue;
            }

            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, node.pos());
            if (pipe == null) {
                continue;
            }

            refreshAdjacentOutlets(level, node.pos());

            if (node.distance() >= BoilerOutletBlockEntity.PRESSURE_RANGE) {
                continue;
            }

            for (Direction direction : FluidPropagator.getPipeConnections(level.getBlockState(node.pos()), pipe)) {
                BlockPos next = node.pos().relative(direction);
                if (!level.isLoaded(next) || !visited.add(next)) {
                    continue;
                }

                if (FluidPropagator.getPipe(level, next) != null) {
                    queue.add(new PipeNode(next, node.distance() + 1));
                }
            }
        }
    }

    private static void refreshAdjacentOutlets(Level level, BlockPos pipePos) {
        for (Direction direction : Direction.values()) {
            BlockPos outletPos = pipePos.relative(direction);
            if (!level.isLoaded(outletPos)) {
                continue;
            }

            BlockState state = level.getBlockState(outletPos);
            if (!state.is(ModBlocks.BOILER_OUTLET.get())
                    || !BoilerOutletBlock.getOutputPipePos(outletPos, state).equals(pipePos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(outletPos);
            if (blockEntity instanceof BoilerOutletBlockEntity outlet) {
                outlet.forcePipePressureRefresh();
            }
        }
    }

    private record PipeNode(BlockPos pos, int distance) {
    }

    private SteamPipePressureCoordinator() {
    }
}
