package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.compat.create.FullSteamBoilerIntegration;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import dev.gustavo.fullsteamahead.registry.ModParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class BoilerOutletBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final float PRESSURE_PER_MB = 2.0f;
    private static final int BUFFER_CAPACITY = 256_000;
    private static final int PRESSURE_REFRESH_TICKS = 5;
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String BUFFER_KEY = "SteamBuffer";
    private static final String HEAT_UNITS_KEY = "HeatUnits";
    private static final String TOTAL_HEAT_UNITS_KEY = "TotalHeatUnits";
    private static final String OUTLET_COUNT_KEY = "OutletCount";
    private static final String PRODUCTION_RATE_KEY = "ProductionRate";
    private static final String PUSHED_RATE_KEY = "PushedRate";
    private static final String PRESSURE_BAR_KEY = "PressureBar";
    private static final String BOILER_VOLUME_KEY = "BoilerVolume";
    private static final String BOILER_TEMP_KEY = "BoilerTemperatureK";
    private static final String STATUS_KEY = "Status";

    private final FluidTank steamBuffer = new FluidTank(BUFFER_CAPACITY, stack -> stack.is(ModFluids.STEAM.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler outputHandler = new OutputOnlySteamHandler();

    private BlockPos boilerPos;
    private int heatUnits;
    private int totalHeatUnits;
    private int outletCount;
    private int productionRate;
    private int pushedRate;
    private int externallyDrainedSteam;
    private int pipePressureCooldown;
    private int lastPressureAmount;
    private double steamPressureBar;
    private int boilerVolume;
    private int boilerTemperatureK;
    private boolean lit;
    private PipePressureResult cachedPipePressure = PipePressureResult.NONE;
    private boolean venting;
    private String status = "Missing boiler";

    public BoilerOutletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER_OUTLET.get(), pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide()) {
            refreshBoilerState();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }

        int previousHeatUnits = heatUnits;
        int previousTotalHeatUnits = totalHeatUnits;
        int previousOutletCount = outletCount;
        int previousProduction = productionRate;
        int previousPushed = pushedRate;
        int previousBuffer = steamBuffer.getFluidAmount();
        double previousPressure = steamPressureBar;
        boolean previousVenting = venting;
        String previousStatus = status;
        int networkMovedLastTick = externallyDrainedSteam;
        externallyDrainedSteam = 0;
        if (pipePressureCooldown > 0) {
            pipePressureCooldown--;
        }
        venting = false;

        FluidTankBlockEntity boiler = getBoiler();
        // calculateSteamBudget also stores boilerVolume, boilerTemperatureK and lit.
        SteamBudget budget = calculateSteamBudget(boiler);
        heatUnits = budget.outletUnits();
        totalHeatUnits = budget.totalUnits();
        outletCount = budget.outletCount();
        productionRate = budget.outletUnits();

        // Boil steam into the vessel, then deliver what consumers/open-ends will take. Steam that stays
        // behind raises the stored amount, raising pressure.
        if (productionRate > 0) {
            steamBuffer.fill(new FluidStack(ModFluids.STEAM.get(), productionRate), IFluidHandler.FluidAction.EXECUTE);
        }
        steamPressureBar = SteamPhysics.pressureBar(steamBuffer.getFluidAmount(), boilerTemperatureK, boilerVolume);

        if (steamBuffer.getFluidAmount() > 0) {
            SteamOutput output = pushSteam(steamBuffer.getFluidAmount(), networkMovedLastTick);
            venting = output.venting();
            pushedRate = networkMovedLastTick + output.moved();
            status = output.blocked() ? "Steam blocked"
                    : venting ? "Steam venting" : "Boiler producing steam";
        } else {
            resetPipePressureCache();
            pushedRate = 0;
            status = boiler == null || boiler.boiler == null ? "Missing boiler"
                    : lit ? "Boiler producing steam" : "Needs active heat and water";
        }

        // Recompute pressure from the steam still stored after delivery.
        steamPressureBar = SteamPhysics.pressureBar(steamBuffer.getFluidAmount(), boilerTemperatureK, boilerVolume);

        if (FullSteamConfig.overpressureEnabled() && boiler != null && boiler.boiler != null
                && steamPressureBar >= FullSteamConfig.steamBurstBar()) {
            explodeBoiler(boiler);
            return;
        }
        if (boiler != null && boiler.boiler != null && steamPressureBar >= FullSteamConfig.steamWarnBar()) {
            status = "Overpressure!";
            emitOverpressureWarning(boiler);
        }

        if (previousHeatUnits != heatUnits
                || previousTotalHeatUnits != totalHeatUnits
                || previousOutletCount != outletCount
                || previousProduction != productionRate
                || previousPushed != pushedRate
                || previousBuffer != steamBuffer.getFluidAmount()
                || previousPressure != steamPressureBar
                || previousVenting != venting
                || !previousStatus.equals(status)) {
            notifyUpdate();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide()) {
            refreshBoilerState();
        }
    }

    public IFluidHandler getFluidHandler(Direction side) {
        Direction facing = BoilerOutletBlock.getFacing(getBlockState());
        if (side == null || side == facing) {
            return outputHandler;
        }
        return null;
    }

    public void refreshBoilerState() {
        FluidTankBlockEntity boiler = getBoiler();
        boilerPos = boiler == null ? null : boiler.getBlockPos();
        if (boiler != null && !boiler.isRemoved()) {
            boiler.updateBoilerState();
        }
        notifyUpdate();
    }

    public void clearBoilerState() {
        FluidTankBlockEntity boiler = getBoiler();
        boilerPos = null;
        heatUnits = 0;
        totalHeatUnits = 0;
        outletCount = 0;
        productionRate = 0;
        pushedRate = 0;
        status = "Outlet removed";
        if (boiler != null && !boiler.isRemoved()) {
            boiler.updateBoilerState();
        }
        notifyUpdate();
    }

    public FluidTankBlockEntity getBoiler() {
        if (level == null) {
            return null;
        }

        BlockPos tankPos = BoilerOutletBlock.getAttachedTankPos(worldPosition, getBlockState());
        if (!level.isLoaded(tankPos)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(tankPos);
        if (blockEntity instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity controller = tank.getControllerBE();
            return controller == null ? tank : controller;
        }
        return null;
    }

    public boolean isAttachedToBoiler(FluidTankBlockEntity controller) {
        FluidTankBlockEntity boiler = getBoiler();
        return boiler != null && controller != null && boiler.getBlockPos().equals(controller.getBlockPos());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("Boiler Outlet").style(ChatFormatting.GRAY).forGoggles(tooltip);
        CreateLang.text(status)
                .style(productionRate > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);
        CreateLang.text("Steam: " + steamBuffer.getFluidAmount() + "/" + steamBuffer.getCapacity() + " mB")
                .style(steamBuffer.getFluidAmount() > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Produced (outlet/boiler): " + heatUnits + "/" + totalHeatUnits + " mB/t")
                .style(heatUnits > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);
        CreateLang.text("Attached outlets: " + outletCount)
                .style(outletCount > 1 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Production: " + productionRate + " mB/t")
                .style(productionRate > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);
        CreateLang.text("Pushed: " + pushedRate + " mB/t")
                .style(pushedRate > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        if (venting) {
            CreateLang.text("Venting open steam").style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
        boolean warning = steamPressureBar >= FullSteamConfig.steamWarnBar();
        CreateLang.text(String.format("Pressure: %.1f bar", steamPressureBar))
                .style(warning ? ChatFormatting.RED : steamPressureBar > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Boiler volume: " + boilerVolume + " (" + boilerTemperatureK + " K)")
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        if (warning) {
            CreateLang.text("OVERPRESSURE — burst at " + String.format("%.0f", FullSteamConfig.steamBurstBar()) + " bar")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }
        CreateLang.text("Pressure range: " + FullSteamConfig.boilerOutletPressureRange() + " blocks")
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    private SteamBudget calculateSteamBudget(FluidTankBlockEntity boiler) {
        boilerVolume = 0;
        boilerTemperatureK = (int) Math.round(FullSteamConfig.steamTempBaseK());
        lit = false;
        if (boiler == null || boiler.boiler == null) {
            return SteamBudget.NONE;
        }

        BoilerData data = boiler.boiler;
        boiler.updateBoilerTemperature();
        if (data.needsHeatLevelUpdate) {
            data.updateTemperature(boiler);
        }

        int volume = boiler.getTotalTankSize();
        boilerVolume = volume;

        int waterGatedHeat = Math.min(data.activeHeat, data.getMaxHeatLevelForWaterSupply());
        boilerTemperatureK = (int) Math.round(SteamPhysics.temperatureK(waterGatedHeat));
        lit = data.activeHeat > 0 && data.getMaxHeatLevelForWaterSupply() > 0;

        int outlets = FullSteamBoilerIntegration.countAttachedOutlets(boiler);
        if (!lit) {
            return SteamBudget.withOutlets(outlets);
        }

        // Steam boiled per tick scales with vessel volume and how hard the boiler is fired.
        double heatFactor = SteamPhysics.heatFactor(waterGatedHeat);
        int totalProductionMb = SteamPhysics.productionMb(volume, heatFactor);
        if (totalProductionMb <= 0 || outlets <= 0) {
            return SteamBudget.withOutlets(outlets);
        }

        int outletProductionMb = FullSteamBoilerIntegration.steamUnitsForOutlet(boiler, worldPosition, totalProductionMb);
        return new SteamBudget(outletProductionMb, totalProductionMb, outlets);
    }

    private SteamOutput pushSteam(int maxAmount, int networkMovedLastTick) {
        if (level == null || steamBuffer.isEmpty()) {
            return SteamOutput.NONE;
        }

        Direction facing = BoilerOutletBlock.getFacing(getBlockState());
        BlockPos startPos = worldPosition.relative(facing);
        int remaining = Math.min(maxAmount, steamBuffer.getFluidAmount());

        if (!level.isLoaded(startPos)) {
            return ventSteam(worldPosition, facing, remaining);
        }

        FluidTransportBehaviour startPipe = FluidPropagator.getPipe(level, startPos);
        if (startPipe != null) {
            if (!canSteamPassThrough(startPipe, level.getBlockState(startPos), facing.getOpposite())) {
                resetPipePressureCache();
                return SteamOutput.BLOCKED;
            }

            PipePressureResult result = getOrApplyPipePressure(startPos, facing.getOpposite(), startPipe, maxAmount);
            if (result.openEnd()) {
                venting = true;
            }
            if (result.hasEndpoint()) {
                int fallbackMoved = tryFillTargetsThroughPipes(startPos, facing.getOpposite(), pipeFallbackBudget(remaining));
                boolean blockedOnly = result.blocked() && fallbackMoved == 0 && !result.openEnd();
                return new SteamOutput(fallbackMoved, result.openEnd(), blockedOnly);
            }
            if (result.blocked()) {
                return SteamOutput.BLOCKED;
            }
            return ventSteam(worldPosition, facing, remaining);
        }

        resetPipePressureCache();
        int moved = tryFillTarget(startPos, facing.getOpposite(), remaining);
        if (moved > 0) {
            return new SteamOutput(moved, false, false);
        }

        return ventSteam(worldPosition, facing, remaining);
    }

    public void forcePipePressureRefresh() {
        if (level == null || level.isClientSide() || productionRate <= 0) {
            resetPipePressureCache();
            return;
        }

        Direction facing = BoilerOutletBlock.getFacing(getBlockState());
        BlockPos startPos = worldPosition.relative(facing);
        if (!level.isLoaded(startPos)) {
            resetPipePressureCache();
            return;
        }

        FluidTransportBehaviour startPipe = FluidPropagator.getPipe(level, startPos);
        if (startPipe == null) {
            resetPipePressureCache();
            return;
        }

        cachedPipePressure = applyPipePressure(startPos, facing.getOpposite(), productionRate);
        lastPressureAmount = productionRate;
        pipePressureCooldown = PRESSURE_REFRESH_TICKS;
        venting = cachedPipePressure.openEnd();
    }

    private PipePressureResult getOrApplyPipePressure(
            BlockPos startPos,
            Direction sourceSide,
            FluidTransportBehaviour startPipe,
            int maxAmount
    ) {
        if (pipePressureCooldown <= 0 || lastPressureAmount != maxAmount || !startPipe.hasAnyPressure()) {
            cachedPipePressure = applyPipePressure(startPos, sourceSide, maxAmount);
            lastPressureAmount = maxAmount;
            pipePressureCooldown = PRESSURE_REFRESH_TICKS;
        }
        return cachedPipePressure;
    }

    private void resetPipePressureCache() {
        pipePressureCooldown = 0;
        lastPressureAmount = 0;
        cachedPipePressure = PipePressureResult.NONE;
    }

    private int pipeFallbackBudget(int requested) {
        if (requested <= 0) {
            return 0;
        }

        // Keep live source fluid available so Create's pipe flow renderer can show steam in pipes.
        int flowReserve = Math.max(productionRate, FullSteamConfig.maxPipedSteamPerTick());
        int transferable = steamBuffer.getFluidAmount() - flowReserve;
        return Math.max(0, Math.min(requested, transferable));
    }

    private PipePressureResult applyPipePressure(BlockPos startPos, Direction sourceSide, int maxAmount) {
        float pressure = Math.max(2.0f, maxAmount * PRESSURE_PER_MB);
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
                if (!canSteamPassThrough(pipe, pipeState, direction)) {
                    blocked = true;
                    continue;
                }

                BlockPos next = node.pos().relative(direction);
                if (next.equals(worldPosition) || !level.isLoaded(next)) {
                    continue;
                }

                FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, next);
                if (nextPipe != null) {
                    if (!canSteamPassThrough(nextPipe, level.getBlockState(next), direction.getOpposite())) {
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
                    // Steam itself moves through Create's pipe pull, so report engine pressure here (the
                    // pressure BFS always reaches the inlet) rather than in the fallback push path.
                    if (level.getBlockEntity(next) instanceof SteamInletBlockEntity inlet && inlet.isInletAssembled()) {
                        inlet.reportSupplyPressure((float) steamPressureBar);
                    }
                    continue;
                }

                if (FluidPropagator.isOpenEnd(level, node.pos(), direction)) {
                    pipe.addPressure(direction, false, pressure);
                    spawnSteamLeakParticles(node.pos(), direction);
                    hasEndpoint = true;
                    openEnd = true;
                }
            }
        }

        return new PipePressureResult(hasEndpoint, openEnd, blocked);
    }

    private int tryFillTargetsThroughPipes(BlockPos startPos, Direction sourceSide, int maxAmount) {
        int remaining = Math.min(maxAmount, steamBuffer.getFluidAmount());
        if (remaining <= 0) {
            return 0;
        }

        List<FillTarget> targets = collectFillTargetsThroughPipes(startPos, sourceSide);
        if (targets.isEmpty()) {
            return 0;
        }

        List<FillTarget> steamInlets = targets.stream()
                .filter(FillTarget::steamInlet)
                .toList();
        if (steamInlets.isEmpty()) {
            return fillTargetsEvenly(targets, remaining);
        }

        int moved = fillTargetsEvenly(steamInlets, remaining);
        remaining -= moved;
        if (remaining <= 0) {
            return moved;
        }

        List<FillTarget> passiveTargets = targets.stream()
                .filter(target -> !target.steamInlet())
                .toList();
        if (!passiveTargets.isEmpty()) {
            moved += fillTargetsEvenly(passiveTargets, remaining);
        }
        return moved;
    }

    private List<FillTarget> collectFillTargetsThroughPipes(BlockPos startPos, Direction sourceSide) {
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
                if (direction == node.incomingSide()) {
                    continue;
                }
                if (!canSteamPassThrough(pipe, pipeState, direction)) {
                    continue;
                }

                BlockPos next = node.pos().relative(direction);
                if (next.equals(worldPosition) || !level.isLoaded(next)) {
                    continue;
                }

                FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, next);
                if (nextPipe != null) {
                    if (!canSteamPassThrough(nextPipe, level.getBlockState(next), direction.getOpposite())) {
                        continue;
                    }
                    if (node.distance() + 1 <= FullSteamConfig.boilerOutletPressureRange() && visited.add(next)) {
                        queue.add(new PipeNode(next, direction.getOpposite(), node.distance() + 1));
                    }
                    continue;
                }

                IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, next, direction.getOpposite());
                if (target == null) {
                    continue;
                }
                if (!seenTargets.add(next)) {
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

    private boolean canSteamPassThrough(FluidTransportBehaviour pipe, BlockState state, Direction side) {
        return pipe.canHaveFlowToward(state, side)
                && pipe.canPullFluidFrom(new FluidStack(ModFluids.STEAM.get(), 1), state, side);
    }

    private int fillTargetsEvenly(List<FillTarget> targets, int maxAmount) {
        int remaining = Math.min(maxAmount, steamBuffer.getFluidAmount());
        int moved = 0;
        int[] delivered = new int[targets.size()];
        List<Integer> openTargetIndexes = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            openTargetIndexes.add(i);
        }
        int offset = targetOrderOffset(openTargetIndexes.size());

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
                int filled = tryFillTarget(target.pos(), target.side(), allowance);
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

    private int targetOrderOffset(int targetCount) {
        if (targetCount <= 1) {
            return 0;
        }
        int hash = worldPosition.getX() * 31 + worldPosition.getY() * 17 + worldPosition.getZ() * 13;
        return Math.floorMod(hash, targetCount);
    }

    private int tryFillTarget(BlockPos targetPos, Direction side, int maxAmount) {
        if (level == null || maxAmount <= 0 || targetPos.equals(worldPosition) || !level.isLoaded(targetPos)) {
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

        int available = steamBuffer.drain(accepted, IFluidHandler.FluidAction.SIMULATE).getAmount();
        if (available <= 0) {
            return 0;
        }

        int filled = target.fill(new FluidStack(ModFluids.STEAM.get(), available), IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            steamBuffer.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        }
        return filled;
    }

    private SteamOutput ventSteam(BlockPos sourcePos, Direction direction, int maxAmount) {
        int leaked = steamBuffer.drain(maxAmount, IFluidHandler.FluidAction.EXECUTE).getAmount();
        if (leaked > 0) {
            spawnSteamLeakParticles(sourcePos, direction, leaked);
            return new SteamOutput(leaked, true, false);
        }
        return SteamOutput.NONE;
    }

    private void spawnSteamLeakParticles(BlockPos sourcePos, Direction direction) {
        spawnSteamLeakParticles(sourcePos, direction, productionRate);
    }

    private void spawnSteamLeakParticles(BlockPos sourcePos, Direction direction, int amount) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
        double x = sourcePos.getX() + 0.5 + normal.x * 0.58;
        double y = sourcePos.getY() + 0.5 + normal.y * 0.58;
        double z = sourcePos.getZ() + 0.5 + normal.z * 0.58;
        double intensity = Math.min(1.0D, Math.max(0.25D, amount / 90.0D));
        int jets = 7 + (int) Math.round(intensity * 7.0D);
        for (int i = 0; i < jets; i++) {
            double jitterX = (serverLevel.random.nextDouble() - 0.5D) * 0.11D;
            double jitterY = (serverLevel.random.nextDouble() - 0.5D) * 0.11D;
            double jitterZ = (serverLevel.random.nextDouble() - 0.5D) * 0.11D;
            double speed = 0.055D + serverLevel.random.nextDouble() * 0.035D + intensity * 0.055D;
            serverLevel.sendParticles(
                    ModParticleTypes.STEAM_LEAK.get(),
                    x + jitterX,
                    y + jitterY,
                    z + jitterZ,
                    0,
                    normal.x * speed + jitterX * 0.55D,
                    normal.y * speed + 0.05D + jitterY * 0.55D,
                    normal.z * speed + jitterZ * 0.55D,
                    1.0D
            );
        }
    }

    private void emitOverpressureWarning(FluidTankBlockEntity boiler) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 8L != 0L) {
            return;
        }

        Vec3 center = boilerCenter(boiler);
        serverLevel.sendParticles(ModParticleTypes.STEAM_LEAK.get(),
                center.x, center.y, center.z, 12, 0.4D, 0.4D, 0.4D, 0.02D);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.6F);
    }

    private void explodeBoiler(FluidTankBlockEntity boiler) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 center = boilerCenter(boiler);
        double power = Math.min(
                FullSteamConfig.overpressureMaxPower(),
                FullSteamConfig.overpressureBasePower()
                        + FullSteamConfig.overpressurePowerPerVolume() * boiler.getTotalTankSize());
        Level.ExplosionInteraction interaction = FullSteamConfig.overpressureBreaksBlocks()
                ? Level.ExplosionInteraction.BLOCK
                : Level.ExplosionInteraction.NONE;
        serverLevel.explode(null, center.x, center.y, center.z, (float) power, interaction);
    }

    private Vec3 boilerCenter(FluidTankBlockEntity boiler) {
        BlockPos pos = boiler.getBlockPos();
        int width = Math.max(1, boiler.getWidth());
        int height = Math.max(1, boiler.getHeight());
        return new Vec3(pos.getX() + width / 2.0D, pos.getY() + height / 2.0D, pos.getZ() + width / 2.0D);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (boilerPos == null) {
            tag.remove(BOILER_POS_KEY);
        } else {
            tag.putLong(BOILER_POS_KEY, boilerPos.asLong());
        }
        tag.put(BUFFER_KEY, steamBuffer.writeToNBT(registries, new CompoundTag()));
        tag.putInt(HEAT_UNITS_KEY, heatUnits);
        tag.putInt(TOTAL_HEAT_UNITS_KEY, totalHeatUnits);
        tag.putInt(OUTLET_COUNT_KEY, outletCount);
        tag.putInt(PRODUCTION_RATE_KEY, productionRate);
        tag.putInt(PUSHED_RATE_KEY, pushedRate);
        tag.putDouble(PRESSURE_BAR_KEY, steamPressureBar);
        tag.putInt(BOILER_VOLUME_KEY, boilerVolume);
        tag.putInt(BOILER_TEMP_KEY, boilerTemperatureK);
        tag.putString(STATUS_KEY, status);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        boilerPos = tag.contains(BOILER_POS_KEY) ? BlockPos.of(tag.getLong(BOILER_POS_KEY)) : null;
        steamBuffer.readFromNBT(registries, tag.getCompound(BUFFER_KEY));
        heatUnits = tag.getInt(HEAT_UNITS_KEY);
        totalHeatUnits = tag.getInt(TOTAL_HEAT_UNITS_KEY);
        outletCount = tag.getInt(OUTLET_COUNT_KEY);
        productionRate = tag.getInt(PRODUCTION_RATE_KEY);
        pushedRate = tag.getInt(PUSHED_RATE_KEY);
        steamPressureBar = tag.getDouble(PRESSURE_BAR_KEY);
        boilerVolume = tag.getInt(BOILER_VOLUME_KEY);
        boilerTemperatureK = tag.getInt(BOILER_TEMP_KEY);
        status = tag.contains(STATUS_KEY) ? tag.getString(STATUS_KEY) : "Missing boiler";
    }

    private record PipeNode(BlockPos pos, Direction incomingSide, int distance) {
    }

    private record FillTarget(BlockPos pos, Direction side, boolean steamInlet, int maxFillPerTick) {
    }

    private record PipePressureResult(boolean hasEndpoint, boolean openEnd, boolean blocked) {
        private static final PipePressureResult NONE = new PipePressureResult(false, false, false);
    }

    private record SteamOutput(int moved, boolean venting, boolean blocked) {
        private static final SteamOutput NONE = new SteamOutput(0, false, false);
        private static final SteamOutput BLOCKED = new SteamOutput(0, false, true);
    }

    private record SteamBudget(int outletUnits, int totalUnits, int outletCount) {
        private static final SteamBudget NONE = new SteamBudget(0, 0, 0);

        private static SteamBudget withOutlets(int outletCount) {
            return new SteamBudget(0, 0, outletCount);
        }
    }

    private class OutputOnlySteamHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return steamBuffer.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return steamBuffer.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return steamBuffer.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!resource.is(ModFluids.STEAM.get())) {
                return FluidStack.EMPTY;
            }
            FluidStack drained = steamBuffer.drain(resource, action);
            if (!action.simulate() && !drained.isEmpty()) {
                externallyDrainedSteam += drained.getAmount();
            }
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = steamBuffer.drain(maxDrain, action);
            if (!action.simulate() && !drained.isEmpty()) {
                externallyDrainedSteam += drained.getAmount();
            }
            return drained;
        }
    }
}
