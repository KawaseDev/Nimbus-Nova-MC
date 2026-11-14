package cc.nimbusnova.mc.client;

import cc.nimbusnova.mc.NimbusNovaMod;
import net.fabricmc.api.ClientModInitializer;

public final class NimbusNovaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NimbusNovaMod.LOGGER.info("Nimbus Nova client initialized");
    }
}
