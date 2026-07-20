package dev.gustavo.fullsteamahead.content.shaft;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.gustavo.fullsteamahead.content.piston.EngineLinkageContinuity;
import dev.gustavo.fullsteamahead.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class FullSteamPoweredShaftBlockEntity extends GeneratingKineticBlockEntity {
    private static final String ENGINE_POS_KEY = "EnginePos";
    private static final String GENERATED_SPEED_KEY = "GeneratedSpeed";
    private static final String TARGET_SPEED_KEY = "TargetSpeed";
    private static final String GENERATED_CAPACITY_KEY = "GeneratedCapacity";

    // Ticks after placement/load during which we re-assert the generated rotation if the
    // kinetic network has not picked it up yet. See tick().
    private static final int INITIAL_SYNC_TICKS = 5;
    private static final float MIN_CAPACITY_UPDATE_SU = 1.0F;

    private BlockPos enginePos;
    private float targetSpeed;
    private float generatedSpeed;
    private float generatedCapacitySu;
    private float coordinatedOwnerSpeed;
    private float lastNotifiedCapacitySu;
    private int initialTicks = INITIAL_SYNC_TICKS;
    private long lastAppliedGameTime = Long.MIN_VALUE;
    private long lastTargetChangeGameTime = Long.MIN_VALUE;

    public FullSteamPoweredShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWERED_SHAFT.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        BlockPos engineWorldPos = getEngineWorldPos();
        if (level != null && engineWorldPos != null) {
            EngineLinkageContinuity.restoreDuringInitialLoad(level, engineWorldPos, worldPosition);
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        restoreLinkageContinuity();
    }

    @Override
    public void tick() {
        // Restore a coordinated owner command before Create's first post-load kinetic validation.
        // This matters when the saved topology owner has no personal steam but another loaded engine
        // in the same bank is still an active capacity source.
        if (level != null && !level.isClientSide() && initialTicks > 0 && getTheoreticalSpeed() != 0.0F) {
            ActiveKineticNetworkRetimer.prepareNetworkCommand(this);
        }
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (initialTicks > 0) {
            initialTicks--;
            // This block entity is created the instant the engine assembles, which happens
            // inside a block-place event before the entity has been attached to the kinetic
            // network. Re-assert fresh non-zero output until Create has picked it up.
            if (getGeneratedSpeed() != 0 && getSpeed() == 0) {
                propagateGeneratedRotation(gameTime);
            } else if (getGeneratedSpeed() == 0.0F
                    && getTheoreticalSpeed() != 0.0F) {
                // Saved Create network speed and FSA generator NBT are separate snapshots. Retry
                // reconciliation during the short load window so the end-of-tick coordinator can
                // dismantle an all-zero cyclic source graph once every member is available. A real
                // external generator remains on Create's normal passive-drive path.
                propagateGeneratedRotation(gameTime);
            }
        }

        if (KineticSpeedUpdatePolicy.shouldApplyDeferredUpdate(
                generatedSpeed,
                targetSpeed,
                gameTime,
                lastAppliedGameTime,
                lastTargetChangeGameTime
        )) {
            applyTargetSpeed(gameTime);
        }
    }

    public void update(BlockPos engineWorldPos, float speed, float capacitySu) {
        BlockPos relativeEnginePos = worldPosition.subtract(engineWorldPos);
        boolean ownerChanged = !relativeEnginePos.equals(enginePos);
        speed = finiteOrZero(speed);
        capacitySu = finiteOrZero(capacitySu);
        boolean targetSpeedChanged = !KineticSpeedUpdatePolicy.sameSpeed(targetSpeed, speed);
        boolean capacityChanged = ownerChanged
                || meaningfullyChanged(lastNotifiedCapacitySu, capacitySu, MIN_CAPACITY_UPDATE_SU);
        boolean appliedSpeedRequiresReconciliation =
                KineticSpeedUpdatePolicy.requiresImmediateUpdate(generatedSpeed, speed);
        if (!ownerChanged
                && !targetSpeedChanged
                && !capacityChanged
                && !appliedSpeedRequiresReconciliation) {
            return;
        }

        enginePos = relativeEnginePos;
        if (ownerChanged) {
            restoreLinkageContinuity();
        }
        targetSpeed = speed;
        generatedCapacitySu = capacitySu;
        long gameTime = level == null ? 0L : level.getGameTime();
        if (targetSpeedChanged) {
            lastTargetChangeGameTime = gameTime;
        }

        boolean immediateSpeedUpdate = ownerChanged
                || appliedSpeedRequiresReconciliation;
        boolean propagated = immediateSpeedUpdate && applyTargetSpeed(gameTime);
        if (!propagated && capacityChanged) {
            // Keep SU responsive while the applied RPM is coalesced. Updating network capacity does
            // not detach the kinetic source or reset adjacent Create fluid networks.
            if (hasNetwork() && getGeneratedSpeed() != 0.0F) {
                notifyStressCapacityChange(calculateAddedStressCapacity());
            }
            lastNotifiedCapacitySu = generatedCapacitySu;
        }
        setChanged();
        notifyUpdate();
    }

    public void remove(BlockPos engineWorldPos) {
        if (!isPoweredBy(engineWorldPos)) {
            return;
        }
        boolean changed = enginePos != null || generatedSpeed != 0 || generatedCapacitySu != 0;
        enginePos = null;
        targetSpeed = 0;
        generatedSpeed = 0;
        generatedCapacitySu = 0;
        lastNotifiedCapacitySu = 0;
        if (changed) {
            propagateGeneratedRotation(level == null ? 0L : level.getGameTime());
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

    private boolean applyTargetSpeed(long gameTime) {
        if (KineticSpeedUpdatePolicy.sameSpeed(generatedSpeed, targetSpeed)) {
            return false;
        }
        generatedSpeed = targetSpeed;
        propagateGeneratedRotation(gameTime);
        return true;
    }

    private void propagateGeneratedRotation(long gameTime) {
        lastAppliedGameTime = gameTime;
        lastNotifiedCapacitySu = generatedCapacitySu;
        setChanged();
        KineticNetworkUpdateCoordinator.schedule(this);
    }

    void flushScheduledRotation() {
        lastNotifiedCapacitySu = generatedCapacitySu;
        updateGeneratedRotation();
    }

    void refreshGeneratedCapacity(KineticNetwork network) {
        lastNotifiedCapacitySu = generatedCapacitySu;
        network.updateCapacityFor(this, calculateAddedStressCapacity());
    }

    @Override
    public void updateGeneratedRotation() {
        if (level != null && !level.isClientSide()) {
            ActiveKineticNetworkRetimer.prepareNetworkCommand(this);
        }
        super.updateGeneratedRotation();
    }

    @Override
    public void applyNewSpeed(float previousSpeed, float speed) {
        if (ActiveKineticNetworkRetimer.tryRetime(this, previousSpeed)) {
            return;
        }
        super.applyNewSpeed(previousSpeed, speed);
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    public BlockPos getEngineWorldPos() {
        return enginePos == null ? null : worldPosition.subtract(enginePos);
    }

    private void restoreLinkageContinuity() {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos engineWorldPos = getEngineWorldPos();
        if (engineWorldPos != null) {
            EngineLinkageContinuity.restoreFromPoweredShaft(level, engineWorldPos, worldPosition);
        }
    }

    @Override
    public float getGeneratedSpeed() {
        if (coordinatedOwnerSpeed != 0.0F
                && network != null
                && network.longValue() == worldPosition.asLong()) {
            return coordinatedOwnerSpeed;
        }
        return getIndividualGeneratedSpeed();
    }

    float getIndividualGeneratedSpeed() {
        return generatedCapacitySu > 0.0F ? generatedSpeed : 0.0F;
    }

    void setCoordinatedOwnerSpeed(float speed) {
        coordinatedOwnerSpeed = finiteOrZero(speed);
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
        tag.putFloat(TARGET_SPEED_KEY, targetSpeed);
        tag.putFloat(GENERATED_CAPACITY_KEY, generatedCapacitySu);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        enginePos = tag.contains(ENGINE_POS_KEY) ? BlockPos.of(tag.getLong(ENGINE_POS_KEY)) : null;
        generatedSpeed = tag.getFloat(GENERATED_SPEED_KEY);
        targetSpeed = tag.contains(TARGET_SPEED_KEY) ? tag.getFloat(TARGET_SPEED_KEY) : generatedSpeed;
        generatedCapacitySu = tag.getFloat(GENERATED_CAPACITY_KEY);
        if (!clientPacket) {
            lastNotifiedCapacitySu = generatedCapacitySu;
            coordinatedOwnerSpeed = 0.0F;
            initialTicks = INITIAL_SYNC_TICKS;
            lastAppliedGameTime = Long.MIN_VALUE;
            lastTargetChangeGameTime = Long.MIN_VALUE;
        }
    }
}
