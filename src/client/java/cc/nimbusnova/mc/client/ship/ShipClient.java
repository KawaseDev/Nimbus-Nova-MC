package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.ObjectiveGuidance;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.ship.ShipActionPayload;
import cc.nimbusnova.mc.ship.ShipPayload;
import cc.nimbusnova.mc.world.moon.LunarSurface;
import cc.nimbusnova.mc.world.ship.ShipMap;
import cc.nimbusnova.mc.client.suit.SuitLifeSupportClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class ShipClient implements ShipPalette {
    private static @Nullable ShipPayload state;

    private ShipClient() {
        /* w */
    }

    public static @Nullable ShipPayload state() {
        return state;
    }

    public static void open() {
        if (state != null)
            Minecraft.getInstance().setScreenAndShow(new ShipScreen(state));
        else if (ClientPlayNetworking.canSend(ShipActionPayload.TYPE))
            ClientPlayNetworking.send(new ShipActionPayload("open", BlockPos.ZERO, 0, 0));
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ShipPayload.TYPE, (packet, context) -> context.client().execute(() -> {
            state = packet.phase().isEmpty() ? null : packet;
            if (context.client().gui.screen() instanceof ShipScreen screen) {
                if (state == null)
                    screen.onClose();
                else
                    screen.update(packet);
            } else if (packet.open() && state != null)
                context.client().setScreenAndShow(new ShipScreen(packet));
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> state = null);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Identifier.parse("nimbus_nova:ship_hud"), (graphics, _) -> {
            final Minecraft client = Minecraft.getInstance();
            if (state == null || client.level == null || client.player == null || client.gui.screen() != null || client.gui.hud.isHidden()) return;
            if (client.level.dimension().equals(LunarSurface.DIMENSION)) {
                final int width = Math.min(324, graphics.guiWidth() - 16);
                NovaRenderer.hud(graphics, 8, 8, width, 82, BACKGROUND);
                NovaRenderer.text(graphics, "Moon surface", 18, 14, 10, ACCENT);
                final var suit = SuitLifeSupportClient.state();
                NovaRenderer.text(graphics, (suit == null ? "" : "Suit air " + (int) Math.ceil(suit.air()) + "% / ") + "J crew tablet", 18, 29, 8, MUTED);
                NovaRenderer.wrap(graphics, state.instruction(), 18, 45, width - 20, 9, 2, TEXT);
                if (state.hasWaypoint())
                    ObjectiveGuidance.waypointHint(graphics, state.target(), 18, 73, WARNING);
                return;
            }
            if (!client.level.dimension().equals(ShipMap.DIMENSION)) return;

            final int panelWidth = Math.min(324, graphics.guiWidth() - 16);
            NovaRenderer.hud(graphics, 8, 8, panelWidth, 34, BACKGROUND);
            final String title = state.variant().equals("EXPLORATION") ? "Moon Expedition" : "Return Window";
            NovaRenderer.text(graphics, title + " / " + label(state.role().isEmpty() ? "Choose a role" : state.role()), 18, 14, 10, ACCENT);
            final String resource = telemetry(state);
            NovaRenderer.text(graphics, resource, 18, 29, 8, MUTED);
            final String status = state.phase().equals("READY") ? "PREPARE" : state.phase().equals("ACTIVE")
                    ? state.seconds() / 60 + ":" + String.format("%02d", state.seconds() % 60) : state.phase();
            NovaRenderer.text(graphics, status, 8 + panelWidth - NovaRenderer.width(status, 8) - 10, 29, 8, WARNING);

            NovaRenderer.hud(graphics, 8, 46, panelWidth, 72, BACKGROUND);
            NovaRenderer.text(graphics, state.title(), 18, 54, 11, TEXT);
            NovaRenderer.wrap(graphics, state.instruction(), 18, 72, panelWidth - 20, 9, 2, TEXT);
            if (!state.hasWaypoint()) {
                NovaRenderer.text(graphics, state.phase().equals("PAUSED") ? "J  Read the crew briefing" : "J  Open crew tablet", 18, 102, 8, ACCENT);
                return;
            }

            ObjectiveGuidance.waypointHint(graphics, state.target(), 18, 102, WARNING);
        });
    }

    static String telemetry(final ShipPayload packet) {
        return packet.battery() >= 0 ? "Battery " + packet.battery() + "%"
                : packet.oxygen() >= 0 ? "Oxygen " + packet.oxygen() + "% / CO2 " + packet.co2()
                : packet.water() >= 0 ? "Water " + packet.water() + "%"
                : packet.parts() >= 0 ? "Parts " + packet.parts() : packet.fuel() >= 0 ? "Fuel " + packet.fuel() + "%" : "Share readings with your crew";
    }

    static String label(final String value) {
        return value.toLowerCase().replace('_', ' ');
    }
}
