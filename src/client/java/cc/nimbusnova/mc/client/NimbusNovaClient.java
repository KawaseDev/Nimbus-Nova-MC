package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.OpenConsolePayload;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.vehicle.RoverRenderer;
import cc.nimbusnova.mc.client.npc.MissionNpcClient;
import cc.nimbusnova.mc.client.effect.DustStormEffects;
import cc.nimbusnova.mc.client.guidance.HologramGuidance;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class NimbusNovaClient implements ClientModInitializer {
    static MissionSnapshotPayload snapshot;
    static KeyMapping consoleKey;

    public static MissionSnapshotPayload missionSnapshot() {
        return snapshot;
    }

    @Override
    public void onInitializeClient() {
        NovaRenderer.initialize();
        GuidedMissionClient.register();
        HologramGuidance.register();
        RoverRenderer.register();
        MissionNpcClient.register();
        DustStormEffects.register();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                var widgets = Screens.getWidgets(screen);
                for (int i = 0; i < widgets.size(); i++) {
                    AbstractWidget widget = widgets.get(i);
                    if (widget instanceof Button && widget.getMessage().equals(Component.translatable("menu.online"))) {
                        int x = widget.getX(), y = widget.getY();
                        for (AbstractWidget lower : widgets) {
                            if (lower.getY() >= y && lower.getY() <= y + 48
                                    && lower.getX() >= x - 24 && lower.getX() <= x + 200) {
                                lower.setY(lower.getY() + 24);
                            }
                        }
                        widgets.add(i, Button.builder(Component.literal("Join session"),
                                button -> client.setScreenAndShow(new JoinSessionScreen(screen)))
                                .bounds(x, y, 200, 20).build());
                        break;
                    }
                }
            }
        });
        consoleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nimbus_nova.console", InputConstants.KEY_J,
                KeyMapping.Category.register(Identifier.parse("nimbus_nova:mission"))));
        ClientPlayNetworking.registerGlobalReceiver(MissionSnapshotPayload.TYPE,
                (packet, context) -> context.client().execute(() -> snapshot = packet));
        ClientPlayNetworking.registerGlobalReceiver(OpenConsolePayload.TYPE, (packet, context) ->
                context.client().execute(() -> context.client().setScreenAndShow(new MissionConsoleScreen(
                        snapshot != null && snapshot.state().phase().ended() ? MissionConsoleScreen.DEBRIEF_TAB : Math.clamp(packet.tab(), 0, 3)))));
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
            snapshot = null;
            MissionTutorial.consoleOpened = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (consoleKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null)
                    client.setScreenAndShow(new MissionConsoleScreen());
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.parse("nimbus_nova:mission_hud"), MissionHud::render);
    }
}
