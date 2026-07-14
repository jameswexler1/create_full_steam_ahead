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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps Create's absolute-time kinetic animation formula phase-continuous while an FSA source
 * changes speed. State is client-only and weakly keyed so chunk unloads do not retain block entities.
 */
public final class KineticPhaseContinuity {
    private static final float DEGREES_PER_TICK_PER_RPM = 0.3F;
    private static final float LINKAGE_ZERO_SPEED_GRACE_TICKS = 3.0F;
    private static final int SOURCE_SCAN_LIMIT = 128;
    private static final long INACTIVE_PROBE_INTERVAL_TICKS = 20L;

    private static final Map<KineticBlockEntity, KineticState> KINETIC_STATES = new WeakHashMap<>();
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
        synchronized (KINETIC_STATES) {
            KineticState state = KINETIC_STATES.computeIfAbsent(
                    blockEntity,
                    ignored -> new KineticState(speed)
            );
            boolean speedChanged = !Mth.equal(state.lastSpeed, speed);

            if (!state.fullSteamDriven) {
                boolean probeDue = speedChanged || gameTime >= state.nextProbeGameTime;
                if (!probeDue) {
                    return 0;
                }
                state.nextProbeGameTime = gameTime + INACTIVE_PROBE_INTERVAL_TICKS;
                state.fullSteamDriven = isFullSteamDriven(blockEntity);
                if (!state.fullSteamDriven) {
                    state.lastSpeed = speed;
                    return 0;
                }

                // Preserve Create's current phase when first observing an already-running network.
                state.lastSpeed = speed;
                return Math.round(state.correctionDegrees);
            }

            if (speedChanged) {
                float renderTime = AnimationTickHolder.getRenderTime(level);
                double correction = state.correctionDegrees
                        + (double) renderTime
                        * (state.lastSpeed - speed)
                        * DEGREES_PER_TICK_PER_RPM;
                state.correctionDegrees = Mth.wrapDegrees((float) correction);
                state.lastSpeed = speed;
            }

            return Math.round(state.correctionDegrees);
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

    private static boolean isFullSteamDriven(KineticBlockEntity blockEntity) {
        if (blockEntity instanceof FullSteamPoweredShaftBlockEntity) {
            return true;
        }

        Level level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }

        BlockPos networkSource = networkSource(blockEntity);
        if (networkSource != null && level.isLoaded(networkSource)) {
            BlockEntity networkOwner = level.getBlockEntity(networkSource);
            if (networkOwner != null) {
                // Create's generating network id is its source position. This is the common path
                // and avoids walking every ordinary Create network once per probe interval.
                return networkOwner instanceof FullSteamPoweredShaftBlockEntity;
            }
        }

        KineticBlockEntity cursor = blockEntity;
        for (int depth = 0; depth < SOURCE_SCAN_LIMIT; depth++) {
            BlockPos sourcePos = cursor.source;
            if (sourcePos == null || !level.isLoaded(sourcePos)) {
                return false;
            }
            BlockEntity source = level.getBlockEntity(sourcePos);
            if (source instanceof FullSteamPoweredShaftBlockEntity) {
                return true;
            }
            if (!(source instanceof KineticBlockEntity kineticSource) || kineticSource == cursor) {
                return false;
            }
            cursor = kineticSource;
        }
        return false;
    }

    private static BlockPos networkSource(KineticBlockEntity blockEntity) {
        return blockEntity.network == null ? null : BlockPos.of(blockEntity.network);
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static final class KineticState {
        private float lastSpeed;
        private float correctionDegrees;
        private long nextProbeGameTime;
        private boolean fullSteamDriven;

        private KineticState(float lastSpeed) {
            this.lastSpeed = lastSpeed;
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
