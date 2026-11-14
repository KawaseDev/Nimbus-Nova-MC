package cc.nimbusnova.mc.ship;

import cc.nimbusnova.mc.moon.MoonMission;

public enum ShipTask {
    PILOT("Flight guidance", MoonMission.Role.PILOT),
    GALLEY("Galley and thermal loop", MoonMission.Role.SUPPLIES),
    MEDICAL("Crew health and air filter", MoonMission.Role.LIFE_SUPPORT),
    MEETING("Crew intercom", null),
    ENGINE("Power and engines", MoonMission.Role.POWER),
    SCIENCE("Science archive", MoonMission.Role.MAINTENANCE),
    COMMS("Communications", MoonMission.Role.MAINTENANCE),
    PLANTS("Plant care", MoonMission.Role.LIFE_SUPPORT),
    EXERCISE("Exercise station", null),
    STORAGE("Cargo storage", MoonMission.Role.SUPPLIES);

    public final String label;
    public final MoonMission.Role role;

    ShipTask(final String label, final MoonMission.Role role) {
        this.label = label;
        this.role = role;
    }
}
