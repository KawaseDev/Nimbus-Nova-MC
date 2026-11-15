package cc.nimbusnova.mc.client.scenario;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.scenario.ScenarioActionPayload;
import cc.nimbusnova.mc.scenario.ScenarioPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import java.awt.Color;

public final class ScenarioClient {
    private static JsonObject state;
    private ScenarioClient() {
        /* w */
    }
    public static @Nullable JsonObject state() {
        return state;
    }
    public static void open() {
        if (ClientPlayNetworking.canSend(ScenarioActionPayload.TYPE))
            ClientPlayNetworking.send(new ScenarioActionPayload("open", -1, 0, state == null ? 0 : state.get("revision").getAsInt()));
    }
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> state = null);
        ClientPlayNetworking.registerGlobalReceiver(ScenarioPayload.TYPE, (packet, context) -> context.client().execute(() -> {
            final JsonObject next = JsonParser.parseString(packet.json()).getAsJsonObject();
            state = next.has("id") ? next : null;
            if (context.client().gui.screen() instanceof ScenarioScreen screen) {
                if (state == null) screen.onClose();
                else screen.update(state);
            } else if (state != null && packet.open()) context.client().setScreenAndShow(new ScenarioScreen(state));
        }));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Identifier.parse("nimbus_nova:scenario_hud"), (graphics, _) -> {
            final var client = Minecraft.getInstance();
            if (state == null || client.level == null || client.player == null || client.gui.screen() != null || client.gui.hud.isHidden()
                    || !client.level.dimension().identifier().toString().startsWith("nimbus_nova:scenario_")) return;
            final int width = Math.min(324, graphics.guiWidth() - 16);
            NovaRenderer.rect(graphics, 8, 8, width, 94, 7, new Color(10, 23, 31, 220));
            NovaRenderer.text(graphics, state.get("title").getAsString(), 17, 15, 11, new Color(112, 226, 232));
            final int left = Math.max(0, 150 - state.get("elapsed").getAsInt());
            final String progress = switch (state.get("id").getAsString()) {
                case "shelter" -> " / live data " + (state.get("observation").getAsBoolean() ? "YES" : "NO") + " / dose " + state.get("dose").getAsInt();
                case "long_night" -> " / lab " + state.get("labSeconds").getAsInt() + "/30s / air flow " + state.get("lifeFlow").getAsInt();
                default -> " / extraction humidity " + state.get("cabinHumidity").getAsInt();
            };
            NovaRenderer.text(graphics, state.get("phase").getAsString() + " / " + left / 60 + ":" + String.format("%02d", left % 60) + progress, 17, 30, 8, Color.LIGHT_GRAY);
            NovaRenderer.wrap(graphics, state.get("roleObjective").getAsString(), 17, 45, width - 18, 9, 3, Color.WHITE);
            final double deltaX = state.get("targetX").getAsDouble() + 0.5 - client.player.getX();
            final double deltaZ = state.get("targetZ").getAsDouble() + 0.5 - client.player.getZ();
            final double turn = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-deltaX, deltaZ)) - client.player.getYRot());
            final String direction = Math.abs(turn) < 25 ? "Ahead" : Math.abs(turn) > 150 ? "Behind" : turn < 0 ? "Left" : "Right";
            NovaRenderer.text(graphics, direction + " / " + (int) Math.ceil(Math.hypot(deltaX, deltaZ)) + " blocks / right-click / J tablet",
                    17, 86, 8, new Color(112, 226, 232));
        });
    }
}
