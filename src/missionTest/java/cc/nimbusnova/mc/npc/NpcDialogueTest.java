package cc.nimbusnova.mc.npc;

import cc.nimbusnova.mc.mission.CrewRole;
import cc.nimbusnova.mc.mission.DustStorm;
import cc.nimbusnova.mc.mission.MissionState;

public final class NpcDialogueTest {
    private NpcDialogueTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final MissionState mission = new MissionState();
        assert NpcDialogue.briefing(mission.snapshot()).contains("Choose your role");
        assert NpcDialogue.powerHint(mission.snapshot()).contains("80 percent");
        assert NpcDialogue.oxygenHint(mission.snapshot()).contains("75 percent");
        mission.start();
        mission.setEquipment(MissionState.Equipment.RESEARCH, false);
        mission.setEquipment(MissionState.Equipment.OXYGEN, false);
        assert NpcDialogue.oxygenHint(mission.snapshot()).contains("generator is off");
        for (int second = 0; second < MissionState.STORM_START_SECONDS; second++)
            mission.tickSecond();

        assert NpcDialogue.briefing(mission.snapshot()).contains("dust front is here");
        assert NpcDialogue.powerHint(mission.snapshot()).contains("Dust is reducing");
        mission.pause();
        assert NpcDialogue.briefing(mission.snapshot()).contains("Time and reserves are frozen");
        mission.abort();
        assert NpcDialogue.briefing(mission.snapshot()).contains("recalled");

        final MissionState survival = new MissionState();
        survival.start();
        survival.setEquipment(MissionState.Equipment.RESEARCH, false);
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++)
            survival.tickSecond();

        assert NpcDialogue.briefing(survival.snapshot()).contains("research objective was incomplete");
        final MissionState failed = new MissionState();
        failed.start();
        for (int second = 0; second < MissionState.DURATION_SECONDS; second++)
            failed.tickSecond();

        assert NpcDialogue.briefing(failed.snapshot()).contains("shared reserve reached zero");
        final MissionState storm2007 = new MissionState(DustStorm.OPPORTUNITY_2007);
        assert NpcDialogue.briefing(storm2007.snapshot(), CrewRole.WEATHER).contains("Only you can see the sun");
        storm2007.start();
        for (int second = 0; second < MissionState.STORM_START_SECONDS + 5; second++)
            storm2007.tickSecond();

        assert NpcDialogue.briefing(storm2007.snapshot()).contains("18%") : "Storm lines quote the real storm";
        assert NpcDialogue.powerHint(storm2007.snapshot()).contains("would not help") : "Hints must not promise cleaning fixes a storm";
        assert NpcDialogue.oxygenHint(storm2007.snapshot()).contains("2 remaining oxygen canisters");
        assert NpcDialogue.briefing(failed.snapshot()).contains("Debrief tab") && NpcDialogue.briefing(failed.snapshot()).length() <= 1024;
        System.out.println("NPC dialogue check passed: mission phases, dust event and current prototype reserves.");
    }
}
