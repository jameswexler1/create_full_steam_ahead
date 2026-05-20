package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.gustavo.fullsteamahead.content.cylinder.CylinderConnectivity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
import java.util.Objects;

public class SteamInletBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int BUFFER_CAPACITY = 8_000;
    private static final String ASSEMBLED_KEY = "Assembled";
    private static final String ROOT_POS_KEY = "RootPos";
    private static final String RING_ORIGIN_KEY = "RingOrigin";
    private static final String BOILER_POS_KEY = "BoilerPos";
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

    private boolean assembled;
    private BlockPos rootPos;
    private BlockPos ringOrigin;
    private BlockPos boilerPos;
    private int acceptedLastTick;
    private int consumedLastTick;

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

        if (acceptedLastTick != 0 || consumedLastTick != 0) {
            acceptedLastTick = 0;
            consumedLastTick = 0;
            notifyUpdate();
        }
    }

    public void applyRingState(BlockPos ringOrigin, BlockPos rootPos, BlockPos boilerPos) {
        boolean changed = !assembled
                || !Objects.equals(this.rootPos, rootPos)
                || !Objects.equals(this.ringOrigin, ringOrigin)
                || !Objects.equals(this.boilerPos, boilerPos);

        assembled = true;
        this.rootPos = rootPos;
        this.ringOrigin = ringOrigin;
        this.boilerPos = boilerPos;

        if (changed) {
            notifyUpdate();
        }
    }

    public void clearRingState() {
        if (!assembled && rootPos == null && ringOrigin == null && boilerPos == null) {
            return;
        }

        assembled = false;
        rootPos = null;
        ringOrigin = null;
        boilerPos = null;
        notifyUpdate();
    }

    public boolean isInletAssembled() {
        return assembled;
    }

    public BlockPos getRootPos() {
        return rootPos;
    }

    public BlockPos getRingOrigin() {
        return ringOrigin;
    }

    public BlockPos getCrankshaftPos() {
        return ringOrigin == null ? null : ringOrigin.offset(1, 4, 1);
    }

    public int getSteamAmount() {
        return steamBuffer.getFluidAmount();
    }

    public FluidStack consumeSteam(int maxAmount, boolean execute) {
        FluidStack drained = steamBuffer.drain(maxAmount,
                execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE);
        if (execute && !drained.isEmpty()) {
            consumedLastTick += drained.getAmount();
            setChanged();
        }
        return drained;
    }

    public IFluidHandler getFluidHandler(Direction side) {
        return assembled ? inputHandler : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("Steam Inlet").withStyle(ChatFormatting.GRAY));

        if (!assembled) {
            tooltip.add(Component.literal("Not part of assembled cylinder").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal("Cylinder ring linked").withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Component.literal("Steam: " + steamBuffer.getFluidAmount() + "/" + steamBuffer.getCapacity() + " mB")
                .withStyle(steamBuffer.getFluidAmount() > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Accepted: " + acceptedLastTick + " mB/t")
                .withStyle(acceptedLastTick > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Consumed: " + consumedLastTick + " mB/t")
                .withStyle(consumedLastTick > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));

        BlockPos crankshaftPos = getCrankshaftPos();
        tooltip.add(Component.literal(crankshaftPos == null ? "No crankshaft link" : "Crankshaft linked")
                .withStyle(crankshaftPos == null ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean(ASSEMBLED_KEY, assembled);
        writePos(tag, ROOT_POS_KEY, rootPos);
        writePos(tag, RING_ORIGIN_KEY, ringOrigin);
        writePos(tag, BOILER_POS_KEY, boilerPos);
        tag.put(BUFFER_KEY, steamBuffer.writeToNBT(registries, new CompoundTag()));
        tag.putInt(ACCEPTED_LAST_TICK_KEY, acceptedLastTick);
        tag.putInt(CONSUMED_LAST_TICK_KEY, consumedLastTick);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        assembled = tag.getBoolean(ASSEMBLED_KEY);
        rootPos = readPos(tag, ROOT_POS_KEY);
        ringOrigin = readPos(tag, RING_ORIGIN_KEY);
        boilerPos = readPos(tag, BOILER_POS_KEY);
        steamBuffer.readFromNBT(registries, tag.getCompound(BUFFER_KEY));
        acceptedLastTick = tag.getInt(ACCEPTED_LAST_TICK_KEY);
        consumedLastTick = tag.getInt(CONSUMED_LAST_TICK_KEY);
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
            if (!assembled || !resource.is(ModFluids.STEAM.get())) {
                return 0;
            }

            int filled = steamBuffer.fill(resource, action);
            if (!action.simulate() && filled > 0) {
                acceptedLastTick += filled;
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
    }
}
