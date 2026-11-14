package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.NimbusNovaMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class NimbusNovaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Join session"),
                        button -> client.setScreenAndShow(new JoinSessionScreen(screen)))
                        .bounds(width - 110, 10, 100, 20).build());
            }
        });
        NimbusNovaMod.LOGGER.info("Nimbus Nova client initialized");
    }
}
