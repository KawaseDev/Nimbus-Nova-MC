package cc.nimbusnova.mc;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.hub.MissionHub;
import cc.nimbusnova.mc.report.ReportStore;
import cc.nimbusnova.mc.learning.LearningChecks;
import cc.nimbusnova.mc.scenario.OutpostScenarioController;
import cc.nimbusnova.mc.moon.MoonController;
import cc.nimbusnova.mc.bridge.TeacherBridge;
import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.content.ModBlocks;
import cc.nimbusnova.mc.content.OutpostContent;
import cc.nimbusnova.mc.content.ScifiBlocks;
import cc.nimbusnova.mc.content.ScienceStations;
import cc.nimbusnova.mc.content.ShipContent;
import cc.nimbusnova.mc.content.RepairContent;
import cc.nimbusnova.mc.ship.rooms.ShipRooms;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import cc.nimbusnova.mc.world.EntranceAirlock;
import cc.nimbusnova.mc.suit.SuitLifeSupport;
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
        ScifiBlocks.register();
        ScienceStations.register();
        ShipContent.register();
        MissionController.register();
        RepairContent.register();
        ShipRooms.register();
        ShipController.register();
        MoonController.register();
        OutpostScenarioController.register();
        LearningChecks.register();
        MissionHub.register();
        ReportStore.register();
        EntranceAirlock.register();
        SuitLifeSupport.register();
        TeacherBridge.register();
        RoverContent.register();
        MissionNpcs.register();
        DustParticles.register();
        MarsTrainingMap.register();
        LOGGER.info("Nimbus Nova mission trainer initialized");
    }
}
