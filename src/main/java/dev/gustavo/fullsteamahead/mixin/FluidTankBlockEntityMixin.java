package dev.gustavo.fullsteamahead.mixin;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.compat.create.BoilerSteamPort;
import dev.gustavo.fullsteamahead.compat.create.FullSteamBoilerIntegration;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.steam.BoilerBurst;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerPortState;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerPipeTransfer;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerSteamHandler;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerSteamBudget;
import dev.gustavo.fullsteamahead.content.steam.FullSteamDirectBoilerSource;
import dev.gustavo.fullsteamahead.content.steam.SteamNetworkManager;
import dev.gustavo.fullsteamahead.content.steam.SteamPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(FluidTankBlockEntity.class)
public abstract class FluidTankBlockEntityMixin implements FullSteamDirectBoilerSource {
    @Unique
    private static final String fullSteamAhead$DIRECT_PORTS_KEY = "FullSteamAheadDirectSteamPorts";
    @Unique
    private static final String fullSteamAhead$PORT_POS_KEY = "PortPos";
    @Unique
    private static final String fullSteamAhead$PORT_DIRECTION_KEY = "PortDirection";
    @Unique
    private static final String fullSteamAhead$PORT_STEAM_KEY = "StoredSteam";

    @Shadow
    public BoilerData boiler;

    @Shadow
    public abstract boolean isController();

    @Shadow
    public abstract FluidTankBlockEntity getControllerBE();

    @Shadow
    public abstract int getTotalTankSize();

    @Shadow
    public abstract int getHeight();

    @Shadow
    public abstract void updateBoilerTemperature();

    @Shadow
    public abstract void updateBoilerState();

    @Unique
    private final Map<BoilerSteamPort, DirectBoilerPortState> fullSteamAhead$directPortStates = new HashMap<>();
    @Unique
    private int fullSteamAhead$networkProductionRate;
    @Unique
    private int fullSteamAhead$networkConsumedRate;
    @Unique
    private int fullSteamAhead$networkVolume;
    @Unique
    private int fullSteamAhead$networkEngines;
    @Unique
    private double fullSteamAhead$networkPressurePn;
    @Unique
    private boolean fullSteamAhead$networkVenting;
    @Unique
    private boolean fullSteamAhead$networkWarn;
    @Unique
    private long fullSteamAhead$lastNetworkGameTime = Long.MIN_VALUE;

    @Inject(method = "tick", at = @At("TAIL"))
    private void fullSteamAhead$tickDirectSteamSource(CallbackInfo ci) {
        FluidTankBlockEntity self = fullSteamAhead$self();
        Level level = self.getLevel();
        if (level == null || level.isClientSide() || !self.isController()) {
            return;
        }

        if (!FullSteamConfig.directBoilerPipeOutputEnabled()) {
            fullSteamAhead$directPortStates.clear();
            return;
        }

        SteamNetworkManager.registerDirectBoiler(level, self.getBlockPos());
        self.updateBoilerState();

        List<BoilerSteamPort> ports = FullSteamBoilerIntegration.attachedDirectPipePorts(self);
        Set<BoilerSteamPort> activePorts = new HashSet<>(ports);
        fullSteamAhead$directPortStates.keySet().removeIf(port -> !activePorts.contains(port));
        if (ports.isEmpty()) {
            return;
        }

        DirectBoilerSteamBudget budget = fullSteamAhead$calculateSteamBudget(self);
        for (BoilerSteamPort port : ports) {
            DirectBoilerPortState state = fullSteamAhead$directPortStates.computeIfAbsent(port, ignored -> new DirectBoilerPortState());
            int previousStored = state.storedMb;
            int previousProduction = state.productionMb;
            boolean previousVenting = state.venting;

            state.productionMb = FullSteamBoilerIntegration.amountForPort(self, port, budget.totalProductionMb());
            state.totalProductionMb = budget.totalProductionMb();
            state.portCount = budget.portCount();
            state.boilerVolume = budget.boilerVolume();
            state.temperatureK = budget.temperatureK();
            state.lit = budget.lit();

            if (state.productionMb > 0) {
                int room = Math.max(0, FullSteamConfig.steamBufferCapMb() - state.storedMb);
                state.storedMb += Math.min(state.productionMb, room);
            }

            if (state.storedMb > 0) {
                DirectBoilerPipeTransfer.Result result = DirectBoilerPipeTransfer.push(level, this, port, state.storedMb);
                state.pushedMb = state.externallyDrainedSteam + result.moved();
                state.venting = result.venting();
            } else {
                state.pushedMb = 0;
                state.venting = false;
            }
            state.externallyDrainedSteam = 0;

            if (previousStored != state.storedMb
                    || previousProduction != state.productionMb
                    || previousVenting != state.venting) {
                self.setChanged();
            }
        }
    }

    @Inject(method = "lambda$registerCapabilities$0", at = @At("RETURN"), cancellable = true)
    private static void fullSteamAhead$wrapFluidTankCapability(
            FluidTankBlockEntity tank,
            Direction side,
            CallbackInfoReturnable<IFluidHandler> cir
    ) {
        if (tank instanceof FullSteamDirectBoilerSource source) {
            cir.setReturnValue(source.fullSteamAhead$getDirectSteamFluidHandler(
                    tank.getBlockPos(),
                    side,
                    cir.getReturnValue()
            ));
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void fullSteamAhead$writeDirectSteam(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket,
            CallbackInfo ci
    ) {
        if (!fullSteamAhead$self().isController() || fullSteamAhead$directPortStates.isEmpty()) {
            return;
        }

        ListTag ports = new ListTag();
        for (Map.Entry<BoilerSteamPort, DirectBoilerPortState> entry : fullSteamAhead$directPortStates.entrySet()) {
            DirectBoilerPortState state = entry.getValue();
            if (state.storedMb <= 0) {
                continue;
            }

            CompoundTag portTag = new CompoundTag();
            portTag.putLong(fullSteamAhead$PORT_POS_KEY, entry.getKey().pos().asLong());
            portTag.putInt(fullSteamAhead$PORT_DIRECTION_KEY, entry.getKey().direction().ordinal());
            portTag.putInt(fullSteamAhead$PORT_STEAM_KEY, state.storedMb);
            ports.add(portTag);
        }
        if (!ports.isEmpty()) {
            tag.put(fullSteamAhead$DIRECT_PORTS_KEY, ports);
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void fullSteamAhead$readDirectSteam(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket,
            CallbackInfo ci
    ) {
        fullSteamAhead$directPortStates.clear();
        if (!tag.contains(fullSteamAhead$DIRECT_PORTS_KEY)) {
            return;
        }

        ListTag ports = tag.getList(fullSteamAhead$DIRECT_PORTS_KEY, Tag.TAG_COMPOUND);
        Direction[] directions = Direction.values();
        for (int i = 0; i < ports.size(); i++) {
            CompoundTag portTag = ports.getCompound(i);
            int directionIndex = portTag.getInt(fullSteamAhead$PORT_DIRECTION_KEY);
            if (directionIndex < 0 || directionIndex >= directions.length) {
                continue;
            }

            BoilerSteamPort port = BoilerSteamPort.directPipe(
                    BlockPos.of(portTag.getLong(fullSteamAhead$PORT_POS_KEY)),
                    directions[directionIndex]
            );
            DirectBoilerPortState state = new DirectBoilerPortState();
            state.storedMb = Math.min(FullSteamConfig.steamBufferCapMb(), Math.max(0, portTag.getInt(fullSteamAhead$PORT_STEAM_KEY)));
            fullSteamAhead$directPortStates.put(port, state);
        }
    }

    @Override
    public List<BoilerSteamPort> fullSteamAhead$getActiveDirectSteamPorts() {
        FluidTankBlockEntity self = fullSteamAhead$self();
        if (self.getLevel() == null || !self.isController() || !FullSteamConfig.directBoilerPipeOutputEnabled()) {
            return List.of();
        }
        return FullSteamBoilerIntegration.attachedDirectPipePorts(self);
    }

    @Override
    public IFluidHandler fullSteamAhead$getDirectSteamFluidHandler(BlockPos tankPos, Direction side, IFluidHandler fallback) {
        if (side == null || !FullSteamConfig.directBoilerPipeOutputEnabled()) {
            return fallback;
        }

        FluidTankBlockEntity self = fullSteamAhead$self();
        FluidTankBlockEntity controller = self.isController() ? self : self.getControllerBE();
        if (!(controller instanceof FullSteamDirectBoilerSource source) || controller.getLevel() == null) {
            return fallback;
        }

        BoilerSteamPort port = FullSteamBoilerIntegration.directPipePortAt(controller.getLevel(), tankPos, side);
        if (port == null || !controller.getBlockPos().equals(FullSteamBoilerIntegration.resolveDirectPortController(controller.getLevel(), port))) {
            return fallback;
        }
        return new DirectBoilerSteamHandler(source, port, fallback);
    }

    @Override
    public int fullSteamAhead$getDirectStoredSteamMb(BoilerSteamPort port) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.get(port);
        return state == null ? 0 : state.storedMb;
    }

    @Override
    public int fullSteamAhead$getDirectProductionMb(BoilerSteamPort port) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.get(port);
        return state == null ? 0 : state.productionMb;
    }

    @Override
    public int fullSteamAhead$getDirectBoilerVolume(BoilerSteamPort port) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.get(port);
        return state == null ? Math.max(1, fullSteamAhead$self().getTotalTankSize()) : state.boilerVolume;
    }

    @Override
    public double fullSteamAhead$getDirectTemperatureK(BoilerSteamPort port) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.get(port);
        return state == null ? FullSteamConfig.steamTemperatureBaseK() : state.temperatureK;
    }

    @Override
    public double fullSteamAhead$getDirectNetworkPressurePn(BoilerSteamPort port) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.get(port);
        return state == null ? 0.0D : state.pressurePn;
    }

    @Override
    public int fullSteamAhead$drainDirectSteam(BoilerSteamPort port, int amount, boolean externallyDrained) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.get(port);
        if (state == null || amount <= 0 || state.storedMb <= 0) {
            return 0;
        }

        int drained = Math.min(amount, state.storedMb);
        state.storedMb -= drained;
        if (externallyDrained) {
            state.externallyDrainedSteam += drained;
        }
        fullSteamAhead$self().setChanged();
        return drained;
    }

    @Override
    public void fullSteamAhead$applyDirectNetworkState(
            BoilerSteamPort port,
            double pressurePn,
            boolean venting,
            boolean warn,
            int production,
            int networkVolume,
            int engines,
            int consumed
    ) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.computeIfAbsent(port, ignored -> new DirectBoilerPortState());
        state.pressurePn = pressurePn;
        state.networkVenting = venting;
        state.networkWarn = warn;
        state.networkProductionMb = production;
        state.networkVolume = networkVolume;
        state.networkEngines = engines;
        state.networkConsumedMb = consumed;

        Level level = fullSteamAhead$self().getLevel();
        long gameTime = level == null ? 0L : level.getGameTime();
        if (fullSteamAhead$lastNetworkGameTime != gameTime) {
            fullSteamAhead$resetAggregateReadout();
            fullSteamAhead$lastNetworkGameTime = gameTime;
        }

        fullSteamAhead$networkPressurePn = Math.max(fullSteamAhead$networkPressurePn, pressurePn);
        fullSteamAhead$networkVenting |= venting;
        fullSteamAhead$networkWarn |= warn;
        fullSteamAhead$networkProductionRate += production;
        fullSteamAhead$networkConsumedRate += consumed;
        fullSteamAhead$networkVolume = Math.max(fullSteamAhead$networkVolume, networkVolume);
        fullSteamAhead$networkEngines = Math.max(fullSteamAhead$networkEngines, engines);
    }

    @Override
    public void fullSteamAhead$clearDirectEffectivePressure() {
        for (DirectBoilerPortState state : fullSteamAhead$directPortStates.values()) {
            state.pressurePn = 0.0D;
        }
        fullSteamAhead$networkPressurePn = 0.0D;
    }

    @Override
    public void fullSteamAhead$burstDirectBoiler(double networkVolumeM3, double pressurePn) {
        FluidTankBlockEntity self = fullSteamAhead$self();
        if (self.getLevel() instanceof ServerLevel serverLevel) {
            BoilerBurst.explode(serverLevel, self, networkVolumeM3, pressurePn);
        }
    }

    @Override
    public double getNetworkPressurePn() {
        fullSteamAhead$expireAggregateReadout();
        return fullSteamAhead$networkPressurePn;
    }

    @Override
    public int getNetworkProductionRate() {
        fullSteamAhead$expireAggregateReadout();
        return fullSteamAhead$networkProductionRate;
    }

    @Override
    public int getNetworkConsumedRate() {
        fullSteamAhead$expireAggregateReadout();
        return fullSteamAhead$networkConsumedRate;
    }

    @Override
    public int getNetworkVolume() {
        fullSteamAhead$expireAggregateReadout();
        return fullSteamAhead$networkVolume;
    }

    @Override
    public int getNetworkEngineCount() {
        fullSteamAhead$expireAggregateReadout();
        return fullSteamAhead$networkEngines;
    }

    @Override
    public String getSteamNetworkStatusKey() {
        fullSteamAhead$expireAggregateReadout();
        if (!FullSteamConfig.directBoilerPipeOutputEnabled() || fullSteamAhead$directPortStates.isEmpty()) {
            return "no_network";
        }
        boolean lit = fullSteamAhead$directPortStates.values().stream().anyMatch(state -> state.lit && state.productionMb > 0);
        if (!lit) {
            return "no_heat_water";
        }
        if (fullSteamAhead$networkPressurePn >= FullSteamConfig.steamBurstPressure()) {
            return "burst_risk";
        }
        if (fullSteamAhead$networkWarn) {
            return "overpressure";
        }
        if (fullSteamAhead$networkVenting) {
            return "venting";
        }
        if (fullSteamAhead$networkPressurePn < FullSteamConfig.steamRatedPressure()) {
            return "low_pressure";
        }
        return "stable";
    }

    @Unique
    private DirectBoilerSteamBudget fullSteamAhead$calculateSteamBudget(FluidTankBlockEntity boilerController) {
        int boilerVolume = Math.max(1, boilerController.getTotalTankSize());
        int temperatureK = (int) Math.round(FullSteamConfig.steamTemperatureBaseK());
        if (boilerController.boiler == null) {
            return new DirectBoilerSteamBudget(0, 0, boilerVolume, temperatureK, false);
        }

        BoilerData data = boilerController.boiler;
        boilerController.updateBoilerTemperature();
        if (data.needsHeatLevelUpdate) {
            data.updateTemperature(boilerController);
        }

        int targetHeat = FullSteamBoilerIntegration.usableHeatUnits(boilerController);
        boolean lit = targetHeat > 0;
        boolean dry = data.getMaxHeatLevelForWaterSupply() <= 0;
        double effectiveHeat = SteamNetworkManager.effectiveBoilerHeat(
                boilerController.getLevel(),
                boilerController.getBlockPos(),
                targetHeat,
                dry
        );
        temperatureK = (int) Math.round(SteamPhysics.temperatureK(effectiveHeat));
        int portCount = FullSteamBoilerIntegration.countAttachedSteamPorts(boilerController);
        if (!lit && effectiveHeat <= 0.0D) {
            return new DirectBoilerSteamBudget(0, portCount, boilerVolume, temperatureK, false);
        }

        int totalProductionMb = SteamPhysics.productionMb(effectiveHeat, Math.max(1, boilerController.getHeight()));
        return new DirectBoilerSteamBudget(totalProductionMb, portCount, boilerVolume, temperatureK, lit);
    }

    @Unique
    private void fullSteamAhead$expireAggregateReadout() {
        Level level = fullSteamAhead$self().getLevel();
        if (level == null || fullSteamAhead$lastNetworkGameTime == level.getGameTime()) {
            return;
        }
        fullSteamAhead$resetAggregateReadout();
    }

    @Unique
    private void fullSteamAhead$resetAggregateReadout() {
        fullSteamAhead$networkProductionRate = 0;
        fullSteamAhead$networkConsumedRate = 0;
        fullSteamAhead$networkVolume = 0;
        fullSteamAhead$networkEngines = 0;
        fullSteamAhead$networkPressurePn = 0.0D;
        fullSteamAhead$networkVenting = false;
        fullSteamAhead$networkWarn = false;
    }

    @Unique
    private FluidTankBlockEntity fullSteamAhead$self() {
        return (FluidTankBlockEntity) (Object) this;
    }

}
