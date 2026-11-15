package cc.nimbusnova.mc.client.scenario;

import cc.nimbusnova.mc.client.ObjectiveGuidance;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.mission.MissionProgressPayload;
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
import net.minecraft.core.BlockPos;
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

    public static @Nullable MissionProgressPayload waypoint() {
        if (state == null || !state.has("targetTitle") || state.get("targetTitle").getAsString().isEmpty()) return null;
        return new MissionProgressPayload("SCENARIO", state.get("targetTitle").getAsString(), state.get("roleObjective").getAsString(),
                "Follow the HUD instructions", state.get("targetX").getAsInt(), state.get("targetY").getAsInt(), state.get("targetZ").getAsInt(),
                0, 0, 0, "", false);
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
            final var waypoint = waypoint();
            final String phase = state.get("phase").getAsString();
            NovaRenderer.rect(graphics, 8, 8, width, waypoint == null ? 87 : 108, 7, new Color(10, 23, 31, 220));
            NovaRenderer.text(graphics, state.get("title").getAsString(), 17, 15, 11, new Color(112, 226, 232));
            final int left = Math.max(0, 150 - state.get("elapsed").getAsInt());
            final String progress = switch (state.get("id").getAsString()) {
                case "shelter" -> " / live data " + (state.get("observation").getAsBoolean() ? "YES" : "NO") + " / dose " + state.get("dose").getAsInt();
                case "long_night" -> " / lab " + state.get("labSeconds").getAsInt() + "/30s / air flow " + state.get("lifeFlow").getAsInt();
                default -> " / extraction humidity " + state.get("cabinHumidity").getAsInt();
            };
            final var accent = new Color(248, 193, 108);
            final String clock = phase.equals("READY") ? "Prepare / no timer" : phase.equals("ACTIVE") ? left / 60 + ":" + String.format("%02d", left % 60) : phase;
            NovaRenderer.text(graphics, clock + progress, 17, 30, 8, Color.LIGHT_GRAY);
            if (waypoint == null) {
                final String detail = state.get("role").getAsString().isEmpty() && (phase.equals("READY") || phase.equals("ACTIVE"))
                        ? "Choose a role to see your objective." : phase.equals("PAUSED") ? "Wait for Mission Control to resume." : "Open the tablet for your crew's status.";
                NovaRenderer.wrap(graphics, detail, 17, 45, width - 18, 9, 2, Color.WHITE);
                NovaRenderer.text(graphics, "J  Open crew tablet", 17, 75, 8, accent);
                return;
            }

            NovaRenderer.text(graphics, waypoint.title(), 17, 43, 9, Color.WHITE);
            NovaRenderer.wrap(graphics, waypoint.instruction(), 17, 57, width - 18, 9, 3, Color.WHITE);
            ObjectiveGuidance.waypointHint(graphics, new BlockPos(waypoint.targetX(), waypoint.targetY(), waypoint.targetZ()), 17, 100, accent);
        });
    }
}
