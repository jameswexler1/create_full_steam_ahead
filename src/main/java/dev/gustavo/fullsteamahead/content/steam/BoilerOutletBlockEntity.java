package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.gustavo.fullsteamahead.compat.create.FullSteamBoilerIntegration;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class BoilerOutletBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int STEAM_PER_HEAT_UNIT = 10;
    public static final int PRESSURE_RANGE = 30;
    private static final float PRESSURE_PER_MB = 2.0f;
    private static final int BUFFER_CAPACITY = 16_000;
    private static final int PRESSURE_REFRESH_TICKS = 20;
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String BUFFER_KEY = "SteamBuffer";
    private static final String HEAT_UNITS_KEY = "HeatUnits";
    private static final String TOTAL_HEAT_UNITS_KEY = "TotalHeatUnits";
    private static final String OUTLET_COUNT_KEY = "OutletCount";
    private static final String PRODUCTION_RATE_KEY = "ProductionRate";
    private static final String PUSHED_RATE_KEY = "PushedRate";
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
        boolean previousVenting = venting;
        String previousStatus = status;
        int networkMovedLastTick = externallyDrainedSteam;
        externallyDrainedSteam = 0;
        if (pipePressureCooldown > 0) {
            pipePressureCooldown--;
        }
        venting = false;

        FluidTankBlockEntity boiler = getBoiler();
        SteamBudget budget = calculateSteamBudget(boiler);
        heatUnits = budget.outletUnits();
        totalHeatUnits = budget.totalUnits();
        outletCount = budget.outletCount();
        productionRate = heatUnits * STEAM_PER_HEAT_UNIT;
        if (productionRate > 0) {
            steamBuffer.fill(new FluidStack(ModFluids.STEAM.get(), productionRate), IFluidHandler.FluidAction.EXECUTE);
            SteamOutput output = pushSteam(productionRate, networkMovedLastTick);
            venting = output.venting();
            pushedRate = networkMovedLastTick + output.moved();
            status = venting ? "Steam venting" : "Boiler producing steam";
        } else if (boiler == null || boiler.boiler == null) {
            resetPipePressureCache();
            pushedRate = 0;
            status = "Missing boiler";
        } else {
            resetPipePressureCache();
            pushedRate = 0;
            status = "Needs active heat and water";
        }

        if (previousHeatUnits != heatUnits
                || previousTotalHeatUnits != totalHeatUnits
                || previousOutletCount != outletCount
                || previousProduction != productionRate
                || previousPushed != pushedRate
                || previousBuffer != steamBuffer.getFluidAmount()
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
        tooltip.add(Component.literal("Boiler Outlet").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(status)
                .withStyle(productionRate > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Steam: " + steamBuffer.getFluidAmount() + "/" + steamBuffer.getCapacity() + " mB")
                .withStyle(steamBuffer.getFluidAmount() > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Steam units: " + heatUnits + "/" + totalHeatUnits)
                .withStyle(heatUnits > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Attached outlets: " + outletCount)
                .withStyle(outletCount > 1 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Production: " + productionRate + " mB/t")
                .withStyle(productionRate > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Pushed: " + pushedRate + " mB/t")
                .withStyle(pushedRate > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        if (venting) {
            tooltip.add(Component.literal("Venting open steam")
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.literal("Pressure range: " + PRESSURE_RANGE + " blocks")
                .withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    private SteamBudget calculateSteamBudget(FluidTankBlockEntity boiler) {
        if (boiler == null || boiler.boiler == null) {
            return SteamBudget.NONE;
        }

        BoilerData data = boiler.boiler;
        boiler.updateBoilerTemperature();
        if (data.needsHeatLevelUpdate) {
            data.updateTemperature(boiler);
        }

        if (data.activeHeat <= 0) {
            return SteamBudget.withOutlets(FullSteamBoilerIntegration.countAttachedOutlets(boiler));
        }

        int boilerHeight = Math.max(1, boiler.getHeight());
        int thermalUnits = data.activeHeat * boilerHeight;
        int waterLimitedUnits = data.getMaxHeatLevelForWaterSupply() * boilerHeight;
        int totalUnits = Math.min(thermalUnits, waterLimitedUnits);
        int outlets = FullSteamBoilerIntegration.countAttachedOutlets(boiler);
        if (totalUnits <= 0 || outlets <= 0) {
            return SteamBudget.withOutlets(outlets);
        }

        int outletUnits = FullSteamBoilerIntegration.steamUnitsForOutlet(boiler, worldPosition, totalUnits);
        return new SteamBudget(outletUnits, totalUnits, outlets);
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
            PipePressureResult result = getOrApplyPipePressure(startPos, facing.getOpposite(), startPipe, maxAmount);
            if (result.openEnd()) {
                venting = true;
            }
            if (result.hasEndpoint()) {
                int fallbackMoved = networkMovedLastTick == 0
                        ? tryFillTargetsThroughPipes(startPos, facing.getOpposite(), remaining)
                        : 0;
                return new SteamOutput(fallbackMoved, result.openEnd());
            }
            return ventSteam(worldPosition, facing, remaining);
        }

        resetPipePressureCache();
        int moved = tryFillTarget(startPos, facing.getOpposite(), remaining);
        if (moved > 0) {
            return new SteamOutput(moved, false);
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

    private PipePressureResult applyPipePressure(BlockPos startPos, Direction sourceSide, int maxAmount) {
        float pressure = Math.max(2.0f, maxAmount * PRESSURE_PER_MB);
        Set<BlockPos> visited = new HashSet<>();
        Queue<PipeNode> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(new PipeNode(startPos, sourceSide, 0));
        boolean hasEndpoint = false;
        boolean openEnd = false;

        while (!queue.isEmpty()) {
            PipeNode node = queue.remove();
            if (!level.isLoaded(node.pos())) {
                continue;
            }

            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, node.pos());
            if (pipe == null) {
                continue;
            }
            pipe.addPressure(node.incomingSide(), true, pressure);

            for (Direction direction : FluidPropagator.getPipeConnections(level.getBlockState(node.pos()), pipe)) {
                if (direction == node.incomingSide()) {
                    continue;
                }

                BlockPos next = node.pos().relative(direction);
                if (next.equals(worldPosition) || !level.isLoaded(next)) {
                    continue;
                }

                FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, next);
                if (nextPipe != null) {
                    pipe.addPressure(direction, false, pressure);
                    if (node.distance() + 1 <= PRESSURE_RANGE && visited.add(next)) {
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
                    spawnSteamLeakParticles(node.pos(), direction);
                    hasEndpoint = true;
                    openEnd = true;
                }
            }
        }

        return new PipePressureResult(hasEndpoint, openEnd);
    }

    private int tryFillTargetsThroughPipes(BlockPos startPos, Direction sourceSide, int maxAmount) {
        int remaining = Math.min(maxAmount, steamBuffer.getFluidAmount());
        int moved = 0;
        Set<BlockPos> visited = new HashSet<>();
        Queue<PipeNode> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(new PipeNode(startPos, sourceSide, 0));

        while (!queue.isEmpty() && remaining > 0) {
            PipeNode node = queue.remove();
            if (!level.isLoaded(node.pos())) {
                continue;
            }

            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, node.pos());
            if (pipe == null) {
                continue;
            }

            for (Direction direction : FluidPropagator.getPipeConnections(level.getBlockState(node.pos()), pipe)) {
                if (direction == node.incomingSide()) {
                    continue;
                }

                BlockPos next = node.pos().relative(direction);
                if (next.equals(worldPosition) || !level.isLoaded(next)) {
                    continue;
                }

                FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, next);
                if (nextPipe != null) {
                    if (node.distance() + 1 <= PRESSURE_RANGE && visited.add(next)) {
                        queue.add(new PipeNode(next, direction.getOpposite(), node.distance() + 1));
                    }
                    continue;
                }

                int filled = tryFillTarget(next, direction.getOpposite(), remaining);
                moved += filled;
                remaining -= filled;
                if (remaining <= 0) {
                    break;
                }
            }
        }

        return moved;
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
            spawnSteamLeakParticles(sourcePos, direction);
            return new SteamOutput(leaked, true);
        }
        return SteamOutput.NONE;
    }

    private void spawnSteamLeakParticles(BlockPos sourcePos, Direction direction) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
        double x = sourcePos.getX() + 0.5 + normal.x * 0.58;
        double y = sourcePos.getY() + 0.5 + normal.y * 0.58;
        double z = sourcePos.getZ() + 0.5 + normal.z * 0.58;
        serverLevel.sendParticles(
                ParticleTypes.CLOUD,
                x,
                y,
                z,
                4,
                0.08,
                0.08,
                0.08,
                0.025
        );
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
        status = tag.contains(STATUS_KEY) ? tag.getString(STATUS_KEY) : "Missing boiler";
    }

    private record PipeNode(BlockPos pos, Direction incomingSide, int distance) {
    }

    private record PipePressureResult(boolean hasEndpoint, boolean openEnd) {
        private static final PipePressureResult NONE = new PipePressureResult(false, false);
    }

    private record SteamOutput(int moved, boolean venting) {
        private static final SteamOutput NONE = new SteamOutput(0, false);
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
