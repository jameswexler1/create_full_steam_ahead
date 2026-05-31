package dev.gustavo.fullsteamahead.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class SteamLeakParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;

    private SteamLeakParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.lifetime = 22 + this.random.nextInt(14);
        this.startSize = 0.36F + this.random.nextFloat() * 0.28F;
        this.quadSize = this.startSize;
        this.hasPhysics = false;
        this.gravity = -0.008F;
        this.friction = 0.90F;
        this.roll = this.random.nextFloat() * 6.2831855F;
        this.oRoll = this.roll;
        this.alpha = 0.0F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float progress = this.age / (float) this.lifetime;
        float fadeIn = Math.min(1.0F, progress * 5.0F);
        float fadeOut = 1.0F - progress;
        this.quadSize = this.startSize * (1.0F + progress * 1.75F);
        this.alpha = 0.64F * fadeIn * fadeOut;
        this.roll = this.oRoll + progress * 0.22F;
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed
        ) {
            return new SteamLeakParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
