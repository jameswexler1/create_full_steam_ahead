package dev.gustavo.fullsteamahead.client;

import dev.gustavo.fullsteamahead.content.steam.SteamAdmissionValveControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Maps the valve's real controller cell back to its owning pipe-body block. */
public final class AdmissionValveTargeting {
    public static BlockHitResult redirectControllerHit(BlockGetter level, HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult controllerHit)
                || controllerHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos controllerPos = controllerHit.getBlockPos();
        BlockState controllerState = level.getBlockState(controllerPos);
        if (!(controllerState.getBlock() instanceof SteamAdmissionValveControllerBlock)
                || SteamAdmissionValveControllerBlock.getOwningValve(
                        level, controllerPos, controllerState) == null) {
            return null;
        }

        BlockPos basePos = SteamAdmissionValveControllerBlock.getBasePos(controllerPos, controllerState);
        Vec3 location = normalizeInteractionLocation(controllerHit.getLocation(), basePos);
        return new BlockHitResult(
                location,
                controllerHit.getDirection(),
                basePos,
                controllerHit.isInside()
        );
    }

    private static Vec3 normalizeInteractionLocation(Vec3 location, BlockPos basePos) {
        return new Vec3(
                clampToUsePacket(location.x, basePos.getX()),
                clampToUsePacket(location.y, basePos.getY()),
                clampToUsePacket(location.z, basePos.getZ())
        );
    }

    private static double clampToUsePacket(double coordinate, int baseCoordinate) {
        return Math.max(baseCoordinate - 0.499999D, Math.min(baseCoordinate + 1.499999D, coordinate));
    }

    private AdmissionValveTargeting() {
    }
}
