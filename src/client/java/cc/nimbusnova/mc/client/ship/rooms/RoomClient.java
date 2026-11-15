package cc.nimbusnova.mc.client.ship.rooms;

import cc.nimbusnova.mc.NimbusNovaMod;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.ship.ShipPalette;
import cc.nimbusnova.mc.ship.rooms.RoomActionPayload;
import cc.nimbusnova.mc.ship.rooms.RoomStatusPayload;
import cc.nimbusnova.mc.world.ship.ShipMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.awt.Color;

public final class RoomClient implements ShipPalette {
    private static @Nullable JsonObject state;

    private RoomClient() {
        /* w */
    }

    public static @Nullable JsonObject state() {
        return state;
    }

    public static void open() {
        if (ClientPlayNetworking.canSend(RoomActionPayload.TYPE))
            ClientPlayNetworking.send(new RoomActionPayload("", "status"));
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> state = null);
        ClientPlayNetworking.registerGlobalReceiver(RoomStatusPayload.TYPE, (packet, context) -> context.client().execute(() -> {
            try {
                final JsonObject snapshot = JsonParser.parseString(packet.json()).getAsJsonObject();
                state = snapshot;
                if (context.client().gui.screen() instanceof RoomScreen screen)
                    screen.update(snapshot);
                else if (packet.open())
                    context.client().setScreenAndShow(new RoomScreen(snapshot));
            } catch (final RuntimeException error) {
                state = null;
                NimbusNovaMod.LOGGER.warn("Invalid room status: {}", error.getMessage());
            }
        }));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Identifier.parse("nimbus_nova:room_hud"), (graphics, _) -> {
            final Minecraft client = Minecraft.getInstance();
            if (state == null || client.level == null || !client.level.dimension().equals(ShipMap.DIMENSION)
                    || client.gui.screen() != null || client.gui.hud.isHidden()) return;
            final int width = Math.min(252, graphics.guiWidth() - 12);
            final String current = state.get("currentRoom").getAsString(), alert = state.get("alert").getAsString();
            final boolean alarm = !alert.isEmpty() && !alert.equals("All compartments nominal");
            String name = "Corridor", pressure = "", power = "";
            for (final var entry : state.getAsJsonArray("rooms")) {
                final JsonObject room = entry.getAsJsonObject();
                if (!room.get("id").getAsString().equals(current)) continue;
                name = room.get("name").getAsString();
                pressure = "AIR " + room.get("pressure").getAsInt() + "%";
                power = room.get("powered").getAsBoolean() ? "POWER ON" : "POWER OFF";
                break;
            }

            final int top = graphics.guiHeight() - (alarm ? 96 : 50);
            NovaRenderer.hud(graphics, 6, top, width, 21, BACKGROUND);
            NovaRenderer.text(graphics, name, 13, top + 7, 8, TEXT);
            if (!pressure.isEmpty()) {
                NovaRenderer.chip(graphics, power, 6 + width - 6 - NovaRenderer.chipWidth(power), top + 5, power.endsWith("ON") ? SUCCESS : WARNING);
                NovaRenderer.chip(graphics, pressure, 6 + width - 10 - NovaRenderer.chipWidth(power) - NovaRenderer.chipWidth(pressure), top + 5,
                        pressure.equals("AIR 100%") ? ACCENT : WARNING);
            }

            if (!alarm) return;
            final Color flash = NovaRenderer.mix(WARNING, new Color(255, 107, 107), NovaRenderer.pulse(7));
            NovaRenderer.hud(graphics, 6, top + 24, width, 44, BACKGROUND);
            NovaRenderer.rect(graphics, 8, top + 27, 2, 38, flash);
            NovaRenderer.text(graphics, alert, 15, top + 30, 8, flash);
            NovaRenderer.wrap(graphics, state.get("instruction").getAsString(), 15, top + 42, width - 22, 8, 2, TEXT);
        });
    }
}
