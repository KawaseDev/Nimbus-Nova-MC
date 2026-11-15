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
            final int width = Math.min(252, graphics.guiWidth() - 12);
            final var waypoint = waypoint();
            final String phase = state.get("phase").getAsString();
            final int left = Math.max(0, 150 - state.get("elapsed").getAsInt());
            final String progress = switch (state.get("id").getAsString()) {
                case "shelter" -> "Live data " + (state.get("observation").getAsBoolean() ? "YES" : "NO") + " / dose " + state.get("dose").getAsInt();
                case "long_night" -> "Lab " + state.get("labSeconds").getAsInt() + "/30s / air " + state.get("lifeFlow").getAsInt();
                default -> "Humidity " + state.get("cabinHumidity").getAsInt();
            };
            final Color accent = new Color(255, 200, 87), cyan = new Color(95, 225, 240);
            final String clock = phase.equals("READY") ? "PREPARE" : phase.equals("ACTIVE") ? left / 60 + ":" + String.format("%02d", left % 60) : phase;
            NovaRenderer.hud(graphics, 6, 6, width, 21, new Color(16, 21, 34));
            NovaRenderer.text(graphics, progress, 13, 13, 8, cyan);
            NovaRenderer.chip(graphics, clock, 6 + width - 6 - NovaRenderer.chipWidth(clock), 11,
                    phase.equals("ACTIVE") && left <= 30 ? NovaRenderer.mix(new Color(255, 107, 107), Color.WHITE, NovaRenderer.pulse(8) * 0.5F) : accent);
            if (waypoint == null) {
                final int footer = NovaRenderer.objective(graphics, 6, 30, width, state.get("title").getAsString(), "CREW",
                        state.get("role").getAsString().isEmpty() && (phase.equals("READY") || phase.equals("ACTIVE"))
                                ? "Choose a role to see your objective." : phase.equals("PAUSED") ? "Wait for Mission Control to resume."
                                : "Open the tablet for your crew's status.", accent);
                NovaRenderer.text(graphics, "J  Open crew tablet", 15, footer, 8, accent);
                return;
            }

            final int footer = NovaRenderer.objective(graphics, 6, 30, width, waypoint.title(), "TASK", waypoint.instruction(), accent);
            ObjectiveGuidance.waypointHint(graphics, new BlockPos(waypoint.targetX(), waypoint.targetY(), waypoint.targetZ()), 15, footer, accent);
        });
    }
}
