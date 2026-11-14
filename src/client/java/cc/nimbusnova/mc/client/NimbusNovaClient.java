package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.mission.MissionSnapshotPayload;
import cc.nimbusnova.mc.mission.OpenConsolePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class NimbusNovaClient implements ClientModInitializer {
    static MissionSnapshotPayload snapshot;
    static KeyMapping consoleKey;

    @Override
    public void onInitializeClient() {
        consoleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nimbus_nova.console", InputConstants.KEY_J,
                KeyMapping.Category.register(Identifier.parse("nimbus_nova:mission"))));
        ClientPlayNetworking.registerGlobalReceiver(MissionSnapshotPayload.TYPE,
                (packet, context) -> context.client().execute(() -> snapshot = packet));
        ClientPlayNetworking.registerGlobalReceiver(OpenConsolePayload.TYPE, (_, context) ->
                context.client().execute(() -> context.client().setScreenAndShow(new MissionConsoleScreen())));
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> snapshot = null);
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
