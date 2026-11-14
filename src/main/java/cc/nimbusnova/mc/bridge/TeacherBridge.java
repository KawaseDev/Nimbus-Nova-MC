package cc.nimbusnova.mc.bridge;

import cc.nimbusnova.mc.NimbusNovaMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
            bridge.start();
            NimbusNovaMod.LOGGER.info("Teacher roster bridge listening on 127.0.0.1:3001");
        } catch (IOException error) {
            NimbusNovaMod.LOGGER.error("Could not start teacher roster bridge", error);
        }
    }

    private TeacherBridge() { }
}
