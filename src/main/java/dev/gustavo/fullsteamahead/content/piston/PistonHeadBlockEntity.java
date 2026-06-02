package dev.gustavo.fullsteamahead.content.piston;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.steamEngine.SteamJetParticleData;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlock;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import dev.gustavo.fullsteamahead.content.steam.SteamInletBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
    private static final String ACTIVE_BURNERS_KEY = "ActiveBurners";
    private static final String HEAT_UNITS_KEY = "HeatUnits";
    private static final String GENERATED_SPEED_KEY = "GeneratedSpeed";
    private static final String GENERATED_CAPACITY_KEY = "GeneratedCapacity";
    private static final String WATER_SUPPLY_KEY = "WaterSupply";
    private static final String SOURCE_MODE_KEY = "SourceMode";
    private static final String STEAM_CONSUMED_KEY = "SteamConsumed";
    private static final String LEGACY_STEAM_POWER_KEY = "SteamPower";
    private static final String STATUS_KEY = "Status";

    private static final int MAX_ACTIVE_BURNERS = 9;
    private static final int MAX_HEAT_UNITS = 18;
    private static final int MAX_PIPED_HEAT_UNITS = 9;
    private static final float MAX_RPM = 64.0F;
    private static final int MIN_STEAM_SOUND_INTERVAL_TICKS = 5;
    private static final float HALF_TURN_RADIANS = (float) Math.PI;

    private boolean assembled;
    private BlockPos ringOrigin;
    private BlockPos cylinderRootPos;
    private BlockPos boilerPos;
    private BlockPos inletPos;
    private BlockPos shaftPos;
    private int activeBurners;
    private int heatUnits;
    private float generatedSpeed;
    private float generatedCapacitySu;
    private boolean hasWaterSupply;
    private SourceMode sourceMode = SourceMode.NONE;
    private int steamConsumedRate;
    private String status = "Incomplete structure";
    private float previousEffectAngle = Float.NaN;
    private int steamSoundCooldown;

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
            tickClientEffects();
            return;
        }

        if (!assembled) {
            return;
        }

        SteamOutput output = calculateBestSteamOutput(true);
        if (applySteamOutput(output)) {
            updateShaftOutput();
            notifyUpdate();
        }
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
        if (!result.valid()) {
            markInvalid(result.message(), null);
            return;
        }
        if (!ensurePoweredShaft(result.shaft())) {
            markInvalid("Could not claim shaft", null);
            return;
        }

        boolean changed = !assembled
                || !Objects.equals(ringOrigin, result.ringOrigin())
                || !Objects.equals(cylinderRootPos, result.cylinderRoot())
                || !Objects.equals(boilerPos, result.boilerPos())
                || !Objects.equals(inletPos, result.inletPos())
                || !Objects.equals(shaftPos, result.shaft())
                || !Objects.equals(status, result.message());

        assembled = true;
        ringOrigin = result.ringOrigin();
        cylinderRootPos = result.cylinderRoot();
        boilerPos = result.boilerPos();
        inletPos = result.inletPos();
        shaftPos = result.shaft();
        status = result.message();

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
                || steamConsumedRate != 0
                || sourceMode != SourceMode.NONE
                || !Objects.equals(status, reason);

        clearShaftPower();
        clearPistonStates(skippedPistonPos);

        assembled = false;
        ringOrigin = null;
        cylinderRootPos = null;
        boilerPos = null;
        inletPos = null;
        shaftPos = null;
        activeBurners = 0;
        heatUnits = 0;
        generatedSpeed = 0;
        generatedCapacitySu = 0;
        hasWaterSupply = false;
        sourceMode = SourceMode.NONE;
        steamConsumedRate = 0;
        status = reason;

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
        Direction.Axis shaftAxis = getShaftAxis();
        int coordinate = shaftAxis == Direction.Axis.X ? worldPosition.getX() : worldPosition.getZ();
        return (Math.floorDiv(coordinate, 3) & 1) == 0 ? 0.0F : HALF_TURN_RADIANS;
    }

    public FullSteamPoweredShaftBlockEntity getShaft() {
        FullSteamPoweredShaftBlockEntity shaft = shaftEntityAt(shaftPos);
        if (shaft != null) {
            return shaft;
        }
        // Fallback for relocated worlds such as Ponder scenes, where the stored absolute shaftPos is
        // stale: the shaft always sits three blocks along the stroke direction from the head.
        return shaftEntityAt(worldPosition.relative(getStrokeDirection(), 3));
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
        if (inlet != null && inlet.isInletAssembled()) {
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
                return calculateDirectSteamOutput(boiler);
            }
        }

        return SteamOutput.none(inlet == null ? SourceMode.NONE : SourceMode.PIPED_STEAM);
    }

    private SteamOutput calculateDirectSteamOutput(FluidTankBlockEntity boiler) {
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
        return new SteamOutput(
                SourceMode.DIRECT_BOILER,
                burnerHeat.activeBurners(),
                burnerHeat.heatUnits(),
                hasWater,
                0,
                burnerHeat.heatUnits() * FullSteamConfig.SU_PER_HEAT_UNIT
        );
    }

    private SteamOutput calculatePipedSteamOutput(SteamInletBlockEntity inlet, boolean execute) {
        int targetSteam = Math.min(FullSteamConfig.maxPipedSteamPerTick(), inlet.getSteamAmount());
        if (targetSteam <= 0) {
            return SteamOutput.none(SourceMode.PIPED_STEAM);
        }

        int consumed = inlet.consumeSteam(targetSteam, execute).getAmount();
        if (consumed <= 0) {
            return SteamOutput.none(SourceMode.PIPED_STEAM);
        }

        int heat = Mth.clamp(Mth.ceil(consumed / (float) FullSteamConfig.steamPerHeatUnit()), 1, MAX_PIPED_HEAT_UNITS);
        int activeEquivalent = Math.min(MAX_ACTIVE_BURNERS, heat);
        return new SteamOutput(
                SourceMode.PIPED_STEAM,
                activeEquivalent,
                heat,
                true,
                consumed,
                Math.min((float) FullSteamConfig.baseEngineCapacity(), consumed * FullSteamConfig.suPerSteamMb())
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
        boolean changed = sourceMode != output.sourceMode()
                || activeBurners != output.activeBurners()
                || heatUnits != output.heatUnits()
                || hasWaterSupply != output.hasWaterSupply()
                || steamConsumedRate != output.steamConsumedRate()
                || !Mth.equal(generatedSpeed, speed)
                || !Mth.equal(generatedCapacitySu, capacity);

        sourceMode = output.sourceMode();
        activeBurners = output.activeBurners();
        heatUnits = output.heatUnits();
        hasWaterSupply = output.hasWaterSupply();
        steamConsumedRate = output.steamConsumedRate();
        generatedSpeed = speed;
        generatedCapacitySu = capacity;
        return changed;
    }

    private float getTargetCapacitySu() {
        return assembled ? generatedCapacitySu : 0;
    }

    private static float rpmForActiveBurners(int activeBurners) {
        return switch (Mth.clamp(activeBurners, 0, MAX_ACTIVE_BURNERS)) {
            case 0 -> 0;
            case 1, 2 -> 16;
            case 3, 4 -> 32;
            case 5, 6, 7, 8 -> 48;
            default -> MAX_RPM;
        };
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

    private boolean ensurePoweredShaft(BlockPos targetShaftPos) {
        if (level == null || targetShaftPos == null || !level.isLoaded(targetShaftPos)) {
            return false;
        }

        BlockState state = level.getBlockState(targetShaftPos);
        if (state.is(ModBlocks.POWERED_SHAFT.get())) {
            return level.getBlockEntity(targetShaftPos) instanceof FullSteamPoweredShaftBlockEntity;
        }
        if (!AllBlocks.SHAFT.has(state)) {
            return false;
        }

        level.setBlock(targetShaftPos, FullSteamPoweredShaftBlock.equivalentOf(state), Block.UPDATE_ALL);
        return level.getBlockEntity(targetShaftPos) instanceof FullSteamPoweredShaftBlockEntity;
    }

    private void updateShaftOutput() {
        FullSteamPoweredShaftBlockEntity shaft = getShaft();
        if (shaft == null) {
            if (assembled) {
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
        if (!state.is(ModBlocks.POWERED_SHAFT.get())) {
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
        setPiston(result.piston(), true, PistonSection.INSIDE_HIGH, shaftAxis, result.strokeDirection());
    }

    private void clearPistonStates(BlockPos skippedPistonPos) {
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            EngineValidator.PistonPositions pistons = EngineValidator.pistonPositions(worldPosition, direction);
            clearPistonHead(pistons.pistonHead(), skippedPistonPos);
            clearPiston(pistons.piston(), skippedPistonPos);
            clearPiston(pistons.emptyStroke(), skippedPistonPos);
        }
    }

    private void clearPiston(BlockPos pos, BlockPos skippedPistonPos) {
        if (!pos.equals(skippedPistonPos)) {
            setPiston(pos, false, PistonSection.INSIDE_LOW, null, null);
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
        tag.putInt(ACTIVE_BURNERS_KEY, activeBurners);
        tag.putInt(HEAT_UNITS_KEY, heatUnits);
        tag.putFloat(GENERATED_SPEED_KEY, generatedSpeed);
        tag.putFloat(GENERATED_CAPACITY_KEY, generatedCapacitySu);
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
        activeBurners = tag.getInt(ACTIVE_BURNERS_KEY);
        heatUnits = tag.getInt(HEAT_UNITS_KEY);
        generatedSpeed = tag.getFloat(GENERATED_SPEED_KEY);
        generatedCapacitySu = tag.getFloat(GENERATED_CAPACITY_KEY);
        hasWaterSupply = tag.getBoolean(WATER_SUPPLY_KEY);
        sourceMode = SourceMode.byName(tag.getString(SOURCE_MODE_KEY));
        steamConsumedRate = tag.getInt(STEAM_CONSUMED_KEY);
        if (generatedSpeed == 0 && generatedCapacitySu == 0 && tag.contains(LEGACY_STEAM_POWER_KEY)) {
            float legacySteamPower = tag.getFloat(LEGACY_STEAM_POWER_KEY);
            generatedSpeed = MAX_RPM * Math.min(legacySteamPower, 1.0F);
            generatedCapacitySu = FullSteamConfig.baseEngineCapacity() * legacySteamPower;
            sourceMode = SourceMode.DIRECT_BOILER;
        }
        status = tag.contains(STATUS_KEY) ? tag.getString(STATUS_KEY) : "Incomplete structure";
    }

    private void tickClientEffects() {
        if (steamSoundCooldown > 0) {
            steamSoundCooldown--;
        }

        if (!isEngineRunning()) {
            previousEffectAngle = Float.NaN;
            return;
        }

        float angle = getCurrentEffectAngle();
        if (!Float.isNaN(previousEffectAngle)
                && steamSoundCooldown <= 0
                && crossedCrankPhase(previousEffectAngle, angle)) {
            emitSteamEffects();
            steamSoundCooldown = MIN_STEAM_SOUND_INTERVAL_TICKS;
        }
        previousEffectAngle = angle;
    }

    private float getCurrentEffectAngle() {
        FullSteamPoweredShaftBlockEntity shaft = getShaft();
        if (shaft == null) {
            return 0;
        }

        float radians = KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), getShaftAxis());
        radians += getAnimationPhaseOffset();
        return Mth.positiveModulo((float) Math.toDegrees(radians), 360.0F);
    }

    private static boolean crossedCrankPhase(float previousAngle, float angle) {
        float delta = angle - previousAngle;
        return delta > 180.0F || delta < -180.0F;
    }

    private void emitSteamEffects() {
        if (level == null) {
            return;
        }

        float intensity = getEffectIntensity();
        Direction exhaustDirection = getExhaustDirection(getShaftAxis());
        Direction strokeDirection = getStrokeDirection();
        Vec3 normal = Vec3.atLowerCornerOf(exhaustDirection.getNormal());
        Vec3 origin = Vec3.atCenterOf(worldPosition)
                .add(Vec3.atLowerCornerOf(strokeDirection.getNormal()).scale(0.95D))
                .add(normal.scale(0.68D));
        Vec3 jitter = new Vec3(
                (level.random.nextDouble() - 0.5D) * 0.08D,
                (level.random.nextDouble() - 0.5D) * 0.04D,
                (level.random.nextDouble() - 0.5D) * 0.08D
        );
        Vec3 motion = normal.scale(0.26D + 0.12D * intensity)
                .add(0.0D, 0.05D + 0.04D * intensity, 0.0D)
                .add(jitter);

        level.addParticle(
                new SteamJetParticleData(0.65F + 0.35F * intensity),
                origin.x,
                origin.y,
                origin.z,
                motion.x,
                motion.y,
                motion.z
        );

        if (intensity >= 0.75F) {
            Vec3 secondaryOrigin = origin.add(
                    (level.random.nextDouble() - 0.5D) * 0.16D,
                    (level.random.nextDouble() - 0.5D) * 0.08D,
                    (level.random.nextDouble() - 0.5D) * 0.16D
            );
            level.addParticle(
                    new SteamJetParticleData(0.45F + 0.25F * intensity),
                    secondaryOrigin.x,
                    secondaryOrigin.y,
                    secondaryOrigin.z,
                    motion.x * 0.8D,
                    motion.y * 0.8D,
                    motion.z * 0.8D
            );
        }

        float volume = 0.34F + 0.18F * intensity;
        float pitch = 0.8F + (level.random.nextFloat() - 0.5F) * 0.04F;
        AllSoundEvents.STEAM.playAt(level, origin, volume, pitch, false);
    }

    private float getEffectIntensity() {
        float speedFactor = Math.abs(getGeneratedSpeed()) / MAX_RPM;
        float heatFactor = heatUnits / (float) MAX_HEAT_UNITS;
        return Mth.clamp(Math.max(speedFactor, heatFactor), 0.25F, 1.0F);
    }

    private static Direction getExhaustDirection(Direction.Axis shaftAxis) {
        return shaftAxis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
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
            float targetCapacitySu
    ) {
        private static SteamOutput none(SourceMode sourceMode) {
            return new SteamOutput(sourceMode, 0, 0, false, 0, 0);
        }

        private float generatedSpeed() {
            return canRun() ? rpmForActiveBurners(activeBurners) : 0;
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
