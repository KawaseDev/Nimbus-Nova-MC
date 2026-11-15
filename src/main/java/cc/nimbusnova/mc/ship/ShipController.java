package cc.nimbusnova.mc.ship;

import cc.nimbusnova.mc.ship.rooms.ShipRooms;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.moon.MoonController;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.npc.MissionNpcs;
import cc.nimbusnova.mc.world.ship.ShipMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ShipController {
    private static final Map<MinecraftServer, ShipController> SESSIONS = new IdentityHashMap<>();
    private final Map<UUID, MoonMission.Role> roles = new HashMap<>();
    private final Map<UUID, BlockPos> opened = new HashMap<>();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private final Map<UUID, HeldAction> holds = new HashMap<>();
    private final Map<UUID, Integer> punchTicks = new HashMap<>();
    private ShipMission mission;
    private int ticks;
    private boolean roomPowerEvent, roomBreachEvent;

    public static ShipController get(final MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, _ -> new ShipController());
    }

    public ShipMission mission() {
        return mission;
    }

    public MoonMission.Role role(final ServerPlayer player) {
        return roles.get(player.getUUID());
    }

    public boolean selected() {
        return mission != null;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ShipPayload.TYPE, ShipPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShipActionPayload.TYPE, ShipActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ShipActionPayload.TYPE, (packet, context) ->
                context.server().execute(() -> get(context.server()).action(context.player(), packet)));
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> server.execute(() -> {
            final ShipController controller = get(server);
            if (controller.selected()) controller.join(handler.player);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            final ShipController controller = get(server);
            controller.opened.remove(handler.player.getUUID());
            controller.holds.remove(handler.player.getUUID());
            controller.punchTicks.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            final ShipController controller = get(server);
            if (controller.mission == null) return;
            if (server.getTickCount() % 5 == 0)
                controller.holds.entrySet().removeIf(entry -> {
                    final ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    final HeldAction hold = entry.getValue();
                    return player == null || hold.mission != controller.mission
                            || controller.mission.snapshot().phase() != hold.phase
                            || controller.mission.snapshot().stage() != hold.stage
                            || (long) server.getTickCount() - hold.startedTick > 200 || !near(player, hold.position);
                });
            if (++controller.ticks < 20) return;
            controller.ticks = 0;
            if (server.getPlayerList().getPlayerCount() == 0 && controller.mission.snapshot().phase() == ShipMission.Phase.ACTIVE)
                controller.mission.pause();
            controller.mission.tickSecond();
            ShipRooms.tick(server, controller.mission.snapshot().phase() != ShipMission.Phase.ACTIVE);
            final var level = server.getLevel(ShipMap.DIMENSION);
            if (level != null && controller.mission.snapshot().phase() == ShipMission.Phase.ACTIVE) {
                final var stage = controller.mission.snapshot().stage();
                for (final ShipMap.Marker marker : ShipMap.MARKERS) {
                    final var block = level.getBlockState(marker.position());
                    if (marker.task() == ShipTask.EXERCISE) continue;
                    final boolean active = controller.mission.activeTask(marker.task()) || controller.mission.completed(marker.task());
                    if (block.is(ShipMap.block(marker.task())) && block.getValue(BlockStateProperties.POWERED) != active)
                        level.setBlock(marker.position(), block.setValue(BlockStateProperties.POWERED, active), Block.UPDATE_CLIENTS);
                    if (marker.task() != ShipTask.ENGINE) continue;
                    if (stage == MoonMission.Stage.EMERGENCY)
                        level.sendParticles(ParticleTypes.SMOKE, marker.position().getX() + 0.5, marker.position().getY() + 1,
                                marker.position().getZ() + 0.5, 3, 0.15, 0.1, 0.15, 0.025);
                    if (stage == MoonMission.Stage.RETURN && (controller.mission.board(ShipTask.PILOT) & 256) != 0)
                        level.sendParticles(ParticleTypes.FLAME, marker.position().getX() + 0.5, marker.position().getY() + 0.5,
                                marker.position().getZ() - 1, 6, 0.15, 0.15, 0.3, 0.08);
                }
            }
            controller.broadcast(server, false);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.dimension().equals(ShipMap.DIMENSION) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (ShipMap.task(level.getBlockState(hit.getBlockPos()).getBlock()) == null) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer)
                get(serverPlayer.level().getServer()).openStation(serverPlayer, hit.getBlockPos());
            return InteractionResult.SUCCESS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, position, _) -> {
            if (!level.dimension().equals(ShipMap.DIMENSION) || hand != InteractionHand.MAIN_HAND
                    || ShipMap.task(level.getBlockState(position).getBlock()) != ShipTask.EXERCISE) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) {
                final ShipController controller = get(serverPlayer.level().getServer());
                if (controller.mission != null && controller.mission.activeTask(ShipTask.EXERCISE) && near(serverPlayer, position)) {
                    final int now = serverPlayer.level().getServer().getTickCount();
                    final Integer previous = controller.punchTicks.get(serverPlayer.getUUID());
                    if (previous != null && now - previous < 6) return InteractionResult.SUCCESS;
                    controller.punchTicks.put(serverPlayer.getUUID(), now);
                    controller.action(serverPlayer, new ShipActionPayload("task", position, controller.mission.revision(), 0));
                    final var bag = serverPlayer.level().getBlockState(position);
                    serverPlayer.level().setBlock(position, bag.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_CLIENTS);
                    serverPlayer.level().scheduleTick(position, bag.getBlock(), 6);
                    serverPlayer.level().sendParticles(ParticleTypes.CRIT, position.getX() + 0.5, position.getY() + 0.6,
                            position.getZ() + 0.5, 4, 0.15, 0.2, 0.15, 0.03);
                }
            }
            return InteractionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, _, _, _) ->
                !level.dimension().equals(ShipMap.DIMENSION) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            final var command = Commands.literal("ship").executes(context -> {
                get(context.getSource().getServer()).openConsole(context.getSource().getPlayerOrException());
                return 1;
            });
            command.then(Commands.literal("join").executes(context -> {
                get(context.getSource().getServer()).join(context.getSource().getPlayerOrException());
                return 1;
            }));
            command.then(Commands.literal("leave").executes(context -> {
                get(context.getSource().getServer()).leave(context.getSource().getPlayerOrException());
                return 1;
            }));
            for (final String action : new String[] { "select", "start", "pause", "acknowledge", "resume", "abort", "reset", "end" }) {
                command.then(Commands.literal(action).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    try {
                        get(context.getSource().getServer()).teacherAction(context.getSource().getServer(), "ship_" + action);
                        return 1;
                    } catch (final IllegalArgumentException | IllegalStateException error) {
                        context.getSource().sendFailure(Component.literal(error.getMessage()));
                        return 0;
                    }
                }));
            }
            command.then(Commands.literal("rooms").executes(context -> {
                ShipRooms.send(context.getSource().getPlayerOrException(), true);
                return 1;
            }));
            command.then(Commands.literal("fault").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("room", StringArgumentType.word()).then(Commands.argument("fault", StringArgumentType.word())
                            .executes(context -> {
                                if (!get(context.getSource().getServer()).selected()) return 0;
                                ShipRooms.trigger(context.getSource().getServer(), StringArgumentType.getString(context, "room"), StringArgumentType.getString(context, "fault"));
                                return 1;
                            }))));
            dispatcher.register(command);
        });
    }

    public void teacherAction(final MinecraftServer server, final String action) {
        if (action.equals("ship_select")) {
            if (mission != null && !mission.snapshot().phase().ended()) throw new IllegalStateException("End the selected ship attempt before selecting another.");
            final MissionState.Phase mars = MissionController.get(server).snapshot().phase();
            if (mars == MissionState.Phase.ACTIVE || mars == MissionState.Phase.PAUSED || MoonController.get(server).active())
                throw new IllegalStateException("End the current Mars or Moon attempt before selecting Return Window.");
            try {
                ShipMap.create(server);
            } catch (final IOException error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
            ShipRooms.clear(server);
            roomPowerEvent = false;
            roomBreachEvent = false;
            mission = new ShipMission();
            roles.clear();
            opened.clear();
            for (final ServerPlayer player : server.getPlayerList().getPlayers()) join(player);
        } else {
            if (mission == null) throw new IllegalStateException("Select Return Window first with /ship select.");
            switch (action) {
                case "ship_start" -> mission.start();
                case "ship_pause" -> mission.pause();
                case "ship_acknowledge" -> mission.acknowledge();
                case "ship_resume" -> mission.resume();
                case "ship_abort" -> mission.abort();
                case "ship_reset" -> {
                    ShipRooms.clear(server);
                    roomPowerEvent = false;
                    roomBreachEvent = false;
                    mission = new ShipMission();
                    roles.clear();
                    opened.clear();
                }
                case "ship_end" -> {
                    ShipRooms.clear(server);
                    for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
                        if (player.level().dimension().equals(ShipMap.DIMENSION)) leave(player);
                    }
                    mission = null;
                    roles.clear();
                    opened.clear();
                }
                default -> throw new IllegalArgumentException("Unknown ship action.");
            }
        }
        holds.clear();
        punchTicks.clear();
        broadcast(server, true);
    }

    public void join(final ServerPlayer player) {
        if (mission == null) {
            player.sendSystemMessage(Component.literal("Ask Mission Control to select Return Window first."));
            return;
        }
        final var server = player.level().getServer();
        if (!player.level().dimension().equals(ShipMap.DIMENSION))
            returns.put(player.getUUID(), new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
        player.stopRiding();
        player.teleportTo(server.getLevel(ShipMap.DIMENSION), ShipMap.SPAWN.getX() + 0.5, ShipMap.SPAWN.getY(), ShipMap.SPAWN.getZ() + 0.5, Set.of(), 0, 0, true);
        player.resetFallDistance();
        MissionNpcs.spawnAt(player.level(), 1.5, 81, 10.5, false);
        MissionNpcs.spawnAt(player.level(), -1.5, 81, -4.5, true);
        send(player, true);
    }

    public void leave(final ServerPlayer player) {
        holds.remove(player.getUUID());
        punchTicks.remove(player.getUUID());
        if (!player.level().dimension().equals(ShipMap.DIMENSION)) return;
        final MinecraftServer server = player.level().getServer();
        final ReturnPoint point = returns.remove(player.getUUID());
        final var destination = point == null ? server.overworld() : server.getLevel(point.dimension);
        if (destination == null) return;
        final BlockPos spawn = destination.getRespawnData().pos();
        final Vec3 location = point == null ? new Vec3(spawn.getX() + 0.5,
                destination.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5) : point.position;
        player.teleportTo(destination, location.x, location.y, location.z, Set.of(), point == null ? 0 : point.yaw, point == null ? 0 : point.pitch, true);
        player.resetFallDistance();
    }

    public void openConsole(final ServerPlayer player) {
        opened.remove(player.getUUID());
        if (mission == null) player.sendSystemMessage(Component.literal("No ship mission selected. Mission Control can use /ship select."));
        else send(player, true);
    }

    public void openStation(final ServerPlayer player, final BlockPos position) {
        if (mission == null || !near(player, position)) return;
        opened.put(player.getUUID(), position.immutable());
        send(player, true);
    }

    private static boolean near(final ServerPlayer player, final BlockPos position) {
        if (!player.isAlive() || player.isSpectator() || !player.level().dimension().equals(ShipMap.DIMENSION)
                || player.distanceToSqr(Vec3.atCenterOf(position)) > 36
                || ShipMap.task(player.level().getBlockState(position).getBlock()) == null) return false;
        return player.level().getBlockState(position).getShape(player.level(), position).toAabbs().stream()
                .map(box -> box.getCenter().add(Vec3.atLowerCornerOf(position)))
                .anyMatch(point -> player.level().clip(new ClipContext(player.getEyePosition(), point,
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).getBlockPos().equals(position));
    }

    private void action(final ServerPlayer player, final ShipActionPayload packet) {
        if (mission == null) return;
        try {
            final boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
            if (packet.action().equals("leave")) {
                leave(player);
                return;
            }
            if (packet.action().equals("hold_cancel")) {
                holds.remove(player.getUUID());
                return;
            }
            if (!player.level().dimension().equals(ShipMap.DIMENSION)) throw new IllegalStateException("Join the ship before controlling its systems.");
            if (packet.action().equals("open")) {
                openConsole(player);
                return;
            }
            if (packet.action().equals("role")) {
                if (mission.snapshot().phase() != ShipMission.Phase.READY) throw new IllegalStateException("Assign roles before launch.");
                if (packet.value() < 0 || packet.value() >= MoonMission.Role.values().length) throw new IllegalArgumentException("Invalid crew role.");
                final MoonMission.Role role = MoonMission.Role.values()[packet.value()];
                if (roles.containsValue(role) && roles.get(player.getUUID()) != role) throw new IllegalStateException("That crew role is already occupied.");
                roles.put(player.getUUID(), role);
            } else if (packet.action().equals("task") || packet.action().equals("hold_begin") || packet.action().equals("hold_finish")) {
                stationAction(player, packet);
            } else {
                if (!operator) throw new IllegalStateException("Mission Control manages the mission clock and learning pauses.");
                teacherAction(player.level().getServer(), "ship_" + packet.action());
                return;
            }
            broadcast(player.level().getServer(), false);
        } catch (final IllegalArgumentException | IllegalStateException error) {
            player.sendSystemMessage(Component.literal(error.getMessage()));
            send(player, false);
        }
    }

    private void stationAction(final ServerPlayer player, final ShipActionPayload packet) {
        final HeldAction held = packet.action().equals("hold_finish") ? holds.remove(player.getUUID()) : null;
        final ShipTask task = ShipMap.task(player.level().getBlockState(packet.position()).getBlock());
        if (task == null || !near(player, packet.position())) throw new IllegalStateException("Move close to the marked ship equipment first.");
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                && task.role != null && roles.get(player.getUUID()) != task.role)
            throw new IllegalStateException("Ask the " + task.role.name().replace('_', ' ').toLowerCase() + " specialist to use this system.");

        if (ShipRooms.blockedStation(player, packet.position()))
            throw new IllegalStateException("This room has no power. Collect a wire kit from storage and repair its junction.");
        final var state = mission.snapshot();
        if (task == ShipTask.PILOT && state.stage() == MoonMission.Stage.RETURN && packet.value() == 1 && ShipRooms.hasUnresolved(player.level().getServer()))
            throw new IllegalStateException("Resolve the room alerts before committing the return burn.");
        final boolean requiresHold = ShipHoldActions.requires(task, state.stage(), packet.value());
        if (packet.action().equals("hold_begin")) {
            holds.remove(player.getUUID());
            if (!requiresHold || packet.revision() != mission.revision()
                    || state.phase() != ShipMission.Phase.READY && state.phase() != ShipMission.Phase.ACTIVE
                    || !mission.activeTask(task) || mission.completed(task))
                throw new IllegalStateException("Check the station before charging this control.");
            holds.put(player.getUUID(), new HeldAction(mission, packet.position().immutable(), task,
                    state.phase(), state.stage(), mission.board(task), packet.value(), player.level().getServer().getTickCount()));
            return;
        }

        final int revision;
        if (packet.action().equals("hold_finish")) {
            if (!requiresHold || held == null || held.mission != mission || held.task != task
                    || !held.position.equals(packet.position()) || held.action != packet.value()
                    || held.phase != state.phase() || held.stage != state.stage() || held.board != mission.board(task)
                    || !ShipHoldActions.elapsedValid(held.startedTick, player.level().getServer().getTickCount(),
                    ShipHoldActions.durationTicks(task, state.stage(), packet.value())))
                throw new IllegalStateException("Hold the control continuously until its charge is complete. Interrupted work is not applied.");
            revision = mission.revision();
        } else {
            holds.remove(player.getUUID());
            if (requiresHold) throw new IllegalStateException("Hold this control to operate it; a quick press cannot commit the action.");
            revision = packet.revision();
        }

        if (!mission.action(task, revision, packet.value()))
            throw new IllegalStateException("Check the updated station state; that control is not available now.");
        if (mission.snapshot().phase() == ShipMission.Phase.PAUSED || mission.snapshot().phase().ended())
            holds.clear();
        opened.put(player.getUUID(), packet.position());
        final var block = player.level().getBlockState(packet.position());
        player.level().setBlock(packet.position(), block.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_CLIENTS);
    }

    private static void triggerRoomFault(final MinecraftServer server, final String room, final String fault) {
        try {
            ShipRooms.trigger(server, room, fault);
        } catch (final IllegalStateException error) {
            for (final ServerPlayer player : server.getPlayerList().getPlayers())
                player.sendSystemMessage(Component.literal("Room scenario could not start: " + error.getMessage()));
        }
    }

    private void broadcast(final MinecraftServer server, final boolean open) {
        if (mission != null) {
            if (mission.snapshot().stage() == MoonMission.Stage.EMERGENCY && !roomPowerEvent) {
                roomPowerEvent = true;
                triggerRoomFault(server, "ENGINEERING", "POWER");
            }
            if (mission.snapshot().stage() == MoonMission.Stage.FILTER && !roomBreachEvent) {
                roomBreachEvent = true;
                triggerRoomFault(server, "SCIENCE", "BREACH");
            }
        }
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) send(player, open);
    }

    private void send(final ServerPlayer player, final boolean open) {
        if (!ServerPlayNetworking.canSend(player, ShipPayload.TYPE)) return;
        final boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        if (mission == null) {
            ServerPlayNetworking.send(player, new ShipPayload("", "", "", 0, 0, -1, -1, -1, -1, -1, -1, "", "", "", "", "", 0, 0, false, BlockPos.ZERO, BlockPos.ZERO, open, operator));
            return;
        }
        final ShipMission.Snapshot state = mission.snapshot();
        final MoonMission.Role role = roles.get(player.getUUID());
        final ShipTask objective = mission.objectiveTask(role);
        final BlockPos station = opened.getOrDefault(player.getUUID(), BlockPos.ZERO);
        final ShipTask task = ShipMap.task(player.level().getBlockState(station).getBlock());
        final String summary;
        if (state.phase().ended()) {
            final String choices = String.join(" ", mission.debrief().stream()
                    .filter(line -> line.contains("CO2 removal") || line.contains("energy units"))
                    .limit(2).toList());
            summary = state.outcome() + " Battery " + (int) state.battery() + ", oxygen " + (int) state.oxygen()
                    + ", fuel " + (int) state.fuel() + ". " + choices;
        } else
            summary = mission.instruction();
        ServerPlayNetworking.send(player, new ShipPayload(state.phase().name(), state.stage().name(), role == null ? "" : role.name(), state.revision(), state.remaining(),
                operator || role == MoonMission.Role.POWER ? (int) state.battery() : -1,
                operator || role == MoonMission.Role.LIFE_SUPPORT ? (int) state.oxygen() : -1,
                operator || role == MoonMission.Role.SUPPLIES ? (int) state.water() : -1,
                operator || role == MoonMission.Role.PILOT ? (int) state.fuel() : -1,
                operator || role == MoonMission.Role.MAINTENANCE ? state.parts() : -1,
                operator || role == MoonMission.Role.LIFE_SUPPORT ? (int) state.co2() : -1,
                state.phase().ended() ? state.phase().name() : objective.label, summary, state.feedback(), state.incident() == null ? "" : state.incident().name(),
                task == null ? "" : task.name(), task == null ? 0 : mission.board(task), task == null ? 0 : mission.aux(task),
                task != null && mission.completed(task), station, ShipMap.target(objective, player.blockPosition()), open, operator));
    }

    public JsonObject teacherState() {
        if (mission == null) return null;
        final ShipMission.Snapshot state = mission.snapshot();
        final JsonObject result = new JsonObject();
        result.addProperty("title", "Return Window / spacecraft rescue");
        result.addProperty("phase", state.phase().name());
        result.addProperty("stage", state.stage().name());
        result.addProperty("battery", state.battery());
        result.addProperty("oxygen", state.oxygen());
        result.addProperty("water", state.water());
        result.addProperty("fuel", state.fuel());
        result.addProperty("parts", state.parts());
        result.addProperty("co2", state.co2());
        result.addProperty("feedback", state.feedback());
        result.addProperty("incident", state.incident() == null ? "" : state.incident().name());
        result.addProperty("acknowledged", mission.underlying().acknowledged());
        final JsonArray journal = new JsonArray();
        for (final String decision : mission.debrief()) journal.add(decision);
        result.add("decisions", journal);
        return result;
    }

    private record HeldAction(ShipMission mission, BlockPos position, ShipTask task, ShipMission.Phase phase,
                              MoonMission.Stage stage, int board, int action, int startedTick) {
        /* w */
    }

    private record ReturnPoint(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
        /* w */
    }
}
