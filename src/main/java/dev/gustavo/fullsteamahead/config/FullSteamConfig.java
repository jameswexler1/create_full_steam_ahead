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

    // Unified ideal-gas steam model (P = gasConstant * storedMb * tempK / volume, in bar).
    private static final double DEFAULT_STEAM_PER_BLOCK = 90.0D / 27.0D;
    private static final int DEFAULT_STEAM_HEAT_NOMINAL = 9;
    private static final double DEFAULT_STEAM_HEAT_FACTOR_MAX = 2.0D;
    private static final double DEFAULT_STEAM_GAS_CONSTANT = 2.0e-4D;
    private static final double DEFAULT_STEAM_TEMP_BASE_K = 373.0D;
    private static final double DEFAULT_STEAM_TEMP_PER_HEAT_K = 100.0D;
    private static final double DEFAULT_STEAM_RPM_PER_BAR = 6.4D;
    private static final double DEFAULT_STEAM_MAX_RPM = 64.0D;
    private static final double DEFAULT_STEAM_SU_PER_BAR = 14_745.6D;
    private static final int DEFAULT_STEAM_SU_MAX = 147_456;
    private static final double DEFAULT_STEAM_FLOW_PER_BAR = 9.0D;
    private static final int DEFAULT_STEAM_MAX_INTAKE_MB = 90;
    private static final double DEFAULT_STEAM_VENT_PER_BAR = 12.0D;
    private static final double DEFAULT_STEAM_WARN_BAR = 15.0D;
    private static final double DEFAULT_STEAM_BURST_BAR = 25.0D;
    private static final int DEFAULT_STEAM_BUFFER_CAP_MB = 16_000;

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
    private static final ModConfigSpec.DoubleValue STEAM_PER_BLOCK;
    private static final ModConfigSpec.IntValue STEAM_HEAT_NOMINAL;
    private static final ModConfigSpec.DoubleValue STEAM_HEAT_FACTOR_MAX;
    private static final ModConfigSpec.DoubleValue STEAM_GAS_CONSTANT;
    private static final ModConfigSpec.DoubleValue STEAM_TEMP_BASE_K;
    private static final ModConfigSpec.DoubleValue STEAM_TEMP_PER_HEAT_K;
    private static final ModConfigSpec.DoubleValue STEAM_RPM_PER_BAR;
    private static final ModConfigSpec.DoubleValue STEAM_MAX_RPM;
    private static final ModConfigSpec.DoubleValue STEAM_SU_PER_BAR;
    private static final ModConfigSpec.IntValue STEAM_SU_MAX;
    private static final ModConfigSpec.DoubleValue STEAM_FLOW_PER_BAR;
    private static final ModConfigSpec.IntValue STEAM_MAX_INTAKE_MB;
    private static final ModConfigSpec.DoubleValue STEAM_VENT_PER_BAR;
    private static final ModConfigSpec.DoubleValue STEAM_WARN_BAR;
    private static final ModConfigSpec.DoubleValue STEAM_BURST_BAR;
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

        builder.comment("Unified ideal-gas steam model. One pressure drives everything. Tune freely.",
                        "P (bar) = gasConstant * storedSteamMb * temperatureK / volumeBlocks.",
                        "Steam is produced into the vessel, drawn by engines (flow ~ pressure) and vented by open",
                        "pipes (~ pressure); pressure self-regulates to an equilibrium. RPM and SU both rise with",
                        "pressure (pure single-cylinder physics). If pressure climbs past burstBar the boiler explodes.")
                .push("steamPhysics");

        STEAM_PER_BLOCK = builder
                .comment("Steam (mB/t) boiled per tank block at heat factor 1.0 (normally fired).",
                        "Default 90/27 so a normally-fired 3x3x3 boiler produces 90 mB/t.")
                .defineInRange("steamPerBlock", DEFAULT_STEAM_PER_BLOCK, 0.0D, 1_000_000.0D);

        STEAM_HEAT_NOMINAL = builder
                .comment("Water-gated boiler heat that counts as heat factor 1.0 (a normally-fired boiler).",
                        "Blaze cakes push heat higher -> factor > 1 -> more steam and pressure (toward bursting).")
                .defineInRange("heatNominal", DEFAULT_STEAM_HEAT_NOMINAL, 1, 100_000);

        STEAM_HEAT_FACTOR_MAX = builder
                .comment("Upper clamp on the heat factor (super-heat headroom).")
                .defineInRange("heatFactorMax", DEFAULT_STEAM_HEAT_FACTOR_MAX, 0.0D, 64.0D);

        STEAM_GAS_CONSTANT = builder
                .comment("Ideal-gas constant folding R and unit conversions: P = this * storedMb * tempK / volume.",
                        "Sets how much stored steam a given pressure needs; raise for higher pressures.")
                .defineInRange("gasConstant", DEFAULT_STEAM_GAS_CONSTANT, 0.0D, 1_000.0D);

        STEAM_TEMP_BASE_K = builder
                .comment("Steam temperature (Kelvin) with no extra heat (boiling point).")
                .defineInRange("temperatureBaseK", DEFAULT_STEAM_TEMP_BASE_K, 0.0D, 100_000.0D);

        STEAM_TEMP_PER_HEAT_K = builder
                .comment("Extra steam temperature (Kelvin) per unit of water-gated boiler heat.")
                .defineInRange("temperaturePerHeatK", DEFAULT_STEAM_TEMP_PER_HEAT_K, 0.0D, 100_000.0D);

        STEAM_RPM_PER_BAR = builder
                .comment("Engine RPM produced per bar of delivered pressure (clamped at maxRpm).",
                        "Default 6.4 so ~10 bar (a normally-fired 3x3x3) gives 64 RPM.")
                .defineInRange("rpmPerBar", DEFAULT_STEAM_RPM_PER_BAR, 0.0D, 1_000.0D);

        STEAM_MAX_RPM = builder
                .comment("Upper clamp on engine RPM regardless of pressure.")
                .defineInRange("maxRpm", DEFAULT_STEAM_MAX_RPM, 1.0D, 256.0D);

        STEAM_SU_PER_BAR = builder
                .comment("Engine stress capacity (SU) per bar of delivered pressure (clamped at suMax).",
                        "Default 14745.6 so ~10 bar gives 147456 SU.")
                .defineInRange("suPerBar", DEFAULT_STEAM_SU_PER_BAR, 0.0D, 1_000_000_000.0D);

        STEAM_SU_MAX = builder
                .comment("Upper cap on stress capacity a single cylinder can produce.")
                .defineInRange("suMax", DEFAULT_STEAM_SU_MAX, 1, 1_000_000_000);

        STEAM_FLOW_PER_BAR = builder
                .comment("Steam (mB/t) an engine draws per bar of pressure (capped at maxIntakeMb).",
                        "Lower = pressure builds more easily; this is what makes pressure self-regulate.")
                .defineInRange("flowPerBar", DEFAULT_STEAM_FLOW_PER_BAR, 0.0D, 1_000_000.0D);

        STEAM_MAX_INTAKE_MB = builder
                .comment("Maximum steam (mB/t) a single cylinder can draw, regardless of pressure.")
                .defineInRange("maxIntakeMb", DEFAULT_STEAM_MAX_INTAKE_MB, 1, 1_000_000);

        STEAM_VENT_PER_BAR = builder
                .comment("Steam (mB/t) an open pipe end vents per bar of pressure (relief). Higher = open pipes",
                        "relieve faster. Make this >= flowPerBar so an open pipe reliably prevents bursting.")
                .defineInRange("ventPerBar", DEFAULT_STEAM_VENT_PER_BAR, 0.0D, 1_000_000.0D);

        STEAM_WARN_BAR = builder
                .comment("Pressure (bar) at which the boiler hisses and shows an overpressure warning.")
                .defineInRange("warnBar", DEFAULT_STEAM_WARN_BAR, 0.0D, 1_000_000.0D);

        STEAM_BURST_BAR = builder
                .comment("Pressure (bar) at which the boiler explodes.")
                .defineInRange("burstBar", DEFAULT_STEAM_BURST_BAR, 0.0D, 1_000_000.0D);

        STEAM_BUFFER_CAP_MB = builder
                .comment("Maximum steam (mB) a boiler vessel can store. Should be high enough to reach burstBar.")
                .defineInRange("bufferCapMb", DEFAULT_STEAM_BUFFER_CAP_MB, 1, 1_000_000_000);

        builder.pop();

        builder.comment("Boiler burst explosion (triggered when pressure exceeds steamPhysics.burstBar).")
                .push("steamOverpressure");

        OVERPRESSURE_ENABLED = builder
                .comment("Whether over-pressured boilers explode.")
                .define("enabled", DEFAULT_OVERPRESSURE_ENABLED);

        OVERPRESSURE_BASE_POWER = builder
                .comment("Base explosion power (4.0 ~= TNT) before the per-volume bonus.")
                .defineInRange("explosionBasePower", DEFAULT_OVERPRESSURE_BASE_POWER, 0.0D, 1_000.0D);

        OVERPRESSURE_POWER_PER_VOLUME = builder
                .comment("Extra explosion power added per boiler tank block (bigger boiler = bigger blast).")
                .defineInRange("explosionPowerPerVolume", DEFAULT_OVERPRESSURE_POWER_PER_VOLUME, 0.0D, 1_000.0D);

        OVERPRESSURE_MAX_POWER = builder
                .comment("Upper cap on explosion power regardless of boiler size.")
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

    public static double steamPerBlock() {
        return loaded() ? STEAM_PER_BLOCK.get() : DEFAULT_STEAM_PER_BLOCK;
    }

    public static int steamHeatNominal() {
        return loaded() ? STEAM_HEAT_NOMINAL.get() : DEFAULT_STEAM_HEAT_NOMINAL;
    }

    public static double steamHeatFactorMax() {
        return loaded() ? STEAM_HEAT_FACTOR_MAX.get() : DEFAULT_STEAM_HEAT_FACTOR_MAX;
    }

    public static double steamGasConstant() {
        return loaded() ? STEAM_GAS_CONSTANT.get() : DEFAULT_STEAM_GAS_CONSTANT;
    }

    public static double steamTempBaseK() {
        return loaded() ? STEAM_TEMP_BASE_K.get() : DEFAULT_STEAM_TEMP_BASE_K;
    }

    public static double steamTempPerHeatK() {
        return loaded() ? STEAM_TEMP_PER_HEAT_K.get() : DEFAULT_STEAM_TEMP_PER_HEAT_K;
    }

    public static double steamRpmPerBar() {
        return loaded() ? STEAM_RPM_PER_BAR.get() : DEFAULT_STEAM_RPM_PER_BAR;
    }

    public static double steamMaxRpm() {
        return loaded() ? STEAM_MAX_RPM.get() : DEFAULT_STEAM_MAX_RPM;
    }

    public static double steamSuPerBar() {
        return loaded() ? STEAM_SU_PER_BAR.get() : DEFAULT_STEAM_SU_PER_BAR;
    }

    public static int steamSuMax() {
        return loaded() ? STEAM_SU_MAX.get() : DEFAULT_STEAM_SU_MAX;
    }

    public static double steamFlowPerBar() {
        return loaded() ? STEAM_FLOW_PER_BAR.get() : DEFAULT_STEAM_FLOW_PER_BAR;
    }

    public static int steamMaxIntakeMb() {
        return loaded() ? STEAM_MAX_INTAKE_MB.get() : DEFAULT_STEAM_MAX_INTAKE_MB;
    }

    public static double steamVentPerBar() {
        return loaded() ? STEAM_VENT_PER_BAR.get() : DEFAULT_STEAM_VENT_PER_BAR;
    }

    public static double steamWarnBar() {
        return loaded() ? STEAM_WARN_BAR.get() : DEFAULT_STEAM_WARN_BAR;
    }

    public static double steamBurstBar() {
        return loaded() ? STEAM_BURST_BAR.get() : DEFAULT_STEAM_BURST_BAR;
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

    /** Maximum steam in mB/t one cylinder draws (and the per-inlet intake cap). Alias of steamMaxIntakeMb. */
    public static int maxPipedSteamPerTick() {
        return steamMaxIntakeMb();
    }

    private FullSteamConfig() {
    }
}
