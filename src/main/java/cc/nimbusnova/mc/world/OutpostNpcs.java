package cc.nimbusnova.mc.world;

import cc.nimbusnova.mc.NimbusNovaMod;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.NpcDialogue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two fictional NPCs at Aster Outpost: a mission specialist in the habitat and a maintenance robot in the power yard.
 * Right-clicking one shows its next line in chat; lines change with the mission phase.
 */
public final class OutpostNpcs {
    static final String TAG = "nimbus_nova_npc";
    static final String SPECIALIST_TAG = "nimbus_nova_specialist";
    static final String ROBOT_TAG = "nimbus_nova_robot";
    public static final BlockPos SPECIALIST_POSITION = new BlockPos(2, 66, 3);
    public static final BlockPos ROBOT_POSITION = new BlockPos(-20, 66, -2);
    // Next line index per server, player, and NPC, so each click advances and players can replay.
    private static final Map<MinecraftServer, Map<String, Integer>> PROGRESS = new IdentityHashMap<>();

    private OutpostNpcs() {
        /* w */
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(PROGRESS::remove);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!entity.entityTags().contains(TAG)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

            talk(serverPlayer, entity);
            return InteractionResult.SUCCESS;
        });
    }

    /** Spawns any NPC that is missing near its post. Safe to call repeatedly. */
    static void ensureSpawned(final ServerLevel level) {
        if (!present(level, SPECIALIST_POSITION, SPECIALIST_TAG)) {
            final Mob specialist = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            if (specialist != null) place(level, specialist, SPECIALIST_POSITION, NpcDialogue.SPECIALIST, SPECIALIST_TAG, 180);
        }

        if (!present(level, ROBOT_POSITION, ROBOT_TAG)) {
            final CopperGolem robot = EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.COMMAND);
            if (robot != null) {
                robot.setWeatherState(WeatheringCopper.WeatherState.UNAFFECTED);
                place(level, robot, ROBOT_POSITION, NpcDialogue.ROBOT, ROBOT_TAG, 90);
            }
        }
    }

    private static boolean present(final ServerLevel level, final BlockPos position, final String tag) {
        level.getChunk(position);
        return !level.getEntitiesOfClass(Entity.class, new AABB(position).inflate(6),
                entity -> entity.entityTags().contains(tag)).isEmpty();
    }

    private static void place(final ServerLevel level, final Mob npc, final BlockPos position, final String name,
                              final String tag, final float yaw) {
        npc.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, yaw, 0);
        npc.setYHeadRot(yaw);
        npc.setNoAi(true);
        npc.setPermanentlyInvulnerable(true);
        npc.setSilent(true);
        npc.setPersistenceRequired();
        npc.setCustomName(Component.literal(name));
        npc.setCustomNameVisible(true);
        npc.addTag(TAG);
        npc.addTag(tag);
        level.addFreshEntity(npc);
        NimbusNovaMod.LOGGER.info("Placed outpost NPC {} at {}", name, position);
    }

    private static void talk(final ServerPlayer player, final Entity npc) {
        final MissionController controller = MissionController.get(player.level().getServer());
        final boolean robot = npc.entityTags().contains(ROBOT_TAG);
        final List<String> lines = robot ? NpcDialogue.robot(controller.snapshot())
                : NpcDialogue.specialist(controller.snapshot(), controller.role(player.getUUID()));
        final Map<String, Integer> progress = PROGRESS.computeIfAbsent(player.level().getServer(), _ -> new HashMap<>());
        final String key = player.getUUID() + (robot ? ":robot" : ":specialist");
        final int index = progress.getOrDefault(key, 0) % lines.size();
        progress.put(key, index + 1);

        player.sendSystemMessage(Component.literal("[" + (robot ? NpcDialogue.ROBOT : NpcDialogue.SPECIALIST) + "] ")
                .withStyle(robot ? ChatFormatting.AQUA : ChatFormatting.GOLD)
                .append(Component.literal(lines.get(index)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  (" + (index + 1) + "/" + lines.size() + ")").withStyle(ChatFormatting.GRAY)));
    }
}
