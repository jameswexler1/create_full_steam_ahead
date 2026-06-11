package dev.gustavo.fullsteamahead.content.steam;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.gustavo.fullsteamahead.compat.simulated.SableBurstCompat;
import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import dev.gustavo.fullsteamahead.network.BoilerBurstPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BoilerBurst {
    public static void explode(ServerLevel level, FluidTankBlockEntity boiler, double networkVolumeM3, double pressurePn) {
        Vec3 localCenter = boilerCenter(boiler);
        float power = SteamPhysics.burstPower(networkVolumeM3, pressurePn);
        long seed = level.random.nextLong();
        SableBurstCompat.BurstContext burstContext = SableBurstCompat.resolve(boiler, localCenter);

        Vec3 center = burstContext.worldCenter();
        double effectRadius = FullSteamConfig.overpressureEffectRadius();
        if (effectRadius > 0.0D) {
            PacketDistributor.sendToPlayersNear(
                    level,
                    null,
                    center.x,
                    center.y,
                    center.z,
                    effectRadius,
                    new BoilerBurstPayload(center.x, center.y, center.z, power, networkVolumeM3, seed)
            );
        }
        Level.ExplosionInteraction interaction = FullSteamConfig.overpressureBreaksBlocks()
                ? Level.ExplosionInteraction.BLOCK
                : Level.ExplosionInteraction.NONE;
        level.explode(null, center.x, center.y, center.z, power, interaction);
        if (FullSteamConfig.overpressureBreaksBlocks()) {
            SableBurstCompat.damageSubLevelBlocks(level, burstContext, power, seed);
        }
    }

    private static Vec3 boilerCenter(FluidTankBlockEntity boiler) {
        BlockPos pos = boiler.getBlockPos();
        int width = Math.max(1, boiler.getWidth());
        int height = Math.max(1, boiler.getHeight());
        return new Vec3(pos.getX() + width / 2.0D, pos.getY() + height / 2.0D, pos.getZ() + width / 2.0D);
    }

    private BoilerBurst() {
    }
}
