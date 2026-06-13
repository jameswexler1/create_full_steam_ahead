package dev.gustavo.fullsteamahead.content.steam;

import dev.gustavo.fullsteamahead.config.FullSteamConfig;
import net.minecraft.util.Mth;

/**
 * Player-facing steam pressure unit helpers.
 *
 * <p>Pressure is stored internally as a {@code double} in {@code pN/m^2} (Sable-style: pN is the
 * force unit, pressure is force over area). The rated operating pressure is
 * {@code P_RATED = 1.0 MpN/m^2 = 1_000_000 pN/m^2}. Displays use metric prefixes
 * ({@code pN/m^2}, {@code kpN/m^2}, {@code MpN/m^2}) and never {@code bar}.</p>
 */
public final class SteamPressure {
    public static final double KILO = 1_000.0D;
    public static final double MEGA = 1_000_000.0D;
    public static final double ZERO_EPSILON = 1.0D;

    /** Rated operating pressure: a normally supplied single engine stabilizes here. */
    public static double rated() {
        return FullSteamConfig.steamRatedPressure();
    }

    /** clamp(P / P_RATED, 0, 1) — the pressure side of engine output. */
    public static float pressureFactor(double pressurePn) {
        double rated = Math.max(1.0D, rated());
        return (float) Mth.clamp(pressurePn / rated, 0.0D, 1.0D);
    }

    /** Create-style RPM tiers from a 0..1 output factor: 0 / 16 / 32 / 48 / 64. */
    public static float rpmTier(float outputFactor) {
        float max = (float) FullSteamConfig.steamMaxRpm();
        if (outputFactor <= 0.0F) {
            return 0.0F;
        }
        if (outputFactor <= 0.25F) {
            return max * 0.25F;
        }
        if (outputFactor <= 0.50F) {
            return max * 0.50F;
        }
        if (outputFactor <= 0.75F) {
            return max * 0.75F;
        }
        return max;
    }

    /** Format a pN/m^2 value with metric prefixes, e.g. "1.02 MpN/m^2". */
    public static String format(double pressurePn) {
        pressurePn = zeroIfNegligible(pressurePn);
        if (pressurePn >= MEGA) {
            return String.format("%.2f MpN/m²", pressurePn / MEGA);
        }
        if (pressurePn >= KILO) {
            return String.format("%.1f kpN/m²", pressurePn / KILO);
        }
        return String.format("%.0f pN/m²", pressurePn);
    }

    public static boolean isEffectivelyZero(double pressurePn) {
        if (!Double.isFinite(pressurePn)) {
            return true;
        }
        return Math.abs(pressurePn) <= ZERO_EPSILON;
    }

    public static double zeroIfNegligible(double pressurePn) {
        return isEffectivelyZero(pressurePn) ? 0.0D : Math.max(0.0D, pressurePn);
    }

    private SteamPressure() {
    }
}
