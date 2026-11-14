package cc.nimbusnova.mc.npc;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

public final class MissionNpcs {
    public static final EntityType<MissionAstronaut> GUIDE = type("mission_guide");
    public static final EntityType<MissionAstronaut> ENGINEER = type("mission_engineer");

    private MissionNpcs() {
        /* w */
    }

    private static EntityType<MissionAstronaut> type(final String name) {
        final ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.parse("nimbus_nova:" + name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.of(MissionAstronaut::new, MobCategory.CREATURE).sized(0.6F, 1.9F)
                        .clientTrackingRange(8).build(key));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(GUIDE, Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.FOLLOW_RANGE, 12));
        FabricDefaultAttributeRegistry.register(ENGINEER, Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.FOLLOW_RANGE, 12));
        PayloadTypeRegistry.clientboundPlay().register(NpcDialoguePayload.TYPE, NpcDialoguePayload.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            final var spawn = Commands.literal("spawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
            for (final boolean engineer : new boolean[] { false, true }) {
                spawn.then(Commands.literal(engineer ? "engineer" : "guide").executes(context -> {
                    final ServerPlayer player = context.getSource().getPlayerOrException();
                    for (int direction = 0; direction < 8; direction++) {
                        final double angle = Math.toRadians(player.getYRot() + direction * 45);
                        final double x = Math.floor(player.getX() - Math.sin(angle) * 3) + 0.5;
                        final double z = Math.floor(player.getZ() + Math.cos(angle) * 3) + 0.5;
                        for (int offset = 2; offset >= -2; offset--) {
                            if (spawnAt(player.level(), x, player.blockPosition().getY() + offset, z, engineer) == null) continue;
                            context.getSource().sendSuccess(() -> Component.literal("Astronaut ready. Right-click to talk."), false);
                            return 1;
                        }
                    }

                    context.getSource().sendFailure(Component.literal("No clear, dry ground nearby for an astronaut."));
                    return 0;
                }));
            }

            dispatcher.register(Commands.literal("npc").then(spawn));
        });
    }

    public static MissionAstronaut spawnAt(final ServerLevel level, final double x, final double y, final double z, final boolean engineer) {
        final EntityType<MissionAstronaut> type = engineer ? ENGINEER : GUIDE;
        for (final MissionAstronaut existing : level.getEntitiesOfClass(MissionAstronaut.class,
                new AABB(x - 2, y - 2, z - 2, x + 2, y + 2, z + 2))) {
            if (existing.getType() == type) return existing;
        }

        final BlockPos position = BlockPos.containing(x, y, z);
        if (!level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP)
                || !level.getFluidState(position).isEmpty() || !level.getFluidState(position.above()).isEmpty()) return null;

        final MissionAstronaut npc = type.create(level, EntitySpawnReason.COMMAND);
        if (npc == null) return null;

        npc.snapTo(x, y, z, 180, 0);
        if (!level.noCollision(npc)) return null;

        npc.setCustomName(Component.literal(engineer ? "Engineer Sol" : "Commander Vega"));
        npc.setCustomNameVisible(true);
        return level.addFreshEntity(npc) ? npc : null;
    }
}
