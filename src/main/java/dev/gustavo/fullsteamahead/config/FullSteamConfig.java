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

    private static final boolean DEFAULT_STEAM_LEAK_DAMAGE_ENABLED = true;
    private static final int DEFAULT_STEAM_LEAK_DAMAGE_INTERVAL = 10;
    private static final double DEFAULT_STEAM_LEAK_DAMAGE_RADIUS = 0.5D;
    private static final double DEFAULT_STEAM_LEAK_BASE_DAMAGE = 6.0D;
    private static final int DEFAULT_STEAM_LEAK_DAMAGE_REFERENCE_MB = 1_000;
    private static final double DEFAULT_STEAM_LEAK_MAX_DAMAGE = 20.0D;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue BASE_ENGINE_CAPACITY;
    private static final ModConfigSpec.IntValue STEAM_PER_HEAT_UNIT;
    private static final ModConfigSpec.IntValue BOILER_OUTLET_PRESSURE_RANGE;
    private static final ModConfigSpec.BooleanValue ENABLE_DIRECT_COMPACT_MODE;
    private static final ModConfigSpec.BooleanValue STEAM_LEAK_DAMAGE_ENABLED;
    private static final ModConfigSpec.IntValue STEAM_LEAK_DAMAGE_INTERVAL;
    private static final ModConfigSpec.DoubleValue STEAM_LEAK_DAMAGE_RADIUS;
    private static final ModConfigSpec.DoubleValue STEAM_LEAK_BASE_DAMAGE;
    private static final ModConfigSpec.IntValue STEAM_LEAK_DAMAGE_REFERENCE_MB;
    private static final ModConfigSpec.DoubleValue STEAM_LEAK_MAX_DAMAGE;

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

        builder.comment("Damage dealt by steam leaking from open pipe ends").push("steamLeak");

        STEAM_LEAK_DAMAGE_ENABLED = builder
                .comment("Whether the steam cloud from an open pipe end scalds nearby entities.")
                .define("steamLeakDamageEnabled", DEFAULT_STEAM_LEAK_DAMAGE_ENABLED);

        STEAM_LEAK_DAMAGE_INTERVAL = builder
                .comment("How often (in ticks) entities in a steam cloud are scalded.")
                .defineInRange("steamLeakDamageInterval", DEFAULT_STEAM_LEAK_DAMAGE_INTERVAL, 1, 200);

        STEAM_LEAK_DAMAGE_RADIUS = builder
                .comment("How far (in blocks) the open pipe's leak area is expanded to match the visible cloud.")
                .defineInRange("steamLeakDamageRadius", DEFAULT_STEAM_LEAK_DAMAGE_RADIUS, 0.0D, 4.0D);

        STEAM_LEAK_BASE_DAMAGE = builder
                .comment("Minimum scald damage (in half-hearts: 2.0 = 1 heart) dealt per hit by any steam leak.",
                        "Leaks venting more than steamLeakDamageReferenceMb scale up from here toward steamLeakMaxDamage.")
                .defineInRange("steamLeakBaseDamage", DEFAULT_STEAM_LEAK_BASE_DAMAGE, 0.0D, 1_000.0D);

        STEAM_LEAK_DAMAGE_REFERENCE_MB = builder
                .comment("The leaking steam amount in mB above which damage scales beyond steamLeakBaseDamage.")
                .defineInRange("steamLeakDamageReferenceMb", DEFAULT_STEAM_LEAK_DAMAGE_REFERENCE_MB, 1, 1_000_000);

        STEAM_LEAK_MAX_DAMAGE = builder
                .comment("Upper cap on scald damage per hit regardless of leak size.")
                .defineInRange("steamLeakMaxDamage", DEFAULT_STEAM_LEAK_MAX_DAMAGE, 0.0D, 1_000.0D);

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

    public static boolean steamLeakDamageEnabled() {
        return loaded() ? STEAM_LEAK_DAMAGE_ENABLED.get() : DEFAULT_STEAM_LEAK_DAMAGE_ENABLED;
    }

    public static int steamLeakDamageInterval() {
        return loaded() ? STEAM_LEAK_DAMAGE_INTERVAL.get() : DEFAULT_STEAM_LEAK_DAMAGE_INTERVAL;
    }

    public static double steamLeakDamageRadius() {
        return loaded() ? STEAM_LEAK_DAMAGE_RADIUS.get() : DEFAULT_STEAM_LEAK_DAMAGE_RADIUS;
    }

    public static double steamLeakBaseDamage() {
        return loaded() ? STEAM_LEAK_BASE_DAMAGE.get() : DEFAULT_STEAM_LEAK_BASE_DAMAGE;
    }

    public static int steamLeakDamageReferenceMb() {
        return loaded() ? STEAM_LEAK_DAMAGE_REFERENCE_MB.get() : DEFAULT_STEAM_LEAK_DAMAGE_REFERENCE_MB;
    }

    public static double steamLeakMaxDamage() {
        return loaded() ? STEAM_LEAK_MAX_DAMAGE.get() : DEFAULT_STEAM_LEAK_MAX_DAMAGE;
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
