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
            final int width = Math.min(324, graphics.guiWidth() - 16);
            final String current = state.get("currentRoom").getAsString();
            String details = "Corridor / room control on the crew tablet";
            for (final var entry : state.getAsJsonArray("rooms")) {
                final JsonObject room = entry.getAsJsonObject();
                if (!room.get("id").getAsString().equals(current)) continue;
                details = room.get("name").getAsString() + " / Air " + room.get("pressure").getAsInt()
                        + "% / Power " + (room.get("powered").getAsBoolean() ? "ON" : "OFF");
                break;
            }
            NovaRenderer.rect(graphics, 8, 122, width, 24, 6, BACKGROUND);
            NovaRenderer.text(graphics, details, 17, 129, 8, TEXT);
            final String alert = state.get("alert").getAsString();
            if (alert.isEmpty()) return;
            NovaRenderer.rect(graphics, 8, 150, width, 43, 6, BACKGROUND);
            NovaRenderer.text(graphics, alert, 17, 156, 9, WARNING);
            NovaRenderer.wrap(graphics, state.get("instruction").getAsString(), 17, 169, width - 18, 8, 2, TEXT);
        });
    }
}
