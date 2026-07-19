package dev.gustavo.fullsteamahead.client;

import dev.gustavo.fullsteamahead.content.steam.SteamAdmissionValveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Extends vanilla block picking into the adjacent cell occupied by the valve's tall controller.
 */
public final class AdmissionValveTargeting {
    private static final double DISTANCE_EPSILON = 1.0E-7D;

    public static BlockHitResult findCloserTowerHit(
            BlockGetter level,
            Vec3 start,
            Vec3 end,
            HitResult vanillaHit
    ) {
        Ray ray = new Ray(level, start, end);
        BlockHitResult towerHit = BlockGetter.traverseBlocks(
                start,
                end,
                ray,
                AdmissionValveTargeting::hitTowerInTraversedCell,
                ignored -> null
        );
        if (towerHit == null) {
            return null;
        }

        double towerDistance = towerHit.getLocation().distanceToSqr(start);
        if (vanillaHit != null
                && vanillaHit.getType() != HitResult.Type.MISS
                && vanillaHit.getLocation().distanceToSqr(start) <= towerDistance + DISTANCE_EPSILON) {
            return null;
        }
        return normalizeInteractionLocation(towerHit);
    }

    private static BlockHitResult hitTowerInTraversedCell(Ray ray, BlockPos traversedPos) {
        BlockHitResult upright = clipValveTower(ray, traversedPos.below(), false);
        BlockHitResult inverted = clipValveTower(ray, traversedPos.above(), true);
        if (upright == null) {
            return inverted;
        }
        if (inverted == null) {
            return upright;
        }
        return upright.getLocation().distanceToSqr(ray.start())
                <= inverted.getLocation().distanceToSqr(ray.start())
                ? upright
                : inverted;
    }

    private static BlockHitResult clipValveTower(Ray ray, BlockPos basePos, boolean inverted) {
        BlockState state = ray.level().getBlockState(basePos);
        if (!(state.getBlock() instanceof SteamAdmissionValveBlock)
                || state.getValue(SteamAdmissionValveBlock.INVERTED) != inverted) {
            return null;
        }

        VoxelShape shape = state.getShape(ray.level(), basePos, CollisionContext.empty());
        return shape.clip(ray.start(), ray.end(), basePos);
    }

    private static BlockHitResult normalizeInteractionLocation(BlockHitResult hit) {
        BlockPos basePos = hit.getBlockPos();
        Vec3 location = hit.getLocation();
        // Vanilla validates use packets against a two-block-tall cube centered on the reported base.
        // Keep the real X/Z and hit face, but project the overhanging cap onto that accepted range.
        double y = Math.max(basePos.getY() - 0.5D, Math.min(basePos.getY() + 1.5D, location.y));
        if (y == location.y) {
            return hit;
        }
        return new BlockHitResult(
                new Vec3(location.x, y, location.z),
                hit.getDirection(),
                basePos,
                hit.isInside()
        );
    }

    private record Ray(BlockGetter level, Vec3 start, Vec3 end) {
    }

    private AdmissionValveTargeting() {
    }
}
