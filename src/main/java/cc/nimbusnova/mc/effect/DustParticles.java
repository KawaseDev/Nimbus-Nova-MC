package cc.nimbusnova.mc.effect;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class DustParticles {
    public static final SimpleParticleType STORM_CLOUD = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Identifier.parse("nimbus_nova:storm_cloud"), FabricParticleTypes.simple());

    private DustParticles() {
        /* w */
    }

    public static void register() {
        /* w */
    }
}
