package dev.gustavo.fullsteamahead.content.shaft;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class FullSteamPoweredShaftBlockEntity extends GeneratingKineticBlockEntity {
    private static final String ENGINE_POS_KEY = "EnginePos";
    private static final String GENERATED_SPEED_KEY = "GeneratedSpeed";
    private static final String GENERATED_CAPACITY_KEY = "GeneratedCapacity";

    private BlockPos enginePos;
    private float generatedSpeed;
    private float generatedCapacitySu;

    public FullSteamPoweredShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWERED_SHAFT.get(), pos, state);
    }

    public void update(BlockPos engineWorldPos, float speed, float capacitySu) {
        BlockPos relativeEnginePos = worldPosition.subtract(engineWorldPos);
        boolean changed = !relativeEnginePos.equals(enginePos)
                || !Mth.equal(generatedSpeed, speed)
                || !Mth.equal(generatedCapacitySu, capacitySu);
        enginePos = relativeEnginePos;
        generatedSpeed = speed;
        generatedCapacitySu = capacitySu;
        if (changed) {
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    public void remove(BlockPos engineWorldPos) {
        if (!isPoweredBy(engineWorldPos)) {
            return;
        }
        boolean changed = enginePos != null || generatedSpeed != 0 || generatedCapacitySu != 0;
        enginePos = null;
        generatedSpeed = 0;
        generatedCapacitySu = 0;
        if (changed) {
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    public boolean isPoweredBy(BlockPos engineWorldPos) {
        return enginePos != null && worldPosition.subtract(engineWorldPos).equals(enginePos);
    }

    @Override
    public float getGeneratedSpeed() {
        return generatedCapacitySu > 0 ? generatedSpeed : 0;
    }

    @Override
    public float calculateAddedStressCapacity() {
        float speed = Math.abs(getGeneratedSpeed());
        if (speed == 0) {
            lastCapacityProvided = 0;
            return 0;
        }

        lastCapacityProvided = generatedCapacitySu / speed;
        return lastCapacityProvided;
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = 0;
        return 0;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (enginePos != null) {
            tag.putLong(ENGINE_POS_KEY, enginePos.asLong());
        } else {
            tag.remove(ENGINE_POS_KEY);
        }
        tag.putFloat(GENERATED_SPEED_KEY, generatedSpeed);
        tag.putFloat(GENERATED_CAPACITY_KEY, generatedCapacitySu);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        enginePos = tag.contains(ENGINE_POS_KEY) ? BlockPos.of(tag.getLong(ENGINE_POS_KEY)) : null;
        generatedSpeed = tag.getFloat(GENERATED_SPEED_KEY);
        generatedCapacitySu = tag.getFloat(GENERATED_CAPACITY_KEY);
    }
}
