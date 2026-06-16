package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderConnectivity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
import java.util.Objects;

public class SteamInletBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int BUFFER_CAPACITY = 360;
    private static final String ASSEMBLED_KEY = "Assembled";
    private static final String ROOT_POS_KEY = "RootPos";
    private static final String RING_ORIGIN_KEY = "RingOrigin";
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String ACTIVE_KEY = "Active";
    private static final String BUFFER_KEY = "SteamBuffer";
    private static final String ACCEPTED_LAST_TICK_KEY = "AcceptedLastTick";
    private static final String CONSUMED_LAST_TICK_KEY = "ConsumedLastTick";

    private final FluidTank steamBuffer = new FluidTank(BUFFER_CAPACITY, stack -> stack.is(ModFluids.STEAM.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler inputHandler = new InputOnlySteamHandler();
    private final IFluidHandler passiveHandler = new PassiveSteamHandler();

    private boolean assembled;
    private boolean active;
    private BlockPos rootPos;
    private BlockPos ringOrigin;
    private BlockPos boilerPos;
    private int acceptedLastTick;
    private int consumedLastTick;
    private int acceptedThisTick;
    private int consumedThisTick;
    private long acceptedGameTime = Long.MIN_VALUE;
    private int acceptedThisGameTick;
    private double networkPressurePn;
    private int networkDrawCap;
    private long networkGameTime = Long.MIN_VALUE;

    private static final int NETWORK_DECAY_TICKS = 3;

    public SteamInletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_INLET.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide()) {
            CylinderConnectivity.refreshFrom(level, worldPosition);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }

        // Pipe fills and the engine's draw happen later in the game tick than this method, so the
        // accumulators now hold the totals from the previous tick. Publish them to the synced
        // display fields (so the goggle overlay shows real rates) when they change, then reset.
        if (acceptedLastTick != acceptedThisTick || consumedLastTick != consumedThisTick) {
            acceptedLastTick = acceptedThisTick;
            consumedLastTick = consumedThisTick;
            notifyUpdate();
        }
        acceptedThisTick = 0;
        consumedThisTick = 0;
    }

    public void applyRingState(BlockPos ringOrigin, BlockPos rootPos, BlockPos boilerPos, boolean active) {
        boolean changed = !assembled
                || this.active != active
                || !Objects.equals(this.rootPos, rootPos)
                || !Objects.equals(this.ringOrigin, ringOrigin)
                || !Objects.equals(this.boilerPos, boilerPos);

        assembled = true;
        this.active = active;
        this.rootPos = rootPos;
        this.ringOrigin = ringOrigin;
        this.boilerPos = boilerPos;

        if (!active) {
            clearPassiveSteamState();
        }

        if (changed) {
            invalidateFluidCapability();
            notifyUpdate();
        }
    }

    public void clearRingState() {
        if (!assembled && rootPos == null && ringOrigin == null && boilerPos == null) {
            return;
        }

        assembled = false;
        active = false;
        rootPos = null;
        ringOrigin = null;
        boilerPos = null;
        networkPressurePn = 0.0D;
        networkDrawCap = 0;
        networkGameTime = Long.MIN_VALUE;
        invalidateFluidCapability();
        notifyUpdate();
    }

    public boolean isInletAssembled() {
        return assembled;
    }

    public boolean isActiveInlet() {
        return assembled && active;
    }

    public BlockPos getRootPos() {
        return rootPos;
    }

    public BlockPos getRingOrigin() {
        return ringOrigin;
    }

    public BlockPos getEnginePos() {
        if (ringOrigin == null) {
            return null;
        }

        Direction facing = getBlockState().hasProperty(SteamInletBlock.FACING)
                ? getBlockState().getValue(SteamInletBlock.FACING)
                : Direction.UP;
        return ringOrigin.offset(1, facing == Direction.DOWN ? 1 : 0, 1);
    }

    public int getSteamAmount() {
        return active ? steamBuffer.getFluidAmount() : 0;
    }

    public int getStoredSteamMb() {
        return active ? steamBuffer.getFluidAmount() : 0;
    }

    public int getDisplayConsumedSteamMb() {
        return active ? Math.max(consumedThisTick, consumedLastTick) : 0;
    }

    /** Drains up to {@code amount} mB from this inlet's buffer (network venting; not counted as consumed). */
    public int drainSteam(int amount) {
        if (!active || amount <= 0) {
            return 0;
        }
        return steamBuffer.drain(amount, IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    /** Called by SteamNetworkManager each tick: the engine's network pressure and fair draw cap. */
    public void applyNetworkState(double pressurePn, int drawCap) {
        pressurePn = SteamPressure.zeroIfNegligible(pressurePn);
        this.networkPressurePn = pressurePn;
        this.networkDrawCap = drawCap;
        this.networkGameTime = level == null ? 0L : level.getGameTime();
    }

    /** True when the network manager has reported state recently (so a draw cap of 0 is deliberate). */
    public boolean isNetworkFresh() {
        return level != null
                && networkGameTime != Long.MIN_VALUE
                && level.getGameTime() - networkGameTime <= NETWORK_DECAY_TICKS;
    }

    private boolean networkFresh() {
        return isNetworkFresh();
    }

    /** Network steam pressure (pN/m^2) at this inlet, or 0 if the manager has not refreshed recently. */
    public double getNetworkPressurePn() {
        return networkFresh() ? networkPressurePn : 0.0D;
    }

    /** Fair per-engine draw cap (mB/t) assigned by the network manager, or 0 if stale. */
    public int getNetworkDrawCap() {
        return networkFresh() ? networkDrawCap : 0;
    }

    public FluidStack consumeSteam(int maxAmount, boolean execute) {
        if (!active) {
            return FluidStack.EMPTY;
        }
        FluidStack drained = steamBuffer.drain(maxAmount,
                execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE);
        if (execute && !drained.isEmpty()) {
            consumedThisTick += drained.getAmount();
            setChanged();
        }
        return drained;
    }

    public IFluidHandler getFluidHandler(Direction side) {
        if (!assembled) {
            return null;
        }
        return active ? inputHandler : passiveHandler;
    }

    private void clearPassiveSteamState() {
        int stored = steamBuffer.getFluidAmount();
        if (stored > 0) {
            steamBuffer.drain(stored, IFluidHandler.FluidAction.EXECUTE);
        }
        acceptedThisTick = 0;
        consumedThisTick = 0;
        acceptedLastTick = 0;
        consumedLastTick = 0;
        acceptedThisGameTick = 0;
        networkPressurePn = 0.0D;
        networkDrawCap = 0;
        networkGameTime = Long.MIN_VALUE;
    }

    private void invalidateFluidCapability() {
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            // Create pipes cache both connection shape and flow graph state; the inlet can change
            // capability availability without being replaced, so refresh adjacent pipes explicitly.
            refreshAdjacentPipes();
        }
    }

    private void refreshAdjacentPipes() {
        for (Direction direction : Direction.values()) {
            BlockPos pipePos = worldPosition.relative(direction);
            if (!level.isLoaded(pipePos) || FluidPropagator.getPipe(level, pipePos) == null) {
                continue;
            }

            BlockState pipeState = level.getBlockState(pipePos);
            BlockState refreshedState = Block.updateFromNeighbourShapes(pipeState, level, pipePos);
            if (refreshedState != pipeState) {
                level.setBlock(pipePos, refreshedState, Block.UPDATE_CLIENTS);
                pipeState = refreshedState;
            }

            FluidPropagator.propagateChangedPipe(level, pipePos, pipeState);
            SteamPipePressureCoordinator.refreshSteamPressureNear(level, pipePos);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("Steam Inlet").style(ChatFormatting.GRAY).forGoggles(tooltip);

        if (!assembled) {
            CreateLang.text("Not part of assembled cylinder").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        } else if (!active) {
            CreateLang.text("Passive decorative inlet").style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
        } else {
            CreateLang.text("Active steam inlet").style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
        }

        CreateLang.text("Network pressure: " + SteamPressure.format(getNetworkPressurePn()))
                .style(getNetworkPressurePn() > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        int storedSteam = getSteamAmount();
        CreateLang.text("Steam: " + storedSteam + "/" + steamBuffer.getCapacity() + " mB")
                .style(storedSteam > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        int displayedAccepted = active ? acceptedLastTick : 0;
        int displayedConsumed = active ? consumedLastTick : 0;
        CreateLang.text("Accepted: " + displayedAccepted + " mB/t  Consumed: " + displayedConsumed + " mB/t")
                .style(displayedConsumed > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);

        BlockPos enginePos = active ? getEnginePos() : null;
        CreateLang.text(enginePos == null ? "No engine link" : "Engine linked")
                .style(enginePos == null ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean(ASSEMBLED_KEY, assembled);
        writePos(tag, ROOT_POS_KEY, rootPos);
        writePos(tag, RING_ORIGIN_KEY, ringOrigin);
        writePos(tag, BOILER_POS_KEY, boilerPos);
        tag.putBoolean(ACTIVE_KEY, active);
        tag.put(BUFFER_KEY, steamBuffer.writeToNBT(registries, new CompoundTag()));
        tag.putInt(ACCEPTED_LAST_TICK_KEY, acceptedLastTick);
        tag.putInt(CONSUMED_LAST_TICK_KEY, consumedLastTick);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        assembled = tag.getBoolean(ASSEMBLED_KEY);
        active = assembled && (!tag.contains(ACTIVE_KEY) || tag.getBoolean(ACTIVE_KEY));
        rootPos = readPos(tag, ROOT_POS_KEY);
        ringOrigin = readPos(tag, RING_ORIGIN_KEY);
        boilerPos = readPos(tag, BOILER_POS_KEY);
        steamBuffer.readFromNBT(registries, tag.getCompound(BUFFER_KEY));
        int overflow = steamBuffer.getFluidAmount() - BUFFER_CAPACITY;
        if (overflow > 0) {
            steamBuffer.drain(overflow, IFluidHandler.FluidAction.EXECUTE);
        }
        acceptedLastTick = tag.getInt(ACCEPTED_LAST_TICK_KEY);
        consumedLastTick = tag.getInt(CONSUMED_LAST_TICK_KEY);
        if (assembled && !active) {
            clearPassiveSteamState();
        }
    }

    private static void writePos(CompoundTag tag, String key, BlockPos pos) {
        if (pos == null) {
            tag.remove(key);
            return;
        }
        tag.putLong(key, pos.asLong());
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    private class InputOnlySteamHandler implements IFluidHandler {
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
            return stack.is(ModFluids.STEAM.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!assembled || !active || !resource.is(ModFluids.STEAM.get())) {
                return 0;
            }

            int tickAllowance = acceptedSteamAllowance();
            if (tickAllowance <= 0) {
                return 0;
            }

            int fillAmount = Math.min(resource.getAmount(), tickAllowance);
            int filled = steamBuffer.fill(new FluidStack(ModFluids.STEAM.get(), fillAmount), action);
            if (!action.simulate() && filled > 0) {
                acceptedThisTick += filled;
                acceptedThisGameTick += filled;
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }

        private int acceptedSteamAllowance() {
            // The network manager governs how much this engine may draw; cap intake to that fair share
            // (falling back to the full-engine flow before the manager has reported).
            if (!active) {
                return 0;
            }
            int cap = networkFresh() ? networkDrawCap : FullSteamConfig.maxPipedSteamPerTick();
            int maxPerTick = Math.max(0, cap);
            if (level == null) {
                return maxPerTick;
            }

            long gameTime = level.getGameTime();
            if (acceptedGameTime != gameTime) {
                acceptedGameTime = gameTime;
                acceptedThisGameTick = 0;
            }
            return Math.max(0, maxPerTick - acceptedThisGameTick);
        }
    }

    private class PassiveSteamHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return BUFFER_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(ModFluids.STEAM.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
