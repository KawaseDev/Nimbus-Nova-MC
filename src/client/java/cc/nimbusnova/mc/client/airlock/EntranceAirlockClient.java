package cc.nimbusnova.mc.client.airlock;

import cc.nimbusnova.mc.airlock.ScanPayload;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.awt.Color;

public final class EntranceAirlockClient {
    private static ScanPayload scan;

    private EntranceAirlockClient() {
        /* w */
    }

    public static ScanPayload scan() {
        return scan;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ScanPayload.TYPE, (payload, context) -> context.client().execute(() -> scan = payload));
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> scan = null);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Identifier.parse("nimbus_nova:entrance_scan"), (graphics, _) -> {
            final Minecraft client = Minecraft.getInstance();
            if (scan == null || !scan.visible() || client.player == null || client.level == null || client.gui.screen() != null
                    || client.gui.hud.isHidden() || !client.level.dimension().equals(MarsTrainingMap.DIMENSION)) return;

            final float width = Math.min(300, graphics.guiWidth() - 20), left = (graphics.guiWidth() - width) / 2, top = graphics.guiHeight() - 102;
            final Color cyan = new Color(107, 232, 222), text = new Color(223, 239, 241), muted = new Color(161, 184, 192);
            final String title = scan.stage() == 1 ? "Dust purge / suit scan" : scan.stage() == 2
                    ? scan.outbound() ? "Exit ready" : "Scan complete / cabin ready" : scan.outbound() ? "Preparing EVA exit" : "Dome entrance lock";
            final String detail = scan.stage() == 1 ? "Stand inside. Both laser barriers are locked."
                    : scan.stage() == 2 ? scan.outbound() ? scan.helmet() ? "Outer barrier ready. Watch your suit air." : "Helmet missing. Equip it before leaving!"
                    : "Dust removed. Walk through the inner barrier." : scan.outbound() ? "Step into the lock for the exit cycle." : "Enter the chamber to start the safety check.";
            NovaRenderer.rect(graphics, left, top, width, 51, 8, new Color(9, 24, 32, 236));
            NovaRenderer.text(graphics, title, left + 10, top + 7, 10, cyan);
            NovaRenderer.wrap(graphics, detail, left + 10, top + 23, width - 20, 8.5F, 1, text);
            NovaRenderer.rect(graphics, left + 10, top + 40, width - 20, 3, 1.5F, muted);
            if (scan.stage() != 0)
                NovaRenderer.rect(graphics, left + 10, top + 40, (width - 20) * Math.clamp(scan.elapsed() / 80F, 0, 1), 3, 1.5F, cyan);
        });
    }
}
