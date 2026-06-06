package dev.gustavo.fullsteamahead.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side balance configuration for Create: Full Steam Ahead.
 *
 * <p>Steam uses a per-network ideal-gas pressure model (see {@code new_steam_physics.md}). Pressure
 * is in {@code pN/m^2}; the rated operating point is {@code P_RATED = 1.0 MpN/m^2 = 1_000_000}.
 * Accessors guard against an early read (before the server config loads) by returning defaults.</p>
 */
public final class FullSteamConfig {
    // Fixed engine constants.
    public static final float SU_PER_HEAT_UNIT = 16_384.0F;
    public static final int MAX_PIPED_HEAT_UNITS = 9;

    private static final int DEFAULT_BASE_ENGINE_CAPACITY = 147_456;
    private static final int DEFAULT_STEAM_PER_HEAT_UNIT = 10;
    private static final int DEFAULT_PRESSURE_RANGE = 30;

    // Per-network ideal-gas steam model (P pN/m^2 = gasConstant * storedMb * tempK / volumeM3).
    private static final double DEFAULT_STEAM_GAS_CONSTANT = 1.4D;
    private static final double DEFAULT_STEAM_RATED_PRESSURE = 1_000_000.0D;
    private static final double DEFAULT_STEAM_WARN_PRESSURE = 1_500_000.0D;
    private static final double DEFAULT_STEAM_BURST_PRESSURE = 2_500_000.0D;
    private static final double DEFAULT_STEAM_TEMP_BASE_K = 373.0D;
    private static final double DEFAULT_STEAM_TEMP_PER_HEAT_K = 100.0D;
    private static final int DEFAULT_STEAM_FULL_ENGINE_SU = 147_456;
    private static final int DEFAULT_STEAM_FULL_ENGINE_FLOW_MB = 90;
    private static final double DEFAULT_STEAM_MAX_RPM = 64.0D;
    private static final double DEFAULT_STEAM_VENT_COEFFICIENT = 120.0D;
    private static final int DEFAULT_STEAM_BUFFER_CAP_MB = 256_000;

    private static final boolean DEFAULT_OVERPRESSURE_ENABLED = true;
    private static final double DEFAULT_OVERPRESSURE_BASE_POWER = 4.0D;
    private static final double DEFAULT_OVERPRESSURE_POWER_PER_VOLUME = 0.15D;
    private static final double DEFAULT_OVERPRESSURE_MAX_POWER = 12.0D;
    private static final boolean DEFAULT_OVERPRESSURE_BREAKS_BLOCKS = true;

    private static final boolean DEFAULT_STEAM_LEAK_DAMAGE_ENABLED = true;
    private static final int DEFAULT_STEAM_LEAK_DAMAGE_INTERVAL = 10;
    private static final double DEFAULT_STEAM_LEAK_DAMAGE_RADIUS = 0.75D;
    private static final double DEFAULT_STEAM_LEAK_BASE_DAMAGE = 6.0D;
    private static final int DEFAULT_STEAM_LEAK_DAMAGE_REFERENCE_MB = 1_000;
    private static final double DEFAULT_STEAM_LEAK_MAX_DAMAGE = 20.0D;
    private static final boolean DEFAULT_ENGINE_EXHAUST_ENABLED = true;
    private static final double DEFAULT_ENGINE_EXHAUST_DAMAGE_SCALE = 0.5D;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue BASE_ENGINE_CAPACITY;
    private static final ModConfigSpec.IntValue STEAM_PER_HEAT_UNIT;
    private static final ModConfigSpec.IntValue BOILER_OUTLET_PRESSURE_RANGE;
    private static final ModConfigSpec.BooleanValue ENABLE_DIRECT_COMPACT_MODE;
    private static final ModConfigSpec.DoubleValue STEAM_GAS_CONSTANT;
    private static final ModConfigSpec.DoubleValue STEAM_RATED_PRESSURE;
    private static final ModConfigSpec.DoubleValue STEAM_WARN_PRESSURE;
    private static final ModConfigSpec.DoubleValue STEAM_BURST_PRESSURE;
    private static final ModConfigSpec.DoubleValue STEAM_TEMP_BASE_K;
    private static final ModConfigSpec.DoubleValue STEAM_TEMP_PER_HEAT_K;
    private static final ModConfigSpec.IntValue STEAM_FULL_ENGINE_SU;
    private static final ModConfigSpec.IntValue STEAM_FULL_ENGINE_FLOW_MB;
    private static final ModConfigSpec.DoubleValue STEAM_MAX_RPM;
    private static final ModConfigSpec.DoubleValue STEAM_VENT_COEFFICIENT;
    private static final ModConfigSpec.IntValue STEAM_BUFFER_CAP_MB;
    private static final ModConfigSpec.BooleanValue OVERPRESSURE_ENABLED;
    private static final ModConfigSpec.DoubleValue OVERPRESSURE_BASE_POWER;
    private static final ModConfigSpec.DoubleValue OVERPRESSURE_POWER_PER_VOLUME;
    private static final ModConfigSpec.DoubleValue OVERPRESSURE_MAX_POWER;
    private static final ModConfigSpec.BooleanValue OVERPRESSURE_BREAKS_BLOCKS;
    private static final ModConfigSpec.BooleanValue STEAM_LEAK_DAMAGE_ENABLED;
    private static final ModConfigSpec.IntValue STEAM_LEAK_DAMAGE_INTERVAL;
    private static final ModConfigSpec.DoubleValue STEAM_LEAK_DAMAGE_RADIUS;
    private static final ModConfigSpec.DoubleValue STEAM_LEAK_BASE_DAMAGE;
    private static final ModConfigSpec.IntValue STEAM_LEAK_DAMAGE_REFERENCE_MB;
    private static final ModConfigSpec.DoubleValue STEAM_LEAK_MAX_DAMAGE;
    private static final ModConfigSpec.BooleanValue ENGINE_EXHAUST_ENABLED;
    private static final ModConfigSpec.DoubleValue ENGINE_EXHAUST_DAMAGE_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Balance settings for Create: Full Steam Ahead engines").push("balance");

        BASE_ENGINE_CAPACITY = builder
                .comment("Stress Units a single full engine provides at maximum output.")
                .defineInRange("baseEngineCapacity", DEFAULT_BASE_ENGINE_CAPACITY, 1, 1_000_000_000);

        STEAM_PER_HEAT_UNIT = builder
                .comment("Steam in mB/t produced per usable heat unit per boiler height level (10 = one steam unit).")
                .defineInRange("steamPerHeatUnit", DEFAULT_STEAM_PER_HEAT_UNIT, 1, 100_000);

        BOILER_OUTLET_PRESSURE_RANGE = builder
                .comment("How many blocks along a pipe network steam pressure traverses from a boiler outlet.")
                .defineInRange("boilerOutletPressureRange", DEFAULT_PRESSURE_RANGE, 1, 512);

        ENABLE_DIRECT_COMPACT_MODE = builder
                .comment("Allow upright engines to run directly from a compact boiler.",
                        "When false, engines must be fed steam through pipes via a steam inlet.")
                .define("enableDirectCompactMode", true);

        builder.pop();

        builder.comment("Per-network ideal-gas steam model. Pressure is in pN/m^2; P_RATED = 1.0 MpN/m^2.",
                        "P = gasConstant * storedSteamMb * temperatureK / networkVolumeM3.",
                        "A normally supplied engine stabilizes near P_RATED. Engine output = min(pressure, flow).",
                        "Tune freely; calibrate gasConstant so a 3x3x1/9-burner/1-engine network sits near P_RATED.")
                .push("steamPhysics");

        STEAM_GAS_CONSTANT = builder
                .comment("Ideal-gas constant folding R and unit conversions. Raise for higher pressure per stored steam.")
                .defineInRange("gasConstant", DEFAULT_STEAM_GAS_CONSTANT, 0.0D, 1_000_000.0D);

        STEAM_RATED_PRESSURE = builder
                .comment("Rated operating pressure (pN/m^2). A normally supplied engine makes full output here.",
                        "Default 1_000_000 = 1.0 MpN/m^2.")
                .defineInRange("ratedPressure", DEFAULT_STEAM_RATED_PRESSURE, 1.0D, 1.0e12D);

        STEAM_WARN_PRESSURE = builder
                .comment("Pressure (pN/m^2) at which a network shows an overpressure warning. Default 1.5 MpN/m^2.")
                .defineInRange("warnPressure", DEFAULT_STEAM_WARN_PRESSURE, 0.0D, 1.0e12D);

        STEAM_BURST_PRESSURE = builder
                .comment("Pressure (pN/m^2) at which a boiler bursts. Default 2.5 MpN/m^2.")
                .defineInRange("burstPressure", DEFAULT_STEAM_BURST_PRESSURE, 0.0D, 1.0e12D);

        STEAM_TEMP_BASE_K = builder
                .comment("Steam temperature (Kelvin) with no extra heat (boiling point).")
                .defineInRange("temperatureBaseK", DEFAULT_STEAM_TEMP_BASE_K, 0.0D, 100_000.0D);

        STEAM_TEMP_PER_HEAT_K = builder
                .comment("Extra steam temperature (Kelvin) per usable heat unit (superheat from blaze cakes).")
                .defineInRange("temperaturePerHeatK", DEFAULT_STEAM_TEMP_PER_HEAT_K, 0.0D, 100_000.0D);

        STEAM_FULL_ENGINE_SU = builder
                .comment("Stress capacity a single engine makes at full output (pressure and flow both rated).")
                .defineInRange("fullEngineSu", DEFAULT_STEAM_FULL_ENGINE_SU, 1, 1_000_000_000);

        STEAM_FULL_ENGINE_FLOW_MB = builder
                .comment("Steam (mB/t) a single engine consumes at full output (and the per-inlet intake cap).")
                .defineInRange("fullEngineFlowMb", DEFAULT_STEAM_FULL_ENGINE_FLOW_MB, 1, 1_000_000);

        STEAM_MAX_RPM = builder
                .comment("Maximum engine RPM (top tier). RPM snaps to 0/0.25/0.5/0.75/1.0 of this by output factor.")
                .defineInRange("maxRpm", DEFAULT_STEAM_MAX_RPM, 1.0D, 256.0D);

        STEAM_VENT_COEFFICIENT = builder
                .comment("Steam (mB/t) an open pipe end vents at rated pressure (scales with pressure factor).",
                        "Keep above fullEngineFlowMb so an open pipe reliably relieves a boiler.")
                .defineInRange("ventCoefficient", DEFAULT_STEAM_VENT_COEFFICIENT, 0.0D, 1_000_000.0D);

        STEAM_BUFFER_CAP_MB = builder
                .comment("Maximum steam (mB) a boiler outlet vessel stores. High enough that pressure can reach burst.")
                .defineInRange("bufferCapMb", DEFAULT_STEAM_BUFFER_CAP_MB, 1, 1_000_000_000);

        builder.pop();

        builder.comment("Boiler burst explosion (triggered when network pressure exceeds steamPhysics.burstPressure).")
                .push("steamOverpressure");

        OVERPRESSURE_ENABLED = builder
                .comment("Whether over-pressured boilers explode.")
                .define("enabled", DEFAULT_OVERPRESSURE_ENABLED);

        OVERPRESSURE_BASE_POWER = builder
                .comment("Base explosion power (4.0 ~= TNT) before the per-volume bonus.")
                .defineInRange("explosionBasePower", DEFAULT_OVERPRESSURE_BASE_POWER, 0.0D, 1_000.0D);

        OVERPRESSURE_POWER_PER_VOLUME = builder
                .comment("Extra explosion power added per network volume m^3 (bigger system = bigger blast).")
                .defineInRange("explosionPowerPerVolume", DEFAULT_OVERPRESSURE_POWER_PER_VOLUME, 0.0D, 1_000.0D);

        OVERPRESSURE_MAX_POWER = builder
                .comment("Upper cap on explosion power regardless of system size.")
                .defineInRange("explosionMaxPower", DEFAULT_OVERPRESSURE_MAX_POWER, 0.0D, 1_000.0D);

        OVERPRESSURE_BREAKS_BLOCKS = builder
                .comment("Whether the burst explosion destroys blocks (false = entity damage only).")
                .define("explosionBreaksBlocks", DEFAULT_OVERPRESSURE_BREAKS_BLOCKS);

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

        ENGINE_EXHAUST_ENABLED = builder
                .comment("Whether running engines emit a brief steam cloud from the cylinder bore at the outer stroke.")
                .define("engineExhaustEnabled", DEFAULT_ENGINE_EXHAUST_ENABLED);

        ENGINE_EXHAUST_DAMAGE_SCALE = builder
                .comment("Multiplier applied to cylinder exhaust scald damage.",
                        "Open pipe damage is unaffected by this value.")
                .defineInRange("engineExhaustDamageScale", DEFAULT_ENGINE_EXHAUST_DAMAGE_SCALE, 0.0D, 100.0D);

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

    public static double steamGasConstant() {
        return loaded() ? STEAM_GAS_CONSTANT.get() : DEFAULT_STEAM_GAS_CONSTANT;
    }

    public static double steamRatedPressure() {
        return loaded() ? STEAM_RATED_PRESSURE.get() : DEFAULT_STEAM_RATED_PRESSURE;
    }

    public static double steamWarnPressure() {
        return loaded() ? STEAM_WARN_PRESSURE.get() : DEFAULT_STEAM_WARN_PRESSURE;
    }

    public static double steamBurstPressure() {
        return loaded() ? STEAM_BURST_PRESSURE.get() : DEFAULT_STEAM_BURST_PRESSURE;
    }

    public static double steamTemperatureBaseK() {
        return loaded() ? STEAM_TEMP_BASE_K.get() : DEFAULT_STEAM_TEMP_BASE_K;
    }

    public static double steamTemperaturePerHeatK() {
        return loaded() ? STEAM_TEMP_PER_HEAT_K.get() : DEFAULT_STEAM_TEMP_PER_HEAT_K;
    }

    public static int steamFullEngineSu() {
        return loaded() ? STEAM_FULL_ENGINE_SU.get() : DEFAULT_STEAM_FULL_ENGINE_SU;
    }

    public static int steamFullEngineFlowMb() {
        return loaded() ? STEAM_FULL_ENGINE_FLOW_MB.get() : DEFAULT_STEAM_FULL_ENGINE_FLOW_MB;
    }

    public static double steamMaxRpm() {
        return loaded() ? STEAM_MAX_RPM.get() : DEFAULT_STEAM_MAX_RPM;
    }

    public static double steamVentCoefficient() {
        return loaded() ? STEAM_VENT_COEFFICIENT.get() : DEFAULT_STEAM_VENT_COEFFICIENT;
    }

    public static int steamBufferCapMb() {
        return loaded() ? STEAM_BUFFER_CAP_MB.get() : DEFAULT_STEAM_BUFFER_CAP_MB;
    }

    public static boolean overpressureEnabled() {
        return !loaded() || OVERPRESSURE_ENABLED.get();
    }

    public static double overpressureBasePower() {
        return loaded() ? OVERPRESSURE_BASE_POWER.get() : DEFAULT_OVERPRESSURE_BASE_POWER;
    }

    public static double overpressurePowerPerVolume() {
        return loaded() ? OVERPRESSURE_POWER_PER_VOLUME.get() : DEFAULT_OVERPRESSURE_POWER_PER_VOLUME;
    }

    public static double overpressureMaxPower() {
        return loaded() ? OVERPRESSURE_MAX_POWER.get() : DEFAULT_OVERPRESSURE_MAX_POWER;
    }

    public static boolean overpressureBreaksBlocks() {
        return !loaded() || OVERPRESSURE_BREAKS_BLOCKS.get();
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

    public static boolean engineExhaustEnabled() {
        return !loaded() || ENGINE_EXHAUST_ENABLED.get();
    }

    public static double engineExhaustDamageScale() {
        return loaded() ? ENGINE_EXHAUST_DAMAGE_SCALE.get() : DEFAULT_ENGINE_EXHAUST_DAMAGE_SCALE;
    }

    /** Stress Units per mB of steam consumed, derived from the fixed SU-per-unit and the steam rate. */
    public static float suPerSteamMb() {
        return SU_PER_HEAT_UNIT / steamPerHeatUnit();
    }

    /** Maximum steam in mB/t one cylinder draws (and the per-inlet intake cap). Alias of fullEngineFlowMb. */
    public static int maxPipedSteamPerTick() {
        return steamFullEngineFlowMb();
    }

    private FullSteamConfig() {
    }
}
