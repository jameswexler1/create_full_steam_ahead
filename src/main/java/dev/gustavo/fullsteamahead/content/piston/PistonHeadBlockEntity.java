package dev.gustavo.fullsteamahead.content.piston;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlock;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import dev.gustavo.fullsteamahead.compat.create.FullSteamBoilerIntegration;
import dev.gustavo.fullsteamahead.content.steam.SteamCloudEffects;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.SteamPhysics;
import dev.gustavo.fullsteamahead.content.steam.SteamPressure;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class PistonHeadBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String ASSEMBLED_KEY = "Assembled";
    private static final String RING_ORIGIN_KEY = "RingOrigin";
    private static final String CYLINDER_ROOT_KEY = "CylinderRoot";
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String INLET_POS_KEY = "InletPos";
    private static final String SHAFT_POS_KEY = "ShaftPos";
    private static final String PISTON_BODY_COUNT_KEY = "PistonBodyCount";
    private static final String SHAFT_GAP_KEY = "ShaftGap";
    private static final String ACTIVE_BURNERS_KEY = "ActiveBurners";
    private static final String HEAT_UNITS_KEY = "HeatUnits";
    private static final String GENERATED_SPEED_KEY = "GeneratedSpeed";
    private static final String GENERATED_CAPACITY_KEY = "GeneratedCapacity";
    private static final String PRESSURE_PN_KEY = "PressurePn";
    private static final String EFFECTIVE_HEAT_KEY = "EffectiveHeat";
    private static final String WATER_SUPPLY_KEY = "WaterSupply";
    private static final String SOURCE_MODE_KEY = "SourceMode";
    private static final String STEAM_CONSUMED_KEY = "SteamConsumed";
    private static final String LEGACY_STEAM_POWER_KEY = "SteamPower";
    private static final String STATUS_KEY = "Status";

    private static final int MAX_ACTIVE_BURNERS = 9;
    private static final int MAX_HEAT_UNITS = 18;
    private static final int MAX_PIPED_HEAT_UNITS = 9;
    private static final int MIN_STEAM_SOUND_INTERVAL_TICKS = 5;
    private static final int MAX_PHASE_SHAFT_SCAN = 128;
    private static final float HALF_TURN_RADIANS = (float) Math.PI;
    private static final float DEGREES_PER_TICK_PER_RPM = 0.3F;
    private static final float EXHAUST_PHASE_DEGREES = 180.0F;
    private static final double OUTER_BORE_OFFSET = 2.05D;
    private static final int VALIDATION_RETRY_TICKS = 5;

    private boolean assembled;
    private BlockPos ringOrigin;
    private BlockPos cylinderRootPos;
    private BlockPos boilerPos;
    private BlockPos inletPos;
    private BlockPos shaftPos;
    private int pistonBodyCount = EngineValidator.MIN_PISTON_BODIES;
    private int shaftGap = EngineValidator.MIN_SHAFT_GAP;
    private int activeBurners;
    private int heatUnits;
    private float generatedSpeed;
    private float generatedCapacitySu;
    private float pressurePn;
    private double effectiveHeat;
    private boolean hasWaterSupply;
    private SourceMode sourceMode = SourceMode.NONE;
    private int steamConsumedRate;
    private String status = "Incomplete structure";
    private float exhaustPhaseDegrees = Float.NaN;
    private float previousSoundAngle = Float.NaN;
    private int steamSoundCooldown;
    private int validationRetryTicks;

    public PistonHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PISTON_HEAD.get(), pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide()) {
            revalidateStructure();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }

        if (level.isClientSide()) {
            tickClientSound();
            return;
        }

        if (validationRetryTicks > 0 && --validationRetryTicks == 0) {
            revalidateStructure();
        }

        if (!assembled) {
            return;
        }

        SteamOutput output = calculateBestSteamOutput(true);
        if (applySteamOutput(output)) {
            updateShaftOutput();
            notifyUpdate();
        }
        tickServerExhaust();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide()) {
            revalidateStructure();
        }
    }

    public void revalidateStructure() {
        if (level == null || level.isClientSide()) {
            return;
        }

        EngineValidator.Result result = EngineValidator.validate(level, worldPosition);
        if (result.pending()) {
            requestValidationRetry();
            return;
        }
        if (!result.valid()) {
            markInvalid(result.message(), null);
            return;
        }
        ShaftClaimResult shaftClaim = ensurePoweredShaft(result.shaft());
        if (shaftClaim == ShaftClaimResult.PENDING) {
            requestValidationRetry();
            return;
        }
        if (shaftClaim == ShaftClaimResult.INVALID) {
            markInvalid("Could not claim shaft", null);
            return;
        }

        boolean changed = !assembled
                || !Objects.equals(ringOrigin, result.ringOrigin())
                || !Objects.equals(cylinderRootPos, result.cylinderRoot())
                || !Objects.equals(boilerPos, result.boilerPos())
                || !Objects.equals(inletPos, result.inletPos())
                || !Objects.equals(shaftPos, result.shaft())
                || pistonBodyCount != result.pistonBodyCount()
                || shaftGap != result.shaftGap()
                || !Objects.equals(status, result.message());

        assembled = true;
        ringOrigin = result.ringOrigin();
        cylinderRootPos = result.cylinderRoot();
        boilerPos = result.boilerPos();
        inletPos = result.inletPos();
        shaftPos = result.shaft();
        pistonBodyCount = result.pistonBodyCount();
        shaftGap = result.shaftGap();
        status = result.message();
        validationRetryTicks = 0;

        EngineLinkageContinuity.install(level, result);
        setPistonsAssembled(result);

        FluidTankBlockEntity boiler = getBoiler();
        refreshBoilerState(boiler);

        SteamOutput output = calculateBestSteamOutput(false);
        boolean outputChanged = applySteamOutput(output);
        if (changed || outputChanged) {
            updateShaftOutput();
            notifyUpdate();
        }
    }

    public void clearAssembly() {
        markInvalid("Piston head removed", null);
    }

    private void markInvalid(String reason, BlockPos skippedPistonPos) {
        FluidTankBlockEntity previousBoiler = getBoiler();
        boolean changed = assembled
                || activeBurners != 0
                || heatUnits != 0
                || generatedSpeed != 0
                || generatedCapacitySu != 0
                || hasWaterSupply
                || ringOrigin != null
                || cylinderRootPos != null
                || boilerPos != null
                || inletPos != null
                || shaftPos != null
                || pistonBodyCount != EngineValidator.MIN_PISTON_BODIES
                || shaftGap != EngineValidator.MIN_SHAFT_GAP
                || steamConsumedRate != 0
                || sourceMode != SourceMode.NONE
                || !Objects.equals(status, reason);

        EngineLinkageContinuity.clear(level, worldPosition, pistonBodyCount, shaftGap);
        clearShaftPower();
        clearPistonStates(skippedPistonPos);

        assembled = false;
        ringOrigin = null;
        cylinderRootPos = null;
        boilerPos = null;
        inletPos = null;
        shaftPos = null;
        pistonBodyCount = EngineValidator.MIN_PISTON_BODIES;
        shaftGap = EngineValidator.MIN_SHAFT_GAP;
        activeBurners = 0;
        heatUnits = 0;
        generatedSpeed = 0;
        generatedCapacitySu = 0;
        pressurePn = 0;
        hasWaterSupply = false;
        sourceMode = SourceMode.NONE;
        steamConsumedRate = 0;
        status = reason;
        exhaustPhaseDegrees = Float.NaN;
        previousSoundAngle = Float.NaN;
        steamSoundCooldown = 0;
        validationRetryTicks = 0;

        refreshBoilerState(previousBoiler);

        if (changed) {
            notifyUpdate();
        }
    }

    public boolean isEngineAssembled() {
        return assembled;
    }

    public boolean isEngineRunning() {
        return assembled && generatedSpeed != 0;
    }

    public boolean isLinkageMoving() {
        FullSteamPoweredShaftBlockEntity shaft = getShaft();
        return assembled && shaft != null && shaft.getSpeed() != 0;
    }

    public float getGeneratedSpeed() {
        return assembled ? generatedSpeed : 0;
    }

    public float getGeneratedCapacitySu() {
        return getTargetCapacitySu();
    }

    public float getPressurePn() {
        return assembled ? pressurePn : 0;
    }

    public String getSourceModeName() {
        return sourceMode.name();
    }

    public BlockPos getRingOrigin() {
        return ringOrigin;
    }

    public BlockPos getInletPos() {
        return inletPos;
    }

    public BlockPos getShaftPos() {
        return shaftPos;
    }

    public int getPistonBodyCount() {
        return Mth.clamp(pistonBodyCount, EngineValidator.MIN_PISTON_BODIES, EngineValidator.MAX_PISTON_BODIES);
    }

    public int getShaftGap() {
        return EngineValidator.clampShaftGap(shaftGap);
    }

    public int getShaftDistance() {
        return EngineValidator.shaftDistanceForPistonBodies(getPistonBodyCount(), getShaftGap());
    }

    public Direction getStrokeDirection() {
        return EngineValidator.pistonHeadFacing(getBlockState());
    }

    public Direction.Axis getShaftAxis() {
        FullSteamPoweredShaftBlockEntity shaft = getShaft();
        if (shaft == null || level == null) {
            return Direction.Axis.X;
        }
        return EngineValidator.shaftAxis(level, shaft.getBlockPos());
    }

    public float getAnimationPhaseOffset() {
        int bankIndex = getShaftBankIndex();
        return (bankIndex & 1) == 0 ? 0.0F : HALF_TURN_RADIANS;
    }

    public FullSteamPoweredShaftBlockEntity getShaft() {
        FullSteamPoweredShaftBlockEntity shaft = shaftEntityAt(shaftPos);
        if (shaft != null) {
            return shaft;
        }
        // Fallback for relocated worlds such as Ponder scenes, where the stored absolute shaftPos is
        // stale: the shaft sits at the validated piston column length along the stroke direction.
        return shaftEntityAt(worldPosition.relative(getStrokeDirection(), getShaftDistance()));
    }

    private int getShaftBankIndex() {
        if (level == null || !assembled) {
            return 0;
        }

        FullSteamPoweredShaftBlockEntity shaft = getShaft();
        if (shaft == null) {
            return 0;
        }

        Direction.Axis shaftAxis = getShaftAxis();
        BlockPos currentShaftPos = shaft.getBlockPos();
        if (!isShaftOnAxis(currentShaftPos, shaftAxis)) {
            return 0;
        }

        BlockPos start = currentShaftPos;
        for (int steps = 0; steps < MAX_PHASE_SHAFT_SCAN; steps++) {
            BlockPos previous = offsetAlong(start, shaftAxis, -1);
            if (!isShaftOnAxis(previous, shaftAxis)) {
                break;
            }
            start = previous;
        }

        int engineIndex = 0;
        BlockPos cursor = start;
        for (int steps = 0; steps <= MAX_PHASE_SHAFT_SCAN * 2; steps++) {
            if (!isShaftOnAxis(cursor, shaftAxis)) {
                break;
            }

            if (hasClaimedEngineShaft(cursor, shaftAxis)) {
                if (cursor.equals(currentShaftPos)) {
                    return engineIndex;
                }
                engineIndex++;
            }

            cursor = offsetAlong(cursor, shaftAxis, 1);
        }

        return 0;
    }

    private boolean hasClaimedEngineShaft(BlockPos pos, Direction.Axis shaftAxis) {
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof FullSteamPoweredShaftBlockEntity shaft)) {
            return false;
        }

        BlockPos enginePos = shaft.getEngineWorldPos();
        if (enginePos == null || !level.isLoaded(enginePos)) {
            return false;
        }

        BlockEntity engineEntity = level.getBlockEntity(enginePos);
        if (!(engineEntity instanceof PistonHeadBlockEntity engine) || !engine.isEngineAssembled()) {
            return false;
        }

        return shaft.isPoweredBy(enginePos) && engine.getShaftAxis() == shaftAxis;
    }

    private boolean isShaftOnAxis(BlockPos pos, Direction.Axis shaftAxis) {
        return level != null
                && level.isLoaded(pos)
                && EngineValidator.isValidShaft(level, pos)
                && EngineValidator.shaftAxis(level, pos) == shaftAxis;
    }

    private static BlockPos offsetAlong(BlockPos pos, Direction.Axis axis, int distance) {
        return switch (axis) {
            case X -> pos.offset(distance, 0, 0);
            case Z -> pos.offset(0, 0, distance);
            default -> pos;
        };
    }

    private FullSteamPoweredShaftBlockEntity shaftEntityAt(BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof FullSteamPoweredShaftBlockEntity shaft ? shaft : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("Full Steam Engine").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if (!assembled) {
            CreateLang.text(status).style(ChatFormatting.RED).forGoggles(tooltip, 1);
            return true;
        }

        CreateLang.text("Engine assembled").style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
        CreateLang.text(getStrokeDirection() == Direction.DOWN ? "Orientation: upside down" : "Orientation: upright")
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Piston bodies: " + getPistonBodyCount() + "/" + EngineValidator.MAX_PISTON_BODIES)
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Stroke gap: " + getShaftGap() + "/" + EngineValidator.MAX_SHAFT_GAP)
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("Source: " + sourceMode.displayName())
                .style(sourceMode == SourceMode.NONE ? ChatFormatting.YELLOW : ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        if (sourceMode == SourceMode.PIPED_STEAM) {
            CreateLang.text("Steam consumed: " + steamConsumedRate + " mB/t")
                    .style(steamConsumedRate > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 1);
            CreateLang.text("Steam units: " + heatUnits + "/" + MAX_PIPED_HEAT_UNITS)
                    .style(heatUnits > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.text("Active burners: " + activeBurners + "/" + MAX_ACTIVE_BURNERS)
                    .style(activeBurners > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 1);
            CreateLang.text("Heat units: " + heatUnits + "/" + MAX_HEAT_UNITS)
                    .style(heatUnits > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 1);
            CreateLang.text("Water supply: " + (hasWaterSupply ? "available" : "missing"))
                    .style(hasWaterSupply ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 1);
        }
        CreateLang.text("Pressure: " + SteamPressure.format(pressurePn))
                .style(pressurePn > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);
        CreateLang.text("RPM: " + Math.round(getGeneratedSpeed()))
                .style(getGeneratedSpeed() > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);
        CreateLang.text("Capacity: " + Math.round(getTargetCapacitySu()) + " SU")
                .style(getTargetCapacitySu() > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);
        CreateLang.text(shaftPos == null ? "No shaft link" : "Shaft linked")
                .style(shaftPos == null ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    private SteamOutput calculateBestSteamOutput(boolean executePiped) {
        SteamInletBlockEntity inlet = getInlet();
        if (inlet != null && inlet.isActiveInlet()) {
            SteamOutput piped = calculatePipedSteamOutput(inlet, executePiped);
            if (piped.canRun() || boilerPos == null || getStrokeDirection() == Direction.DOWN) {
                return piped;
            }
        }

        if (getStrokeDirection() == Direction.DOWN) {
            return SteamOutput.none(inlet == null ? SourceMode.NONE : SourceMode.PIPED_STEAM);
        }

        if (FullSteamConfig.directCompactModeEnabled()) {
            FluidTankBlockEntity boiler = getBoiler();
            if (boiler != null && boiler.boiler != null) {
                return calculateDirectSteamOutput(boiler, executePiped);
            }
        }

        return SteamOutput.none(inlet == null ? SourceMode.NONE : SourceMode.PIPED_STEAM);
    }

    private SteamOutput calculateDirectSteamOutput(FluidTankBlockEntity boiler, boolean execute) {
        if (boiler == null || boiler.boiler == null) {
            return SteamOutput.none(SourceMode.NONE);
        }

        BoilerData data = boiler.boiler;
        boiler.updateBoilerTemperature();
        if (data.needsHeatLevelUpdate) {
            data.updateTemperature(boiler);
        }

        BurnerHeat burnerHeat = scanBurners();
        boolean hasWater = data.getMaxHeatLevelForWaterSupply() > 0;

        // Direct/compact mode is an implicit one-boiler network: supply = production, demand = one engine.
        int targetHeat = FullSteamBoilerIntegration.usableHeatUnits(boiler);
        // Boiler thermal inertia (only advanced on the real tick, not on revalidation).
        if (FullSteamConfig.thermalInertiaEnabled()) {
            if (execute) {
                boolean dry = data.getMaxHeatLevelForWaterSupply() <= 0;
                double tau = targetHeat >= effectiveHeat
                        ? FullSteamConfig.heatUpTauTicks()
                        : dry ? FullSteamConfig.dryBoilerCoolDownTauTicks() : FullSteamConfig.coolDownTauTicks();
                effectiveHeat = SteamPhysics.approachExp(effectiveHeat, targetHeat, tau);
            }
        } else {
            effectiveHeat = targetHeat;
        }
        int height = Math.max(1, boiler.getHeight());
        int production = SteamPhysics.productionMb(effectiveHeat, height);
        int fullFlow = FullSteamConfig.steamFullEngineFlowMb();
        int consumed = Math.min(fullFlow, production);
        // Steady-state pressure: rated when the boiler can fully supply one engine, scaling down below that.
        double supplyFactor = Mth.clamp(production / (double) Math.max(1, fullFlow), 0.0D, 1.0D);
        double pressure = SteamPressure.rated() * supplyFactor;
        float of = SteamPhysics.outputFactor(pressure, consumed);
        return new SteamOutput(
                SourceMode.DIRECT_BOILER,
                burnerHeat.activeBurners(),
                Math.max(targetHeat, burnerHeat.heatUnits()),
                hasWater,
                consumed,
                SteamPhysics.su(of),
                SteamPhysics.rpm(of),
                (float) pressure
        );
    }

    private SteamOutput calculatePipedSteamOutput(SteamInletBlockEntity inlet, boolean execute) {
        // RPM/SU come from the shared network pressure; draw is the manager's fair per-engine cap.
        double pressure = inlet.getNetworkPressurePn();
        int requested = SteamPhysics.requestedFlowMb(pressure);
        // A fresh cap of 0 is a deliberate "no share" (shortage / no engine); only an absent report
        // (manager hasn't run yet) falls back to the uncapped request.
        int allowed = inlet.isNetworkFresh() ? Math.min(requested, inlet.getNetworkDrawCap()) : requested;
        int targetSteam = Math.min(allowed, inlet.getSteamAmount());
        if (targetSteam <= 0) {
            return SteamOutput.none(SourceMode.PIPED_STEAM);
        }

        int consumed = inlet.consumeSteam(targetSteam, execute).getAmount();
        if (consumed <= 0) {
            return SteamOutput.none(SourceMode.PIPED_STEAM);
        }

        int heat = Mth.clamp(Mth.ceil(consumed / (float) FullSteamConfig.steamPerHeatUnit()), 1, MAX_PIPED_HEAT_UNITS);
        int activeEquivalent = Math.min(MAX_ACTIVE_BURNERS, heat);
        float of = SteamPhysics.outputFactor(pressure, consumed);
        return new SteamOutput(
                SourceMode.PIPED_STEAM,
                activeEquivalent,
                heat,
                true,
                consumed,
                SteamPhysics.su(of),
                SteamPhysics.rpm(of),
                (float) pressure
        );
    }

    private BurnerHeat scanBurners() {
        if (level == null || ringOrigin == null) {
            return BurnerHeat.NONE;
        }

        int active = 0;
        int units = 0;
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                BlockPos burnerPos = ringOrigin.offset(x, -2, z);
                if (!level.isLoaded(burnerPos)) {
                    continue;
                }

                BlazeBurnerBlock.HeatLevel heat = BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(burnerPos));
                if (!heat.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING)) {
                    continue;
                }

                active++;
                units += heat.isAtLeast(BlazeBurnerBlock.HeatLevel.SEETHING) ? 2 : 1;
            }
        }

        return new BurnerHeat(
                Math.min(active, MAX_ACTIVE_BURNERS),
                Math.min(units, MAX_HEAT_UNITS)
        );
    }

    private boolean applySteamOutput(SteamOutput output) {
        float speed = output.generatedSpeed();
        float capacity = output.capacitySu();
        float pressure = output.canRun() ? output.pressurePn() : 0.0F;
        boolean changed = sourceMode != output.sourceMode()
                || activeBurners != output.activeBurners()
                || heatUnits != output.heatUnits()
                || hasWaterSupply != output.hasWaterSupply()
                || steamConsumedRate != output.steamConsumedRate()
                || !Mth.equal(generatedSpeed, speed)
                || !Mth.equal(generatedCapacitySu, capacity)
                || !Mth.equal(pressurePn, pressure);

        sourceMode = output.sourceMode();
        activeBurners = output.activeBurners();
        heatUnits = output.heatUnits();
        hasWaterSupply = output.hasWaterSupply();
        steamConsumedRate = output.steamConsumedRate();
        generatedSpeed = speed;
        generatedCapacitySu = capacity;
        pressurePn = pressure;
        return changed;
    }

    private float getTargetCapacitySu() {
        return assembled ? generatedCapacitySu : 0;
    }

    private FluidTankBlockEntity getBoiler() {
        if (level == null || boilerPos == null || !level.isLoaded(boilerPos)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(boilerPos);
        if (blockEntity instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity controller = tank.getControllerBE();
            return controller == null ? tank : controller;
        }
        return null;
    }

    private SteamInletBlockEntity getInlet() {
        if (level == null || inletPos == null || !level.isLoaded(inletPos)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(inletPos);
        return blockEntity instanceof SteamInletBlockEntity inlet ? inlet : null;
    }

    private void refreshBoilerState(FluidTankBlockEntity boiler) {
        if (boiler != null && !boiler.isRemoved()) {
            boiler.updateBoilerState();
        }
    }

    private ShaftClaimResult ensurePoweredShaft(BlockPos targetShaftPos) {
        if (level == null || targetShaftPos == null || !level.isLoaded(targetShaftPos)) {
            return ShaftClaimResult.PENDING;
        }

        BlockState state = level.getBlockState(targetShaftPos);
        if (FullSteamPoweredShaftBlock.isPoweredShaft(state)) {
            return level.getBlockEntity(targetShaftPos) instanceof FullSteamPoweredShaftBlockEntity
                    ? ShaftClaimResult.CLAIMED
                    : ShaftClaimResult.PENDING;
        }
        if (!FullSteamPoweredShaftBlock.isClaimableShaft(state)) {
            return ShaftClaimResult.INVALID;
        }

        level.setBlock(targetShaftPos, FullSteamPoweredShaftBlock.equivalentOf(state), Block.UPDATE_ALL);
        return level.getBlockEntity(targetShaftPos) instanceof FullSteamPoweredShaftBlockEntity
                ? ShaftClaimResult.CLAIMED
                : ShaftClaimResult.PENDING;
    }

    private void updateShaftOutput() {
        FullSteamPoweredShaftBlockEntity shaft = getShaft();
        if (shaft == null) {
            BlockPos expectedShaftPos = worldPosition.relative(getStrokeDirection(), getShaftDistance());
            if (!level.isLoaded(expectedShaftPos)
                    || FullSteamPoweredShaftBlock.isPoweredShaft(level.getBlockState(expectedShaftPos))) {
                requestValidationRetry();
            } else if (assembled) {
                markInvalid("Missing shaft", null);
            }
            return;
        }

        shaft.update(worldPosition, getGeneratedSpeed(), getTargetCapacitySu());
    }

    private void clearShaftPower() {
        if (level == null || shaftPos == null || !level.isLoaded(shaftPos)) {
            return;
        }

        BlockState state = level.getBlockState(shaftPos);
        if (!FullSteamPoweredShaftBlock.isPoweredShaft(state)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(shaftPos);
        if (blockEntity instanceof FullSteamPoweredShaftBlockEntity shaft && shaft.isPoweredBy(worldPosition)) {
            shaft.remove(worldPosition);
            level.setBlock(shaftPos, FullSteamPoweredShaftBlock.asRegularShaft(state), Block.UPDATE_ALL);
        }
    }

    private void setPistonsAssembled(EngineValidator.Result result) {
        setPistonHead(result.pistonHead(), true, result.strokeDirection());
        Direction.Axis shaftAxis = EngineValidator.shaftAxis(level, result.shaft());
        for (int index = 0; index < result.pistons().size(); index++) {
            PistonSection section = index == result.pistons().size() - 1
                    ? PistonSection.INSIDE_LOW
                    : PistonSection.INSIDE_HIGH;
            setPiston(result.pistons().get(index), true, section, shaftAxis, result.strokeDirection());
        }
    }

    private void clearPistonStates(BlockPos skippedPistonPos) {
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            EngineValidator.PistonPositions pistons =
                    EngineValidator.pistonPositions(worldPosition, direction, EngineValidator.MAX_PISTON_BODIES);
            clearPistonHead(pistons.pistonHead(), skippedPistonPos);
            for (BlockPos piston : pistons.pistons()) {
                clearPiston(piston, skippedPistonPos);
            }
            for (BlockPos strokeSpace : pistons.emptyStrokeSpaces()) {
                clearPiston(strokeSpace, skippedPistonPos);
            }
        }
    }

    private void clearPiston(BlockPos pos, BlockPos skippedPistonPos) {
        if (level == null || pos == null || pos.equals(skippedPistonPos) || !level.isLoaded(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.PISTON.get())) {
            setPiston(pos, false, SteamPistonBlock.detectedSection(level, pos, state), null, null);
        }
    }

    private void clearPistonHead(BlockPos pos, BlockPos skippedPistonPos) {
        if (!pos.equals(skippedPistonPos)) {
            setPistonHead(pos, false, null);
        }
    }

    private void setPiston(
            BlockPos pos,
            boolean assembled,
            PistonSection section,
            Direction.Axis axis,
            Direction strokeDirection
    ) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.PISTON.get())) {
            return;
        }

        BlockState newState = state
                .setValue(SteamPistonBlock.ASSEMBLED, assembled)
                .setValue(SteamPistonBlock.PISTON_SECTION, section);
        if (axis != null && state.hasProperty(SteamPistonBlock.AXIS)) {
            newState = newState.setValue(SteamPistonBlock.AXIS, axis);
        }
        if (strokeDirection != null && state.hasProperty(SteamPistonBlock.FACING)) {
            newState = newState.setValue(SteamPistonBlock.FACING, strokeDirection);
        }
        if (newState != state) {
            level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
        }
    }

    private void setPistonHead(BlockPos pos, boolean assembled, Direction strokeDirection) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.PISTON_HEAD.get())) {
            return;
        }

        BlockState newState = state.setValue(PistonHeadBlock.ASSEMBLED, assembled);
        if (strokeDirection != null && state.hasProperty(PistonHeadBlock.FACING)) {
            newState = newState.setValue(PistonHeadBlock.FACING, strokeDirection);
        }
        if (newState != state) {
            level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
        }
    }

    public static void revalidateNearbyEngines(Level level, BlockPos changedPos) {
        forNearbyEngines(level, changedPos, PistonHeadBlockEntity::revalidateStructure);
    }

    private void requestValidationRetry() {
        if (validationRetryTicks == 0) {
            validationRetryTicks = VALIDATION_RETRY_TICKS;
        }
    }

    public static void revalidateAt(Level level, BlockPos pos) {
        if (level.isClientSide() || !level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.PISTON_HEAD.get())) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PistonHeadBlockEntity engine) {
            engine.revalidateStructure();
        }
    }

    public static void invalidateNearbyEngines(Level level, BlockPos changedPos, String reason, BlockPos skippedPistonPos) {
        forNearbyEngines(level, changedPos, be -> be.markInvalid(reason, skippedPistonPos));
    }

    private static void forNearbyEngines(
            Level level,
            BlockPos changedPos,
            Consumer<PistonHeadBlockEntity> action
    ) {
        if (level.isClientSide()) {
            return;
        }

        for (BlockPos pos : EngineValidator.candidatePistonHeadsNear(changedPos)) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.PISTON_HEAD.get())) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PistonHeadBlockEntity engine) {
                action.accept(engine);
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean(ASSEMBLED_KEY, assembled);
        writePos(tag, RING_ORIGIN_KEY, ringOrigin);
        writePos(tag, CYLINDER_ROOT_KEY, cylinderRootPos);
        writePos(tag, BOILER_POS_KEY, boilerPos);
        writePos(tag, INLET_POS_KEY, inletPos);
        writePos(tag, SHAFT_POS_KEY, shaftPos);
        tag.putInt(PISTON_BODY_COUNT_KEY, pistonBodyCount);
        tag.putInt(SHAFT_GAP_KEY, shaftGap);
        tag.putInt(ACTIVE_BURNERS_KEY, activeBurners);
        tag.putInt(HEAT_UNITS_KEY, heatUnits);
        tag.putFloat(GENERATED_SPEED_KEY, generatedSpeed);
        tag.putFloat(GENERATED_CAPACITY_KEY, generatedCapacitySu);
        tag.putFloat(PRESSURE_PN_KEY, pressurePn);
        tag.putDouble(EFFECTIVE_HEAT_KEY, effectiveHeat);
        tag.putBoolean(WATER_SUPPLY_KEY, hasWaterSupply);
        tag.putString(SOURCE_MODE_KEY, sourceMode.name());
        tag.putInt(STEAM_CONSUMED_KEY, steamConsumedRate);
        tag.putString(STATUS_KEY, status);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        assembled = tag.getBoolean(ASSEMBLED_KEY);
        ringOrigin = readPos(tag, RING_ORIGIN_KEY);
        cylinderRootPos = readPos(tag, CYLINDER_ROOT_KEY);
        boilerPos = readPos(tag, BOILER_POS_KEY);
        inletPos = readPos(tag, INLET_POS_KEY);
        shaftPos = readPos(tag, SHAFT_POS_KEY);
        pistonBodyCount = tag.contains(PISTON_BODY_COUNT_KEY)
                ? Mth.clamp(
                        tag.getInt(PISTON_BODY_COUNT_KEY),
                        EngineValidator.MIN_PISTON_BODIES,
                        EngineValidator.MAX_PISTON_BODIES
                )
                : EngineValidator.MIN_PISTON_BODIES;
        shaftGap = tag.contains(SHAFT_GAP_KEY)
                ? EngineValidator.clampShaftGap(tag.getInt(SHAFT_GAP_KEY))
                : EngineValidator.defaultShaftGapForPistonBodies(pistonBodyCount);
        activeBurners = tag.getInt(ACTIVE_BURNERS_KEY);
        heatUnits = tag.getInt(HEAT_UNITS_KEY);
        generatedSpeed = tag.getFloat(GENERATED_SPEED_KEY);
        generatedCapacitySu = tag.getFloat(GENERATED_CAPACITY_KEY);
        pressurePn = tag.getFloat(PRESSURE_PN_KEY);
        effectiveHeat = tag.getDouble(EFFECTIVE_HEAT_KEY);
        hasWaterSupply = tag.getBoolean(WATER_SUPPLY_KEY);
        sourceMode = SourceMode.byName(tag.getString(SOURCE_MODE_KEY));
        steamConsumedRate = tag.getInt(STEAM_CONSUMED_KEY);
        if (generatedSpeed == 0 && generatedCapacitySu == 0 && tag.contains(LEGACY_STEAM_POWER_KEY)) {
            float legacySteamPower = Mth.clamp(tag.getFloat(LEGACY_STEAM_POWER_KEY), 0.0F, 1.0F);
            generatedSpeed = SteamPhysics.rpm(legacySteamPower);
            generatedCapacitySu = FullSteamConfig.baseEngineCapacity() * legacySteamPower;
            sourceMode = SourceMode.DIRECT_BOILER;
        }
        status = tag.contains(STATUS_KEY) ? tag.getString(STATUS_KEY) : "Incomplete structure";
        if (!clientPacket && assembled) {
            validationRetryTicks = 1;
        }
    }

    private enum ShaftClaimResult {
        CLAIMED,
        PENDING,
        INVALID
    }

    private void tickServerExhaust() {
        if (!(level instanceof ServerLevel serverLevel)
                || !FullSteamConfig.engineExhaustEnabled()
                || !isEngineRunning()) {
            exhaustPhaseDegrees = Float.NaN;
            return;
        }

        if (Float.isNaN(exhaustPhaseDegrees)) {
            exhaustPhaseDegrees = 0.0F;
            return;
        }

        float previous = effectiveExhaustPhase(exhaustPhaseDegrees);
        exhaustPhaseDegrees = Mth.positiveModulo(
                exhaustPhaseDegrees + Math.abs(getGeneratedSpeed()) * DEGREES_PER_TICK_PER_RPM,
                360.0F
        );
        float current = effectiveExhaustPhase(exhaustPhaseDegrees);
        if (crossedPhase(previous, current, EXHAUST_PHASE_DEGREES)) {
            emitSteamExhaust(serverLevel);
        }
    }

    private void tickClientSound() {
        if (steamSoundCooldown > 0) {
            steamSoundCooldown--;
        }

        if (!isEngineRunning()) {
            previousSoundAngle = Float.NaN;
            return;
        }

        float angle = getCurrentSoundAngle();
        if (!Float.isNaN(previousSoundAngle)
                && steamSoundCooldown <= 0
                && crossedPhase(previousSoundAngle, angle, EXHAUST_PHASE_DEGREES)) {
            playSteamSound();
            steamSoundCooldown = MIN_STEAM_SOUND_INTERVAL_TICKS;
        }
        previousSoundAngle = angle;
    }

    private float getCurrentSoundAngle() {
        FullSteamPoweredShaftBlockEntity shaft = getShaft();
        if (shaft == null) {
            return 0.0F;
        }

        float radians = KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), getShaftAxis());
        radians += getAnimationPhaseOffset();
        return Mth.positiveModulo((float) Math.toDegrees(radians), 360.0F);
    }

    private float effectiveExhaustPhase(float basePhase) {
        float phaseOffset = (float) Math.toDegrees(getAnimationPhaseOffset());
        return Mth.positiveModulo(basePhase + phaseOffset, 360.0F);
    }

    private static boolean crossedPhase(float previous, float current, float threshold) {
        if (previous < current) {
            return previous < threshold && current >= threshold;
        }
        if (previous > current) {
            return previous < threshold || current >= threshold;
        }
        return false;
    }

    private void emitSteamExhaust(ServerLevel serverLevel) {
        Direction strokeDirection = getStrokeDirection();
        Vec3 origin = exhaustOrigin(strokeDirection);
        SteamCloudEffects.emitEngineExhaust(serverLevel, origin, strokeDirection, getExhaustSteamAmount());
    }

    private void playSteamSound() {
        if (level == null) {
            return;
        }

        float intensity = getEffectIntensity();
        Direction strokeDirection = getStrokeDirection();
        Vec3 origin = exhaustOrigin(strokeDirection);
        float volume = 0.34F + 0.18F * intensity;
        float pitch = 0.8F + (level.random.nextFloat() - 0.5F) * 0.04F;
        AllSoundEvents.STEAM.playAt(level, origin, volume, pitch, false);
    }

    private Vec3 exhaustOrigin(Direction strokeDirection) {
        return Vec3.atCenterOf(worldPosition)
                .add(Vec3.atLowerCornerOf(strokeDirection.getNormal()).scale(OUTER_BORE_OFFSET));
    }

    private float getEffectIntensity() {
        float speedFactor = Math.abs(getGeneratedSpeed())
                / Math.max(1.0F, (float) FullSteamConfig.steamMaxRpm());
        float heatFactor = heatUnits / (float) MAX_HEAT_UNITS;
        return Mth.clamp(Math.max(speedFactor, heatFactor), 0.25F, 1.0F);
    }

    private int getExhaustSteamAmount() {
        if (sourceMode == SourceMode.PIPED_STEAM) {
            return steamConsumedRate;
        }
        if (sourceMode == SourceMode.DIRECT_BOILER) {
            return heatUnits * FullSteamConfig.steamPerHeatUnit();
        }
        return 0;
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

    private record BurnerHeat(int activeBurners, int heatUnits) {
        private static final BurnerHeat NONE = new BurnerHeat(0, 0);
    }

    private record SteamOutput(
            SourceMode sourceMode,
            int activeBurners,
            int heatUnits,
            boolean hasWaterSupply,
            int steamConsumedRate,
            float targetCapacitySu,
            float targetSpeed,
            float pressurePn
    ) {
        private static SteamOutput none(SourceMode sourceMode) {
            return new SteamOutput(sourceMode, 0, 0, false, 0, 0, 0, 0);
        }

        private float generatedSpeed() {
            return canRun() ? targetSpeed : 0;
        }

        private float capacitySu() {
            return canRun() ? targetCapacitySu : 0;
        }

        private boolean canRun() {
            return switch (sourceMode) {
                case DIRECT_BOILER -> hasWaterSupply && activeBurners > 0 && heatUnits > 0 && targetCapacitySu > 0;
                case PIPED_STEAM -> steamConsumedRate > 0 && targetCapacitySu > 0;
                case NONE -> false;
            };
        }
    }

    private enum SourceMode {
        NONE("No steam"),
        DIRECT_BOILER("Direct boiler"),
        PIPED_STEAM("Piped steam");

        private final String displayName;

        SourceMode(String displayName) {
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }

        private static SourceMode byName(String name) {
            for (SourceMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
            return NONE;
        }
    }
}
