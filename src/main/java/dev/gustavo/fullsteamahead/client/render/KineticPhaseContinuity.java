package dev.gustavo.fullsteamahead.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.gustavo.fullsteamahead.content.piston.PistonHeadBlockEntity;
import dev.gustavo.fullsteamahead.content.shaft.FullSteamPoweredShaftBlockEntity;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Keeps Create's absolute-time kinetic animation formula phase-continuous while an FSA source
 * changes speed. State is client-only and weakly keyed so chunk unloads do not retain block entities.
 */
public final class KineticPhaseContinuity {
    private static final float LINKAGE_ZERO_SPEED_GRACE_TICKS = 3.0F;
    private static final float MIN_RATIO_SPEED = 1.0E-5F;
    private static final int SOURCE_SCAN_LIMIT = 128;
    private static final long INACTIVE_PROBE_INTERVAL_TICKS = 20L;
    private static final long NETWORK_CLEANUP_INTERVAL_TICKS = 200L;
    private static final long NETWORK_STATE_TTL_TICKS = 1200L;

    private static final Object PHASE_LOCK = new Object();
    private static final Map<Level, LevelPhaseStates> NETWORK_STATES = new WeakHashMap<>();
    private static final Map<KineticBlockEntity, ComponentState> COMPONENT_STATES = new WeakHashMap<>();
    private static final Map<PistonHeadBlockEntity, LinkageState> LINKAGE_STATES = new WeakHashMap<>();

    /**
     * Returns an integer-degree correction suitable for Create's getRotationAngleOffset hook.
     * Create and Flywheel both consume that hook, so one correction covers both render paths.
     */
    public static int rotationOffsetDegrees(KineticBlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null || !level.isClientSide()) {
            return 0;
        }

        float speed = finiteOrZero(blockEntity.getSpeed());
        long gameTime = level.getGameTime();
        synchronized (PHASE_LOCK) {
            ComponentState component = COMPONENT_STATES.computeIfAbsent(
                    blockEntity,
                    ignored -> new ComponentState(speed)
            );
            boolean speedChanged = !Mth.equal(component.lastSpeed, speed);
            Long networkId = blockEntity.network;
            boolean networkChanged = !Objects.equals(component.observedNetworkId, networkId);
            KineticBlockEntity reference = component.reference.get();
            boolean referenceUnavailable = reference == null || reference.isRemoved();
            boolean probeDue = networkChanged
                    || referenceUnavailable
                    || gameTime >= component.nextProbeGameTime
                    || !component.fullSteamDriven && speedChanged;

            if (probeDue) {
                NetworkContext context = resolveNetworkContext(blockEntity);
                component.updateContext(networkId, context, gameTime);
                reference = component.reference.get();
            }

            component.lastSpeed = speed;
            if (!component.fullSteamDriven || reference == null || reference.isRemoved()) {
                return 0;
            }

            LevelPhaseStates levelStates = NETWORK_STATES.computeIfAbsent(
                    level,
                    ignored -> new LevelPhaseStates()
            );
            levelStates.cleanup(gameTime);
            NetworkPhaseEntry entry = levelStates.networks.get(component.networkKey);
            KineticBlockEntity trackedReference = entry == null ? null : entry.reference.get();
            if (trackedReference != null && !trackedReference.isRemoved()) {
                reference = trackedReference;
                component.reference = new WeakReference<>(reference);
            }

            float referenceSpeed = finiteOrZero(reference.getSpeed());
            float ratio = component.resolveSpeedRatio(
                    component.networkKey,
                    speed,
                    referenceSpeed,
                    blockEntity == reference
            );
            if (entry == null || trackedReference == null || trackedReference.isRemoved()) {
                double seedCorrection = component.referenceCorrectionSeed(ratio);
                entry = new NetworkPhaseEntry(reference, referenceSpeed, seedCorrection, gameTime);
                levelStates.networks.put(component.networkKey, entry);
            }

            float renderTime = AnimationTickHolder.getRenderTime(level);
            entry.phase.observeReferenceSpeed(referenceSpeed, renderTime);
            entry.lastAccessGameTime = gameTime;
            double localCorrection = entry.phase.correctionForRatio(ratio);
            component.recordCorrection(component.networkKey, localCorrection);
            return entry.phase.roundedCorrectionForRatio(ratio);
        }
    }

    /**
     * Supplies the corrected crank angle and masks the brief zero-speed state Create can expose
     * while a still-powered source is being re-propagated through its kinetic network.
     */
    public static float linkageAngle(
            PistonHeadBlockEntity engine,
            FullSteamPoweredShaftBlockEntity shaft,
            Direction.Axis shaftAxis
    ) {
        Level level = engine.getLevel();
        if (level == null) {
            return 0.0F;
        }

        float renderTime = AnimationTickHolder.getRenderTime(level);
        float shaftSpeed = finiteOrZero(shaft.getSpeed());
        synchronized (LINKAGE_STATES) {
            LinkageState state = LINKAGE_STATES.computeIfAbsent(engine, ignored -> new LinkageState());
            if (shaftSpeed != 0.0F) {
                float angle = KineticBlockEntityRenderer.getAngleForBe(
                        shaft,
                        shaft.getBlockPos(),
                        shaftAxis
                );
                state.lastAngle = angle;
                state.lastMovingRenderTime = renderTime;
                state.hasMovingAngle = true;
                return angle;
            }

            float timeSinceMoving = renderTime - state.lastMovingRenderTime;
            boolean transientStop = state.hasMovingAngle
                    && (engine.isEngineRunning()
                    || timeSinceMoving >= 0.0F && timeSinceMoving <= LINKAGE_ZERO_SPEED_GRACE_TICKS);
            if (transientStop) {
                return state.lastAngle;
            }

            state.hasMovingAngle = false;
            state.lastAngle = 0.0F;
            return 0.0F;
        }
    }

    private static NetworkContext resolveNetworkContext(KineticBlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return null;
        }

        Long networkId = blockEntity.network;
        KineticBlockEntity networkOwner = kineticEntityAt(level, networkId == null ? null : BlockPos.of(networkId));
        if (networkOwner instanceof FullSteamPoweredShaftBlockEntity) {
            return new NetworkContext(networkId, networkOwner);
        }
        if (blockEntity instanceof FullSteamPoweredShaftBlockEntity) {
            return new NetworkContext(networkId, networkOwner == null ? blockEntity : networkOwner);
        }

        KineticBlockEntity cursor = blockEntity;
        for (int depth = 0; depth < SOURCE_SCAN_LIMIT; depth++) {
            if (cursor instanceof FullSteamPoweredShaftBlockEntity) {
                return new NetworkContext(networkId, networkOwner == null ? cursor : networkOwner);
            }
            BlockPos sourcePos = cursor.source;
            if (sourcePos == null || !level.isLoaded(sourcePos)) {
                return null;
            }
            BlockEntity source = level.getBlockEntity(sourcePos);
            if (source instanceof FullSteamPoweredShaftBlockEntity) {
                return new NetworkContext(networkId, networkOwner == null
                        ? (FullSteamPoweredShaftBlockEntity) source
                        : networkOwner);
            }
            if (!(source instanceof KineticBlockEntity kineticSource) || kineticSource == cursor) {
                return null;
            }
            cursor = kineticSource;
        }
        return null;
    }

    private static KineticBlockEntity kineticEntityAt(Level level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof KineticBlockEntity kinetic ? kinetic : null;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static final class ComponentState {
        private float lastSpeed;
        private Long observedNetworkId;
        private long nextProbeGameTime;
        private boolean fullSteamDriven;
        private long networkKey;
        private WeakReference<KineticBlockEntity> reference = new WeakReference<>(null);
        private float speedRatio = 1.0F;
        private long ratioNetworkKey;
        private boolean hasSpeedRatio;
        private double localCorrectionDegrees;
        private boolean hasCorrection;

        private ComponentState(float lastSpeed) {
            this.lastSpeed = lastSpeed;
        }

        private void updateContext(Long networkId, NetworkContext context, long gameTime) {
            observedNetworkId = networkId;
            nextProbeGameTime = gameTime + INACTIVE_PROBE_INTERVAL_TICKS;
            fullSteamDriven = context != null;
            if (context == null) {
                reference = new WeakReference<>(null);
                return;
            }
            networkKey = context.networkKey;
            reference = new WeakReference<>(context.reference);
        }

        private float resolveSpeedRatio(
                long currentNetworkKey,
                float localSpeed,
                float referenceSpeed,
                boolean isReference
        ) {
            if (isReference) {
                speedRatio = 1.0F;
                ratioNetworkKey = currentNetworkKey;
                hasSpeedRatio = true;
                return speedRatio;
            }
            if (hasSpeedRatio && ratioNetworkKey == currentNetworkKey) {
                return speedRatio;
            }
            if (Math.abs(localSpeed) >= MIN_RATIO_SPEED && Math.abs(referenceSpeed) >= MIN_RATIO_SPEED) {
                float candidate = localSpeed / referenceSpeed;
                if (Float.isFinite(candidate)) {
                    speedRatio = candidate;
                    ratioNetworkKey = currentNetworkKey;
                    hasSpeedRatio = true;
                    return speedRatio;
                }
            }
            return 1.0F;
        }

        private double referenceCorrectionSeed(float ratio) {
            if (!hasCorrection || Math.abs(ratio) < MIN_RATIO_SPEED) {
                return 0.0D;
            }
            return localCorrectionDegrees / ratio;
        }

        private void recordCorrection(long currentNetworkKey, double correctionDegrees) {
            networkKey = currentNetworkKey;
            localCorrectionDegrees = correctionDegrees;
            hasCorrection = true;
        }
    }

    private static final class LevelPhaseStates {
        private final Map<Long, NetworkPhaseEntry> networks = new HashMap<>();
        private long nextCleanupGameTime;

        private void cleanup(long gameTime) {
            if (gameTime < nextCleanupGameTime) {
                return;
            }
            long oldestRetainedTick = gameTime - NETWORK_STATE_TTL_TICKS;
            networks.values().removeIf(entry -> entry.reference.get() == null
                    || entry.lastAccessGameTime < oldestRetainedTick);
            nextCleanupGameTime = gameTime + NETWORK_CLEANUP_INTERVAL_TICKS;
        }
    }

    private static final class NetworkPhaseEntry {
        private final WeakReference<KineticBlockEntity> reference;
        private final KineticNetworkPhaseState phase;
        private long lastAccessGameTime;

        private NetworkPhaseEntry(
                KineticBlockEntity reference,
                float referenceSpeed,
                double seedCorrection,
                long gameTime
        ) {
            this.reference = new WeakReference<>(reference);
            phase = new KineticNetworkPhaseState(referenceSpeed, seedCorrection);
            lastAccessGameTime = gameTime;
        }
    }

    private record NetworkContext(long networkKey, KineticBlockEntity reference) {
        private NetworkContext(Long networkId, KineticBlockEntity reference) {
            this(networkId == null ? reference.getBlockPos().asLong() : networkId, reference);
        }
    }

    private static final class LinkageState {
        private float lastAngle;
        private float lastMovingRenderTime;
        private boolean hasMovingAngle;
    }

    private KineticPhaseContinuity() {
    }
}
