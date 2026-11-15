package cc.nimbusnova.mc.mixin;

import cc.nimbusnova.mc.world.MarsGravity;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
abstract class LivingFallDamageMixin {
    @ModifyVariable(method = "calculateFallDamage(DF)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double nimbusNova$scaleFallDistance(final double distance) {
        final LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().dimension().equals(LunarSurface.DIMENSION)) return distance * LunarSurface.GRAVITY_RATIO;
        // Compare impact energy with vanilla's safe-fall threshold, rather than scaling damage after that threshold.
        return entity.level().dimension().equals(MarsTrainingMap.DIMENSION)
                ? distance * MarsGravity.EARTH_GRAVITY_RATIO : distance;
    }
}
