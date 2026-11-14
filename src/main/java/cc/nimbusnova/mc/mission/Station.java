package cc.nimbusnova.mc.mission;

/** A block in the world bound to a habitat system. Right-clicking it performs the system's action. */
public enum Station {
    OXYGEN(MissionState.Equipment.OXYGEN),
    RESEARCH(MissionState.Equipment.RESEARCH),
    CANISTER(null);

    public final MissionState.Equipment equipment;

    Station(final MissionState.Equipment equipment) {
        this.equipment = equipment;
    }
}
