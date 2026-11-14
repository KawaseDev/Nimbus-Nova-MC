package cc.nimbusnova.mc.bridge;

import cc.nimbusnova.mc.NimbusNovaMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class TeacherBridge {
    private static HttpServer bridge;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(TeacherBridge::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (bridge != null) { bridge.stop(0); bridge = null; }
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
                            entry.addProperty("name", player.getScoreboardName());
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
            bridge.start();
            NimbusNovaMod.LOGGER.info("Teacher roster bridge listening on 127.0.0.1:3001");
        } catch (IOException error) {
            NimbusNovaMod.LOGGER.error("Could not start teacher roster bridge", error);
        }
    }

    private TeacherBridge() { }
}
