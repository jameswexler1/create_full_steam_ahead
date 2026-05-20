package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
    public static final int MAX_HEAT_UNITS = 18;
    public static final int PRESSURE_RANGE = 30;
    private static final int BUFFER_CAPACITY = 4_000;
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String BUFFER_KEY = "SteamBuffer";
    private static final String HEAT_UNITS_KEY = "HeatUnits";
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
    private int productionRate;
    private int pushedRate;
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
        int previousProduction = productionRate;
        int previousPushed = pushedRate;
        int previousBuffer = steamBuffer.getFluidAmount();
        String previousStatus = status;

        FluidTankBlockEntity boiler = getBoiler();
        heatUnits = calculateHeatUnits(boiler);
        productionRate = heatUnits * STEAM_PER_HEAT_UNIT;
        if (productionRate > 0) {
            steamBuffer.fill(new FluidStack(ModFluids.STEAM.get(), productionRate), IFluidHandler.FluidAction.EXECUTE);
            status = "Boiler producing steam";
        } else if (boiler == null || boiler.boiler == null) {
            status = "Missing boiler";
        } else {
            status = "Needs active heat and water";
        }

        pushedRate = productionRate > 0 ? pushSteam(productionRate) : 0;

        if (previousHeatUnits != heatUnits
                || previousProduction != productionRate
                || previousPushed != pushedRate
                || previousBuffer != steamBuffer.getFluidAmount()
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
        tooltip.add(Component.literal("Production: " + productionRate + " mB/t")
                .withStyle(productionRate > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Pushed: " + pushedRate + " mB/t")
                .withStyle(pushedRate > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Pressure range: " + PRESSURE_RANGE + " blocks")
                .withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    private int calculateHeatUnits(FluidTankBlockEntity boiler) {
        if (boiler == null || boiler.boiler == null) {
            return 0;
        }

        BoilerData data = boiler.boiler;
        boiler.updateBoilerTemperature();
        if (data.needsHeatLevelUpdate) {
            data.updateTemperature(boiler);
        }

        if (data.activeHeat <= 0) {
            return 0;
        }

        int waterLimitedHeat = data.getMaxHeatLevelForWaterSupply();
        if (waterLimitedHeat <= 0) {
            return 0;
        }

        return Math.min(MAX_HEAT_UNITS, Math.min(data.activeHeat, waterLimitedHeat));
    }

    private int pushSteam(int maxAmount) {
        if (level == null || steamBuffer.isEmpty()) {
            return 0;
        }

        Direction facing = BoilerOutletBlock.getFacing(getBlockState());
        BlockPos startPos = worldPosition.relative(facing);
        int remaining = Math.min(maxAmount, steamBuffer.getFluidAmount());

        int moved = tryFillTarget(startPos, facing.getOpposite(), remaining);
        remaining -= moved;
        if (remaining <= 0 || !level.isLoaded(startPos)) {
            return moved;
        }

        FluidTransportBehaviour startPipe = FluidPropagator.getPipe(level, startPos);
        if (startPipe == null) {
            return moved;
        }

        Set<BlockPos> visited = new HashSet<>();
        Queue<PipeNode> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(new PipeNode(startPos, 0));

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
                BlockPos next = node.pos().relative(direction);
                if (next.equals(worldPosition) || !level.isLoaded(next)) {
                    continue;
                }

                FluidTransportBehaviour nextPipe = FluidPropagator.getPipe(level, next);
                if (nextPipe != null) {
                    if (node.distance() + 1 <= PRESSURE_RANGE && visited.add(next)) {
                        queue.add(new PipeNode(next, node.distance() + 1));
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
        productionRate = tag.getInt(PRODUCTION_RATE_KEY);
        pushedRate = tag.getInt(PUSHED_RATE_KEY);
        status = tag.contains(STATUS_KEY) ? tag.getString(STATUS_KEY) : "Missing boiler";
    }

    private record PipeNode(BlockPos pos, int distance) {
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
            return steamBuffer.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return steamBuffer.drain(maxDrain, action);
        }
    }
}
