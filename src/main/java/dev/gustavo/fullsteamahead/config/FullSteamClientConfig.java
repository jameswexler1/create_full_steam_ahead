package dev.gustavo.fullsteamahead.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side presentation options. These never affect steam physics or server gameplay.
 */
public final class FullSteamClientConfig {
    private static final boolean DEFAULT_BURST_EFFECTS_ENABLED = true;
    private static final boolean DEFAULT_BURST_SCREEN_SHAKE_ENABLED = true;
    private static final double DEFAULT_BURST_SOUND_VOLUME_SCALE = 1.0D;
    private static final double DEFAULT_BURST_STEAM_CLOUD_SCALE = 1.0D;
    private static final double DEFAULT_BURST_SCREEN_SHAKE_SCALE = 1.0D;
    private static final double DEFAULT_BURST_WAVE_SPEED = 16.0D;
    private static final double DEFAULT_BURST_SOUND_RADIUS_BLOCKS = 200.0D;
    private static final double DEFAULT_BURST_SCREEN_SHAKE_RADIUS_BLOCKS = 150.0D;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue BURST_EFFECTS_ENABLED;
    private static final ModConfigSpec.BooleanValue BURST_SCREEN_SHAKE_ENABLED;
    private static final ModConfigSpec.DoubleValue BURST_SOUND_VOLUME_SCALE;
    private static final ModConfigSpec.DoubleValue BURST_STEAM_CLOUD_SCALE;
    private static final ModConfigSpec.DoubleValue BURST_SCREEN_SHAKE_SCALE;
    private static final ModConfigSpec.DoubleValue BURST_WAVE_SPEED;
    private static final ModConfigSpec.DoubleValue BURST_SOUND_RADIUS_BLOCKS;
    private static final ModConfigSpec.DoubleValue BURST_SCREEN_SHAKE_RADIUS_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Local visual and audio options for Create: Full Steam Ahead.").push("boilerBurstEffects");

        BURST_EFFECTS_ENABLED = builder
                .comment("Master switch for custom boiler burst particles, delayed sound, and screen shake.")
                .define("enabled", DEFAULT_BURST_EFFECTS_ENABLED);

        BURST_SCREEN_SHAKE_ENABLED = builder
                .comment("Whether boiler bursts shake the camera locally.")
                .define("screenShakeEnabled", DEFAULT_BURST_SCREEN_SHAKE_ENABLED);

        BURST_SOUND_VOLUME_SCALE = builder
                .comment("Local volume multiplier for the layered boiler burst placeholder sounds.")
                .defineInRange("soundVolumeScale", DEFAULT_BURST_SOUND_VOLUME_SCALE, 0.0D, 8.0D);

        BURST_STEAM_CLOUD_SCALE = builder
                .comment("Local multiplier for the number of large burst steam particles.")
                .defineInRange("steamCloudScale", DEFAULT_BURST_STEAM_CLOUD_SCALE, 0.0D, 4.0D);

        BURST_SCREEN_SHAKE_SCALE = builder
                .comment("Local multiplier for boiler burst camera shake strength.")
                .defineInRange("screenShakeScale", DEFAULT_BURST_SCREEN_SHAKE_SCALE, 0.0D, 4.0D);

        BURST_WAVE_SPEED = builder
                .comment("Blocks per tick traveled by the visual blast wave before local sound and shake play.")
                .defineInRange("waveSpeedBlocksPerTick", DEFAULT_BURST_WAVE_SPEED, 1.0D, 128.0D);

        BURST_SOUND_RADIUS_BLOCKS = builder
                .comment("Maximum distance in blocks at which boiler burst sounds play.")
                .defineInRange("soundRadiusBlocks", DEFAULT_BURST_SOUND_RADIUS_BLOCKS, 0.0D, 1024.0D);

        BURST_SCREEN_SHAKE_RADIUS_BLOCKS = builder
                .comment("Maximum distance in blocks at which boiler burst camera shake applies.")
                .defineInRange("screenShakeRadiusBlocks", DEFAULT_BURST_SCREEN_SHAKE_RADIUS_BLOCKS, 0.0D, 1024.0D);

        builder.pop();
        SPEC = builder.build();
    }

    public static boolean burstEffectsEnabled() {
        return !loaded() || BURST_EFFECTS_ENABLED.get();
    }

    public static boolean burstScreenShakeEnabled() {
        return burstEffectsEnabled() && (!loaded() || BURST_SCREEN_SHAKE_ENABLED.get());
    }

    public static double burstSoundVolumeScale() {
        return loaded() ? BURST_SOUND_VOLUME_SCALE.get() : DEFAULT_BURST_SOUND_VOLUME_SCALE;
    }

    public static double burstSteamCloudScale() {
        return loaded() ? BURST_STEAM_CLOUD_SCALE.get() : DEFAULT_BURST_STEAM_CLOUD_SCALE;
    }

    public static double burstScreenShakeScale() {
        return loaded() ? BURST_SCREEN_SHAKE_SCALE.get() : DEFAULT_BURST_SCREEN_SHAKE_SCALE;
    }

    public static double burstWaveSpeedBlocksPerTick() {
        return loaded() ? BURST_WAVE_SPEED.get() : DEFAULT_BURST_WAVE_SPEED;
    }

    public static double burstSoundRadiusBlocks() {
        return loaded() ? BURST_SOUND_RADIUS_BLOCKS.get() : DEFAULT_BURST_SOUND_RADIUS_BLOCKS;
    }

    public static double burstScreenShakeRadiusBlocks() {
        return loaded() ? BURST_SCREEN_SHAKE_RADIUS_BLOCKS.get() : DEFAULT_BURST_SCREEN_SHAKE_RADIUS_BLOCKS;
    }

    private static boolean loaded() {
        return SPEC.isLoaded();
    }

    private FullSteamClientConfig() {
    }
}
