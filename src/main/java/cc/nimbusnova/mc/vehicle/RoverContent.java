package cc.nimbusnova.mc.vehicle;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class RoverContent {
    public static final EntityType<MarsRover> ROVER = createType();

    private RoverContent() {
        /* w */
    }

    private static EntityType<MarsRover> createType() {
        final ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.parse("nimbus_nova:mars_rover"));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.of(MarsRover::new, MobCategory.MISC).sized(2.2F, 2.25F)
                        .clientTrackingRange(10).updateInterval(1).build(key));
    }

    public static void register() {
        MarsRover.verifyDriveMath();
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> dispatcher.register(Commands.literal("rover")
                .then(Commands.literal("spawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            final ServerPlayer player = context.getSource().getPlayerOrException();
                            final ServerLevel level = player.level();
                            final MarsRover rover = ROVER.create(level, EntitySpawnReason.COMMAND);
                            if (rover == null) return 0;

                            for (int direction = 0; direction < 8; direction++) {
                                final double angle = Math.toRadians(player.getYRot() + direction * 45);
                                final int x = (int) Math.floor(player.getX() - Math.sin(angle) * 4);
                                final int z = (int) Math.floor(player.getZ() + Math.cos(angle) * 4);
                                for (int offset = 2; offset >= -3; offset--) {
                                    final BlockPos position = new BlockPos(x, player.blockPosition().getY() + offset, z);
                                    if (!level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP)
                                            || !level.getFluidState(position).isEmpty()) continue;

                                    rover.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, player.getYRot(), 0);
                                    if (!level.noCollision(rover)) continue;

                                    rover.setCustomName(Component.literal("Aster Rover"));
                                    if (!level.addFreshEntity(rover)) return 0;
                                    context.getSource().sendSuccess(() -> Component.literal("Aster Rover deployed. Right-click to board; sneak to exit."), false);
                                    return 1;
                                }
                            }

                            context.getSource().sendFailure(Component.literal("No clear, dry ground nearby for the rover. Move to an open area."));
                            return 0;
                        }))));
    }
}
