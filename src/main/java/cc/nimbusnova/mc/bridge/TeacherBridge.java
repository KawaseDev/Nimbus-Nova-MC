package cc.nimbusnova.mc.bridge;

import cc.nimbusnova.mc.NimbusNovaMod;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class TeacherBridge {
    private static HttpServer bridge;
    private static final Map<MinecraftServer, Map<UUID, String>> nicknames = new IdentityHashMap<>();
    private static final Map<UUID, View> views = new HashMap<>();
    private static final class View {
        UUID request;
        long requestedAt;
        long receivedAt;
        byte[] frame;
    }
    private static final AtomicReference<String> worldStatus = new AtomicReference<>("idle");
    // shortcut: duplicate command IDs are remembered only until server restart; persist them with sessions.
    private static final Map<UUID, String> completed = new LinkedHashMap<>();

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ViewRequestPayload.TYPE, ViewRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ViewFramePayload.TYPE, ViewFramePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(JoinNamePayload.TYPE, JoinNamePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(JoinNamePayload.TYPE, (payload, context) -> context.server().execute(() -> {
            String name = payload.name().trim();
            if (!name.isEmpty() && name.length() <= 32 && name.codePoints().noneMatch(Character::isISOControl))
                nicknames.computeIfAbsent(context.server(), ignored -> new HashMap<>()).put(context.player().getUUID(), name);
        }));
        ServerPlayNetworking.registerGlobalReceiver(ViewFramePayload.TYPE, (payload, context) -> context.server().execute(() -> {
            View view = views.get(context.player().getUUID());
            if (view == null || view.request == null || !view.request.equals(payload.id()) ||
                    System.currentTimeMillis() - view.requestedAt > 5000 || payload.image().length > ViewFramePayload.MAX_BYTES) return;
            view.request = null;
            view.frame = payload.image();
            view.receivedAt = System.currentTimeMillis();
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> views.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STARTED.register(TeacherBridge::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (bridge != null) { bridge.stop(0); bridge = null; }
            nicknames.remove(server);
            completed.clear();
            views.clear();
            worldStatus.set("idle");
        });
    }

    private static void start(MinecraftServer server) {
        String token = System.getenv("BRIDGE_TOKEN");
        if (token == null || token.length() < 32) return;
        try {
            bridge = HttpServer.create(new InetSocketAddress("127.0.0.1", 3001), 8);
            bridge.createContext("/roster", exchange -> {
                try {
                    byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
                    byte[] actual = exchange.getRequestHeaders().getFirst("Authorization") == null ? new byte[0]
                            : exchange.getRequestHeaders().getFirst("Authorization").getBytes(StandardCharsets.UTF_8);
                    if (!MessageDigest.isEqual(expected, actual)) { exchange.sendResponseHeaders(401, -1); return; }
                    if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getPath().equals("/roster")) {
                        exchange.sendResponseHeaders(404, -1); return;
                    }
                    CompletableFuture<String> roster = new CompletableFuture<>();
                    server.execute(() -> {
                        JsonArray players = new JsonArray();
                        server.getPlayerList().getPlayers().forEach(player -> {
                            JsonObject entry = new JsonObject();
                            entry.addProperty("id", player.getUUID().toString());
                            entry.addProperty("name", nicknames.getOrDefault(server, Map.of()).getOrDefault(player.getUUID(), player.getScoreboardName()));
                            players.add(entry);
                        });
                        JsonObject result = new JsonObject();
                        result.add("players", players);
                        roster.complete(result.toString());
                    });
                    byte[] body = roster.get(3, TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (Exception error) {
                    NimbusNovaMod.LOGGER.warn("Teacher roster request failed", error);
                    try { exchange.sendResponseHeaders(503, -1); } catch (IOException ignored) { }
                } finally { exchange.close(); }
            });
            bridge.createContext("/kick", exchange -> {
                try {
                    byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    byte[] actual = authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8);
                    if (!MessageDigest.isEqual(expected, actual)) { exchange.sendResponseHeaders(401, -1); return; }
                    if (!exchange.getRequestMethod().equals("POST") || !exchange.getRequestURI().getPath().equals("/kick")) {
                        exchange.sendResponseHeaders(404, -1); return;
                    }
                    String id = new String(exchange.getRequestBody().readNBytes(37), StandardCharsets.UTF_8);
                    if (!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                        exchange.sendResponseHeaders(400, -1); return;
                    }
                    UUID playerId = UUID.fromString(id);
                    CompletableFuture<Boolean> kicked = new CompletableFuture<>();
                    server.execute(() -> {
                        var player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.connection.disconnect(Component.literal("Removed by the teacher"));
                        kicked.complete(player != null);
                    });
                    exchange.sendResponseHeaders(kicked.get(3, TimeUnit.SECONDS) ? 204 : 404, -1);
                } catch (Exception error) {
                    NimbusNovaMod.LOGGER.warn("Teacher kick request failed", error);
                    try { exchange.sendResponseHeaders(503, -1); } catch (IOException ignored) { }
                } finally { exchange.close(); }
            });
            bridge.createContext("/mission", exchange -> {
                try {
                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    if (!MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                            authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8))) {
                        exchange.sendResponseHeaders(401, -1); return;
                    }
                    if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getPath().equals("/mission")) {
                        exchange.sendResponseHeaders(404, -1); return;
                    }
                    CompletableFuture<String> result = new CompletableFuture<>();
                    server.execute(() -> {
                        MissionState.Snapshot snapshot = MissionController.get(server).snapshot();
                        JsonObject data = new JsonObject();
                        data.addProperty("phase", snapshot.phase().name());
                        data.addProperty("elapsedSeconds", snapshot.elapsedSeconds());
                        data.addProperty("battery", snapshot.battery());
                        data.addProperty("oxygen", snapshot.oxygen());
                        data.addProperty("lastEvent", snapshot.lastEvent());
                        data.addProperty("players", server.getPlayerList().getPlayerCount());
                        result.complete(data.toString());
                    });
                    byte[] body = result.get(3, TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (Exception error) {
                    NimbusNovaMod.LOGGER.warn("Teacher mission request failed", error);
                    try { exchange.sendResponseHeaders(503, -1); } catch (IOException ignored) { }
                } finally { exchange.close(); }
            });
            bridge.createContext("/mission-action", exchange -> {
                try {
                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    if (!MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                            authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8))) {
                        exchange.sendResponseHeaders(401, -1); return;
                    }
                    if (!exchange.getRequestMethod().equals("POST") || !exchange.getRequestURI().getPath().equals("/mission-action")) {
                        exchange.sendResponseHeaders(404, -1); return;
                    }
                    String[] parts = new String(exchange.getRequestBody().readNBytes(65), StandardCharsets.UTF_8).split(" ", -1);
                    if (parts.length != 2 || !parts[0].matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                            || !java.util.Set.of("start", "pause", "resume", "abort", "reset").contains(parts[1])) {
                        exchange.sendResponseHeaders(400, -1); return;
                    }
                    UUID id = UUID.fromString(parts[0]);
                    CompletableFuture<Integer> result = new CompletableFuture<>();
                    server.execute(() -> {
                        String previous = completed.get(id);
                        if (previous != null) { result.complete(previous.equals(parts[1]) ? 204 : 409); return; }
                        MissionController controller = MissionController.get(server);
                        if (parts[1].equals("reset") && !controller.snapshot().phase().ended()) { result.complete(409); return; }
                        if (controller.control(server.createCommandSourceStack(), parts[1]) == 0) { result.complete(409); return; }
                        completed.put(id, parts[1]);
                        if (completed.size() > 256) completed.remove(completed.keySet().iterator().next());
                        result.complete(204);
                    });
                    exchange.sendResponseHeaders(result.get(3, TimeUnit.SECONDS), -1);
                } catch (Exception error) {
                    NimbusNovaMod.LOGGER.warn("Teacher mission action failed", error);
                    try { exchange.sendResponseHeaders(503, -1); } catch (IOException ignored) { }
                } finally { exchange.close(); }
            });
            bridge.createContext("/message", exchange -> {
                try {
                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    if (!MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                            authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8))) {
                        exchange.sendResponseHeaders(401, -1); return;
                    }
                    if (!exchange.getRequestMethod().equals("POST") || !exchange.getRequestURI().getPath().equals("/message")) {
                        exchange.sendResponseHeaders(404, -1); return;
                    }
                    String text = new String(exchange.getRequestBody().readNBytes(1025), StandardCharsets.UTF_8).trim();
                    if (text.isEmpty() || text.length() > 200 || text.codePoints().anyMatch(Character::isISOControl)) {
                        exchange.sendResponseHeaders(400, -1); return;
                    }
                    CompletableFuture<Integer> sent = new CompletableFuture<>();
                    server.execute(() -> {
                        if (server.getPlayerList().getPlayerCount() == 0) { sent.complete(409); return; }
                        Component message = Component.literal("[Mission Control] " + text);
                        server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(message));
                        sent.complete(204);
                    });
                    exchange.sendResponseHeaders(sent.get(3, TimeUnit.SECONDS), -1);
                } catch (Exception error) {
                    NimbusNovaMod.LOGGER.warn("Teacher message failed", error);
                    try { exchange.sendResponseHeaders(503, -1); } catch (IOException ignored) { }
                } finally { exchange.close(); }
            });
            bridge.createContext("/world", exchange -> {
                try {
                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    if (!MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                            authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8))) {
                        exchange.sendResponseHeaders(401, -1); return;
                    }
                    if (!exchange.getRequestURI().getPath().equals("/world")) { exchange.sendResponseHeaders(404, -1); return; }
                    if (exchange.getRequestMethod().equals("POST")) {
                        if (worldStatus.getAndSet("creating").equals("creating")) { exchange.sendResponseHeaders(409, -1); return; }
                        server.execute(() -> {
                            try {
                                worldStatus.set(MarsTrainingMap.create(server.createCommandSourceStack()) == 1 ? "ready" : "failed");
                            } catch (Exception error) {
                                NimbusNovaMod.LOGGER.warn("Mars outpost creation failed", error);
                                worldStatus.set("failed");
                            }
                        });
                        exchange.sendResponseHeaders(202, -1);
                        return;
                    }
                    if (!exchange.getRequestMethod().equals("GET")) { exchange.sendResponseHeaders(405, -1); return; }
                    JsonObject result = new JsonObject();
                    result.addProperty("status", worldStatus.get());
                    byte[] body = result.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (Exception error) {
                    NimbusNovaMod.LOGGER.warn("Teacher world request failed", error);
                    try { exchange.sendResponseHeaders(503, -1); } catch (IOException ignored) { }
                } finally { exchange.close(); }
            });
            bridge.createContext("/view", exchange -> {
                try {
                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    if (!MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                            authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8))) {
                        exchange.sendResponseHeaders(401, -1); return;
                    }
                    String query = exchange.getRequestURI().getRawQuery();
                    if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getPath().equals("/view") ||
                            query == null || !query.matches("id=[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                        exchange.sendResponseHeaders(400, -1); return;
                    }
                    UUID playerId = UUID.fromString(query.substring(3));
                    CompletableFuture<byte[]> response = new CompletableFuture<>();
                    server.execute(() -> {
                        var player = server.getPlayerList().getPlayer(playerId);
                        if (player == null || !ServerPlayNetworking.canSend(player, ViewRequestPayload.TYPE)) {
                            views.remove(playerId);
                            response.complete(null);
                            return;
                        }
                        View view = views.computeIfAbsent(playerId, ignored -> new View());
                        long now = System.currentTimeMillis();
                        if (now - view.requestedAt >= 1000 && (view.request == null || now - view.requestedAt >= 5000)) {
                            view.request = UUID.randomUUID();
                            view.requestedAt = now;
                            ServerPlayNetworking.send(player, new ViewRequestPayload(view.request));
                        }
                        response.complete(view.frame != null && now - view.receivedAt < 5000 ? view.frame : new byte[0]);
                    });
                    byte[] frame = response.get(3, TimeUnit.SECONDS);
                    if (frame == null) { exchange.sendResponseHeaders(404, -1); return; }
                    if (frame.length == 0) { exchange.sendResponseHeaders(204, -1); return; }
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                    exchange.sendResponseHeaders(200, frame.length);
                    exchange.getResponseBody().write(frame);
                } catch (Exception error) {
                    NimbusNovaMod.LOGGER.warn("Teacher view request failed", error);
                    try { exchange.sendResponseHeaders(503, -1); } catch (IOException ignored) { }
                } finally { exchange.close(); }
            });
            bridge.start();
            NimbusNovaMod.LOGGER.info("Teacher roster bridge listening on 127.0.0.1:3001");
        } catch (IOException error) {
            NimbusNovaMod.LOGGER.error("Could not start teacher roster bridge", error);
        }
    }

    private TeacherBridge() { }
}
