package dev.gustavo.fullsteamahead.content.crankshaft;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
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
    private static final String BOILER_EFFICIENCY_KEY = "BoilerEfficiency";
    private static final String STATUS_KEY = "Status";
    private static final String COUNTED_BOILER_POS_KEY = "CountedBoilerPos";
    private static final String COUNTED_ENGINE_COUNT_KEY = "CountedEngineCount";

    private static final float DEFAULT_RPM = 32.0F;
    private static final float BASE_CAPACITY = 1024.0F;
    private static final float NO_FLYWHEEL_CAPACITY = 0.6F;

    private boolean assembled;
    private BlockPos ringOrigin;
    private BlockPos cylinderRootPos;
    private BlockPos boilerPos;
    private float boilerEfficiency;
    private String status = "Incomplete structure";
    private BlockPos countedBoilerPos;
    private int countedEngineCount;

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

        float previousEfficiency = boilerEfficiency;
        boilerEfficiency = calculateBoilerEfficiency(boiler);
        if (!Mth.equal(previousEfficiency, boilerEfficiency)) {
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

        float previousEfficiency = boilerEfficiency;
        boilerEfficiency = calculateBoilerEfficiency(getBoiler());
        if (changed || !Mth.equal(previousEfficiency, boilerEfficiency)) {
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    public void clearAssembly() {
        markInvalid("Crankshaft removed", null);
    }

    private void markInvalid(String reason, BlockPos skippedPistonPos) {
        boolean changed = assembled || boilerEfficiency != 0
                || ringOrigin != null
                || cylinderRootPos != null
                || boilerPos != null
                || !Objects.equals(status, reason);

        clearPistonStates(skippedPistonPos);
        releaseCountedBoiler();

        assembled = false;
        ringOrigin = null;
        cylinderRootPos = null;
        boilerPos = null;
        boilerEfficiency = 0;
        status = reason;

        if (changed) {
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return assembled && boilerEfficiency > 0 ? DEFAULT_RPM : 0;
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
        tooltip.add(Component.literal("Boiler efficiency: " + Math.round(boilerEfficiency * 100.0F) + "%")
                .withStyle(boilerEfficiency > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("RPM: " + Math.round(getGeneratedSpeed()))
                .withStyle(getGeneratedSpeed() > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Capacity: " + Math.round(getTargetCapacitySu()) + " SU")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Flywheel: absent (60% capacity)").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    private float calculateBoilerEfficiency(FluidTankBlockEntity boiler) {
        if (boiler == null || boiler.boiler == null) {
            return 0;
        }

        BoilerData data = boiler.boiler;
        data.updateTemperature(boiler);

        int tankSize = Math.max(1, boiler.getTotalTankSize());
        int consumers = countThisEngine(data);
        if (data.isPassive(tankSize)) {
            return 0.125F / consumers;
        }
        if (data.activeHeat <= 0) {
            return 0;
        }

        int effectiveHeat = Math.min(
                data.activeHeat,
                Math.min(data.getMaxHeatLevelForBoilerSize(tankSize), data.getMaxHeatLevelForWaterSupply())
        );
        return Mth.clamp(effectiveHeat / (float) consumers, 0, 1);
    }

    private int countThisEngine(BoilerData data) {
        int currentConsumers = Math.max(0, data.attachedEngines);
        if (countedBoilerPos != null
                && Objects.equals(countedBoilerPos, boilerPos)
                && countedEngineCount > 0
                && currentConsumers >= countedEngineCount) {
            return currentConsumers;
        }

        int consumers = currentConsumers + 1;
        data.attachedEngines = consumers;
        data.needsHeatLevelUpdate = true;
        countedBoilerPos = boilerPos;
        countedEngineCount = consumers;
        return consumers;
    }

    private void releaseCountedBoiler() {
        if (level == null || countedBoilerPos == null) {
            countedBoilerPos = null;
            countedEngineCount = 0;
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(countedBoilerPos);
        if (blockEntity instanceof FluidTankBlockEntity tank && tank.boiler != null) {
            FluidTankBlockEntity controller = tank.getControllerBE();
            FluidTankBlockEntity boiler = controller == null ? tank : controller;
            if (boiler.boiler.attachedEngines == countedEngineCount && countedEngineCount > 0) {
                boiler.boiler.attachedEngines = Math.max(0, countedEngineCount - 1);
            }
            boiler.boiler.evaluate(boiler);
        }

        countedBoilerPos = null;
        countedEngineCount = 0;
    }

    private float getTargetCapacitySu() {
        return BASE_CAPACITY * boilerEfficiency * NO_FLYWHEEL_CAPACITY;
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
        tag.putFloat(BOILER_EFFICIENCY_KEY, boilerEfficiency);
        tag.putString(STATUS_KEY, status);
        writePos(tag, COUNTED_BOILER_POS_KEY, countedBoilerPos);
        tag.putInt(COUNTED_ENGINE_COUNT_KEY, countedEngineCount);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        assembled = tag.getBoolean(ASSEMBLED_KEY);
        ringOrigin = readPos(tag, RING_ORIGIN_KEY);
        cylinderRootPos = readPos(tag, CYLINDER_ROOT_KEY);
        boilerPos = readPos(tag, BOILER_POS_KEY);
        boilerEfficiency = tag.getFloat(BOILER_EFFICIENCY_KEY);
        status = tag.contains(STATUS_KEY) ? tag.getString(STATUS_KEY) : "Incomplete structure";
        countedBoilerPos = readPos(tag, COUNTED_BOILER_POS_KEY);
        countedEngineCount = tag.getInt(COUNTED_ENGINE_COUNT_KEY);
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
}
