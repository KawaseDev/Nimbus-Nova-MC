package cc.nimbusnova.mc.mission;

public final class EducationPauseTest {
    public static void main(String[] args) {
        MissionState mission = new MissionState();
        EducationPause education = new EducationPause();
        mission.start();
        int incidents = 0;
        while (mission.snapshot().elapsedSeconds() < 80) {
            mission.tickSecond();
            if (!education.afterTick(mission)) continue;
            incidents++;
            MissionState.Snapshot paused = mission.snapshot();
            assert paused.phase() == MissionState.Phase.PAUSED;
            for (int i = 0; i < 10; i++) mission.tickSecond();
            assert paused.equals(mission.snapshot()) : "Learning pauses must freeze the simulation";
            boolean rejected = false;
            try { education.resume(mission); } catch (IllegalStateException expected) { rejected = true; }
            assert rejected : "Resume must require confirmation";
            assert !education.incident().source.isBlank() && !education.incident().evidence.isBlank();
            education.confirm();
            education.resume(mission);
            assert education.incident() == null && mission.snapshot().phase() == MissionState.Phase.ACTIVE;
            assert !education.afterTick(mission) : "A resumed incident must not be announced twice";
        }
        assert incidents == 3 : "Solar warning, dust warning and relay fault each need a learning pause";
        assert mission.relayFault() : "The relay incident must still occur";
        System.out.println("Education pause checks passed: incident, frozen simulation and confirmation.");
    }
}
