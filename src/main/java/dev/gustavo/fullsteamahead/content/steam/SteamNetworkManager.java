package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import dev.gustavo.fullsteamahead.compat.aeronautics.AeronauticsSteamVentCompat;
import dev.gustavo.fullsteamahead.compat.create.FullSteamBoilerIntegration;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final int BOILER_THERMAL_STATE_TTL = 100;
    private static final Map<Level, Set<BlockPos>> OUTLETS = new WeakHashMap<>();
    private static final Map<Level, Set<BlockPos>> RELIEF_VALVES = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, BoilerThermalState>> BOILER_THERMAL_STATES = new WeakHashMap<>();

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

    public static void registerReliefValve(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        RELIEF_VALVES.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregisterReliefValve(Level level, BlockPos pos) {
        if (level == null) {
            return;
        }
        Set<BlockPos> set = RELIEF_VALVES.get(level);
        if (set != null) {
            set.remove(pos);
        }
    }

    public static boolean ruptureBoilerFromProjectile(ServerLevel level, FluidTankBlockEntity hitTank) {
        if (level == null || hitTank == null || !FullSteamConfig.overpressureEnabled()) {
            return false;
        }

        FluidTankBlockEntity boiler = controllerOrSelf(hitTank);
        BlockPos boilerPos = boiler.getBlockPos();
        Set<BlockPos> outlets = OUTLETS.get(level);
        if (outlets == null || outlets.isEmpty()) {
            return false;
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
            if (network == null || !network.boilers.contains(boilerPos)) {
                continue;
            }

            double volume = networkVolume(network);
            double pressure = SteamPhysics.pressurePn(network.storedMb, networkTemperature(network), volume);
            if (pressure <= 0.0D) {
                return ruptureActiveBoilerFallback(level, boiler);
            }

            for (BoilerOutletBlockEntity outlet : network.outlets) {
                if (!boilerPos.equals(currentBoilerControllerPos(outlet))) {
                    continue;
                }

                outlet.burst(volume, pressure);
                drainFromNetwork(level, network, network.storedMb);
                network.storedMb = 0;
                for (BoilerOutletBlockEntity member : network.outlets) {
                    member.clearEffectivePressure();
                }
                return true;
            }
            return ruptureActiveBoilerFallback(level, boiler);
        }
        return ruptureActiveBoilerFallback(level, boiler);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        Map<BlockPos, List<SteamReliefValveBlockEntity>> valvesByBoiler = collectReliefValvesByBoiler(level);
        Set<BlockPos> outlets = OUTLETS.get(level);
        if (outlets == null || outlets.isEmpty()) {
            applyReliefValveStates(Map.of(), valvesByBoiler);
            return;
        }

        Set<BlockPos> visitedOutlets = new HashSet<>();
        List<BlockPos> snapshot = new ArrayList<>(outlets);
        Map<BlockPos, Integer> reliefValveBudgets = new HashMap<>();
        Map<BlockPos, ValveTickState> reliefValveStates = new HashMap<>();
        for (List<SteamReliefValveBlockEntity> valves : valvesByBoiler.values()) {
            for (SteamReliefValveBlockEntity valve : valves) {
                reliefValveBudgets.put(valve.getBlockPos().immutable(), FullSteamConfig.reliefValveVentRateMb());
                reliefValveStates.put(valve.getBlockPos().immutable(), new ValveTickState());
            }
        }

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
                attachReliefValves(network, valvesByBoiler);
                stepAndApply(level, network, reliefValveBudgets, reliefValveStates);
            }
        }
        applyReliefValveStates(reliefValveStates, valvesByBoiler);
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

    private static Map<BlockPos, List<SteamReliefValveBlockEntity>> collectReliefValvesByBoiler(Level level) {
        Map<BlockPos, List<SteamReliefValveBlockEntity>> byBoiler = new HashMap<>();
        Set<BlockPos> valves = RELIEF_VALVES.get(level);
        if (valves == null || valves.isEmpty()) {
            return byBoiler;
        }

        List<BlockPos> snapshot = new ArrayList<>(valves);
        for (BlockPos valvePos : snapshot) {
            if (!level.isLoaded(valvePos)
                    || !(level.getBlockEntity(valvePos) instanceof SteamReliefValveBlockEntity valve)) {
                valves.remove(valvePos);
                continue;
            }
            FluidTankBlockEntity boiler = valve.getBoiler();
            BlockPos boilerPos = boiler == null ? valve.getBoilerControllerPos() : boiler.getBlockPos();
            if (boiler == null && boilerPos == null) {
                valve.refreshBoilerState();
                boilerPos = valve.getBoilerControllerPos();
            } else if (boiler != null && !boiler.getBlockPos().equals(valve.getBoilerControllerPos())) {
                valve.refreshBoilerState();
            }
            if (boilerPos != null) {
                byBoiler.computeIfAbsent(boilerPos.immutable(), ignored -> new ArrayList<>()).add(valve);
            }
        }
        return byBoiler;
    }

    private static void attachReliefValves(
            Network network,
            Map<BlockPos, List<SteamReliefValveBlockEntity>> valvesByBoiler
    ) {
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos boilerPos : network.boilers) {
            List<SteamReliefValveBlockEntity> valves = valvesByBoiler.get(boilerPos);
            if (valves == null) {
                continue;
            }
            for (SteamReliefValveBlockEntity valve : valves) {
                if (seen.add(valve.getBlockPos())) {
                    network.reliefValves.add(valve);
                }
            }
        }
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
                    network.volumeM3 += passiveSteamTankVolumeM3(vessel);
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

    private static double passiveSteamTankVolumeM3(FluidTankBlockEntity vessel) {
        int capacityMb = vessel.getTankInventory().getCapacity();
        if (capacityMb > 0) {
            return capacityMb / 1000.0D;
        }
        return Math.max(1, vessel.getTotalTankSize());
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
        BlockPos boiler = currentBoilerControllerPos(outlet);
        if (boiler != null && network.boilers.add(boiler)) {
            network.volumeM3 += Math.max(1, outlet.getBoilerVolume());
        }

        Direction facing = BoilerOutletBlock.getFacing(outlet.getBlockState());
        BlockPos pipePos = outletPos.relative(facing);
        if (level.isLoaded(pipePos) && FluidPropagator.getPipe(level, pipePos) != null && visitedPipes.add(pipePos)) {
            queue.add(new PipeNode(pipePos, facing.getOpposite()));
        }
    }

    private static BlockPos currentBoilerControllerPos(BoilerOutletBlockEntity outlet) {
        FluidTankBlockEntity boiler = outlet.getBoiler();
        if (boiler != null) {
            return boiler.getBlockPos();
        }
        return outlet.getBoilerControllerPos();
    }

    private static void stepAndApply(
            Level level,
            Network network,
            Map<BlockPos, Integer> reliefValveBudgets,
            Map<BlockPos, ValveTickState> reliefValveStates
    ) {
        if (network.outlets.isEmpty()) {
            return;
        }
        // Pipes and inlets add a little buffering volume so a long run of pipe holds pressure steadier.
        double volume = networkVolume(network);
        double tempK = networkTemperature(network);

        // Vent open ends first (relief), then recompute pressure from what is left. The drain target
        // follows the same pressure smoothing as the engine/display state; otherwise an open pipe
        // would empty the physical buffers immediately while the shown pressure still falls gradually.
        double prePressure = SteamPhysics.pressurePn(network.storedMb, tempK, volume);
        int ventDrained = 0;
        int ventVisualAmount = 0;
        if (!network.openEnds.isEmpty()) {
            ventVisualAmount = SteamPhysics.ventMb(prePressure) * network.openEnds.size();
            double ventTargetPressure = ventTargetPressure(network, prePressure);
            int atmosphericRelief = SteamPhysics.drainToPressureMb(
                    network.storedMb,
                    tempK,
                    volume,
                    ventTargetPressure
            );
            int requestedDrain = Math.min(network.storedMb, atmosphericRelief);
            if (requestedDrain > 0) {
                ventDrained = drainFromNetwork(level, network, requestedDrain);
            }
        }
        if ((ventDrained > 0 || ventVisualAmount > 0) && level instanceof ServerLevel serverLevel) {
            boolean emitCloud = level.getGameTime() % 4L == 0L;
            if (emitCloud) {
                int cloudTotal = Math.max(ventDrained, ventVisualAmount);
                int cloudAmount = Math.max(1, (int) Math.ceil(cloudTotal / (double) network.openEnds.size()));
                for (OpenEnd end : network.openEnds) {
                    emitVentCloud(serverLevel, end, cloudAmount);
                }
            }
        }
        network.storedMb = Math.max(0, network.storedMb - ventDrained);

        double pressureAfterOpenEnds = SteamPhysics.pressurePn(network.storedMb, tempK, volume);
        double reliefReferencePressure = smoothEffectivePressure(network, pressureAfterOpenEnds);
        ReliefResult relief = applyReliefValves(
                level,
                network,
                tempK,
                volume,
                reliefReferencePressure,
                reliefValveBudgets,
                reliefValveStates
        );
        network.storedMb = Math.max(0, network.storedMb - relief.drained());
        boolean openEndVenting = ventDrained > 0 || ventVisualAmount > 0;
        boolean reliefVenting = relief.drained() > 0;
        boolean venting = openEndVenting || reliefVenting;

        int aeronauticsVentDemand = collectAeronauticsVentDemand(level, network);
        int aeronauticsVentConsumed = 0;
        if (aeronauticsVentDemand > 0 && network.storedMb > 0) {
            int requestedDrain = Math.min(network.storedMb, aeronauticsVentDemand);
            aeronauticsVentConsumed = drainFromNetwork(level, network, requestedDrain);
            network.storedMb = Math.max(0, network.storedMb - aeronauticsVentConsumed);
        }

        // Target = live ideal-gas pressure from real steam; effective = smoothed value gameplay sees.
        // Open pipe drains already target a smoothed pressure, so they must not be smoothed twice.
        // Relief valves are capacity-limited safety devices; keep smoothing their effective pressure
        // so a valve can start venting before the burst check sees the still-high raw pressure.
        double target = SteamPhysics.pressurePn(network.storedMb, tempK, volume);
        double effective = openEndVenting ? target : smoothEffectivePressure(network, target);

        // Only inlets backed by a working engine demand steam; others just buffer it (storage).
        List<SteamInletBlockEntity> engines = new ArrayList<>();
        for (SteamInletBlockEntity inlet : network.inlets) {
            if (hasWorkingEngine(level, inlet)) {
                engines.add(inlet);
            }
        }

        // Fair allocation: every engine requests fullFlow * pressureFactor; if short, split evenly.
        int requestedEach = SteamPhysics.requestedFlowMb(effective);
        int engineCount = engines.size();
        int totalRequested = requestedEach * engineCount;
        int perEngineCap = requestedEach;
        if (totalRequested > 0 && network.storedMb < totalRequested) {
            perEngineCap = network.storedMb / Math.max(1, engineCount);
        }
        int consumedMb = 0;
        for (SteamInletBlockEntity inlet : engines) {
            consumedMb += inlet.getDisplayConsumedSteamMb();
        }
        consumedMb += aeronauticsVentConsumed;

        boolean burst = FullSteamConfig.overpressureEnabled() && effective >= FullSteamConfig.steamBurstPressure();
        boolean warn = effective >= FullSteamConfig.steamWarnPressure();

        for (SteamInletBlockEntity inlet : network.inlets) {
            inlet.applyNetworkState(effective, engines.contains(inlet) ? perEngineCap : 0);
        }
        for (BoilerOutletBlockEntity outlet : network.outlets) {
            outlet.applyNetworkState(effective, venting, warn, network.productionMb, (int) Math.round(volume),
                    engineCount, consumedMb);
        }

        if (burst) {
            // One physical boiler bursts once regardless of how many outlets it has...
            Set<BlockPos> burstBoilers = new HashSet<>();
            for (BoilerOutletBlockEntity outlet : network.outlets) {
                BlockPos boilerPos = currentBoilerControllerPos(outlet);
                if (boilerPos != null && !burstBoilers.add(boilerPos)) {
                    continue;
                }
                outlet.burst(volume);
            }
            // ...and the whole network depressurizes so it cannot re-burst every tick.
            drainFromNetwork(level, network, network.storedMb);
            network.storedMb = 0;
            for (BoilerOutletBlockEntity outlet : network.outlets) {
                outlet.clearEffectivePressure();
            }
        }
    }

    private static int collectAeronauticsVentDemand(Level level, Network network) {
        int demand = 0;
        Set<BlockPos> seenBoilers = new HashSet<>();
        for (BoilerOutletBlockEntity outlet : network.outlets) {
            FluidTankBlockEntity boiler = outlet.getBoiler();
            BlockPos boilerPos = boiler == null ? outlet.getBoilerControllerPos() : boiler.getBlockPos();
            if (boilerPos == null || !seenBoilers.add(boilerPos)) {
                continue;
            }

            FluidTankBlockEntity controller = resolveBoilerController(level, boiler, boilerPos);
            if (controller == null) {
                continue;
            }

            int totalDemand = AeronauticsSteamVentCompat.steamDemandMb(level, controller);
            if (totalDemand <= 0) {
                continue;
            }

            for (BoilerOutletBlockEntity member : network.outlets) {
                BlockPos memberBoiler = currentBoilerControllerPos(member);
                if (!boilerPos.equals(memberBoiler)) {
                    continue;
                }
                demand += FullSteamBoilerIntegration.amountForOutlet(
                        controller,
                        member.getBlockPos(),
                        totalDemand
                );
            }
        }
        return demand;
    }

    private static FluidTankBlockEntity resolveBoilerController(
            Level level,
            FluidTankBlockEntity boiler,
            BlockPos boilerPos
    ) {
        if (boiler != null && !boiler.isRemoved()) {
            return controllerOrSelf(boiler);
        }
        if (boilerPos == null || !level.isLoaded(boilerPos)) {
            return null;
        }
        if (level.getBlockEntity(boilerPos) instanceof FluidTankBlockEntity tank) {
            return controllerOrSelf(tank);
        }
        return null;
    }

    private static ReliefResult applyReliefValves(
            Level level,
            Network network,
            double tempK,
            double volume,
            double pressurePn,
            Map<BlockPos, Integer> reliefValveBudgets,
            Map<BlockPos, ValveTickState> reliefValveStates
    ) {
        if (network.reliefValves.isEmpty() || network.storedMb <= 0) {
            recordReliefValvePressure(network, pressurePn, reliefValveStates);
            return ReliefResult.NONE;
        }

        List<SteamReliefValveBlockEntity> openValves = new ArrayList<>();
        int configuredCapacity = 0;
        boolean forced = false;
        for (SteamReliefValveBlockEntity valve : network.reliefValves) {
            ValveTickState state = reliefValveStates.computeIfAbsent(valve.getBlockPos().immutable(), ignored -> new ValveTickState());
            state.recordPressure(pressurePn);
            if (!valve.wouldOpenAt(pressurePn)) {
                continue;
            }

            int available = reliefValveBudgets.getOrDefault(valve.getBlockPos(), 0);
            state.markOpen();
            if (available <= 0) {
                continue;
            }

            openValves.add(valve);
            configuredCapacity += available;
            forced |= valve.isForcedOpen();
        }

        int capacity = effectiveReliefCapacity(network, configuredCapacity);
        if (capacity <= 0 || openValves.isEmpty()) {
            return ReliefResult.NONE;
        }

        double targetPressure = forced
                ? FullSteamConfig.openPipeTargetPressure()
                : FullSteamConfig.reliefValveClosePressure();
        int requestedDrain = Math.min(capacity, SteamPhysics.drainToPressureMb(
                network.storedMb,
                tempK,
                volume,
                targetPressure
        ));
        if (requestedDrain <= 0) {
            return ReliefResult.NONE;
        }

        int drained = drainFromNetwork(level, network, requestedDrain);
        int remaining = drained;
        int remainingValves = openValves.size();
        for (SteamReliefValveBlockEntity valve : openValves) {
            if (remaining <= 0) {
                break;
            }
            int used = (int) Math.ceil(remaining / (double) remainingValves);
            BlockPos valvePos = valve.getBlockPos();
            int available = reliefValveBudgets.getOrDefault(valvePos, 0);
            if (used <= 0) {
                continue;
            }
            reliefValveBudgets.put(valvePos.immutable(), Math.max(0, available - Math.min(available, used)));
            reliefValveStates.computeIfAbsent(valvePos.immutable(), ignored -> new ValveTickState())
                    .recordVent(used);
            remaining -= used;
            remainingValves--;
        }
        return new ReliefResult(drained);
    }

    private static int effectiveReliefCapacity(Network network, int configuredCapacity) {
        if (network.productionMb <= 0) {
            return configuredCapacity;
        }

        // A safety valve must be sized against the boiler it protects, not only act as another
        // small consumer. Keep the configured value as the baseline, but guarantee enough authority
        // to outrun the current boiler production once the valve opens.
        long productionGuard = (long) network.productionMb * 4L;
        long effective = Math.max(configuredCapacity, productionGuard);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, effective));
    }

    private static void recordReliefValvePressure(
            Network network,
            double pressurePn,
            Map<BlockPos, ValveTickState> reliefValveStates
    ) {
        for (SteamReliefValveBlockEntity valve : network.reliefValves) {
            reliefValveStates.computeIfAbsent(valve.getBlockPos().immutable(), ignored -> new ValveTickState())
                    .recordPressure(pressurePn);
        }
    }

    private static void applyReliefValveStates(
            Map<BlockPos, ValveTickState> states,
            Map<BlockPos, List<SteamReliefValveBlockEntity>> valvesByBoiler
    ) {
        for (List<SteamReliefValveBlockEntity> valves : valvesByBoiler.values()) {
            for (SteamReliefValveBlockEntity valve : valves) {
                ValveTickState state = states.getOrDefault(valve.getBlockPos(), ValveTickState.EMPTY);
                boolean open = state.open() || valve.isForcedOpen();
                valve.applyNetworkState(state.pressure(), open, state.vented() > 0, state.vented());
            }
        }
    }

    /**
     * Eases the network's effective pressure toward the live target (first-order). The previous
     * effective is read back from the member outlets (which persist it), so no separate state map is
     * needed. Catches up faster during emergency overpressure so smoothing can't hide a runaway.
     */
    private static double smoothEffectivePressure(Network network, double target) {
        if (!FullSteamConfig.steamSmoothingEnabled()) {
            return target;
        }
        return smoothPressure(previousEffectivePressure(network), target);
    }

    private static double previousEffectivePressure(Network network) {
        double prevEffective = 0.0D;
        for (BoilerOutletBlockEntity outlet : network.outlets) {
            prevEffective = Math.max(prevEffective, outlet.getNetworkPressurePn());
        }
        return prevEffective;
    }

    private static double ventTargetPressure(Network network, double prePressure) {
        double target = FullSteamConfig.openPipeTargetPressure();
        if (!FullSteamConfig.steamSmoothingEnabled()) {
            return target;
        }
        double current = previousEffectivePressure(network);
        if (current <= 0.0D) {
            current = prePressure;
        }
        return smoothPressure(current, target);
    }

    private static double smoothPressure(double prevEffective, double target) {
        double tau = target > prevEffective
                ? FullSteamConfig.pressureRiseTauTicks()
                : FullSteamConfig.pressureFallTauTicks();
        double emergency = FullSteamConfig.steamBurstPressure() * FullSteamConfig.emergencyPressureMultiplier();
        if (target >= emergency) {
            tau /= Math.max(1.0D, FullSteamConfig.emergencyPressureTauDivisor());
        }
        double effective = SteamPhysics.approachExp(prevEffective, target, tau);
        double maxDelta = FullSteamConfig.maxPressureDeltaPerTick();
        if (maxDelta > 0.0D) {
            effective = Mth.clamp(effective, prevEffective - maxDelta, prevEffective + maxDelta);
        }
        return Math.max(0.0D, effective);
    }

    private static double networkVolume(Network network) {
        return Math.max(1.0D, network.volumeM3 + network.pipeCount * 0.5D + network.inlets.size() * 0.5D);
    }

    private static double networkTemperature(Network network) {
        return network.temperatureWeight > 0
                ? network.temperatureNumerator / network.temperatureWeight
                : FullSteamConfig.steamTemperatureBaseK();
    }

    private static FluidTankBlockEntity controllerOrSelf(FluidTankBlockEntity tank) {
        FluidTankBlockEntity controller = tank.getControllerBE();
        return controller == null ? tank : controller;
    }

    private static boolean ruptureActiveBoilerFallback(ServerLevel level, FluidTankBlockEntity boiler) {
        if (boiler == null || boiler.isRemoved()) {
            return false;
        }

        boolean heatedWaterTank = hasHeatedWaterTank(boiler);
        boiler.updateBoilerState();
        if (boiler.boiler == null) {
            if (!heatedWaterTank) {
                return false;
            }
        } else if (boiler.boiler.needsHeatLevelUpdate) {
            boiler.boiler.updateTemperature(boiler);
        }

        int usableHeat = boiler.boiler == null ? 0 : FullSteamBoilerIntegration.usableHeatUnits(boiler);
        if (usableHeat <= 0 && !heatedWaterTank) {
            return false;
        }

        double volume = Math.max(1.0D, boiler.getTotalTankSize());
        BoilerBurst.explode(level, boiler, volume, FullSteamConfig.steamBurstPressure());
        return true;
    }

    private static boolean hasHeatedWaterTank(FluidTankBlockEntity boiler) {
        if (boiler.getTankInventory().getFluidAmount() <= 0
                || !boiler.getTankInventory().getFluid().is(Fluids.WATER)) {
            return false;
        }
        Level level = boiler.getLevel();
        if (level == null) {
            return false;
        }

        BlockPos origin = boiler.getBlockPos();
        int width = Math.max(1, boiler.getWidth());
        int y = origin.getY() - 1;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos burnerPos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
                if (!level.isLoaded(burnerPos)) {
                    continue;
                }
                BlazeBurnerBlock.HeatLevel heat = BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(burnerPos));
                if (heat.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static double effectiveBoilerHeat(Level level, BlockPos boilerPos, double targetHeat, boolean dry) {
        if (!FullSteamConfig.thermalInertiaEnabled() || level == null || level.isClientSide() || boilerPos == null) {
            return targetHeat;
        }

        Map<BlockPos, BoilerThermalState> states =
                BOILER_THERMAL_STATES.computeIfAbsent(level, ignored -> new HashMap<>());
        long now = level.getGameTime();
        if (now % BOILER_THERMAL_STATE_TTL == 0L) {
            states.entrySet().removeIf(entry -> now - entry.getValue().lastSeenGameTime > BOILER_THERMAL_STATE_TTL);
        }

        BoilerThermalState state = states.computeIfAbsent(boilerPos.immutable(),
                ignored -> new BoilerThermalState(targetHeat, now));
        state.lastSeenGameTime = now;
        if (state.lastUpdatedGameTime == now) {
            return state.effectiveHeat;
        }

        double tau = targetHeat >= state.effectiveHeat
                ? FullSteamConfig.heatUpTauTicks()
                : dry ? FullSteamConfig.dryBoilerCoolDownTauTicks() : FullSteamConfig.coolDownTauTicks();
        state.effectiveHeat = SteamPhysics.approachExp(state.effectiveHeat, targetHeat, tau);
        state.lastUpdatedGameTime = now;
        return state.effectiveHeat;
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

    private record ReliefResult(int drained) {
        private static final ReliefResult NONE = new ReliefResult(0);
    }

    private static final class ValveTickState {
        private static final ValveTickState EMPTY = new ValveTickState();
        private double pressure;
        private boolean open;
        private int vented;

        private void recordPressure(double pressure) {
            this.pressure = Math.max(this.pressure, pressure);
        }

        private void markOpen() {
            this.open = true;
        }

        private void recordVent(int amount) {
            if (amount > 0) {
                this.open = true;
                this.vented += amount;
            }
        }

        private double pressure() {
            return pressure;
        }

        private boolean open() {
            return open;
        }

        private int vented() {
            return vented;
        }
    }

    private static final class BoilerThermalState {
        private double effectiveHeat;
        private long lastSeenGameTime;
        private long lastUpdatedGameTime;

        private BoilerThermalState(double effectiveHeat, long gameTime) {
            this.effectiveHeat = effectiveHeat;
            this.lastSeenGameTime = gameTime;
            this.lastUpdatedGameTime = gameTime - 1;
        }
    }

    private static final class Network {
        private final List<BoilerOutletBlockEntity> outlets = new ArrayList<>();
        private final List<SteamInletBlockEntity> inlets = new ArrayList<>();
        private final Set<BlockPos> boilers = new HashSet<>();
        private final Set<BlockPos> steamTanks = new HashSet<>();
        private final List<OpenEnd> openEnds = new ArrayList<>();
        private final List<SteamReliefValveBlockEntity> reliefValves = new ArrayList<>();
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
