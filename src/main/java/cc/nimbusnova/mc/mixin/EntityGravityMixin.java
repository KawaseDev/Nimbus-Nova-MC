package cc.nimbusnova.mc.mixin;

import cc.nimbusnova.mc.world.MarsGravity;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
abstract class EntityGravityMixin {
    @ModifyReturnValue(method = "getGravity()D", at = @At("RETURN"))
    private double nimbusNova$scaleGravity(final double gravity) {
        final Entity entity = (Entity) (Object) this;
        if (entity.level().dimension().equals(LunarSurface.DIMENSION)) return gravity * LunarSurface.GRAVITY_RATIO;
        return entity.level().dimension().equals(MarsTrainingMap.DIMENSION)
                ? gravity * MarsGravity.EARTH_GRAVITY_RATIO : gravity;
    }
}
