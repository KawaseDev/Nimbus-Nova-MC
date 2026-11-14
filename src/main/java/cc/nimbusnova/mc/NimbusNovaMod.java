package cc.nimbusnova.mc;

import cc.nimbusnova.mc.mission.MissionController;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NimbusNovaMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("nimbus_nova");

    @Override
    public void onInitialize() {
        MissionController.register();
        LOGGER.info("Nimbus Nova mission trainer initialized");
    }
}
