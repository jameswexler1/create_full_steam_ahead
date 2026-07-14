package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.util.Mth;

/**
 * Per-network ideal-gas steam model (see {@code new_steam_physics.md}).
 *
 * <p>Pressure ({@code pN/m^2}) comes from the steam stored in a connected network, its temperature
 * and its volume: {@code P = gasConstant * storedMb * temperatureK / volumeM3}. Engine output is
 * gated by BOTH pressure and delivered flow: a normally supplied engine sits at {@code P_RATED} and
 * makes full output; weak pressure or short flow scale it down. Boiler production scales with heat
 * and boiler height (a bigger boiler feeds more engines, it does not make one engine stronger).</p>
 */
public final class SteamPhysics {

    /** Steam temperature (Kelvin) from water-gated usable heat units (fractional from smoothing). */
    public static double temperatureK(double usableHeatUnits) {
        double base = FullSteamConfig.steamTemperatureBaseK();
        if (usableHeatUnits <= 0.0D) {
            return base;
        }
        return base + usableHeatUnits * FullSteamConfig.steamTemperaturePerHeatK();
    }

    /** Ideal-gas pressure in pN/m^2 from stored steam (mB), temperature (K) and network volume (m^3). */
    public static double pressurePn(double storedMb, double temperatureK, double volumeM3) {
        if (storedMb <= 0.0D || volumeM3 <= 0.0D) {
            return 0.0D;
        }
        return FullSteamConfig.steamGasConstant() * storedMb * temperatureK / volumeM3;
    }

    /** Steam boiled per tick by a boiler: usable heat * height * steamPerHeatUnit (10 mB/t per unit). */
    public static int productionMb(double usableHeatUnits, int boilerHeight) {
        if (usableHeatUnits <= 0.0D || boilerHeight <= 0) {
            return 0;
        }
        return (int) Math.floor(usableHeatUnits * boilerHeight * FullSteamConfig.steamPerHeatUnit());
    }

    /** Steam (mB/t) one engine wants at a given pressure: full flow scaled by the pressure factor. */
    public static int requestedFlowMb(double pressurePn) {
        float pf = SteamPressure.pressureFactor(pressurePn);
        return Math.round(FullSteamConfig.steamFullEngineFlowMb() * pf);
    }

    /** Engine output factor 0..1 = min(pressure factor, flow factor). */
    public static float outputFactor(double pressurePn, int consumedMb) {
        float pf = SteamPressure.pressureFactor(pressurePn);
        float ff = Mth.clamp(consumedMb / (float) Math.max(1, FullSteamConfig.steamFullEngineFlowMb()), 0.0F, 1.0F);
        return Math.min(pf, ff);
    }

    /** Continuous stress capacity (SU) from the output factor, capped at the full-engine rating. */
    public static float su(float outputFactor) {
        return Mth.clamp(outputFactor, 0.0F, 1.0F) * FullSteamConfig.steamFullEngineSu();
    }

    /** Continuous engine speed from output factor, with a 1 RPM floor for positive output. */
    public static float rpm(float outputFactor) {
        if (!Float.isFinite(outputFactor) || outputFactor <= 0.0F) {
            return 0.0F;
        }
        float factor = Mth.clamp(outputFactor, 0.0F, 1.0F);
        return Math.max(1.0F, factor * (float) FullSteamConfig.steamMaxRpm());
    }

    /** Steam (mB/t) an open pipe end vents at a given pressure (relief, scales with pressure factor). */
    public static int ventMb(double pressurePn) {
        float pf = SteamPressure.pressureFactor(pressurePn);
        return Math.max(0, Math.round((float) FullSteamConfig.steamVentCoefficient() * pf));
    }

    /**
     * Steam mass that must be removed to bring a network down to the requested pressure.
     * Used by open pipe ends, which are atmospheric relief paths rather than small consumers.
     */
    public static int drainToPressureMb(double storedMb, double temperatureK, double volumeM3, double targetPressurePn) {
        if (storedMb <= 0.0D || temperatureK <= 0.0D || volumeM3 <= 0.0D) {
            return 0;
        }
        if (targetPressurePn <= 0.0D) {
            return (int) Math.min(Integer.MAX_VALUE, Math.ceil(storedMb));
        }

        double targetStoredMb = targetPressurePn * volumeM3 / (FullSteamConfig.steamGasConstant() * temperatureK);
        double toDrain = storedMb - targetStoredMb;
        if (toDrain <= 0.0D) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(toDrain));
    }

    /** Explosion power for a normal overpressure burst, scaling with network/boiler volume. */
    public static float burstPower(double volumeM3) {
        return burstPower(volumeM3, FullSteamConfig.steamBurstPressure());
    }

    /** Explosion power for a rupture, scaling with network/boiler volume and current pressure. */
    public static float burstPower(double volumeM3, double pressurePn) {
        double power = FullSteamConfig.overpressureBasePower()
                + FullSteamConfig.overpressurePowerPerVolume() * volumeM3;
        double capped = Math.min(FullSteamConfig.overpressureMaxPower(), power);
        double burstPressure = FullSteamConfig.steamBurstPressure();
        double pressureScale = burstPressure <= 0.0D ? 1.0D : Mth.clamp(pressurePn / burstPressure, 0.0D, 1.0D);
        return (float) (capped * FullSteamConfig.overpressurePowerScale() * pressureScale);
    }

    /**
     * First-order (exponential) approach of {@code current} toward {@code target} over a time constant
     * {@code tauTicks}. After one tau the value has moved ~63% of the way. tau &lt;= 0 snaps instantly.
     */
    public static double approachExp(double current, double target, double tauTicks) {
        if (tauTicks <= 0.0D) {
            return target;
        }
        double alpha = 1.0D - Math.exp(-1.0D / tauTicks);
        return current + (target - current) * alpha;
    }

    /** Square Create Fluid Tank volume in blocks (m^3). */
    public static int boilerVolume(int width, int height) {
        return Math.max(1, width) * Math.max(1, width) * Math.max(1, height);
    }

    private SteamPhysics() {
    }
}
