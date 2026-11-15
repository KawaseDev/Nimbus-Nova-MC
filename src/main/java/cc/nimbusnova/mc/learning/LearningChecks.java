package cc.nimbusnova.mc.learning;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.moon.MoonController;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.ship.ShipMap;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class LearningChecks {
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();

    private LearningChecks() {
        /* w */
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(LearningPayload.TYPE, LearningPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LearningActionPayload.TYPE, LearningActionPayload.CODEC);
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayNetworking.registerGlobalReceiver(LearningActionPayload.TYPE, (packet, context) -> context.server().execute(() -> {
            if (packet.choice() == -1)
                request(context.player(), packet.source());
            else
                applyAnswer(context.player(), packet.source(), packet.revision(), packet.choice());
        }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (final String source : new String[] { "mars", "moon", "ship" }) {
                final Episode before = SESSIONS.computeIfAbsent(server, _ -> new Session()).episodes.get(source);
                final Episode episode = episode(server, source);
                if (before == episode) continue;
                for (final ServerPlayer player : server.getPlayerList().getPlayers())
                    send(player, source, false);
            }
        });
    }

    public static void clear(final MinecraftServer server, final String source) {
        SESSIONS.computeIfAbsent(server, _ -> new Session()).episodes.remove(source);
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, LearningPayload.TYPE))
                ServerPlayNetworking.send(player, new LearningPayload(source, "", 0, "", -1, -1, "", "", true, "", false));
        }
    }

    public static void requireSolved(final MinecraftServer server, final String source) {
        final Episode episode = episode(server, source);
        if (episode != null && !episode.model.solved())
            throw new IllegalStateException("Complete the Crew check: compare the readings and choose a supported equipment action first.");
    }

    public static LearningPayload snapshot(final MinecraftServer server, final String source, final boolean open) {
        final Episode episode = episode(server, source);
        if (episode == null)
            return new LearningPayload(source, "", 0, "No incident needs a crew check.", -1, -1, "", "", true, "", open);
        final Current current = current(server, source);
        return new LearningPayload(source, episode.incident, episode.revision, current.readings,
                current.first, current.second, current.firstLabel, current.secondLabel,
                episode.model.solved(), episode.model.feedback(), open);
    }

    public static void request(final ServerPlayer player, final String source) {
        if (!participant(player, source)) return;
        send(player, source, true);
    }

    public static void applyAnswer(final ServerPlayer player, final String source, final int revision, final int choice) {
        if (!participant(player, source)) return;
        final MinecraftServer server = player.level().getServer();
        final Episode episode = episode(server, source);
        if (episode == null || episode.revision != revision || choice < 0 || choice > 2) {
            send(player, source, false);
            return;
        }
        episode.model.answer(choice);
        for (final ServerPlayer crew : server.getPlayerList().getPlayers())
            send(crew, source, crew == player);
    }

    private static boolean participant(final ServerPlayer player, final String source) {
        if (!player.isAlive() || player.isSpectator()) return false;
        if (source.equals("mars")) return player.level().dimension().equals(MarsTrainingMap.DIMENSION);
        if (source.equals("ship"))
            return player.level().dimension().equals(ShipMap.DIMENSION) || player.level().dimension().equals(LunarSurface.DIMENSION);
        return source.equals("moon") && MoonController.get(player.level().getServer()).active();
    }

    private static void send(final ServerPlayer player, final String source, final boolean open) {
        if (ServerPlayNetworking.canSend(player, LearningPayload.TYPE))
            ServerPlayNetworking.send(player, snapshot(player.level().getServer(), source, open));
    }

    private static Episode episode(final MinecraftServer server, final String source) {
        final Session session = SESSIONS.computeIfAbsent(server, _ -> new Session());
        final Current current = current(server, source);
        if (current == null) {
            session.episodes.remove(source);
            return null;
        }
        final Episode previous = session.episodes.get(source);
        if (previous != null && previous.owner == current.owner && previous.incident.equals(current.incident)
                && (!current.incident.equals("DUST") || previous.first == current.first && previous.second == current.second)) return previous;
        final Episode next = new Episode(current.owner, current.incident, ++session.revision, current.first, current.second,
                new AppliedLearning(AppliedLearning.Case.valueOf(current.incident), current.first, current.second));
        session.episodes.put(source, next);
        return next;
    }

    private static Current current(final MinecraftServer server, final String source) {
        if (source.equals("mars")) {
            final var controller = MissionController.get(server);
            final var incident = controller.education().incident();
            if (incident == null) return null;
            final var state = controller.snapshot();
            if (incident.name().equals("SOLAR"))
                return new Current(controller.education(), incident.name(), "Crew exposure index " + (int) state.radiationDose()
                        + ". Follow the marked shelter route; protection does not undo exposure already received.",
                        (int) state.radiationDose(), 0, "Recorded exposure", "Protected shelter index");
            if (incident.name().equals("DUST"))
                return new Current(controller.education(), incident.name(), "Compare current generation with electrical load.",
                        (int) Math.round(state.generation() * 100), (int) Math.round(state.demand() * 100), "Supply / game units", "Load / game units");
            return new Current(controller.education(), incident.name(), "Battery " + (int) state.battery()
                    + " / oxygen " + (int) state.oxygen() + ". Current generation " + Math.round(state.generation() * 100)
                    + " versus load " + Math.round(state.demand() * 100) + " model units.",
                    (int) state.battery(), (int) state.oxygen(), "Battery reserve", "Oxygen reserve");
        }
        if (source.equals("ship")) {
            final var mission = ShipController.get(server).mission();
            if (mission == null || mission.snapshot().incident() == null) return null;
            final var state = mission.snapshot();
            if (state.incident().name().equals("FILTER"))
                return new Current(mission, "FILTER", "Oxygen reserve " + (int) state.oxygen() + ". CO2 index " + (int) state.co2()
                        + ". Compare the gas stock with the removal system.", (int) state.oxygen(), (int) state.co2(), "Stored oxygen", "CO2 index");
            return new Current(mission, state.incident().name(), "Battery " + (int) state.battery() + " / oxygen " + (int) state.oxygen()
                    + " / fuel " + (int) state.fuel() + ". " + state.incident().observation,
                    (int) state.battery(), (int) state.oxygen(), "Battery reserve", "Oxygen reserve");
        }
        if (source.equals("moon")) {
            final var mission = MoonController.get(server).mission();
            if (mission == null || mission.incident() == null) return null;
            return new Current(mission, mission.incident().name(), "Battery " + mission.battery() + " / oxygen " + mission.oxygen()
                    + " / fuel " + mission.fuel() + ". " + mission.incident().observation,
                    mission.battery(), mission.oxygen(), "Battery reserve", "Oxygen reserve");
        }
        return null;
    }

    private static final class Session {
        private final Map<String, Episode> episodes = new HashMap<>();
        private int revision;
    }

    private record Episode(Object owner, String incident, int revision, int first, int second, AppliedLearning model) {
        /* w */
    }

    private record Current(Object owner, String incident, String readings, int first, int second, String firstLabel, String secondLabel) {
        /* w */
    }
}
