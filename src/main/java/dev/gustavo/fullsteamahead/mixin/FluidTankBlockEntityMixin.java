package dev.gustavo.fullsteamahead.mixin;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.compat.create.BoilerSteamPort;
import dev.gustavo.fullsteamahead.compat.create.FullSteamBoilerIntegration;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.steam.BoilerBurst;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerPortState;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerPipeTransfer;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerSteamHandler;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerSteamBudget;
import dev.gustavo.fullsteamahead.content.steam.DirectBoilerVesselState;
import dev.gustavo.fullsteamahead.content.steam.FullSteamDirectBoilerSource;
import dev.gustavo.fullsteamahead.content.steam.SteamNetworkManager;
import dev.gustavo.fullsteamahead.content.steam.SteamPressure;
import dev.gustavo.fullsteamahead.content.steam.SteamPhysics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
    @Unique
    private static final String fullSteamAhead$BOILER_VESSEL_KEY = "FullSteamAheadBoilerSteam";
    @Unique
    private static final String fullSteamAhead$VESSEL_STORED_KEY = "StoredSteam";
    @Unique
    private static final String fullSteamAhead$VESSEL_PRODUCTION_KEY = "Production";
    @Unique
    private static final String fullSteamAhead$VESSEL_TOTAL_PRODUCTION_KEY = "TotalProduction";
    @Unique
    private static final String fullSteamAhead$VESSEL_PORT_COUNT_KEY = "PortCount";
    @Unique
    private static final String fullSteamAhead$VESSEL_VOLUME_KEY = "BoilerVolume";
    @Unique
    private static final String fullSteamAhead$VESSEL_TEMPERATURE_KEY = "TemperatureK";
    @Unique
    private static final String fullSteamAhead$VESSEL_LIT_KEY = "Lit";

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
    private final DirectBoilerVesselState fullSteamAhead$boilerVesselState = new DirectBoilerVesselState();
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
            fullSteamAhead$clearBoilerVessel();
            return;
        }

        SteamNetworkManager.registerDirectBoiler(level, self.getBlockPos());
        self.updateBoilerState();

        List<BoilerSteamPort> ports = FullSteamBoilerIntegration.attachedDirectPipePorts(self);
        Set<BoilerSteamPort> activePorts = new HashSet<>(ports);
        fullSteamAhead$directPortStates.keySet().removeIf(port -> !activePorts.contains(port));

        DirectBoilerSteamBudget budget = fullSteamAhead$calculateSteamBudget(self);
        int previousStored = fullSteamAhead$boilerVesselState.storedMb;
        int previousProduction = fullSteamAhead$boilerVesselState.productionMb;
        int previousTotalProduction = fullSteamAhead$boilerVesselState.totalProductionMb;
        int previousPortCount = fullSteamAhead$boilerVesselState.portCount;
        boolean previousLit = fullSteamAhead$boilerVesselState.lit;

        int totalPortCount = budget.portCount();
        int directProductionMb = 0;
        if (totalPortCount <= 0) {
            directProductionMb = budget.totalProductionMb();
        } else {
            for (BoilerSteamPort port : ports) {
                directProductionMb += FullSteamBoilerIntegration.amountForPort(self, port, budget.totalProductionMb());
            }
        }
        if (totalPortCount > 0 && ports.isEmpty()) {
            // A physical boiler_outlet owns its own legacy port buffer. Keep the controller vessel
            // from retaining hidden pressure while that explicit outlet is the only steam port.
            fullSteamAhead$boilerVesselState.storedMb = 0;
        }

        fullSteamAhead$boilerVesselState.productionMb = directProductionMb;
        fullSteamAhead$boilerVesselState.totalProductionMb = budget.totalProductionMb();
        fullSteamAhead$boilerVesselState.portCount = totalPortCount;
        fullSteamAhead$boilerVesselState.boilerVolume = budget.boilerVolume();
        fullSteamAhead$boilerVesselState.temperatureK = budget.temperatureK();
        fullSteamAhead$boilerVesselState.lit = budget.lit();

        if (directProductionMb > 0) {
            int room = Math.max(0, FullSteamConfig.steamBufferCapMb() - fullSteamAhead$boilerVesselState.storedMb);
            fullSteamAhead$boilerVesselState.storedMb += Math.min(directProductionMb, room);
        }

        for (BoilerSteamPort port : ports) {
            DirectBoilerPortState state = fullSteamAhead$directPortStates.computeIfAbsent(port, ignored -> new DirectBoilerPortState());
            int previousPortStored = state.storedMb;
            int previousPortProduction = state.productionMb;
            boolean previousPortVenting = state.venting;

            state.productionMb = FullSteamBoilerIntegration.amountForPort(self, port, budget.totalProductionMb());
            state.totalProductionMb = budget.totalProductionMb();
            state.portCount = budget.portCount();
            state.boilerVolume = budget.boilerVolume();
            state.temperatureK = budget.temperatureK();
            state.lit = budget.lit();
            state.storedMb = fullSteamAhead$getDirectStoredSteamMb(port);

            if (state.storedMb > 0) {
                DirectBoilerPipeTransfer.Result result = DirectBoilerPipeTransfer.push(level, this, port, state.storedMb);
                state.pushedMb = state.externallyDrainedSteam + result.moved();
                state.venting = result.venting();
            } else {
                state.pushedMb = 0;
                state.venting = false;
            }
            state.externallyDrainedSteam = 0;

            state.storedMb = fullSteamAhead$getDirectStoredSteamMb(port);

            if (previousPortStored != state.storedMb
                    || previousPortProduction != state.productionMb
                    || previousPortVenting != state.venting) {
                self.setChanged();
            }
        }
        if (previousStored != fullSteamAhead$boilerVesselState.storedMb
                || previousProduction != fullSteamAhead$boilerVesselState.productionMb
                || previousTotalProduction != fullSteamAhead$boilerVesselState.totalProductionMb
                || previousPortCount != fullSteamAhead$boilerVesselState.portCount
                || previousLit != fullSteamAhead$boilerVesselState.lit) {
            self.setChanged();
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
        if (!fullSteamAhead$self().isController()) {
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
        if (fullSteamAhead$boilerVesselState.storedMb > 0
                || fullSteamAhead$boilerVesselState.productionMb > 0
                || fullSteamAhead$boilerVesselState.totalProductionMb > 0
                || fullSteamAhead$boilerVesselState.pressurePn > 0) {
            CompoundTag vesselTag = new CompoundTag();
            vesselTag.putInt(fullSteamAhead$VESSEL_STORED_KEY, fullSteamAhead$boilerVesselState.storedMb);
            vesselTag.putInt(fullSteamAhead$VESSEL_PRODUCTION_KEY, fullSteamAhead$boilerVesselState.productionMb);
            vesselTag.putInt(fullSteamAhead$VESSEL_TOTAL_PRODUCTION_KEY, fullSteamAhead$boilerVesselState.totalProductionMb);
            vesselTag.putInt(fullSteamAhead$VESSEL_PORT_COUNT_KEY, fullSteamAhead$boilerVesselState.portCount);
            vesselTag.putInt(fullSteamAhead$VESSEL_VOLUME_KEY, fullSteamAhead$boilerVesselState.boilerVolume);
            vesselTag.putInt(fullSteamAhead$VESSEL_TEMPERATURE_KEY, fullSteamAhead$boilerVesselState.temperatureK);
            vesselTag.putBoolean(fullSteamAhead$VESSEL_LIT_KEY, fullSteamAhead$boilerVesselState.lit);
            tag.put(fullSteamAhead$BOILER_VESSEL_KEY, vesselTag);
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
        fullSteamAhead$clearBoilerVessel();
        int migratedPortSteam = 0;
        if (!tag.contains(fullSteamAhead$DIRECT_PORTS_KEY)) {
            migratedPortSteam = 0;
        } else {
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
                migratedPortSteam += state.storedMb;
                fullSteamAhead$directPortStates.put(port, state);
            }
        }

        if (tag.contains(fullSteamAhead$BOILER_VESSEL_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag vesselTag = tag.getCompound(fullSteamAhead$BOILER_VESSEL_KEY);
            fullSteamAhead$boilerVesselState.storedMb = Math.min(
                    FullSteamConfig.steamBufferCapMb(),
                    Math.max(0, vesselTag.getInt(fullSteamAhead$VESSEL_STORED_KEY))
            );
            fullSteamAhead$boilerVesselState.productionMb = Math.max(0, vesselTag.getInt(fullSteamAhead$VESSEL_PRODUCTION_KEY));
            fullSteamAhead$boilerVesselState.totalProductionMb = Math.max(0, vesselTag.getInt(fullSteamAhead$VESSEL_TOTAL_PRODUCTION_KEY));
            fullSteamAhead$boilerVesselState.portCount = Math.max(0, vesselTag.getInt(fullSteamAhead$VESSEL_PORT_COUNT_KEY));
            fullSteamAhead$boilerVesselState.boilerVolume = Math.max(1, vesselTag.getInt(fullSteamAhead$VESSEL_VOLUME_KEY));
            fullSteamAhead$boilerVesselState.temperatureK = Math.max(1, vesselTag.getInt(fullSteamAhead$VESSEL_TEMPERATURE_KEY));
            fullSteamAhead$boilerVesselState.lit = vesselTag.getBoolean(fullSteamAhead$VESSEL_LIT_KEY);
        } else if (migratedPortSteam > 0) {
            fullSteamAhead$boilerVesselState.storedMb = Math.min(FullSteamConfig.steamBufferCapMb(), migratedPortSteam);
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
        return fullSteamAhead$amountForDirectPortVessel(port, fullSteamAhead$boilerVesselState.storedMb);
    }

    @Override
    public int fullSteamAhead$getDirectProductionMb(BoilerSteamPort port) {
        return fullSteamAhead$amountForPort(port, fullSteamAhead$boilerVesselState.totalProductionMb);
    }

    @Override
    public int fullSteamAhead$getDirectBoilerVolume(BoilerSteamPort port) {
        return Math.max(1, fullSteamAhead$boilerVesselState.boilerVolume);
    }

    @Override
    public double fullSteamAhead$getDirectTemperatureK(BoilerSteamPort port) {
        return fullSteamAhead$boilerVesselState.temperatureK;
    }

    @Override
    public double fullSteamAhead$getDirectNetworkPressurePn(BoilerSteamPort port) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.get(port);
        return state == null ? 0.0D : state.pressurePn;
    }

    @Override
    public int fullSteamAhead$drainDirectSteam(BoilerSteamPort port, int amount, boolean externallyDrained) {
        DirectBoilerPortState state = fullSteamAhead$directPortStates.get(port);
        int available = fullSteamAhead$getDirectStoredSteamMb(port);
        if (state == null || amount <= 0 || available <= 0 || fullSteamAhead$boilerVesselState.storedMb <= 0) {
            return 0;
        }

        int drained = Math.min(amount, available);
        fullSteamAhead$boilerVesselState.storedMb -= drained;
        state.storedMb = fullSteamAhead$getDirectStoredSteamMb(port);
        if (externallyDrained) {
            state.externallyDrainedSteam += drained;
        }
        fullSteamAhead$self().setChanged();
        return drained;
    }

    @Override
    public int fullSteamAhead$getBoilerStoredSteamMb() {
        return fullSteamAhead$boilerVesselState.storedMb;
    }

    @Override
    public int fullSteamAhead$getBoilerProductionMb() {
        return fullSteamAhead$boilerVesselState.productionMb;
    }

    @Override
    public int fullSteamAhead$getBoilerVolume() {
        return Math.max(1, fullSteamAhead$boilerVesselState.boilerVolume);
    }

    @Override
    public double fullSteamAhead$getBoilerTemperatureK() {
        return fullSteamAhead$boilerVesselState.temperatureK;
    }

    @Override
    public double fullSteamAhead$getBoilerNetworkPressurePn() {
        return fullSteamAhead$boilerVesselState.pressurePn;
    }

    @Override
    public int fullSteamAhead$drainBoilerSteam(int amount) {
        if (amount <= 0 || fullSteamAhead$boilerVesselState.storedMb <= 0) {
            return 0;
        }
        int drained = Math.min(amount, fullSteamAhead$boilerVesselState.storedMb);
        fullSteamAhead$boilerVesselState.storedMb -= drained;
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
    }

    @Override
    public void fullSteamAhead$applyBoilerNetworkState(
            double pressurePn,
            boolean venting,
            boolean warn,
            int production,
            int networkVolume,
            int engines,
            int consumed
    ) {
        fullSteamAhead$boilerVesselState.pressurePn = pressurePn;
        fullSteamAhead$boilerVesselState.networkVenting = venting;
        fullSteamAhead$boilerVesselState.networkWarn = warn;
        fullSteamAhead$boilerVesselState.networkProductionMb = production;
        fullSteamAhead$boilerVesselState.networkVolume = networkVolume;
        fullSteamAhead$boilerVesselState.networkEngines = engines;
        fullSteamAhead$boilerVesselState.networkConsumedMb = consumed;

        fullSteamAhead$recordAggregateReadout(pressurePn, venting, warn, production, networkVolume, engines, consumed);
    }

    @Override
    public void fullSteamAhead$clearDirectEffectivePressure() {
        for (DirectBoilerPortState state : fullSteamAhead$directPortStates.values()) {
            state.pressurePn = 0.0D;
        }
        fullSteamAhead$boilerVesselState.pressurePn = 0.0D;
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
        return fullSteamAhead$networkProductionRate > 0
                ? fullSteamAhead$networkProductionRate
                : fullSteamAhead$boilerVesselState.totalProductionMb;
    }

    @Override
    public int getNetworkConsumedRate() {
        fullSteamAhead$expireAggregateReadout();
        return fullSteamAhead$networkConsumedRate;
    }

    @Override
    public int getNetworkVolume() {
        fullSteamAhead$expireAggregateReadout();
        return fullSteamAhead$networkVolume > 0
                ? fullSteamAhead$networkVolume
                : fullSteamAhead$boilerVesselState.boilerVolume;
    }

    @Override
    public int getNetworkEngineCount() {
        fullSteamAhead$expireAggregateReadout();
        return fullSteamAhead$networkEngines;
    }

    @Override
    public String getSteamNetworkStatusKey() {
        fullSteamAhead$expireAggregateReadout();
        if (!FullSteamConfig.directBoilerPipeOutputEnabled()) {
            return "no_network";
        }
        if (!fullSteamAhead$boilerVesselState.lit
                && fullSteamAhead$boilerVesselState.productionMb <= 0
                && fullSteamAhead$boilerVesselState.totalProductionMb <= 0
                && fullSteamAhead$boilerVesselState.storedMb <= 0) {
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

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"))
    private void fullSteamAhead$addBoilerPressureTooltip(
            List<Component> tooltip,
            boolean isPlayerSneaking,
            CallbackInfoReturnable<Boolean> cir
    ) {
        FluidTankBlockEntity self = fullSteamAhead$self();
        FluidTankBlockEntity controller = self.isController() ? self : self.getControllerBE();
        if (!(controller instanceof FullSteamDirectBoilerSource source)
                || !FullSteamConfig.directBoilerPipeOutputEnabled()
                || controller.boiler == null) {
            return;
        }

        double pressure = source.getNetworkPressurePn();
        int production = source.getNetworkProductionRate();
        int stored = source.fullSteamAhead$getBoilerStoredSteamMb();
        int volume = source.getNetworkVolume();
        CreateLang.text("Full Steam Ahead").style(ChatFormatting.GRAY).forGoggles(tooltip);
        CreateLang.text("Pressure: " + SteamPressure.format(pressure))
                .style(pressure >= FullSteamConfig.steamWarnPressure()
                        ? ChatFormatting.RED
                        : pressure > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Steam: " + stored + "/" + FullSteamConfig.steamBufferCapMb() + " mB")
                .style(stored > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Production: " + production + " mB/t")
                .style(production > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);
        CreateLang.text("Status: ")
                .add(Component.translatable("full_steam_ahead.display_source.steam_network.status."
                        + source.getSteamNetworkStatusKey()))
                .style(source.getNetworkPressurePn() >= FullSteamConfig.steamWarnPressure()
                        ? ChatFormatting.RED : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        if (isPlayerSneaking) {
            CreateLang.text("Volume: " + volume + " m³; Engines: " + source.getNetworkEngineCount())
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }
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
    private void fullSteamAhead$recordAggregateReadout(
            double pressurePn,
            boolean venting,
            boolean warn,
            int production,
            int networkVolume,
            int engines,
            int consumed
    ) {
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
        fullSteamAhead$networkVolume += networkVolume;
        fullSteamAhead$networkEngines += engines;
    }

    @Unique
    private int fullSteamAhead$amountForPort(BoilerSteamPort port, int totalAmount) {
        if (totalAmount <= 0) {
            return 0;
        }
        return FullSteamBoilerIntegration.amountForPort(fullSteamAhead$self(), port, totalAmount);
    }

    @Unique
    private int fullSteamAhead$amountForDirectPortVessel(BoilerSteamPort port, int totalAmount) {
        if (totalAmount <= 0) {
            return 0;
        }
        List<BoilerSteamPort> ports = FullSteamBoilerIntegration.attachedDirectPipePorts(fullSteamAhead$self());
        int index = ports.indexOf(port);
        if (index < 0 || ports.isEmpty()) {
            return 0;
        }
        int baseShare = totalAmount / ports.size();
        int remainder = totalAmount % ports.size();
        return baseShare + (index < remainder ? 1 : 0);
    }

    @Unique
    private void fullSteamAhead$clearBoilerVessel() {
        fullSteamAhead$boilerVesselState.storedMb = 0;
        fullSteamAhead$boilerVesselState.productionMb = 0;
        fullSteamAhead$boilerVesselState.totalProductionMb = 0;
        fullSteamAhead$boilerVesselState.portCount = 0;
        fullSteamAhead$boilerVesselState.boilerVolume = 1;
        fullSteamAhead$boilerVesselState.temperatureK = (int) Math.round(FullSteamConfig.steamTemperatureBaseK());
        fullSteamAhead$boilerVesselState.lit = false;
        fullSteamAhead$boilerVesselState.pressurePn = 0.0D;
        fullSteamAhead$boilerVesselState.networkVenting = false;
        fullSteamAhead$boilerVesselState.networkWarn = false;
        fullSteamAhead$boilerVesselState.networkProductionMb = 0;
        fullSteamAhead$boilerVesselState.networkVolume = 0;
        fullSteamAhead$boilerVesselState.networkEngines = 0;
        fullSteamAhead$boilerVesselState.networkConsumedMb = 0;
    }

    @Unique
    private FluidTankBlockEntity fullSteamAhead$self() {
        return (FluidTankBlockEntity) (Object) this;
    }

}
