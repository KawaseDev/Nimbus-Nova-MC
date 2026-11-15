package cc.nimbusnova.mc.scenario;

import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.moon.MoonController;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.world.scenario.ScenarioMaps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OutpostScenarioController {
    private static final Map<MinecraftServer, OutpostScenarioController> SESSIONS = new IdentityHashMap<>();
    public static final BlockPos PIPE = new BlockPos(-4, 65, 3);
    private final Map<UUID, MoonMission.Role> roles = new HashMap<>();
    private final Map<UUID, Integer> opened = new HashMap<>();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private ScenarioMission mission;
    private boolean issued, pipeRemoved;
    private int ticks;

    public static OutpostScenarioController get(final MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, _ -> new OutpostScenarioController());
    }

    public boolean selected() {
        return mission != null;
    }

    public ScenarioMission mission() {
        return mission;
    }

    public void select(final MinecraftServer server, final String id) {
        if (mission != null && !mission.ended()) throw new IllegalStateException("End the current outpost attempt first.");
        final MissionState.Phase mars = MissionController.get(server).snapshot().phase();
        if (ShipController.get(server).selected() || MoonController.get(server).active()
                || mars == MissionState.Phase.ACTIVE || mars == MissionState.Phase.PAUSED)
            throw new IllegalStateException("End the current Mars, Moon or ship attempt first.");
        try {
            ScenarioMaps.create(server, id);
        } catch (final IOException error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
        mission = new ScenarioMission(id);
        issued = false;
        pipeRemoved = false;
        roles.clear();
        opened.clear();
        restoreTaskBlocks(server);
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) join(player);
    }

    public void action(final MinecraftServer server, final String action) {
        if (mission == null) throw new IllegalStateException("Select an outpost scenario first.");
        switch (action) {
            case "start" -> mission.start();
            case "pause" -> mission.pause();
            case "resume", "acknowledge" -> mission.resume();
            case "abort" -> { mission.phase = ScenarioMission.Phase.ABORTED; mission.feedback = "Attempt ended by Mission Control."; mission.revision++; }
            case "reset" -> {
                final String id = mission.id;
                removeSupplies(server);
                restoreTaskBlocks(server);
                mission = new ScenarioMission(id);
                roles.clear();
                issued = false;
                pipeRemoved = false;
            }
            case "end" -> { end(server); return; }
            default -> throw new IllegalArgumentException("Unknown scenario control.");
        }
        broadcast(server, true);
    }

    public void end(final MinecraftServer server) {
        if (mission == null) return;
        restoreTaskBlocks(server);
        removeSupplies(server);
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(ScenarioMaps.dimension(mission.id))) continue;
            final ReturnPoint point = returns.remove(player.getUUID());
            final var destination = point == null || server.getLevel(point.dimension) == null ? server.overworld() : server.getLevel(point.dimension);
            final BlockPos spawn = destination.getRespawnData().pos();
            final Vec3 location = point == null ? new Vec3(spawn.getX() + 0.5,
                    destination.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5) : point.position;
            player.teleportTo(destination, location.x, location.y, location.z, Set.of(), point == null ? 0 : point.yaw, point == null ? 0 : point.pitch, true);
            player.resetFallDistance();
        }
        mission = null;
        roles.clear();
        opened.clear();
        broadcast(server, false);
    }

    public void join(final ServerPlayer player) {
        if (mission == null) return;
        final var level = player.level().getServer().getLevel(ScenarioMaps.dimension(mission.id));
        if (level == null) return;
        if (!player.level().dimension().equals(ScenarioMaps.dimension(mission.id)))
            returns.putIfAbsent(player.getUUID(), new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
        final Vec3 spawn = ScenarioMaps.joinpos(mission.id);
        player.stopRiding();
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, Set.of(), 180, 0, true);
        player.resetFallDistance();
        open(player);
    }

    public void open(final ServerPlayer player) {
        opened.remove(player.getUUID());
        send(player, true);
    }

    public static void register() {
        ScenarioContent.register();
        PayloadTypeRegistry.clientboundPlay().register(ScenarioPayload.TYPE, ScenarioPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ScenarioActionPayload.TYPE, ScenarioActionPayload.CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> server.execute(() -> {
            if (get(server).selected()) get(server).join(handler.player);
        }));
        ServerPlayNetworking.registerGlobalReceiver(ScenarioActionPayload.TYPE, (packet, context) -> context.server().execute(() -> {
            final OutpostScenarioController controller = get(context.server());
            final ServerPlayer player = context.player();
            if (controller.mission == null) return;
            try {
                if (!player.level().dimension().equals(ScenarioMaps.dimension(controller.mission.id))) throw new IllegalStateException("Join the scenario before operating equipment.");
                if (packet.action().equals("open")) { controller.open(player); return; }
                if (packet.action().equals("role")) {
                    if (controller.mission.phase != ScenarioMission.Phase.READY || packet.value() < 0 || packet.value() >= MoonMission.Role.values().length)
                        throw new IllegalStateException("Assign roles before launch.");
                    final MoonMission.Role role = MoonMission.Role.values()[packet.value()];
                    if (controller.roles.containsValue(role) && controller.roles.get(player.getUUID()) != role) throw new IllegalStateException("This crew role is already occupied.");
                    controller.roles.put(player.getUUID(), role);
                } else if (packet.action().equals("task")) {
                    if (packet.station() < 0 || packet.station() > 2 || player.distanceToSqr(Vec3.atCenterOf(ScenarioMaps.station(packet.station()))) > 36)
                        throw new IllegalStateException("Move close to the marked equipment.");
                    final MoonMission.Role required = packet.station() == 0 ? controller.mission.id.equals("shelter") ? MoonMission.Role.MAINTENANCE : MoonMission.Role.POWER
                            : packet.station() == 1 ? MoonMission.Role.LIFE_SUPPORT : controller.mission.id.equals("greenhouse") ? MoonMission.Role.SUPPLIES : MoonMission.Role.PILOT;
                    if (!operator(player) && controller.roles.get(player.getUUID()) != required) throw new IllegalStateException("Ask the " + required.name().toLowerCase().replace('_', ' ') + " specialist.");
                    if (!controller.mission.action(packet.station(), packet.value(), packet.revision())) throw new IllegalStateException("Check the updated station state before using this control.");
                } else {
                    if (!operator(player)) throw new IllegalStateException("Mission Control manages the clock.");
                    controller.action(context.server(), packet.action());
                    return;
                }
                controller.broadcast(context.server(), false);
            } catch (final IllegalArgumentException | IllegalStateException error) {
                player.sendSystemMessage(Component.literal(error.getMessage()));
                controller.send(player, false);
            }
        }));
        PlayerBlockBreakEvents.BEFORE.register((level, player, _, _, _) -> !isScenario(level.dimension().identifier().toString()) || operator((ServerPlayer) player));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer crew) || hand != InteractionHand.MAIN_HAND || !isScenario(level.dimension().identifier().toString())) return InteractionResult.PASS;
            final OutpostScenarioController controller = get(crew.level().getServer());
            if (controller.mission == null || !level.dimension().equals(ScenarioMaps.dimension(controller.mission.id))) return InteractionResult.PASS;
            if (player.distanceToSqr(Vec3.atCenterOf(hit.getBlockPos())) > 36) return InteractionResult.FAIL;
            final BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
            if (controller.mission.id.equals("shelter") && player.getItemInHand(hand).is(ScenarioContent.SHIELD.asItem())) {
                int slot = 0;
                for (final BlockPos target : ScenarioMaps.shieldSlots()) {
                    if (target.equals(placed) && level.getBlockState(target).isAir()) {
                        level.setBlock(target, ScenarioContent.SHIELD.defaultBlockState(), Block.UPDATE_ALL);
                        player.getItemInHand(hand).shrink(1);
                        controller.mission.shields |= 1 << slot;
                        controller.mission.revision++;
                        controller.broadcast(crew.level().getServer(), false);
                        return InteractionResult.SUCCESS;
                    }
                    slot++;
                }
                return InteractionResult.FAIL;
            }
            if (controller.mission.id.equals("greenhouse") && controller.mission.pipeBroken && placed.equals(PIPE) && player.getItemInHand(hand).is(RepairContent.PATCH_PLATE.asItem())) {
                if (!operator(crew) && controller.roles.get(crew.getUUID()) != MoonMission.Role.MAINTENANCE) return InteractionResult.FAIL;
                level.setBlock(PIPE, RepairContent.PATCH_PLATE.defaultBlockState(), Block.UPDATE_ALL);
                player.getItemInHand(hand).shrink(1);
                controller.mission.repair();
                controller.broadcast(crew.level().getServer(), false);
                return InteractionResult.SUCCESS;
            }
            if (controller.mission.id.equals("long_night") && controller.mission.cableBroken && hit.getBlockPos().equals(ScenarioMaps.station(0)) && player.getItemInHand(hand).is(RepairContent.WIRE_KIT)) {
                if (!operator(crew) && controller.roles.get(crew.getUUID()) != MoonMission.Role.POWER && controller.roles.get(crew.getUUID()) != MoonMission.Role.MAINTENANCE) return InteractionResult.FAIL;
                player.getItemInHand(hand).shrink(1);
                controller.mission.repair();
                controller.broadcast(crew.level().getServer(), false);
                return InteractionResult.SUCCESS;
            }
            for (int index = 0; index < 4; index++) {
                if (!hit.getBlockPos().equals(ScenarioMaps.station(index))) continue;
                if (index == 3) controller.supplies(crew);
                controller.opened.put(crew.getUUID(), index);
                controller.send(crew, true);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            final OutpostScenarioController controller = get(server);
            if (controller.mission == null || ++controller.ticks < 20) return;
            controller.ticks = 0;
            final ScenarioMission mission = controller.mission;
            final var level = server.getLevel(ScenarioMaps.dimension(mission.id));
            if (level == null) return;
            if (mission.id.equals("shelter")) {
                int mask = 0, slot = 0;
                for (final BlockPos position : ScenarioMaps.shieldSlots()) {
                    if (level.getBlockState(position).is(ScenarioContent.SHIELD)) mask |= 1 << slot;
                    slot++;
                }
                if (mission.shields != mask) { mission.shields = mask; mission.revision++; }
            }
            if (server.getPlayerList().getPlayerCount() == 0 && mission.phase == ScenarioMission.Phase.ACTIVE) mission.pause();
            for (final ServerPlayer player : level.players()) {
                if (Math.abs(player.getX()) <= 25 && Math.abs(player.getZ()) <= 25 && player.getY() >= 58) continue;
                final Vec3 spawn = ScenarioMaps.joinpos(mission.id);
                player.teleportTo(level, spawn.x, spawn.y, spawn.z, Set.of(), 180, 0, true);
                player.resetFallDistance();
                player.sendSystemMessage(Component.literal("Returned to the safe training area boundary."));
            }
            final boolean safe = level.players().stream().filter(player -> !player.isSpectator() && (controller.roles.containsKey(player.getUUID()) || operator(player))).allMatch(player -> protectedPosition(player.blockPosition()));
            mission.tick(safe);
            if (mission.pipeBroken && !controller.pipeRemoved) {
                if (level.getBlockState(PIPE).is(Blocks.COPPER_BLOCK.weathering().unaffected())) {
                    level.setBlock(PIPE, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    controller.pipeRemoved = true;
                }
            }
            controller.broadcast(server, false);
        });
    }

    public static boolean protectedPosition(final BlockPos position) {
        return Math.abs(position.getX()) <= 3 && position.getZ() >= -13 && position.getZ() <= -9 && position.getY() >= 65 && position.getY() <= 69;
    }

    private static boolean isScenario(final String dimension) {
        return dimension.startsWith("nimbus_nova:scenario_");
    }

    private static boolean operator(final ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private void supplies(final ServerPlayer player) {
        if (issued) { player.sendSystemMessage(Component.literal("Repair stores were already issued. Share the crew's existing items.")); return; }
        if (!operator(player) && !roles.containsKey(player.getUUID())) { player.sendSystemMessage(Component.literal("Choose a crew role before collecting stores.")); return; }
        issued = true;
        final ItemStack supplies = mission.id.equals("shelter") ? new ItemStack(ScenarioContent.SHIELD, 3)
                : mission.id.equals("long_night") ? new ItemStack(RepairContent.WIRE_KIT, 2) : new ItemStack(RepairContent.PATCH_PLATE, 2);
        if (!player.addItem(supplies)) player.drop(supplies, false, Prediction.SERVER_ONLY);
    }

    private void restoreTaskBlocks(final MinecraftServer server) {
        final var level = server.getLevel(ScenarioMaps.dimension(mission.id));
        if (level == null) return;
        if (mission.id.equals("greenhouse") && (level.getBlockState(PIPE).isAir() || level.getBlockState(PIPE).is(RepairContent.PATCH_PLATE)))
            level.setBlock(PIPE, Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (mission.id.equals("shelter"))
            for (final BlockPos slot : ScenarioMaps.shieldSlots())
                if (level.getBlockState(slot).is(ScenarioContent.SHIELD)) level.setBlock(slot, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private void removeSupplies(final MinecraftServer server) {
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                final ItemStack item = player.getInventory().getItem(slot);
                if (item.is(RepairContent.WIRE_KIT) || item.is(RepairContent.PATCH_PLATE.asItem()) || item.is(ScenarioContent.SHIELD.asItem())) player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
    }

    public JsonObject teacherState() {
        if (mission == null) return null;
        final JsonObject data = new JsonObject();
        data.addProperty("id", mission.id); data.addProperty("title", mission.title()); data.addProperty("phase", mission.phase.name());
        data.addProperty("feedback", mission.feedback); data.addProperty("ready", mission.phase == ScenarioMission.Phase.READY
                && (!mission.id.equals("shelter") || mission.scans == 7 && mission.shields == 7)
                && (!mission.id.equals("long_night") || (mission.loads & 3) == 3));
        data.addProperty("revision", mission.revision); data.addProperty("elapsed", mission.elapsed); data.addProperty("battery", mission.battery);
        data.addProperty("oxygen", mission.oxygen); data.addProperty("water", mission.water); data.addProperty("temperature", mission.temperature);
        data.addProperty("dose", mission.dose); data.addProperty("scans", mission.scans); data.addProperty("shields", mission.shields);
        data.addProperty("signal", mission.signal); data.addProperty("loads", mission.loads); data.addProperty("valves", mission.valves);
        data.addProperty("harvest", mission.harvest); data.addProperty("objective", mission.objective()); data.addProperty("source", mission.source()); data.addProperty("evidence", mission.evidence());
        data.addProperty("cableBroken", mission.cableBroken); data.addProperty("pipeBroken", mission.pipeBroken);
        final JsonArray moisture = new JsonArray(), rootAir = new JsonArray();
        for (int bed = 0; bed < 3; bed++) { moisture.add(mission.moisture[bed]); rootAir.add(mission.rootAir[bed]); }
        data.add("moisture", moisture); data.add("rootAir", rootAir);
        return data;
    }

    private void send(final ServerPlayer player, final boolean open) {
        final JsonObject data = teacherState();
        if (data == null) ServerPlayNetworking.send(player, new ScenarioPayload("{}", false));
        else {
            final MoonMission.Role role = roles.get(player.getUUID());
            final int target = role == MoonMission.Role.PILOT ? 2 : role == MoonMission.Role.POWER || role == MoonMission.Role.MAINTENANCE ? 0
                    : role == MoonMission.Role.LIFE_SUPPORT ? 1 : 3;
            final BlockPos point = ScenarioMaps.station(target);
            data.addProperty("targetX", point.getX()); data.addProperty("targetZ", point.getZ());
            data.addProperty("station", opened.getOrDefault(player.getUUID(), -1));
            data.addProperty("operator", operator(player)); data.addProperty("role", role == null ? "" : role.name());
            ServerPlayNetworking.send(player, new ScenarioPayload(data.toString(), open));
        }
    }

    private void broadcast(final MinecraftServer server, final boolean open) {
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) send(player, open);
    }
    private record ReturnPoint(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
        /* w */
    }

}
