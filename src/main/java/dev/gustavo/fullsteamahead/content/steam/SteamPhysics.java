package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.util.Mth;

/**
 * Pressure/volume/temperature steam model shared by the direct (compact) engine and the
 * piped engine path.
 *
 * <p>Two boiler quantities drive everything: a vessel {@code volume} (Create Fluid Tank block
 * count) and a {@code heatRatio} (how hot the boiler is for its size, 0..heatRatioMax). From those:
 * <ul>
 *   <li>steam {@code production = steamPerBlock * volume * heatRatio} mB/t,</li>
 *   <li>pressure {@code p = heatRatio * maxVolumeReference / volume} (1.0 at a maxed 3x3x3 boiler),</li>
 *   <li>{@code RPM = clamp(rpmAtMaxVolume * p, 0, maxRpm)} — pressure picks the speed.</li>
 * </ul>
 * Stress is consumption-limited: a cylinder draws at most {@code cylinderMaxIntakeMb} mB/t, giving at
 * most {@code cylinderMaxSu}. So a bigger boiler delivers more steam (more SU, up to the cap) at lower
 * pressure (lower RPM); a small boiler delivers little (low SU) at high pressure (high RPM). A maxed
 * 3x3x3 boiler exactly feeds one cylinder (90 mB/t -> 147456 SU, 16 RPM). Anything producing more than
 * is consumed is surplus that builds boiler pressure (see overpressure handling).</p>
 *
 * <p>Every number here is config-backed ({@link FullSteamConfig}) so the balance can be tuned freely.</p>
 */
public final class SteamPhysics {

    /**
     * Heat factor for production and pressure. 0 when the boiler is cold or dry, otherwise 1.0 so the
     * tiers stay purely volume-driven regardless of boiler size. Clamped to heatRatioMax, leaving room
     * for a future super-heat (blaze cake) bonus above 1.0.
     */
    public static double heatRatio(int activeHeat, int waterMaxHeat) {
        if (activeHeat <= 0 || waterMaxHeat <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, FullSteamConfig.steamHeatRatioMax());
    }

    /** Pressure ratio (1.0 at a full-heat maxed boiler); higher for smaller/hotter boilers. 0 when cold. */
    public static float pressureRatio(double heatRatio, double volumeBlocks) {
        if (heatRatio <= 0.0D || volumeBlocks <= 0.0D) {
            return 0.0F;
        }
        double vMax = Math.max(1.0D, FullSteamConfig.steamMaxVolumeReference());
        return (float) (heatRatio * vMax / volumeBlocks);
    }

    /** Continuous RPM from a pressure ratio, clamped to [0, maxRpm]. */
    public static float rpmFromPressure(float pressureRatio) {
        if (pressureRatio <= 0.0F) {
            return 0.0F;
        }
        return (float) Mth.clamp(
                FullSteamConfig.steamRpmAtMaxVolume() * pressureRatio,
                0.0D,
                FullSteamConfig.steamMaxRpm()
        );
    }

    /** Steam a boiler produces per tick from its volume and heat ratio. */
    public static int productionMb(double heatRatio, double volumeBlocks) {
        if (heatRatio <= 0.0D || volumeBlocks <= 0.0D) {
            return 0;
        }
        return (int) Math.round(FullSteamConfig.steamPerBlock() * volumeBlocks * heatRatio);
    }

    /** Stress capacity from the steam a cylinder actually consumes, capped at cylinderMaxSu. */
    public static float suForConsumed(int consumedMb) {
        if (consumedMb <= 0) {
            return 0.0F;
        }
        return Math.min((float) FullSteamConfig.cylinderMaxSu(), consumedMb * FullSteamConfig.suPerSteamMb());
    }

    /** Boiler vessel volume in blocks for a square Create Fluid Tank ({@code width^2 * height}). */
    public static int boilerVolume(int width, int height) {
        return Math.max(1, width) * Math.max(1, width) * Math.max(1, height);
    }

    private SteamPhysics() {
    }
}
