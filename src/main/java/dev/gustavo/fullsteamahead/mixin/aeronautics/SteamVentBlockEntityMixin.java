package dev.gustavo.fullsteamahead.mixin.aeronautics;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.gustavo.fullsteamahead.compat.aeronautics.AeronauticsSteamVentCompat;
import dev.gustavo.fullsteamahead.compat.aeronautics.FullSteamAeronauticsSteamVent;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.steam.SteamPressure;
import dev.gustavo.fullsteamahead.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity", remap = false)
public abstract class SteamVentBlockEntityMixin implements FullSteamAeronauticsSteamVent {
    @Unique
    private static final int fullSteamAhead$BUFFER_CAPACITY = 360;
    @Unique
    private static final int fullSteamAhead$NETWORK_DECAY_TICKS = 3;
    @Unique
    private static final String fullSteamAhead$BUFFER_KEY = "FullSteamAheadSteamBuffer";
    @Unique
    private static final String fullSteamAhead$ACCEPTED_LAST_TICK_KEY = "FullSteamAheadAcceptedLastTick";
    @Unique
    private static final String fullSteamAhead$CONSUMED_LAST_TICK_KEY = "FullSteamAheadConsumedLastTick";
    @Unique
    private static final String fullSteamAhead$PIPE_GAS_OUTPUT_KEY = "FullSteamAheadPipeGasOutput";

    @Shadow
    public int signalStrength;

    @Shadow
    public int rawSignalStrength;

    @Shadow
    protected ScrollValueBehaviour steamAmountBehaviour;

    @Shadow
    public abstract void updateSignal(int signalStrength);

    @Unique
    private final FluidTank fullSteamAhead$steamBuffer =
            new FluidTank(fullSteamAhead$BUFFER_CAPACITY, stack -> stack.is(ModFluids.STEAM.get())) {
                @Override
                protected void onContentsChanged() {
                    fullSteamAhead$self().setChanged();
                }
            };

    @Unique
    private final IFluidHandler fullSteamAhead$inputHandler = new FullSteamAheadSteamVentHandler();

    @Unique
    private int fullSteamAhead$acceptedLastTick;
    @Unique
    private int fullSteamAhead$consumedLastTick;
    @Unique
    private int fullSteamAhead$acceptedThisTick;
    @Unique
    private int fullSteamAhead$consumedThisTick;
    @Unique
    private long fullSteamAhead$acceptedGameTime = Long.MIN_VALUE;
    @Unique
    private int fullSteamAhead$acceptedThisGameTick;
    @Unique
    private double fullSteamAhead$networkPressurePn;
    @Unique
    private int fullSteamAhead$networkDrawCap;
    @Unique
    private long fullSteamAhead$networkGameTime = Long.MIN_VALUE;
    @Unique
    private double fullSteamAhead$pipeGasOutput;

    @Inject(method = "tick", at = @At("HEAD"))
    private void fullSteamAhead$tickPipeFedSteam(CallbackInfo ci) {
        BlockEntity self = fullSteamAhead$self();
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        fullSteamAhead$syncPipeFedSignal();

        if (fullSteamAhead$acceptedLastTick != fullSteamAhead$acceptedThisTick
                || fullSteamAhead$consumedLastTick != fullSteamAhead$consumedThisTick) {
            fullSteamAhead$acceptedLastTick = fullSteamAhead$acceptedThisTick;
            fullSteamAhead$consumedLastTick = fullSteamAhead$consumedThisTick;
            fullSteamAhead$notifyUpdate();
        }
        fullSteamAhead$acceptedThisTick = 0;
        fullSteamAhead$consumedThisTick = 0;

        double previousOutput = fullSteamAhead$pipeGasOutput;
        fullSteamAhead$pipeGasOutput = 0.0D;
        if (fullSteamAhead$isPipeFedSteamVent() && fullSteamAhead$networkFresh()) {
            int requestedSteam = fullSteamAhead$getRequestedSteamMb(fullSteamAhead$networkPressurePn);
            int cappedSteam = Math.min(requestedSteam, Math.max(0, fullSteamAhead$networkDrawCap));
            int consumedSteam = fullSteamAhead$steamBuffer.drain(cappedSteam, IFluidHandler.FluidAction.EXECUTE)
                    .getAmount();
            fullSteamAhead$consumedThisTick += consumedSteam;

            if (requestedSteam > 0 && consumedSteam > 0) {
                double requestedGas = fullSteamAhead$requestedGasOutput(fullSteamAhead$networkPressurePn);
                fullSteamAhead$pipeGasOutput = requestedGas * Math.min(1.0D, consumedSteam / (double) requestedSteam);
            }
        }

        if (Math.abs(previousOutput - fullSteamAhead$pipeGasOutput) > 0.1D
                || (previousOutput > 0.0D) != (fullSteamAhead$pipeGasOutput > 0.0D)) {
            fullSteamAhead$notifyUpdate();
        }
    }

    @Inject(method = "updateRawSignal", at = @At("RETURN"))
    private void fullSteamAhead$syncPipeFedRawSignal(CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = fullSteamAhead$self();
        Level level = self.getLevel();
        if (level != null && !level.isClientSide()) {
            fullSteamAhead$syncPipeFedSignal();
        }
    }

    @Inject(method = "getGasOutput", at = @At("HEAD"), cancellable = true)
    private void fullSteamAhead$usePipeFedGasOutput(CallbackInfoReturnable<Double> cir) {
        if (fullSteamAhead$canPipeOutput()) {
            cir.setReturnValue(fullSteamAhead$pipeGasOutput);
        }
    }

    @Inject(method = "canOutputGas", at = @At("HEAD"), cancellable = true)
    private void fullSteamAhead$allowPipeFedGasOutput(CallbackInfoReturnable<Boolean> cir) {
        if (fullSteamAhead$canPipeOutput()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void fullSteamAhead$writePipeState(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket,
            CallbackInfo ci
    ) {
        tag.put(fullSteamAhead$BUFFER_KEY, fullSteamAhead$steamBuffer.writeToNBT(registries, new CompoundTag()));
        tag.putInt(fullSteamAhead$ACCEPTED_LAST_TICK_KEY, fullSteamAhead$acceptedLastTick);
        tag.putInt(fullSteamAhead$CONSUMED_LAST_TICK_KEY, fullSteamAhead$consumedLastTick);
        tag.putDouble(fullSteamAhead$PIPE_GAS_OUTPUT_KEY, fullSteamAhead$pipeGasOutput);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void fullSteamAhead$readPipeState(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket,
            CallbackInfo ci
    ) {
        if (tag.contains(fullSteamAhead$BUFFER_KEY)) {
            fullSteamAhead$steamBuffer.readFromNBT(registries, tag.getCompound(fullSteamAhead$BUFFER_KEY));
        }
        fullSteamAhead$acceptedLastTick = tag.getInt(fullSteamAhead$ACCEPTED_LAST_TICK_KEY);
        fullSteamAhead$consumedLastTick = tag.getInt(fullSteamAhead$CONSUMED_LAST_TICK_KEY);
        fullSteamAhead$pipeGasOutput = tag.getDouble(fullSteamAhead$PIPE_GAS_OUTPUT_KEY);

        int overflow = fullSteamAhead$steamBuffer.getFluidAmount() - fullSteamAhead$BUFFER_CAPACITY;
        if (overflow > 0) {
            fullSteamAhead$steamBuffer.drain(overflow, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override
    public boolean fullSteamAhead$isPipeFedSteamVent() {
        if (!FullSteamConfig.aeronauticsSteamVentConsumptionEnabled()
                || FullSteamConfig.aeronauticsSteamVentMbPerM3() <= 0.0D) {
            return false;
        }

        BlockEntity self = fullSteamAhead$self();
        Level level = self.getLevel();
        return level != null
                && AeronauticsSteamVentCompat.isSteamVent(self.getBlockState())
                && FluidPropagator.getPipe(level, self.getBlockPos().relative(Direction.DOWN)) != null;
    }

    @Override
    public IFluidHandler fullSteamAhead$getSteamFluidHandler(Direction side) {
        if (!fullSteamAhead$isPipeFedSteamVent() || side == Direction.UP) {
            return null;
        }
        return fullSteamAhead$inputHandler;
    }

    @Override
    public int fullSteamAhead$getStoredSteamMb() {
        return fullSteamAhead$steamBuffer.getFluidAmount();
    }

    @Override
    public int fullSteamAhead$getDisplayConsumedSteamMb() {
        return Math.max(fullSteamAhead$consumedThisTick, fullSteamAhead$consumedLastTick);
    }

    @Override
    public int fullSteamAhead$getRequestedSteamMb(double pressurePn) {
        double gasOutput = fullSteamAhead$requestedGasOutput(pressurePn);
        return AeronauticsSteamVentCompat.steamMbForGasOutput(gasOutput);
    }

    @Override
    public void fullSteamAhead$applyNetworkState(double pressurePn, int drawCap) {
        fullSteamAhead$networkPressurePn = pressurePn;
        fullSteamAhead$networkDrawCap = drawCap;
        Level level = fullSteamAhead$self().getLevel();
        fullSteamAhead$networkGameTime = level == null ? 0L : level.getGameTime();
    }

    @Override
    public int fullSteamAhead$drainSteam(int amount) {
        if (amount <= 0) {
            return 0;
        }
        return fullSteamAhead$steamBuffer.drain(amount, IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    @Unique
    private double fullSteamAhead$requestedGasOutput(double pressurePn) {
        if (!fullSteamAhead$isPipeFedSteamVent() || steamAmountBehaviour == null || signalStrength <= 0) {
            return 0.0D;
        }
        return steamAmountBehaviour.getValue()
                * Math.max(0.0D, signalStrength / 15.0D)
                * SteamPressure.pressureFactor(pressurePn);
    }

    @Unique
    private boolean fullSteamAhead$canPipeOutput() {
        if (!fullSteamAhead$isPipeFedSteamVent()
                || signalStrength <= 0
                || fullSteamAhead$pipeGasOutput <= 0.0D) {
            return false;
        }

        Level level = fullSteamAhead$self().getLevel();
        return level != null && (level.isClientSide() || fullSteamAhead$networkFresh());
    }

    @Unique
    private boolean fullSteamAhead$networkFresh() {
        Level level = fullSteamAhead$self().getLevel();
        return level != null
                && fullSteamAhead$networkGameTime != Long.MIN_VALUE
                && level.getGameTime() - fullSteamAhead$networkGameTime <= fullSteamAhead$NETWORK_DECAY_TICKS;
    }

    @Unique
    private void fullSteamAhead$syncPipeFedSignal() {
        if (fullSteamAhead$isPipeFedSteamVent() && signalStrength != rawSignalStrength) {
            updateSignal(rawSignalStrength);
        }
    }

    @Unique
    private BlockEntity fullSteamAhead$self() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private void fullSteamAhead$notifyUpdate() {
        ((com.simibubi.create.foundation.blockEntity.SmartBlockEntity) (Object) this).notifyUpdate();
    }

    private class FullSteamAheadSteamVentHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return fullSteamAhead$steamBuffer.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fullSteamAhead$steamBuffer.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return fullSteamAhead$steamBuffer.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(ModFluids.STEAM.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!fullSteamAhead$isPipeFedSteamVent() || !resource.is(ModFluids.STEAM.get())) {
                return 0;
            }

            int allowance = fullSteamAhead$acceptedSteamAllowance();
            if (allowance <= 0) {
                return 0;
            }

            int fillAmount = Math.min(resource.getAmount(), allowance);
            int filled = fullSteamAhead$steamBuffer.fill(new FluidStack(ModFluids.STEAM.get(), fillAmount), action);
            if (!action.simulate() && filled > 0) {
                fullSteamAhead$acceptedThisTick += filled;
                fullSteamAhead$acceptedThisGameTick += filled;
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

        private int fullSteamAhead$acceptedSteamAllowance() {
            if (!fullSteamAhead$networkFresh()) {
                return 0;
            }

            int maxPerTick = Math.max(0, fullSteamAhead$networkDrawCap);
            Level level = fullSteamAhead$self().getLevel();
            if (level == null) {
                return maxPerTick;
            }

            long gameTime = level.getGameTime();
            if (fullSteamAhead$acceptedGameTime != gameTime) {
                fullSteamAhead$acceptedGameTime = gameTime;
                fullSteamAhead$acceptedThisGameTick = 0;
            }
            return Math.max(0, maxPerTick - fullSteamAhead$acceptedThisGameTick);
        }
    }
}
