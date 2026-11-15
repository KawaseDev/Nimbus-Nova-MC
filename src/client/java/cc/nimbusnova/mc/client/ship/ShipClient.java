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

import java.awt.Color;

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
            final int width = Math.min(252, graphics.guiWidth() - 12);
            if (client.level.dimension().equals(LunarSurface.DIMENSION)) {
                final var suit = SuitLifeSupportClient.state();
                final int footer = NovaRenderer.objective(graphics, 6, 6, width, "Moon surface",
                        suit == null ? "EVA" : "SUIT AIR " + (int) Math.ceil(suit.air()) + "%", state.instruction(),
                        suit != null && suit.air() <= 25 ? new Color(255, 107, 107) : ACCENT);
                if (state.hasWaypoint())
                    ObjectiveGuidance.waypointHint(graphics, state.target(), 15, footer, WARNING);
                else
                    NovaRenderer.text(graphics, "J  Open crew tablet", 15, footer, 8, ACCENT);
                return;
            }
            if (!client.level.dimension().equals(ShipMap.DIMENSION)) return;

            final String status = state.phase().equals("READY") ? "PREPARE" : state.phase().equals("ACTIVE")
                    ? state.seconds() / 60 + ":" + String.format("%02d", state.seconds() % 60) : state.phase();
            NovaRenderer.hud(graphics, 6, 6, width, 21, BACKGROUND);
            NovaRenderer.chip(graphics, label(state.role().isEmpty() ? "Choose a role" : state.role()).toUpperCase(), 12, 11, ACCENT);
            NovaRenderer.chip(graphics, status, 6 + width - 6 - NovaRenderer.chipWidth(status), 11, state.phase().equals("ACTIVE") ? TEXT : WARNING);
            final String resource = telemetry(state);
            if (NovaRenderer.width(resource, 8) < width - 24 - NovaRenderer.chipWidth(status)
                    - NovaRenderer.chipWidth(label(state.role().isEmpty() ? "Choose a role" : state.role())))
                NovaRenderer.text(graphics, resource, 18 + NovaRenderer.chipWidth(label(state.role().isEmpty() ? "Choose a role" : state.role()).toUpperCase()), 13, 8, MUTED);

            final int footer = NovaRenderer.objective(graphics, 6, 30, width, state.title(),
                    state.variant().equals("EXPLORATION") ? "MOON" : "RESCUE", state.instruction(), WARNING);
            if (!state.hasWaypoint()) {
                NovaRenderer.text(graphics, state.phase().equals("PAUSED") ? "J  Read the crew briefing" : "J  Open crew tablet", 15, footer, 8, ACCENT);
                return;
            }

            ObjectiveGuidance.waypointHint(graphics, state.target(), 15, footer, WARNING);
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
