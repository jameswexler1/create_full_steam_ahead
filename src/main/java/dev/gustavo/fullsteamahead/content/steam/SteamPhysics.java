package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.util.Mth;

/**
 * Unified ideal-gas steam model. ONE physical pressure drives everything.
 *
 * <p>A boiler/engine is a vessel of volume {@code V} (tank blocks) holding {@code n} mB of steam at
 * temperature {@code T} (Kelvin, from the fire). The ideal gas law gives a single pressure:</p>
 *
 * <pre>P = gasConstant * n * T / V        (bar)</pre>
 *
 * <p>That one pressure: drives engine RPM and SU (both rise with pressure — pure single-cylinder
 * physics, power scales with P), sets how fast the engine draws steam (flow ∝ P, which makes pressure
 * self-regulate to an equilibrium where production = draw), how fast an open pipe vents (relief ∝ P),
 * and triggers the warning + burst when it climbs too high. Steam stored but not drawn or vented
 * raises {@code n}, raising {@code P} toward the burst threshold.</p>
 *
 * <p>Every constant is config-backed ({@link FullSteamConfig}) so the balance can be tuned freely.</p>
 */
public final class SteamPhysics {

    /** Vessel temperature in Kelvin from water-gated boiler heat (0 heat -> base/boiling temperature). */
    public static double temperatureK(int waterGatedHeat) {
        double base = FullSteamConfig.steamTempBaseK();
        if (waterGatedHeat <= 0) {
            return base;
        }
        return base + waterGatedHeat * FullSteamConfig.steamTempPerHeatK();
    }

    /** Ideal-gas pressure in bar from stored steam (mB), temperature (K) and vessel volume (blocks). */
    public static double pressureBar(double storedMb, double temperatureK, double volumeBlocks) {
        if (storedMb <= 0.0D || volumeBlocks <= 0.0D) {
            return 0.0D;
        }
        return FullSteamConfig.steamGasConstant() * storedMb * temperatureK / volumeBlocks;
    }

    /** How hard the boiler is fired: 1.0 = normal, &gt;1 = super-heated (cakes), clamped to heatFactorMax. */
    public static double heatFactor(int waterGatedHeat) {
        if (waterGatedHeat <= 0) {
            return 0.0D;
        }
        double nominal = Math.max(1, FullSteamConfig.steamHeatNominal());
        return Mth.clamp(waterGatedHeat / nominal, 0.0D, FullSteamConfig.steamHeatFactorMax());
    }

    /** Steam (mB) a boiler boils per tick, scaling with vessel volume and how hard it is fired. */
    public static int productionMb(int volumeBlocks, double heatFactor) {
        if (heatFactor <= 0.0D || volumeBlocks <= 0) {
            return 0;
        }
        return (int) Math.round(FullSteamConfig.steamPerBlock() * volumeBlocks * heatFactor);
    }

    /** Engine RPM from pressure, clamped to [0, maxRpm]. */
    public static float rpm(double pressureBar) {
        if (pressureBar <= 0.0D) {
            return 0.0F;
        }
        return (float) Mth.clamp(FullSteamConfig.steamRpmPerBar() * pressureBar, 0.0D, FullSteamConfig.steamMaxRpm());
    }

    /** Engine stress capacity (SU) from pressure, clamped to [0, suMax]. */
    public static float su(double pressureBar) {
        if (pressureBar <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.min(FullSteamConfig.steamSuPerBar() * pressureBar, FullSteamConfig.steamSuMax());
    }

    /** Steam (mB/t) an engine draws at the given pressure, capped at the per-cylinder intake. */
    public static int engineDrawMb(double pressureBar) {
        if (pressureBar <= 0.0D) {
            return 0;
        }
        int flow = (int) Math.round(FullSteamConfig.steamFlowPerBar() * pressureBar);
        return Math.min(FullSteamConfig.steamMaxIntakeMb(), Math.max(0, flow));
    }

    /** Steam (mB/t) an open pipe end vents at the given pressure (relief). */
    public static int ventMb(double pressureBar) {
        if (pressureBar <= 0.0D) {
            return 0;
        }
        return Math.max(0, (int) Math.round(FullSteamConfig.steamVentPerBar() * pressureBar));
    }

    /** Boiler vessel volume in blocks for a square Create Fluid Tank ({@code width^2 * height}). */
    public static int boilerVolume(int width, int height) {
        return Math.max(1, width) * Math.max(1, width) * Math.max(1, height);
    }

    private SteamPhysics() {
    }
}
