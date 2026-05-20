package dev.gustavo.fullsteamahead.content.crankshaft;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.gustavo.fullsteamahead.content.piston.PistonSection;
import dev.gustavo.fullsteamahead.content.piston.SteamPistonBlock;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import dev.gustavo.fullsteamahead.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class CrankshaftBlockEntity extends GeneratingKineticBlockEntity {
    private static final String ASSEMBLED_KEY = "Assembled";
    private static final String RING_ORIGIN_KEY = "RingOrigin";
    private static final String CYLINDER_ROOT_KEY = "CylinderRoot";
    private static final String BOILER_POS_KEY = "BoilerPos";
    private static final String ACTIVE_BURNERS_KEY = "ActiveBurners";
    private static final String HEAT_UNITS_KEY = "HeatUnits";
    private static final String GENERATED_SPEED_KEY = "GeneratedSpeed";
    private static final String GENERATED_CAPACITY_KEY = "GeneratedCapacity";
    private static final String WATER_SUPPLY_KEY = "WaterSupply";
    private static final String LEGACY_STEAM_POWER_KEY = "SteamPower";
    private static final String STATUS_KEY = "Status";

    private static final int MAX_ACTIVE_BURNERS = 9;
    private static final int MAX_HEAT_UNITS = 18;
    private static final float SU_PER_HEAT_UNIT = 16_384.0F;
    private static final float REGULAR_MAX_CAPACITY_SU = 147_456.0F;
    private static final float MAX_RPM = 64.0F;

    private boolean assembled;
    private BlockPos ringOrigin;
    private BlockPos cylinderRootPos;
    private BlockPos boilerPos;
    private int activeBurners;
    private int heatUnits;
    private float generatedSpeed;
    private float generatedCapacitySu;
    private boolean hasWaterSupply;
    private String status = "Incomplete structure";

    public CrankshaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRANKSHAFT.get(), pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
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
        if (level == null || level.isClientSide()) {
            return;
        }

        if (!assembled) {
            return;
        }

        FluidTankBlockEntity boiler = getBoiler();
        if (boiler == null || boiler.boiler == null) {
            markInvalid("Missing Create fluid tank boiler", null);
            return;
        }

        SteamOutput output = calculateSteamOutput(boiler);
        if (applySteamOutput(output)) {
            updateGeneratedRotation();
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

        CrankshaftValidator.Result result = CrankshaftValidator.validate(level, worldPosition);
        if (!result.valid()) {
            markInvalid(result.message(), null);
            return;
        }

        boolean changed = !assembled
                || !Objects.equals(ringOrigin, result.ringOrigin())
                || !Objects.equals(cylinderRootPos, result.cylinderRoot())
                || !Objects.equals(boilerPos, result.boilerPos())
                || !Objects.equals(status, result.message());

        assembled = true;
        ringOrigin = result.ringOrigin();
        cylinderRootPos = result.cylinderRoot();
        boilerPos = result.boilerPos();
        status = result.message();

        setPistonsAssembled(result);

        FluidTankBlockEntity boiler = getBoiler();
        refreshBoilerState(boiler);

        SteamOutput output = calculateSteamOutput(boiler);
        boolean outputChanged = applySteamOutput(output);
        if (changed || outputChanged) {
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    public void clearAssembly() {
        markInvalid("Crankshaft removed", null);
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
                || !Objects.equals(status, reason);

        clearPistonStates(skippedPistonPos);

        assembled = false;
        ringOrigin = null;
        cylinderRootPos = null;
        boilerPos = null;
        activeBurners = 0;
        heatUnits = 0;
        generatedSpeed = 0;
        generatedCapacitySu = 0;
        hasWaterSupply = false;
        status = reason;

        refreshBoilerState(previousBoiler);

        if (changed) {
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return assembled ? generatedSpeed : 0;
    }

    @Override
    public float calculateAddedStressCapacity() {
        float speed = Math.abs(getGeneratedSpeed());
        if (speed == 0) {
            lastCapacityProvided = 0;
            return 0;
        }

        lastCapacityProvided = getTargetCapacitySu() / speed;
        return lastCapacityProvided;
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = 0;
        return 0;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        tooltip.add(Component.literal("Full Steam Crankshaft").withStyle(ChatFormatting.GRAY));
        if (!assembled) {
            tooltip.add(Component.literal(status).withStyle(ChatFormatting.RED));
            return true;
        }

        tooltip.add(Component.literal("Engine assembled").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("Active burners: " + activeBurners + "/" + MAX_ACTIVE_BURNERS)
                .withStyle(activeBurners > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Heat units: " + heatUnits + "/" + MAX_HEAT_UNITS)
                .withStyle(heatUnits > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Water supply: " + (hasWaterSupply ? "available" : "missing"))
                .withStyle(hasWaterSupply ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("RPM: " + Math.round(getGeneratedSpeed()))
                .withStyle(getGeneratedSpeed() > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Capacity: " + Math.round(getTargetCapacitySu()) + " SU")
                .withStyle(getTargetCapacitySu() > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Flywheel: deferred").withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    private SteamOutput calculateSteamOutput(FluidTankBlockEntity boiler) {
        if (boiler == null || boiler.boiler == null) {
            return SteamOutput.NONE;
        }

        BoilerData data = boiler.boiler;
        boiler.updateBoilerTemperature();
        if (data.needsHeatLevelUpdate) {
            data.updateTemperature(boiler);
        }

        BurnerHeat burnerHeat = scanBurners();
        boolean hasWater = data.getMaxHeatLevelForWaterSupply() > 0;
        return new SteamOutput(burnerHeat.activeBurners(), burnerHeat.heatUnits(), hasWater);
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
        boolean changed = activeBurners != output.activeBurners()
                || heatUnits != output.heatUnits()
                || hasWaterSupply != output.hasWaterSupply()
                || !Mth.equal(generatedSpeed, speed)
                || !Mth.equal(generatedCapacitySu, capacity);

        activeBurners = output.activeBurners();
        heatUnits = output.heatUnits();
        hasWaterSupply = output.hasWaterSupply();
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

    private void refreshBoilerState(FluidTankBlockEntity boiler) {
        if (boiler == null || boiler.isRemoved()) {
            return;
        }

        boiler.updateBoilerState();
    }

    private void setPistonsAssembled(CrankshaftValidator.Result result) {
        setPiston(result.insideLow(), true, PistonSection.INSIDE_LOW);
        setPiston(result.insideHigh(), true, PistonSection.INSIDE_HIGH);
        setPiston(result.protrudeLow(), true, PistonSection.PROTRUDE_LOW);
        setPiston(result.protrudeHigh(), true, PistonSection.PROTRUDE_HIGH);
    }

    private void clearPistonStates(BlockPos skippedPistonPos) {
        CrankshaftValidator.PistonPositions pistons = CrankshaftValidator.pistonPositions(worldPosition);
        clearPiston(pistons.insideLow(), skippedPistonPos);
        clearPiston(pistons.insideHigh(), skippedPistonPos);
        clearPiston(pistons.protrudeLow(), skippedPistonPos);
        clearPiston(pistons.protrudeHigh(), skippedPistonPos);
    }

    private void clearPiston(BlockPos pos, BlockPos skippedPistonPos) {
        if (pos.equals(skippedPistonPos)) {
            return;
        }
        setPiston(pos, false, PistonSection.INSIDE_LOW);
    }

    private void setPiston(BlockPos pos, boolean assembled, PistonSection section) {
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
        if (newState != state) {
            level.setBlock(pos, newState, Block.UPDATE_ALL);
        }
    }

    public static void revalidateNearbyCrankshafts(Level level, BlockPos changedPos) {
        forNearbyCrankshafts(level, changedPos, CrankshaftBlockEntity::revalidateStructure);
    }

    public static void revalidateAt(Level level, BlockPos pos) {
        if (level.isClientSide() || !level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.CRANKSHAFT.get())) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CrankshaftBlockEntity crankshaft) {
            crankshaft.revalidateStructure();
        }
    }

    public static void invalidateNearbyCrankshafts(Level level, BlockPos changedPos, String reason, BlockPos skippedPistonPos) {
        forNearbyCrankshafts(level, changedPos, be -> be.markInvalid(reason, skippedPistonPos));
    }

    private static void forNearbyCrankshafts(
            Level level,
            BlockPos changedPos,
            Consumer<CrankshaftBlockEntity> action
    ) {
        if (level.isClientSide()) {
            return;
        }

        for (BlockPos pos : CrankshaftValidator.candidateCrankshaftsNear(changedPos)) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.CRANKSHAFT.get())) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CrankshaftBlockEntity crankshaft) {
                action.accept(crankshaft);
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
        tag.putInt(ACTIVE_BURNERS_KEY, activeBurners);
        tag.putInt(HEAT_UNITS_KEY, heatUnits);
        tag.putFloat(GENERATED_SPEED_KEY, generatedSpeed);
        tag.putFloat(GENERATED_CAPACITY_KEY, generatedCapacitySu);
        tag.putBoolean(WATER_SUPPLY_KEY, hasWaterSupply);
        tag.putString(STATUS_KEY, status);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        assembled = tag.getBoolean(ASSEMBLED_KEY);
        ringOrigin = readPos(tag, RING_ORIGIN_KEY);
        cylinderRootPos = readPos(tag, CYLINDER_ROOT_KEY);
        boilerPos = readPos(tag, BOILER_POS_KEY);
        activeBurners = tag.getInt(ACTIVE_BURNERS_KEY);
        heatUnits = tag.getInt(HEAT_UNITS_KEY);
        generatedSpeed = tag.getFloat(GENERATED_SPEED_KEY);
        generatedCapacitySu = tag.getFloat(GENERATED_CAPACITY_KEY);
        hasWaterSupply = tag.getBoolean(WATER_SUPPLY_KEY);
        if (generatedSpeed == 0 && generatedCapacitySu == 0 && tag.contains(LEGACY_STEAM_POWER_KEY)) {
            float legacySteamPower = tag.getFloat(LEGACY_STEAM_POWER_KEY);
            generatedSpeed = MAX_RPM * Math.min(legacySteamPower, 1.0F);
            generatedCapacitySu = REGULAR_MAX_CAPACITY_SU * legacySteamPower;
        }
        status = tag.contains(STATUS_KEY) ? tag.getString(STATUS_KEY) : "Incomplete structure";
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

    private record SteamOutput(int activeBurners, int heatUnits, boolean hasWaterSupply) {
        private static final SteamOutput NONE = new SteamOutput(0, 0, false);

        private float generatedSpeed() {
            return canRun() ? rpmForActiveBurners(activeBurners) : 0;
        }

        private float capacitySu() {
            return canRun() ? heatUnits * SU_PER_HEAT_UNIT : 0;
        }

        private boolean canRun() {
            return hasWaterSupply && activeBurners > 0 && heatUnits > 0;
        }
    }
}
