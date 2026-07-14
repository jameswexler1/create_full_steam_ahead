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

    // Ticks after placement/load during which we re-assert the generated rotation if the
    // kinetic network has not picked it up yet. See tick().
    private static final int INITIAL_SYNC_TICKS = 5;
    private static final float MIN_SPEED_UPDATE_RPM = 0.01F;
    private static final float MIN_CAPACITY_UPDATE_SU = 1.0F;

    private BlockPos enginePos;
    private float generatedSpeed;
    private float generatedCapacitySu;
    private int initialTicks = INITIAL_SYNC_TICKS;

    public FullSteamPoweredShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWERED_SHAFT.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide() || initialTicks <= 0) {
            return;
        }
        initialTicks--;
        // This block entity is created the instant the engine assembles, which happens
        // inside a block-place event before the entity has been attached to the kinetic
        // network. The engine's initial power push can therefore be lost, leaving the
        // engine assembled but never turning until it is broken and replaced. While the
        // shaft is freshly placed or loaded, re-assert the generated rotation whenever we
        // should be producing speed but the network still reports a standstill, so
        // activation is deterministic instead of timing-dependent.
        if (getGeneratedSpeed() != 0 && getSpeed() == 0) {
            updateGeneratedRotation();
        }
    }

    public void update(BlockPos engineWorldPos, float speed, float capacitySu) {
        BlockPos relativeEnginePos = worldPosition.subtract(engineWorldPos);
        boolean ownerChanged = !relativeEnginePos.equals(enginePos);
        boolean speedChanged = ownerChanged
                || meaningfullyChanged(generatedSpeed, speed, MIN_SPEED_UPDATE_RPM);
        boolean capacityChanged = ownerChanged
                || meaningfullyChanged(generatedCapacitySu, capacitySu, MIN_CAPACITY_UPDATE_SU);
        if (!speedChanged && !capacityChanged) {
            return;
        }

        enginePos = relativeEnginePos;
        if (speedChanged) {
            generatedSpeed = speed;
            generatedCapacitySu = capacitySu;
            updateGeneratedRotation();
        } else {
            // Capacity can vary while the 1 RPM floor keeps speed fixed. Refresh stress without
            // asking Create to detach and re-propagate an otherwise unchanged kinetic source.
            generatedCapacitySu = capacitySu;
            if (hasNetwork() && getGeneratedSpeed() != 0.0F) {
                notifyStressCapacityChange(calculateAddedStressCapacity());
            }
        }
        notifyUpdate();
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

    private static boolean meaningfullyChanged(float previous, float next, float minimumDelta) {
        if (Mth.equal(previous, next)) {
            return false;
        }
        if (!Float.isFinite(previous) || !Float.isFinite(next)) {
            return true;
        }
        if (previous == 0.0F || next == 0.0F || Math.signum(previous) != Math.signum(next)) {
            return true;
        }
        return Math.abs(previous - next) >= minimumDelta;
    }

    public BlockPos getEngineWorldPos() {
        return enginePos == null ? null : worldPosition.subtract(enginePos);
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
        if (!clientPacket) {
            initialTicks = INITIAL_SYNC_TICKS;
        }
    }
}
