package cc.nimbusnova.mc.client.effect;

import cc.nimbusnova.mc.effect.DustStormProfile;
import cc.nimbusnova.mc.client.render.NovaDustPipeline;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class DustCloudParticle extends SingleQuadParticle {
    private DustCloudParticle(final ClientLevel level, final double x, final double y, final double z,
                              final double velocityX, final double velocityY, final double velocityZ,
                              final SpriteSet sprites, final RandomSource particleRandom) {
        super(level, x, y, z, sprites.get(particleRandom));
        lifetime = 80 + particleRandom.nextInt(40);
        quadSize = 3.2F + particleRandom.nextFloat() * 3.2F;
        xd = velocityX;
        yd = velocityY;
        zd = velocityZ;
        hasPhysics = true;
        setSize(1.2F, 1.2F);
        roll = particleRandom.nextFloat() * (float) (Math.PI * 2);
        oRoll = roll;
        setColor(0.68F + particleRandom.nextFloat() * 0.12F, 0.4F + particleRandom.nextFloat() * 0.1F,
                0.22F + particleRandom.nextFloat() * 0.08F);
        setAlpha(0);
    }

    @Override
    public void tick() {
        if (DustStormEffects.paused()) return;

        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (++age >= lifetime || DustStormEffects.intensity() <= 0) {
            remove();
            return;
        }

        roll += 0.003F;
        quadSize += 0.006F;
        move(xd, yd, zd);
        setAlpha(DustStormProfile.opacity(age, lifetime, DustStormEffects.intensity()));
    }

    @Override
    protected Layer getLayer() {
        return NovaDustPipeline.LAYER;
    }

    static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        Provider(final SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(final SimpleParticleType type, final ClientLevel level,
                                       final double x, final double y, final double z,
                                       final double velocityX, final double velocityY, final double velocityZ,
                                       final RandomSource random) {
            return new DustCloudParticle(level, x, y, z, velocityX, velocityY, velocityZ, sprites, random);
        }
    }
}
