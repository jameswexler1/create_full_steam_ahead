package dev.gustavo.fullsteamahead.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class SteamBurstParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;
    private final float maxAlpha;
    private final float spin;

    private SteamBurstParticle(
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
        this.lifetime = 78 + this.random.nextInt(78);
        this.startSize = 1.1F + this.random.nextFloat() * 1.25F;
        this.maxAlpha = 0.48F + this.random.nextFloat() * 0.18F;
        this.spin = (this.random.nextFloat() - 0.5F) * 0.18F;
        this.quadSize = this.startSize;
        this.hasPhysics = false;
        this.gravity = -0.0025F;
        this.friction = 0.935F;
        this.roll = this.random.nextFloat() * 6.2831855F;
        this.oRoll = this.roll;
        this.alpha = 0.0F;
        float shade = 0.86F + this.random.nextFloat() * 0.12F;
        this.setColor(shade, Math.min(1.0F, shade + 0.025F), Math.min(1.0F, shade + 0.035F));
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float progress = this.age / (float) this.lifetime;
        float fadeIn = Math.min(1.0F, progress * 6.0F);
        float fadeOut = (float) Math.pow(1.0F - progress, 1.55D);
        this.quadSize = this.startSize * (1.0F + progress * 2.9F);
        this.alpha = this.maxAlpha * fadeIn * fadeOut;
        this.roll = this.oRoll + progress * this.spin;
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
            return new SteamBurstParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
