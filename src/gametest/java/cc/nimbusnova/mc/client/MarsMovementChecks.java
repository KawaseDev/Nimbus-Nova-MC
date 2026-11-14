package cc.nimbusnova.mc.client;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

final class MarsMovementChecks {
    private MarsMovementChecks() {
        /* w */
    }

    static Sample sample(final ServerLevel level) {
        final var jumper = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (jumper == null) throw new AssertionError("Could not create movement test entity");

        final int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4, -32);
        jumper.snapTo(4.5, ground, -31.5, 0, 0);
        jumper.setOnGround(true);
        final double gravity = jumper.getGravity();
        jumper.setNoGravity(true);
        if (jumper.getGravity() != 0) throw new AssertionError("No-gravity entities must remain unaffected");
        jumper.setNoGravity(false);
        jumper.jumpFromGround();
        double highest = ground;
        int airborneTicks = 0;
        for (int tick = 0; tick < 100; tick++) {
            jumper.travel(Vec3.ZERO);
            highest = Math.max(highest, jumper.getY());
            airborneTicks++;
            if (jumper.onGround()) break;
        }

        if (!jumper.onGround()) throw new AssertionError("The test jump never landed");
        final float health = jumper.getHealth();
        jumper.causeFallDamage(10, 1, level.damageSources().fall());
        final ItemEntity item = new ItemEntity(level, 4.5, ground + 5, -31.5, new ItemStack(Items.STONE));
        return new Sample(gravity, highest - ground, airborneTicks, health - jumper.getHealth(), item.getGravity());
    }

    record Sample(double gravity, double jumpHeight, int airborneTicks, float fallDamage, double itemGravity) {
        /* w */
    }
}
