package cc.nimbusnova.mc.hub;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.moon.MoonController;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.scenario.OutpostScenarioController;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.scenario.ScenarioMaps;
import cc.nimbusnova.mc.world.ship.ShipMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One classroom mission selection; each mission retains its own authoritative gameplay. */
public final class MissionHub {
    public record Mission(String id, String title, String description) {
        /* w */
    }

    public static final Mission[] MISSIONS = {
            new Mission("mars", "The Darkening Sky", "Mars outpost: prepare the crew, survive dust and preserve research."),
            new Mission("moon", "Moon Expedition", "Spacecraft flight, lunar landing, field science and a crew return."),
            new Mission("ship", "Return Window", "Spacecraft rescue: restore systems, repair compartments and return safely."),
            new Mission("shelter", "Shelter Before the Event", "Inspect protection, collect field readings and shelter before the alert."),
            new Mission("long_night", "The Long Night", "Keep essential heating and life support running on stored energy."),
            new Mission("greenhouse", "Greenhouse Emergency", "Balance irrigation and power while protecting the crew's food supply.")
    };
    private static final Map<MinecraftServer, MissionHub> SESSIONS = new IdentityHashMap<>();
    private final Set<UUID> watching = new HashSet<>();
    private String selected = "";

    private MissionHub() {
        /* w */
    }

    public static MissionHub get(final MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, _ -> new MissionHub());
    }

    public static boolean validMission(final String id) {
        for (final Mission mission : MISSIONS)
            if (mission.id.equals(id)) return true;
        return false;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(HubStatePayload.TYPE, HubStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HubActionPayload.TYPE, HubActionPayload.CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> get(server).watching.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            final MissionHub hub = get(server);
            for (final UUID id : Set.copyOf(hub.watching)) {
                final ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) hub.watching.remove(id);
                else hub.send(player, false, "");
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(HubActionPayload.TYPE, (packet, context) -> context.server().execute(() -> {
            final MissionHub hub = get(context.server());
            final ServerPlayer player = context.player();
            if (packet.action().equals("close")) {
                hub.watching.remove(player.getUUID());
                return;
            }
            try {
                if (packet.action().equals("status")) hub.open(player);
                else if (packet.action().equals("join")) hub.join(player);
                else if (packet.action().equals("continue")) hub.continueMission(player);
                else {
                    if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        throw new IllegalStateException("Mission Control selects and manages the classroom attempt.");
                    hub.action(context.server(), packet.action(), packet.mission());
                    if (packet.action().equals("select")) hub.continueMission(player);
                    else hub.open(player);
                }
            } catch (final IllegalArgumentException | IllegalStateException error) {
                hub.send(player, true, error.getMessage());
            }
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            final var command = Commands.literal("nimbus").executes(context -> {
                get(context.getSource().getServer()).open(context.getSource().getPlayerOrException());
                return 1;
            });
            command.then(Commands.literal("select").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("mission", StringArgumentType.word()).suggests((_, builder) -> {
                        for (final Mission mission : MISSIONS) builder.suggest(mission.id);
                        return builder.buildFuture();
                    }).executes(context -> {
                        try {
                            get(context.getSource().getServer()).action(context.getSource().getServer(), "select", StringArgumentType.getString(context, "mission"));
                            return 1;
                        } catch (final IllegalArgumentException | IllegalStateException error) {
                            context.getSource().sendFailure(Component.literal(error.getMessage()));
                            return 0;
                        }
                    })));
            for (final String action : new String[] { "start", "pause", "resume", "reset", "end" })
                command.then(Commands.literal(action).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    try {
                        get(context.getSource().getServer()).action(context.getSource().getServer(), action, "");
                        return 1;
                    } catch (final IllegalArgumentException | IllegalStateException error) {
                        context.getSource().sendFailure(Component.literal(error.getMessage()));
                        return 0;
                    }
                }));
            command.then(Commands.literal("join").executes(context -> {
                get(context.getSource().getServer()).join(context.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(command);
        });
    }

    public void open(final ServerPlayer player) {
        watching.add(player.getUUID());
        send(player, true, "");
    }

    public String selected(final MinecraftServer server) {
        final ShipController ship = ShipController.get(server);
        if (ship.selected()) return ship.mission().underlying().variant() == MoonMission.Variant.EXPLORATION ? "moon" : "ship";
        final JsonObject scenario = OutpostScenarioController.get(server).teacherState();
        if (scenario != null) return scenario.get("id").getAsString();
        if (!selected.equals("mars")) selected = "";
        final MissionState.Phase mars = MissionController.get(server).snapshot().phase();
        if (selected.isEmpty() && (mars == MissionState.Phase.ACTIVE || mars == MissionState.Phase.PAUSED)) return "mars";
        return selected;
    }

    public void action(final MinecraftServer server, final String action, final String missionId) {
        final String current = selected(server);
        if (action.equals("select")) {
            if (!validMission(missionId)) throw new IllegalArgumentException("Unknown mission. Choose a mission in /nimbus.");
            if (current.equals(missionId)) return;
            // Prepare the destination first so an occupied or unavailable map cannot discard an attempt.
            try {
                if (missionId.equals("mars")) {
                    if (MarsTrainingMap.create(server.createCommandSourceStack()) == 0)
                        throw new IllegalStateException("The Mars map could not be prepared. Existing blocks were preserved.");
                } else if (missionId.equals("ship") || missionId.equals("moon"))
                    ShipMap.create(server);
                else
                    ScenarioMaps.create(server, missionId);
            } catch (final IOException error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
            endCurrent(server, current);
            if (MoonController.get(server).mission() != null) MoonController.get(server).teacherAction(server, "moon_end");
            if (missionId.equals("mars")) {
                MissionController.get(server).control(server.createCommandSourceStack(), "reset");
                selected = "mars";
                for (final ServerPlayer player : server.getPlayerList().getPlayers()) fixedCommand(player, "mars join");
            } else if (missionId.equals("ship") || missionId.equals("moon")) {
                ShipController.get(server).teacherAction(server, missionId.equals("moon") ? "ship_explore" : "ship_select");
                selected = missionId;
            } else {
                OutpostScenarioController.get(server).select(server, missionId);
                selected = missionId;
            }
            return;
        }
        if (current.isEmpty()) {
            if (action.equals("end")) return;
            throw new IllegalStateException("Select a mission first in /nimbus.");
        }
        final String phase = state(server).get("phase").getAsString();
        if (action.equals("start") && (phase.equals("ACTIVE") || phase.equals("PAUSED"))
                || action.equals("pause") && phase.equals("PAUSED")
                || action.equals("resume") && phase.equals("ACTIVE")) return;
        if (!Set.of("start", "pause", "resume", "reset", "end").contains(action)) throw new IllegalArgumentException("Unknown mission control.");
        if (action.equals("end")) {
            endCurrent(server, current);
            selected = "";
        } else if (current.equals("mars")) {
            if (action.equals("start") && MissionController.get(server).snapshot().phase() != MissionState.Phase.READY) return;
            if (action.equals("resume") && MissionController.get(server).education().incident() != null
                    && MissionController.get(server).control(server.createCommandSourceStack(), "acknowledge") == 0)
                throw new IllegalStateException("Complete the applied crew check in Minecraft before resuming.");
            if (MissionController.get(server).control(server.createCommandSourceStack(), action) == 0)
                throw new IllegalStateException("Complete the mission's current crew check before using that control.");
        } else if (current.equals("ship") || current.equals("moon")) {
            if (action.equals("resume") && ShipController.get(server).mission().snapshot().incident() != null)
                ShipController.get(server).teacherAction(server, "ship_acknowledge");
            ShipController.get(server).teacherAction(server, "ship_" + action);
        } else
            OutpostScenarioController.get(server).action(server, action);
    }

    private void endCurrent(final MinecraftServer server, final String current) {
        if (ShipController.get(server).selected()) ShipController.get(server).teacherAction(server, "ship_end");
        if (OutpostScenarioController.get(server).teacherState() != null) OutpostScenarioController.get(server).end(server);
        final MissionController mars = MissionController.get(server);
        if (mars.snapshot().phase() == MissionState.Phase.ACTIVE || mars.snapshot().phase() == MissionState.Phase.PAUSED)
            mars.control(server.createCommandSourceStack(), "abort");
        if (current.equals("mars")) {
            for (final ServerPlayer player : server.getPlayerList().getPlayers())
                if (player.level().dimension().equals(MarsTrainingMap.DIMENSION)) fixedCommand(player, "mars leave");
            mars.control(server.createCommandSourceStack(), "reset");
        }
    }

    public void join(final ServerPlayer player) {
        final String current = selected(player.level().getServer());
        watching.remove(player.getUUID());
        if (current.equals("mars")) fixedCommand(player, "mars join");
        else if (current.equals("ship") || current.equals("moon")) ShipController.get(player.level().getServer()).join(player);
        else if (!current.isEmpty()) OutpostScenarioController.get(player.level().getServer()).join(player);
        else throw new IllegalStateException("Mission Control must select a mission before the crew joins.");
    }

    private void continueMission(final ServerPlayer player) {
        final String current = selected(player.level().getServer());
        watching.remove(player.getUUID());
        if (current.equals("mars")) MissionController.get(player.level().getServer()).openConsole(player);
        else if (current.equals("ship") || current.equals("moon")) ShipController.get(player.level().getServer()).openConsole(player);
        else if (!current.isEmpty()) OutpostScenarioController.get(player.level().getServer()).open(player);
        else throw new IllegalStateException("Select a mission first.");
    }

    public JsonObject state(final MinecraftServer server) {
        final String current = selected(server);
        final JsonObject data;
        if (current.equals("mars")) {
            data = new JsonObject();
            data.addProperty("phase", MissionController.get(server).snapshot().phase().name());
            data.addProperty("ready", !server.getPlayerList().getPlayers().isEmpty()
                    && MissionController.get(server).progress(server.getPlayerList().getPlayers().getFirst()).ready());
            data.addProperty("feedback", MissionController.get(server).snapshot().lastEvent());
            final var incident = MissionController.get(server).education().incident();
            data.addProperty("incident", incident == null ? "" : incident.name());
        } else if (current.equals("ship") || current.equals("moon")) data = ShipController.get(server).teacherState();
        else if (!current.isEmpty()) data = OutpostScenarioController.get(server).teacherState();
        else {
            data = new JsonObject();
            data.addProperty("phase", "NONE");
            data.addProperty("ready", false);
        }
        data.addProperty("selected", current);
        final JsonArray missions = new JsonArray();
        for (final Mission mission : MISSIONS) {
            final JsonObject item = new JsonObject();
            item.addProperty("id", mission.id);
            item.addProperty("title", mission.title);
            item.addProperty("description", mission.description);
            missions.add(item);
        }
        data.add("missions", missions);
        return data;
    }

    private void send(final ServerPlayer player, final boolean open, final String error) {
        if (!ServerPlayNetworking.canSend(player, HubStatePayload.TYPE)) return;
        final JsonObject data = state(player.level().getServer());
        data.addProperty("operator", player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        data.addProperty("error", error == null ? "" : error);
        ServerPlayNetworking.send(player, new HubStatePayload(data.toString(), open));
    }

    private static void fixedCommand(final ServerPlayer player, final String command) {
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }
}
