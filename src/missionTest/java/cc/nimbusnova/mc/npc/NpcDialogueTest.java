package cc.nimbusnova.mc.npc;

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
        System.out.println("NPC dialogue check passed: mission phases, dust event and current prototype reserves.");
    }
}
