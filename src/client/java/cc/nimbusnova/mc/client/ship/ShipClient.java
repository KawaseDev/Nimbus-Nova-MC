package cc.nimbusnova.mc.client.ship;

import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.ship.ShipActionPayload;
import cc.nimbusnova.mc.ship.ShipPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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
            if (state == null || client.level == null || client.player == null || client.gui.screen() != null
                    || client.gui.hud.isHidden() || !client.level.dimension().identifier().toString().equals("nimbus_nova:return_window")) return;

            final int panelWidth = Math.min(324, graphics.guiWidth() - 16);
            NovaRenderer.rect(graphics, 8, 8, panelWidth, 34, 7, BACKGROUND);
            NovaRenderer.text(graphics, "Return Window / " + label(state.role().isEmpty() ? "Choose a role" : state.role()), 18, 14, 10, ACCENT);
            final String resource = telemetry(state);
            NovaRenderer.text(graphics, resource, 18, 29, 8, MUTED);
            final String status = state.phase().equals("READY") ? "PREPARE" : state.phase().equals("ACTIVE")
                    ? state.seconds() / 60 + ":" + String.format("%02d", state.seconds() % 60) : state.phase();
            NovaRenderer.text(graphics, status, 8 + panelWidth - NovaRenderer.width(status, 8) - 10, 29, 8, WARNING);

            NovaRenderer.rect(graphics, 8, 46, panelWidth, 72, 7, BACKGROUND);
            NovaRenderer.text(graphics, state.title(), 18, 54, 11, TEXT);
            NovaRenderer.wrap(graphics, state.instruction(), 18, 72, panelWidth - 20, 9, 2, TEXT);
            final double distance = client.player.position().distanceTo(Vec3.atCenterOf(state.target()));
            final int vertical = state.target().getY() - client.player.blockPosition().getY();
            final String deck = vertical > 2 ? " / deck above" : vertical < -2 ? " / deck below" : "";
            final double deltaX = state.target().getX() + 0.5 - client.player.getX(), deltaZ = state.target().getZ() + 0.5 - client.player.getZ();
            final double turn = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-deltaX, deltaZ)) - client.player.getYRot());
            final String direction = Math.abs(turn) < 25 ? "Ahead" : Math.abs(turn) > 150 ? "Behind you" : turn < 0 ? "To your left" : "To your right";
            final String hint = state.phase().equals("PAUSED") ? "J  Read the crew briefing"
                    : direction + " / " + (int) Math.ceil(distance) + " blocks" + deck + " / J tablet";
            NovaRenderer.text(graphics, hint, 18, 102, 8, ACCENT);
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
