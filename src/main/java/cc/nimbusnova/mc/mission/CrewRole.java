package cc.nimbusnova.mc.mission;

public enum CrewRole {
    OBSERVER("Crew observer", "observer"),
    POWER("Power engineer", "power"),
    LIFE_SUPPORT("Life-support engineer", "life_support");

    public final String label, command;

    CrewRole(final String label, final String command) {
        this.label = label;
        this.command = command;
    }

    public boolean canControl(final MissionState.Equipment equipment) {
        return equipment == MissionState.Equipment.OXYGEN ? this == LIFE_SUPPORT : this == POWER;
    }
}
