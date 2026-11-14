package cc.nimbusnova.mc.mission;

public enum CrewRole {
    OBSERVER("Crew observer", "observer"),
    POWER("Power engineer", "power"),
    LIFE_SUPPORT("Life-support engineer", "life_support"),
    WEATHER("Weather officer", "weather");

    public final String label, command;

    CrewRole(final String label, final String command) {
        this.label = label;
        this.command = command;
    }

    /** Only the weather officer (and Mission Control) sees the sunlight readings and forecast. */
    public boolean seesWeather() {
        return this == WEATHER;
    }

    public boolean canControl(final MissionState.Equipment equipment) {
        return equipment == MissionState.Equipment.OXYGEN ? this == LIFE_SUPPORT : this == POWER;
    }
}
