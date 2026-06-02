package dev.gustavo.fullsteamahead.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side balance configuration for Create: Full Steam Ahead.
 *
 * <p>All values are read at runtime from block-entity ticks, which run after the world's server
 * config has loaded. The accessors still guard against an early read (before load) by returning
 * the documented defaults, and they recompute the derived rates so any configured
 * {@link #steamPerHeatUnit()} stays consistent across the boiler outlet, pipe pressure, inlet
 * intake cap, and engine power output.</p>
 */
public final class FullSteamConfig {
    // Fixed engine constants (not part of the Phase 9 config surface).
    public static final float SU_PER_HEAT_UNIT = 16_384.0F;
    public static final int MAX_PIPED_HEAT_UNITS = 9;

    private static final int DEFAULT_BASE_ENGINE_CAPACITY = 147_456;
    private static final int DEFAULT_STEAM_PER_HEAT_UNIT = 10;
    private static final int DEFAULT_PRESSURE_RANGE = 30;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue BASE_ENGINE_CAPACITY;
    private static final ModConfigSpec.IntValue STEAM_PER_HEAT_UNIT;
    private static final ModConfigSpec.IntValue BOILER_OUTLET_PRESSURE_RANGE;
    private static final ModConfigSpec.BooleanValue ENABLE_DIRECT_COMPACT_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Balance settings for Create: Full Steam Ahead engines").push("balance");

        BASE_ENGINE_CAPACITY = builder
                .comment("Stress Units a single full engine provides at maximum output.")
                .defineInRange("baseEngineCapacity", DEFAULT_BASE_ENGINE_CAPACITY, 1, 1_000_000_000);

        STEAM_PER_HEAT_UNIT = builder
                .comment("Steam in mB/t produced and consumed per heat unit (1 unit = this many mB/t).",
                        "Scales boiler outlet production, inlet intake, pipe pressure, and engine output together.")
                .defineInRange("steamPerHeatUnit", DEFAULT_STEAM_PER_HEAT_UNIT, 1, 100_000);

        BOILER_OUTLET_PRESSURE_RANGE = builder
                .comment("How many blocks along a pipe network a boiler outlet pressurizes steam toward engines.")
                .defineInRange("boilerOutletPressureRange", DEFAULT_PRESSURE_RANGE, 1, 512);

        ENABLE_DIRECT_COMPACT_MODE = builder
                .comment("Allow upright engines to run directly from a compact boiler.",
                        "When false, engines must be fed steam through pipes via a steam inlet.")
                .define("enableDirectCompactMode", true);

        builder.pop();
        SPEC = builder.build();
    }

    private static boolean loaded() {
        return SPEC.isLoaded();
    }

    public static int baseEngineCapacity() {
        return loaded() ? BASE_ENGINE_CAPACITY.get() : DEFAULT_BASE_ENGINE_CAPACITY;
    }

    public static int steamPerHeatUnit() {
        return loaded() ? STEAM_PER_HEAT_UNIT.get() : DEFAULT_STEAM_PER_HEAT_UNIT;
    }

    public static int boilerOutletPressureRange() {
        return loaded() ? BOILER_OUTLET_PRESSURE_RANGE.get() : DEFAULT_PRESSURE_RANGE;
    }

    public static boolean directCompactModeEnabled() {
        return !loaded() || ENABLE_DIRECT_COMPACT_MODE.get();
    }

    /** Stress Units per mB of steam consumed, derived from the fixed SU-per-unit and the steam rate. */
    public static float suPerSteamMb() {
        return SU_PER_HEAT_UNIT / steamPerHeatUnit();
    }

    /** Maximum steam in mB/t one engine draws, and the per-inlet intake cap: {@code 9 * steamPerHeatUnit}. */
    public static int maxPipedSteamPerTick() {
        return MAX_PIPED_HEAT_UNITS * steamPerHeatUnit();
    }

    private FullSteamConfig() {
    }
}
