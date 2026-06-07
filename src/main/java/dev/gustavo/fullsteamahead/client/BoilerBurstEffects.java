package dev.gustavo.fullsteamahead.client;

import com.simibubi.create.AllSoundEvents;
import dev.gustavo.fullsteamahead.config.FullSteamClientConfig;
import dev.gustavo.fullsteamahead.network.BoilerBurstPayload;
import dev.gustavo.fullsteamahead.registry.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class BoilerBurstEffects {
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final List<PendingWave> PENDING_WAVES = new ArrayList<>();
    private static final List<CameraShake> SHAKES = new ArrayList<>();
    private static boolean registered;

    public static void register(IEventBus bus) {
        if (registered) {
            return;
        }
        registered = true;
        bus.register(BoilerBurstEffects.class);
    }

    public static void accept(BoilerBurstPayload payload) {
        if (!FullSteamClientConfig.burstEffectsEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }

        Vec3 center = new Vec3(payload.x(), payload.y(), payload.z());
        double distance = player.position().distanceTo(center);
        int delayTicks = Mth.floor(distance / FullSteamClientConfig.burstWaveSpeedBlocksPerTick());
        spawnSteamCloud(level, center, payload.power(), payload.networkVolumeM3(), payload.seed());
        PENDING_WAVES.add(new PendingWave(center, payload.power(), payload.seed(), Math.max(0, delayTicks)));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }

        Iterator<PendingWave> pendingIterator = PENDING_WAVES.iterator();
        while (pendingIterator.hasNext()) {
            PendingWave wave = pendingIterator.next();
            if (wave.tickAndReady()) {
                triggerWave(wave);
                pendingIterator.remove();
            }
        }

        Iterator<CameraShake> shakeIterator = SHAKES.iterator();
        while (shakeIterator.hasNext()) {
            CameraShake shake = shakeIterator.next();
            if (shake.tick()) {
                shakeIterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!FullSteamClientConfig.burstScreenShakeEnabled() || SHAKES.isEmpty()) {
            return;
        }

        double partial = event.getPartialTick();
        double yaw = 0.0D;
        double pitch = 0.0D;
        double roll = 0.0D;
        for (CameraShake shake : SHAKES) {
            double age = shake.age + partial;
            double progress = Mth.clamp(age / shake.duration, 0.0D, 1.0D);
            double envelope = Math.pow(1.0D - progress, 1.8D);
            double base = age * 2.9D + shake.phase;
            yaw += Math.sin(base) * shake.strength * envelope;
            pitch += Math.sin(base * 1.23D + 1.7D) * shake.strength * 0.65D * envelope;
            roll += Math.sin(base * 0.74D + 2.3D) * shake.strength * 0.45D * envelope;
        }

        event.setYaw(event.getYaw() + (float) yaw);
        event.setPitch(event.getPitch() + (float) pitch);
        event.setRoll(event.getRoll() + (float) roll);
    }

    private static void spawnSteamCloud(ClientLevel level, Vec3 center, float power, double volume, long seed) {
        double scale = FullSteamClientConfig.burstSteamCloudScale();
        if (scale <= 0.0D) {
            return;
        }

        RandomSource random = RandomSource.create(seed);
        int particles = (int) Math.round(Mth.clamp(34.0D + power * 5.5D + volume * 0.12D, 36.0D, 260.0D) * scale);
        double spread = Mth.clamp(0.65D + power * 0.08D, 0.8D, 4.0D);
        double lift = Mth.clamp(0.08D + power * 0.008D, 0.12D, 0.45D);

        for (int i = 0; i < particles; i++) {
            double angle = random.nextDouble() * TWO_PI;
            double radius = Math.sqrt(random.nextDouble()) * spread;
            double x = center.x + Math.cos(angle) * radius * 0.45D;
            double y = center.y + (random.nextDouble() - 0.25D) * spread * 0.35D;
            double z = center.z + Math.sin(angle) * radius * 0.45D;
            double speed = 0.025D + random.nextDouble() * 0.09D + power * 0.004D;
            double xSpeed = Math.cos(angle) * speed + (random.nextDouble() - 0.5D) * 0.045D;
            double ySpeed = lift + random.nextDouble() * lift;
            double zSpeed = Math.sin(angle) * speed + (random.nextDouble() - 0.5D) * 0.045D;
            level.addParticle(ModParticleTypes.STEAM_BURST.get(), x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }

    private static void triggerWave(PendingWave wave) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }

        double distance = player.position().distanceTo(wave.center);
        double radius = Math.max(32.0D, wave.power * 16.0D);
        double falloff = Mth.clamp(1.0D - distance / radius, 0.0D, 1.0D);
        playLayeredSound(level, wave.center, wave.power, falloff);

        if (FullSteamClientConfig.burstScreenShakeEnabled() && falloff > 0.0D) {
            RandomSource random = RandomSource.create(wave.seed);
            float strength = (float) Math.min(
                    8.0D,
                    (0.45D + wave.power * 0.15D) * falloff * FullSteamClientConfig.burstScreenShakeScale()
            );
            int duration = Mth.clamp(14 + Math.round(wave.power * 1.4F), 16, 72);
            SHAKES.add(new CameraShake(strength, duration, random.nextFloat() * TWO_PI));
        }
    }

    private static void playLayeredSound(ClientLevel level, Vec3 center, float power, double falloff) {
        double scale = FullSteamClientConfig.burstSoundVolumeScale();
        if (scale <= 0.0D) {
            return;
        }

        float baseVolume = (float) (Math.max(0.35D, falloff) * scale);
        float boomVolume = (float) Mth.clamp(0.8D + power * 0.09D, 0.8D, 4.5D) * baseVolume;
        float hissVolume = (float) Mth.clamp(1.2D + power * 0.075D, 1.2D, 4.0D) * baseVolume;
        float pitch = (float) Mth.clamp(0.72D + 8.0D / Math.max(16.0D, power + 16.0D), 0.72D, 1.05D);

        level.playLocalSound(center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, boomVolume, pitch, false);
        level.playLocalSound(center.x, center.y, center.z,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, hissVolume, 0.55F, false);
        AllSoundEvents.STEAM.playAt(level, center, hissVolume * 1.2F, 0.72F, false);
    }

    private static final class PendingWave {
        private final Vec3 center;
        private final float power;
        private final long seed;
        private final int delayTicks;
        private int age;

        private PendingWave(Vec3 center, float power, long seed, int delayTicks) {
            this.center = center;
            this.power = power;
            this.seed = seed;
            this.delayTicks = delayTicks;
        }

        private boolean tickAndReady() {
            age++;
            return age >= delayTicks;
        }
    }

    private static final class CameraShake {
        private final float strength;
        private final int duration;
        private final double phase;
        private int age;

        private CameraShake(float strength, int duration, double phase) {
            this.strength = strength;
            this.duration = duration;
            this.phase = phase;
        }

        private boolean tick() {
            age++;
            return age >= duration;
        }
    }

    private BoilerBurstEffects() {
    }
}
