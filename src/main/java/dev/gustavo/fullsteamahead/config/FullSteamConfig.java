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

    private static final int DEFAULT_CYLINDER_MAX_INTAKE_MB = 90;
    private static final int DEFAULT_CYLINDER_MAX_SU = 147_456;
    private static final double DEFAULT_STEAM_PER_BLOCK = 90.0D / 27.0D;
    private static final int DEFAULT_STEAM_MAX_VOLUME_REFERENCE = 27;
    private static final double DEFAULT_STEAM_RPM_AT_MAX_VOLUME = 16.0D;
    private static final double DEFAULT_STEAM_MAX_RPM = 64.0D;
    private static final double DEFAULT_STEAM_HEAT_RATIO_MAX = 2.0D;

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
    private static final ModConfigSpec.IntValue CYLINDER_MAX_INTAKE_MB;
    private static final ModConfigSpec.IntValue CYLINDER_MAX_SU;
    private static final ModConfigSpec.DoubleValue STEAM_PER_BLOCK;
    private static final ModConfigSpec.IntValue STEAM_MAX_VOLUME_REFERENCE;
    private static final ModConfigSpec.DoubleValue STEAM_RPM_AT_MAX_VOLUME;
    private static final ModConfigSpec.DoubleValue STEAM_MAX_RPM;
    private static final ModConfigSpec.DoubleValue STEAM_HEAT_RATIO_MAX;
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

        builder.comment("Pressure/volume/temperature steam model. Tune these freely.",
                        "Boiler size (volume) and heat set steam production and pressure; a cylinder is consumption-",
                        "limited, so a bigger boiler gives more SU at lower RPM and a smaller boiler gives less SU at",
                        "higher RPM. A full-heat 3x3x3 boiler (volume 27) exactly maxes one cylinder: 90 mB/t -> 147456",
                        "SU, 16 RPM. Boilers producing more than is consumed build surplus pressure (overpressure).")
                .push("steamPhysics");

        CYLINDER_MAX_INTAKE_MB = builder
                .comment("Maximum steam (mB/t) a single cylinder consumes. Steam beyond this is surplus (overpressure).")
                .defineInRange("cylinderMaxIntakeMb", DEFAULT_CYLINDER_MAX_INTAKE_MB, 1, 1_000_000);

        CYLINDER_MAX_SU = builder
                .comment("Absolute stress capacity cap a single cylinder can ever produce (at full intake).")
                .defineInRange("cylinderMaxSu", DEFAULT_CYLINDER_MAX_SU, 1, 1_000_000_000);

        STEAM_PER_BLOCK = builder
                .comment("Steam (mB/t) produced per boiler tank block at heat ratio 1.0.",
                        "Default 90/27 so a full-heat 3x3x3 boiler (27 blocks) produces exactly 90 mB/t.")
                .defineInRange("steamPerBlock", DEFAULT_STEAM_PER_BLOCK, 0.0D, 1_000_000.0D);

        STEAM_MAX_VOLUME_REFERENCE = builder
                .comment("Boiler volume (blocks) treated as a maxed cylinder: pressure ratio 1.0 at full heat.",
                        "Default 27 = a 3x3x3 Create Fluid Tank.")
                .defineInRange("maxVolumeReference", DEFAULT_STEAM_MAX_VOLUME_REFERENCE, 1, 1_000_000);

        STEAM_RPM_AT_MAX_VOLUME = builder
                .comment("RPM at pressure ratio 1.0 (a full-heat maxVolumeReference boiler). Smaller boilers spin",
                        "faster up to maxRpm. Default 16 = a maxed 3x3x3 boiler.")
                .defineInRange("rpmAtMaxVolume", DEFAULT_STEAM_RPM_AT_MAX_VOLUME, 0.0D, 256.0D);

        STEAM_MAX_RPM = builder
                .comment("Upper clamp on engine RPM regardless of pressure.")
                .defineInRange("maxRpm", DEFAULT_STEAM_MAX_RPM, 1.0D, 256.0D);

        STEAM_HEAT_RATIO_MAX = builder
                .comment("Upper clamp on heat ratio. >1.0 lets super-heated (blaze cake) boilers overproduce and",
                        "raise pressure past a normally-fired boiler. Default 2.0 (cakes can double heat).")
                .defineInRange("heatRatioMax", DEFAULT_STEAM_HEAT_RATIO_MAX, 0.0D, 64.0D);

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

    public static int cylinderMaxIntakeMb() {
        return loaded() ? CYLINDER_MAX_INTAKE_MB.get() : DEFAULT_CYLINDER_MAX_INTAKE_MB;
    }

    public static int cylinderMaxSu() {
        return loaded() ? CYLINDER_MAX_SU.get() : DEFAULT_CYLINDER_MAX_SU;
    }

    public static double steamPerBlock() {
        return loaded() ? STEAM_PER_BLOCK.get() : DEFAULT_STEAM_PER_BLOCK;
    }

    public static int steamMaxVolumeReference() {
        return loaded() ? STEAM_MAX_VOLUME_REFERENCE.get() : DEFAULT_STEAM_MAX_VOLUME_REFERENCE;
    }

    public static double steamRpmAtMaxVolume() {
        return loaded() ? STEAM_RPM_AT_MAX_VOLUME.get() : DEFAULT_STEAM_RPM_AT_MAX_VOLUME;
    }

    public static double steamMaxRpm() {
        return loaded() ? STEAM_MAX_RPM.get() : DEFAULT_STEAM_MAX_RPM;
    }

    public static double steamHeatRatioMax() {
        return loaded() ? STEAM_HEAT_RATIO_MAX.get() : DEFAULT_STEAM_HEAT_RATIO_MAX;
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

    /** Maximum steam in mB/t one cylinder draws (and the per-inlet intake cap). Alias of cylinderMaxIntakeMb. */
    public static int maxPipedSteamPerTick() {
        return cylinderMaxIntakeMb();
    }

    private FullSteamConfig() {
    }
}
