package cc.nimbusnova.mc.mission;

import java.util.EnumSet;

/** Educational interruptions for the existing Mars surface mission. */
public final class EducationPause {
    public enum Incident {
        SOLAR("Solar particle alert", "Sensors warn of increased solar particles.",
                "The Sun can release energetic particles. Shelter reduces exposure; it does not erase exposure already received.",
                "Unprotected crew may receive more radiation. The alert does not mean every electronic system has failed.",
                "What should the crew check before moving to shelter?",
                "NASA observed a September 2017 event. The warning time and dose in this mission are game values.",
                "https://www.jpl.nasa.gov/news/large-solar-storm-sparks-global-aurora-and-doubles-radiation-levels-on-the-martian-surface/"),
        DUST("Dust approaching", "Instruments forecast a drop in sunlight.",
                "Airborne dust blocks some sunlight before it reaches solar panels, reducing their output.",
                "Less generation can drain the battery if demand stays high. Dust does not automatically break every device.",
                "Which equipment is essential, and how long will the battery last?",
                "NASA documented Opportunity's 2018 dust storm. The battery size and time compression are game values.",
                "https://www.jpl.nasa.gov/news/opportunity-hunkers-down-during-dust-storm/"),
        RELAY("Research relay fault", "The research relay draws power without producing science.",
                "A circuit can consume energy without delivering its intended output when a component fails.",
                "The fault wastes stored energy. It does not imply that the oxygen system or all electronics have failed.",
                "What readings would show whether a repair worked?",
                "The relay fault is fictional. NASA's power guidance explains generation, storage and electrical loads.",
                "https://www.nasa.gov/smallsat-institute/sst-soa/power-subsystems/");

        public final String title, observation, explanation, hazard, question, evidence, source;
        Incident(String title, String observation, String explanation, String hazard, String question, String evidence, String source) {
            this.title = title; this.observation = observation; this.explanation = explanation;
            this.hazard = hazard; this.question = question; this.evidence = evidence; this.source = source;
        }
    }

    private Incident incident;
    private boolean confirmed;
    private final EnumSet<Incident> announced = EnumSet.noneOf(Incident.class);

    public boolean afterTick(MissionState mission) {
        if (mission.snapshot().phase() != MissionState.Phase.ACTIVE) return false;
        Incident next = switch (mission.snapshot().elapsedSeconds()) {
            case MissionState.SOLAR_WARNING_SECONDS -> Incident.SOLAR;
            case MissionState.STORM_START_SECONDS - 20 -> Incident.DUST;
            case 80 -> Incident.RELAY;
            default -> null;
        };
        if (next == null || !announced.add(next)) return false;
        incident = next;
        confirmed = false;
        mission.pause();
        return true;
    }

    public void confirm() {
        if (incident == null) throw new IllegalStateException("There is no educational pause to confirm.");
        confirmed = true;
    }

    public void resume(MissionState mission) {
        if (incident != null && !confirmed) throw new IllegalStateException("Confirm understanding before resuming.");
        mission.resume();
        incident = null;
        confirmed = false;
    }

    public void clear() { incident = null; confirmed = false; announced.clear(); }
    public Incident incident() { return incident; }
    public boolean confirmed() { return confirmed; }
}
