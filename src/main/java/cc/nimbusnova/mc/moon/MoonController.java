package cc.nimbusnova.mc.moon;

import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.learning.LearningChecks;
import cc.nimbusnova.mc.report.ReportStore;
import cc.nimbusnova.mc.scenario.OutpostScenarioController;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One classroom mission per server; actions are applied on the server thread. */
public final class MoonController {
    private static final Map<MinecraftServer, MoonController> SESSIONS = new IdentityHashMap<>();
    private final Map<UUID, MoonMission.Role> roles = new HashMap<>();
    private MoonMission mission;

    public static MoonController get(MinecraftServer server) { return SESSIONS.computeIfAbsent(server, ignored -> new MoonController()); }
    public MoonMission mission() { return mission; }
    public boolean active() { return mission != null && mission.stage() != MoonMission.Stage.COMPLETE; }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(MoonPayload.TYPE, MoonPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MoonActionPayload.TYPE, MoonActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MoonActionPayload.TYPE, (packet, context) ->
                context.server().execute(() -> get(context.server()).playerAction(context.player(), packet.action())));
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> get(server).send(handler.player, true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            MoonController controller = get(server);
            MoonMission.Role role = controller.roles.remove(handler.player.getUUID());
            if (role != null && controller.mission != null) controller.mission.removeConfirmation(role);
            controller.broadcast(server, false);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> dispatcher.register(Commands.literal("moon")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MoonController controller = get(context.getSource().getServer());
                    if (controller.mission == null) { context.getSource().sendFailure(Component.literal("No Moon mission is running.")); return 0; }
                    controller.send(player, true);
                    return 1;
                })));
    }

    public Set<MoonMission.Role> assignedRoles() {
        return Set.copyOf(roles.values());
    }

    public void teacherAction(final MinecraftServer server, final String action) {
        ReportStore.capture(server);
        switch (action) {
            case "moon_exploration", "moon_rescue" -> {
                if (active() || ShipController.get(server).selected() || OutpostScenarioController.get(server).selected()) throw new IllegalStateException("Finish or end the current Moon/ship mission first.");
                MissionState.Phase phase = MissionController.get(server).snapshot().phase();
                if (phase == MissionState.Phase.ACTIVE || phase == MissionState.Phase.PAUSED)
                    throw new IllegalStateException("End the Mars mission first.");
                if (server.getPlayerList().getPlayerCount() == 0) throw new IllegalStateException("A student must join first.");
                LearningChecks.clear(server, "moon");
                roles.clear();
                mission = new MoonMission(action.equals("moon_rescue") ? MoonMission.Variant.RESCUE : MoonMission.Variant.EXPLORATION);
            }
            case "moon_pause" -> requireMission().pause();
            case "moon_acknowledge" -> { LearningChecks.requireSolved(server, "moon"); requireMission().acknowledge(); }
            case "moon_resume" -> { LearningChecks.requireSolved(server, "moon"); requireMission().resume(); }
            case "moon_advance" -> requireMission().advance();
            case "moon_end" -> { LearningChecks.clear(server, "moon"); if (mission == null) throw new IllegalStateException("No Moon mission."); mission = null; roles.clear(); }
            default -> throw new IllegalArgumentException("Unknown action.");
        }
        ReportStore.capture(server);
        broadcast(server, action.equals("moon_exploration") || action.equals("moon_rescue") || action.equals("moon_advance"));
    }

    private MoonMission requireMission() {
        if (mission == null) throw new IllegalStateException("No Moon mission.");
        return mission;
    }

    private void playerAction(ServerPlayer player, String action) {
        try {
            MoonMission current = requireMission();
            if (action.startsWith("role:")) {
                MoonMission.Role role = MoonMission.Role.valueOf(action.substring(5));
                if (current.paused() || current.stage() == MoonMission.Stage.COMPLETE)
                    throw new IllegalStateException("Roles can only be selected during an active stage.");
                if (roles.containsValue(role) && roles.get(player.getUUID()) != role)
                    throw new IllegalStateException("That role is already assigned.");
                if (roles.containsKey(player.getUUID())) throw new IllegalStateException("Your role is already assigned.");
                roles.put(player.getUUID(), role);
            } else {
                MoonMission.Role role = roles.get(player.getUUID());
                if (role == null) throw new IllegalStateException("Choose a role first.");
                switch (action) {
                    case "confirm" -> current.confirm(role);
                    case "safe" -> current.choose(role, true);
                    case "alternative" -> current.choose(role, false);
                    default -> throw new IllegalArgumentException("Unknown action.");
                }
            }
            broadcast(player.level().getServer(), false);
        } catch (IllegalArgumentException | IllegalStateException error) {
            player.sendSystemMessage(Component.literal(error.getMessage()));
            send(player, false);
        }
    }

    private void broadcast(MinecraftServer server, boolean open) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, open);
    }
    private void send(ServerPlayer player, boolean open) {
        if (!ServerPlayNetworking.canSend(player, MoonPayload.TYPE)) return;
        MoonMission current = mission;
        if (current == null) {
            ServerPlayNetworking.send(player, new MoonPayload("", "", "", "", false, false, false, 0, 0, 0, 0, 0, 0, 0, "", false));
            return;
        }
        int claimed = 0, confirmed = 0;
        for (MoonMission.Role role : roles.values()) claimed |= 1 << role.ordinal();
        for (MoonMission.Role role : current.confirmations()) confirmed |= 1 << role.ordinal();
        MoonMission.Role role = roles.get(player.getUUID());
        ServerPlayNetworking.send(player, new MoonPayload(current.variant().name(), current.stage().name(),
                current.incident() == null ? "" : current.incident().name(), role == null ? "" : role.name(),
                current.paused(), current.acknowledged(), current.decisionMade(), claimed, confirmed,
                role == MoonMission.Role.POWER ? current.battery() : -1,
                role == MoonMission.Role.LIFE_SUPPORT ? current.oxygen() : -1,
                role == MoonMission.Role.SUPPLIES ? current.water() : -1,
                role == MoonMission.Role.PILOT ? current.fuel() : -1,
                role == MoonMission.Role.MAINTENANCE ? current.parts() : -1, current.feedback(), open));
    }
}
