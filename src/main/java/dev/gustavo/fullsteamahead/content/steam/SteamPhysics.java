package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.util.Mth;

/**
 * Pressure/volume/temperature steam model shared by the direct (compact) engine and the
 * piped engine path.
 *
 * <p>Two intrinsic boiler quantities drive everything: a thermal intensity {@code T}
 * (water-gated heat units) and a vessel {@code volume} (boiler tank block count). From those:
 * <ul>
 *   <li>pressure ratio {@code p = (T/T_ref) / (V/V_ref)} — hotter or smaller raises it,</li>
 *   <li>{@code RPM = clamp(RPM_ref * p, 0, 64)} — pressure picks the speed,</li>
 *   <li>{@code SU = SU_ref * (T/T_ref) * (V/V_ref)} — energy/displacement picks the torque.</li>
 * </ul>
 * Because {@code SU * RPM ∝ T^2}, boiler heat sets the power level while boiler shape is a
 * (roughly) power-neutral torque-vs-speed trade: wide/large = high SU & low RPM, tall/thin =
 * high RPM & low SU.</p>
 */
public final class SteamPhysics {
    public static final float MAX_RPM = 64.0F;

    /** Pressure ratio relative to the reference boiler (1.0 == reference). 0 when cold/empty. */
    public static float pressureRatio(double temperatureUnits, double volumeBlocks) {
        if (temperatureUnits <= 0.0D || volumeBlocks <= 0.0D) {
            return 0.0F;
        }
        double tRef = Math.max(1.0D, FullSteamConfig.steamTemperatureReference());
        double vRef = Math.max(1.0D, FullSteamConfig.steamVolumeReference());
        double raw = (temperatureUnits / tRef) / (volumeBlocks / vRef);
        return (float) Mth.clamp(raw, FullSteamConfig.steamPressureMin(), FullSteamConfig.steamPressureMax());
    }

    /** Continuous RPM from a pressure ratio, clamped to [0, 64]. */
    public static float rpm(float pressureRatio) {
        if (pressureRatio <= 0.0F) {
            return 0.0F;
        }
        return (float) Mth.clamp(FullSteamConfig.steamRpmReference() * pressureRatio, 0.0D, MAX_RPM);
    }

    /** Stress capacity for a directly-fed engine, from heat and vessel volume (not pressure-clamped). */
    public static float directCapacitySu(double temperatureUnits, double volumeBlocks) {
        if (temperatureUnits <= 0.0D || volumeBlocks <= 0.0D) {
            return 0.0F;
        }
        double tRef = Math.max(1.0D, FullSteamConfig.steamTemperatureReference());
        double vRef = Math.max(1.0D, FullSteamConfig.steamVolumeReference());
        double su = FullSteamConfig.steamSuReference() * (temperatureUnits / tRef) * (volumeBlocks / vRef);
        return (float) Math.min(su, FullSteamConfig.steamSuMax());
    }

    /** Boiler vessel volume in blocks for a square Create Fluid Tank ({@code width^2 * height}). */
    public static int boilerVolume(int width, int height) {
        return Math.max(1, width) * Math.max(1, width) * Math.max(1, height);
    }

    private SteamPhysics() {
    }
}
