package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Drives the per-network steam model once per server level tick.
 *
 * <p>Each tick, for every loaded boiler outlet, builds the connected steam network via a valve-aware
 * BFS over Create fluid pipes (stopping at closed {@code FluidValveBlock}s), aggregates stored steam +
 * volume + production + temperature, computes one ideal-gas pressure, fairly allocates engine draw
 * caps, and applies the result back to the member outlets/inlets. Bursts and warns on network
 * pressure. Steam itself stays real Create fluid moving through the existing transport; this manager
 * overlays the shared pressure and fair flow allocation.</p>
 */
public final class SteamNetworkManager {
    private static final Map<Level, Set<BlockPos>> OUTLETS = new WeakHashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(SteamNetworkManager::onLevelTick);
    }

    public static void registerOutlet(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        OUTLETS.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregisterOutlet(Level level, BlockPos pos) {
        if (level == null) {
            return;
        }
        Set<BlockPos> set = OUTLETS.get(level);
        if (set != null) {
            set.remove(pos);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        Set<BlockPos> outlets = OUTLETS.get(level);
        if (outlets == null || outlets.isEmpty()) {
            return;
        }

        Set<BlockPos> visitedOutlets = new HashSet<>();
        List<BlockPos> snapshot = new ArrayList<>(outlets);
        for (BlockPos outletPos : snapshot) {
            if (visitedOutlets.contains(outletPos)) {
                continue;
            }
            if (!level.isLoaded(outletPos)
                    || !(level.getBlockEntity(outletPos) instanceof BoilerOutletBlockEntity)) {
                outlets.remove(outletPos);
                continue;
            }
            Network network = buildNetwork(level, outletPos, visitedOutlets);
            if (network != null) {
                stepAndApply(level, network);
            }
        }
    }

    private static Network buildNetwork(Level level, BlockPos startOutlet, Set<BlockPos> visitedOutlets) {
        Network network = new Network();

        // Each outlet's adjacent pipe is the network entry; seed BFS from every outlet we discover.
        Queue<PipeNode> queue = new ArrayDeque<>();
        Set<BlockPos> visitedPipes = new HashSet<>();
        Set<BlockPos> seenEndpoints = new HashSet<>();

        addOutlet(level, startOutlet, network, visitedOutlets, queue, visitedPipes);

        while (!queue.isEmpty()) {
            PipeNode node = queue.remove();
            if (!level.isLoaded(node.pos())) {
                continue;
            }
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, node.pos());
            if (pipe == null) {
                continue;
            }
            network.pipeCount++;
            BlockState pipeState = level.getBlockState(node.pos());

            for (Direction dir : FluidPropagator.getPipeConnections(pipeState, pipe)) {
                if (dir == node.incomingSide() || !canSteamPassThrough(pipe, pipeState, dir)) {
                    continue;
                }
                BlockPos next = node.pos().relative(dir);
                if (!level.isLoaded(next)) {
                    continue;
                }

                FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, next);
                if (nextPipe != null) {
                    if (canSteamPassThrough(nextPipe, level.getBlockState(next), dir.getOpposite())
                            && visitedPipes.add(next)) {
                        queue.add(new PipeNode(next, dir.getOpposite()));
                    }
                    continue;
                }

                if (!seenEndpoints.add(next)) {
                    continue;
                }
                classifyEndpoint(level, next, dir, node.pos(), network, visitedOutlets, queue, visitedPipes);
            }
        }
        return network;
    }

    private static void classifyEndpoint(Level level, BlockPos pos, Direction dir, BlockPos fromPipe,
                                         Network network, Set<BlockPos> visitedOutlets,
                                         Queue<PipeNode> queue, Set<BlockPos> visitedPipes) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BoilerOutletBlockEntity) {
            addOutlet(level, pos, network, visitedOutlets, queue, visitedPipes);
            return;
        }
        if (be instanceof SteamInletBlockEntity inlet && inlet.isInletAssembled()) {
            network.inlets.add(inlet);
            network.addStoredAtBaseTemp(inlet.getStoredSteamMb());
            return;
        }
        if (be instanceof FluidTankBlockEntity tank && !(be instanceof BoilerOutletBlockEntity)) {
            FluidTankBlockEntity controller = tank.getControllerBE();
            FluidTankBlockEntity vessel = controller == null ? tank : controller;
            if (network.steamTanks.add(vessel.getBlockPos())) {
                FluidStack held = vessel.getTankInventory().getFluid();
                if (held.isEmpty() || held.is(ModFluids.STEAM.get())) {
                    network.volumeM3 += vessel.getTotalTankSize();
                    if (held.is(ModFluids.STEAM.get())) {
                        network.addStoredAtBaseTemp(held.getAmount());
                    }
                }
            }
            return;
        }
        // A bare fluid-handler or an open pipe end relieves pressure.
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir.getOpposite());
        if (handler == null && FluidPropagator.isOpenEnd(level, fromPipe, dir)) {
            network.openEnds.add(new OpenEnd(fromPipe, dir));
        }
    }

    private static boolean hasWorkingEngine(Level level, SteamInletBlockEntity inlet) {
        BlockPos enginePos = inlet.getEnginePos();
        return enginePos != null
                && level.isLoaded(enginePos)
                && level.getBlockEntity(enginePos) instanceof PistonHeadBlockEntity engine
                && engine.isEngineAssembled();
    }

    private static void addOutlet(Level level, BlockPos outletPos, Network network, Set<BlockPos> visitedOutlets,
                                  Queue<PipeNode> queue, Set<BlockPos> visitedPipes) {
        if (!visitedOutlets.add(outletPos)
                || !(level.getBlockEntity(outletPos) instanceof BoilerOutletBlockEntity outlet)) {
            return;
        }
        network.outlets.add(outlet);
        network.storedMb += outlet.getStoredSteamMb();
        network.productionMb += outlet.getBoilerProductionMb();
        // Temperature is a steam-mass-weighted average: weight this boiler's steam (stored + production)
        // by its temperature so a tiny hot boiler can't make a large cold network behave fully hot.
        double weight = Math.max(1, outlet.getStoredSteamMb() + outlet.getBoilerProductionMb());
        network.temperatureNumerator += outlet.getTemperatureK() * weight;
        network.temperatureWeight += weight;
        BlockPos boiler = outlet.getBoilerControllerPos();
        if (boiler != null && network.boilers.add(boiler)) {
            network.volumeM3 += Math.max(1, outlet.getBoilerVolume());
        }

        Direction facing = BoilerOutletBlock.getFacing(outlet.getBlockState());
        BlockPos pipePos = outletPos.relative(facing);
        if (level.isLoaded(pipePos) && FluidPropagator.getPipe(level, pipePos) != null && visitedPipes.add(pipePos)) {
            queue.add(new PipeNode(pipePos, facing.getOpposite()));
        }
    }

    private static void stepAndApply(Level level, Network network) {
        if (network.outlets.isEmpty()) {
            return;
        }
        // Pipes and inlets add a little buffering volume so a long run of pipe holds pressure steadier.
        double volume = network.volumeM3 + network.pipeCount * 0.5D + network.inlets.size() * 0.5D;
        volume = Math.max(1.0D, volume);
        double tempK = network.temperatureWeight > 0
                ? network.temperatureNumerator / network.temperatureWeight
                : FullSteamConfig.steamTemperatureBaseK();

        // Vent open ends first (relief), using the current pressure to set the vent rate, then recompute
        // pressure from what is left so an open vent can actually save a network from bursting.
        double prePressure = SteamPhysics.pressurePn(network.storedMb, tempK, volume);
        int ventEach = SteamPhysics.ventMb(prePressure);
        int ventDrained = 0;
        if (ventEach > 0 && !network.openEnds.isEmpty() && level instanceof ServerLevel serverLevel) {
            boolean emitCloud = level.getGameTime() % 4L == 0L;
            for (OpenEnd end : network.openEnds) {
                ventDrained += drainFromNetwork(level, network, ventEach);
                if (emitCloud) {
                    emitVentCloud(serverLevel, end, ventEach);
                }
            }
        }
        network.storedMb = Math.max(0, network.storedMb - ventDrained);
        boolean venting = ventDrained > 0;

        double pressure = SteamPhysics.pressurePn(network.storedMb, tempK, volume);

        // Only inlets backed by a working engine demand steam; others just buffer it (storage).
        List<SteamInletBlockEntity> engines = new ArrayList<>();
        for (SteamInletBlockEntity inlet : network.inlets) {
            if (hasWorkingEngine(level, inlet)) {
                engines.add(inlet);
            }
        }

        // Fair allocation: every engine requests fullFlow * pressureFactor; if short, split evenly.
        int requestedEach = SteamPhysics.requestedFlowMb(pressure);
        int engineCount = engines.size();
        int totalRequested = requestedEach * engineCount;
        int perEngineCap = requestedEach;
        if (totalRequested > 0 && network.storedMb < totalRequested) {
            perEngineCap = network.storedMb / Math.max(1, engineCount);
        }

        boolean burst = FullSteamConfig.overpressureEnabled() && pressure >= FullSteamConfig.steamBurstPressure();
        boolean warn = pressure >= FullSteamConfig.steamWarnPressure();

        for (SteamInletBlockEntity inlet : network.inlets) {
            inlet.applyNetworkState(pressure, engines.contains(inlet) ? perEngineCap : 0);
        }
        for (BoilerOutletBlockEntity outlet : network.outlets) {
            outlet.applyNetworkState(pressure, venting, warn, network.productionMb, (int) Math.round(volume),
                    engineCount);
        }

        if (burst) {
            // One physical boiler bursts once regardless of how many outlets it has...
            Set<BlockPos> burstBoilers = new HashSet<>();
            for (BoilerOutletBlockEntity outlet : network.outlets) {
                BlockPos boilerPos = outlet.getBoilerControllerPos();
                if (boilerPos != null && !burstBoilers.add(boilerPos)) {
                    continue;
                }
                outlet.burst(volume);
            }
            // ...and the whole network depressurizes so it cannot re-burst every tick.
            drainFromNetwork(level, network, network.storedMb);
            network.storedMb = 0;
        }
    }

    /** Drains up to {@code amount} mB of steam from the network for venting: outlets, then tanks, then inlets. */
    private static int drainFromNetwork(Level level, Network network, int amount) {
        int remaining = amount;
        for (BoilerOutletBlockEntity outlet : network.outlets) {
            if (remaining <= 0) {
                break;
            }
            remaining -= outlet.drainSteam(remaining);
        }
        for (BlockPos tankPos : network.steamTanks) {
            if (remaining <= 0) {
                break;
            }
            if (level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity tank) {
                // Drain a steam-typed stack so no other fluid can ever be removed by accident.
                FluidStack drained = tank.getTankInventory()
                        .drain(new FluidStack(ModFluids.STEAM.get(), remaining), IFluidHandler.FluidAction.EXECUTE);
                remaining -= drained.getAmount();
            }
        }
        for (SteamInletBlockEntity inlet : network.inlets) {
            if (remaining <= 0) {
                break;
            }
            remaining -= inlet.drainSteam(remaining);
        }
        return amount - remaining;
    }

    private static void emitVentCloud(ServerLevel level, OpenEnd end, int amount) {
        BlockPos ventPos = end.pipe().relative(end.dir());
        AABB area = new AABB(ventPos);
        SteamCloudEffects.emitOpenPipe(level, area, amount);
    }

    /** Steam can move toward {@code side} only if the pipe both allows flow and can pull our steam (valve-aware). */
    private static boolean canSteamPassThrough(FluidTransportBehaviour pipe, BlockState state, Direction side) {
        return pipe.canHaveFlowToward(state, side)
                && pipe.canPullFluidFrom(new FluidStack(ModFluids.STEAM.get(), 1), state, side);
    }

    private record PipeNode(BlockPos pos, Direction incomingSide) {
    }

    private record OpenEnd(BlockPos pipe, Direction dir) {
    }

    private static final class Network {
        private final List<BoilerOutletBlockEntity> outlets = new ArrayList<>();
        private final List<SteamInletBlockEntity> inlets = new ArrayList<>();
        private final Set<BlockPos> boilers = new HashSet<>();
        private final Set<BlockPos> steamTanks = new HashSet<>();
        private final List<OpenEnd> openEnds = new ArrayList<>();
        private int storedMb;
        private int productionMb;
        private double volumeM3;
        private double temperatureNumerator;
        private double temperatureWeight;
        private int pipeCount;

        /** Passive steam (tanks, inlet buffers) counts toward pressure at base temperature. */
        private void addStoredAtBaseTemp(int amount) {
            if (amount <= 0) {
                return;
            }
            storedMb += amount;
            temperatureNumerator += FullSteamConfig.steamTemperatureBaseK() * amount;
            temperatureWeight += amount;
        }
    }

    private SteamNetworkManager() {
    }
}
