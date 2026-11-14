package cc.nimbusnova.mc;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.content.ModBlocks;
import cc.nimbusnova.mc.content.OutpostContent;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.vehicle.RoverContent;
import cc.nimbusnova.mc.npc.MissionNpcs;
import cc.nimbusnova.mc.effect.DustParticles;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NimbusNovaMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("nimbus_nova");

    @Override
    public void onInitialize() {
        ModContent.register();
        ModBlocks.register();
        OutpostContent.register();
        MissionController.register();
        RoverContent.register();
        MissionNpcs.register();
        DustParticles.register();
        MarsTrainingMap.register();
        LOGGER.info("Nimbus Nova mission trainer initialized");
    }
}
