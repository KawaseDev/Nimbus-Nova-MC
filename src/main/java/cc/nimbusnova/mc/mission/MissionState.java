package cc.nimbusnova.mc.mission;

public final class MissionState {
    public static final int DURATION_SECONDS = 240;
    public static final int STORM_START_SECONDS = 75;
    public static final int STORM_END_SECONDS = 195;
    public static final int RESEARCH_TARGET_SECONDS = 90;
    // Emergency buffer only: together the canisters cannot replace oxygen production for a whole mission.
    public static final int OXYGEN_CANISTERS = 2;
    public static final double CANISTER_OXYGEN = 8;
    // Solar particle storm, inspired by the real September 2017 event that more than doubled the surface radiation
    // Curiosity had measured. Timing and dose units are game assumptions.
    public static final int SOLAR_WARNING_SECONDS = 10;
    public static final int SOLAR_START_SECONDS = 40;
    public static final int SOLAR_END_SECONDS = 65;
    // Twenty exposed seconds reach the shared limit, independently of class size.
    public static final double DOSE_PER_EXPOSED_SECOND = 5;

    public enum Phase {
        READY, ACTIVE, PAUSED, SUCCESS, PARTIAL, FAILED, ABORTED;

        public boolean ended() {
            return this == SUCCESS || this == PARTIAL || this == FAILED || this == ABORTED;
        }
    }

    public enum Equipment {
        OXYGEN, RESEARCH
    }

    private final DustStorm storm;
    private Phase phase = Phase.READY;
    private int elapsedSeconds;
    private double battery = 80, oxygen = 75;
    private boolean oxygenOnline = true, researchOnline = true;
    private int decisions;
    private int researchSeconds;
    // Debrief measurements: what happened, not why.
    private double lowestBattery = battery, lowestOxygen = oxygen;
    private int firstResearchCutSecond = -1;
    private int canistersLeft = OXYGEN_CANISTERS;
    private int backupCells = 2;
    private double radiationDose;
    private boolean relayFault;
    private String lastEvent = "Choose your role. Mission Control starts the simulation.";

    public MissionState() {
        this(DustStorm.OPPORTUNITY_2018);
    }

    public MissionState(final DustStorm storm) {
        this.storm = storm;
    }

    public void start() {
        if (phase != Phase.READY)
            throw new IllegalStateException("Reset the mission before starting another attempt.");

        phase = Phase.ACTIVE;
        lastEvent = "Mission started. Discuss your power budget before the dust arrives.";
    }

    public void pause() {
        if (phase != Phase.ACTIVE)
            throw new IllegalStateException("Only an active mission can be paused.");

        phase = Phase.PAUSED;
        lastEvent = "Simulation paused. Resources and mission time are frozen.";
    }

    public void resume() {
        if (phase != Phase.PAUSED)
            throw new IllegalStateException("The mission is not paused.");

        phase = Phase.ACTIVE;
        lastEvent = "Simulation resumed. Keep essential equipment powered.";
    }

    public void abort() {
        if (phase != Phase.ACTIVE && phase != Phase.PAUSED)
            throw new IllegalStateException("There is no running mission to abort.");

        phase = Phase.ABORTED;
        lastEvent = "Mission aborted by Mission Control. Review your decisions and retry.";
    }

    public void setEquipment(final Equipment equipment, final boolean online) {
        if (phase != Phase.ACTIVE)
            throw new IllegalStateException("Equipment can only change while the mission is running.");

        if (equipment == Equipment.RESEARCH && online && relayFault)
            throw new IllegalStateException("The research relay is damaged. Repair it at the power yard first.");

        if (equipment == Equipment.OXYGEN) {
            if (oxygenOnline == online) return;
            oxygenOnline = online;
        } else {
            if (researchOnline == online) return;
            researchOnline = online;
            if (!online && firstResearchCutSecond < 0)
                firstResearchCutSecond = elapsedSeconds;
        }

        decisions++;
        lastEvent = (equipment == Equipment.OXYGEN ? "Oxygen generator" : "Research equipment")
                + (online ? " enabled." : " disabled.")
                + (equipment == Equipment.OXYGEN && !online ? " The crew is using stored oxygen." : "");
    }

    public void tickSecond() {
        if (phase != Phase.ACTIVE) return;

        elapsedSeconds++;
        // Solar supply follows Opportunity's 2018 record; battery size and demand are authored game units.
        final double generation = DustStormProfile.generation(storm, elapsedSeconds);
        final double demand = 0.15 + (oxygenOnline ? 0.45 : 0) + (researchOnline ? 0.65 : 0);
        battery = Math.clamp(battery + generation - demand, 0, 100);
        oxygen = Math.clamp(oxygen + (oxygenOnline && battery > 0 ? 0.55 : 0) - 0.4, 0, 100);

        lowestBattery = Math.min(lowestBattery, battery);
        lowestOxygen = Math.min(lowestOxygen, oxygen);
        if (researchOnline && battery > 0 && !relayFault)
            researchSeconds++;

        if (elapsedSeconds == SOLAR_WARNING_SECONDS)
            lastEvent = "Solar storm alert! Fast particles from the Sun reach Mars in " + (SOLAR_START_SECONDS - SOLAR_WARNING_SECONDS)
                    + " seconds. Everyone into the crew shelter south of the habitat!";

        if (elapsedSeconds == SOLAR_START_SECONDS)
            lastEvent = "Solar particles are hitting Mars. Stay inside the crew shelter until 01:05.";

        if (elapsedSeconds == SOLAR_END_SECONDS)
            lastEvent = "The solar particle storm has passed. It is safe to leave the shelter.";

        if (elapsedSeconds == STORM_START_SECONDS - 20)
            lastEvent = "Dust front approaching. Solar output will drop in 20 seconds.";

        if (elapsedSeconds == STORM_START_SECONDS)
            lastEvent = "Dust front arrived. The sky is darkening and solar output is falling. Rebalance power with your crew.";

        if (elapsedSeconds == 80) {
            relayFault = true;
            lastEvent = "Equipment fault: the research relay is drawing power without producing science. Isolate and repair the marked console. This is an authored equipment event.";
        }

        if (elapsedSeconds == DustStormProfile.PEAK_START_SECONDS)
            lastEvent = "Storm peak: the sun gives only " + storm.peakPercent() + "% of its usual energy, like Opportunity's "
                    + (int) storm.peakWattHours + " Wh on " + storm.peakDate + ".";

        if (elapsedSeconds == STORM_END_SECONDS)
            lastEvent = "The dust is thinning and solar output is slowly recovering.";

        if (battery <= 0 || oxygen <= 0) {
            phase = Phase.FAILED;
            lastEvent = battery <= 0
                    ? "Battery depleted. Essential systems lost power. Reduce research demand on your next attempt."
                    : "Oxygen depleted. Keep production running or restore it before reserves run out.";
        } else if (elapsedSeconds >= DURATION_SECONDS) {
            phase = researchSeconds >= RESEARCH_TARGET_SECONDS ? Phase.SUCCESS : Phase.PARTIAL;
            lastEvent = phase == Phase.SUCCESS
                    ? "Crew safe and research complete. You balanced survival with the science objective."
                    : "Crew safe, but research incomplete. Try preserving reserves while making time for science.";
        }
    }

    public void prepareEquipment(final Equipment equipment, final boolean online) {
        if (phase != Phase.READY)
            throw new IllegalStateException("Preparation equipment can only change before the mission starts.");

        if (equipment == Equipment.OXYGEN)
            oxygenOnline = online;
        else
            researchOnline = online;
    }

    public boolean relayFault() {
        return relayFault;
    }

    public void repairRelay() {
        if (phase != Phase.ACTIVE || !relayFault)
            throw new IllegalStateException("There is no active relay fault to repair.");

        relayFault = false;
        decisions++;
        lastEvent = "Research relay repaired. Choose when to restore research power without exhausting the battery.";
    }

    /** Called once per second by the server with the number of crew members outside the shelter. */
    public void recordExposure(final int exposedCrew) {
        if (phase != Phase.ACTIVE || !solarStorm(elapsedSeconds) || exposedCrew <= 0) return;

        radiationDose = Math.min(100, radiationDose + DOSE_PER_EXPOSED_SECOND);
        if (radiationDose >= 100) {
            phase = Phase.FAILED;
            lastEvent = "Radiation dose too high. The crew stayed outside the shelter during the solar storm.";
        }
    }

    public static boolean solarWarning(final int elapsedSeconds) {
        return elapsedSeconds >= SOLAR_WARNING_SECONDS && elapsedSeconds < SOLAR_START_SECONDS;
    }

    public static boolean solarStorm(final int elapsedSeconds) {
        return elapsedSeconds >= SOLAR_START_SECONDS && elapsedSeconds < SOLAR_END_SECONDS;
    }

    public boolean replenishOxygen() {
        if (phase != Phase.ACTIVE || oxygen >= 100 || canistersLeft == 0) return false;

        oxygen = Math.min(100, oxygen + CANISTER_OXYGEN);
        canistersLeft--;
        decisions++;
        lastEvent = "An oxygen canister restored up to " + (int) CANISTER_OXYGEN + " reserve units. " + canistersLeft + " of " + OXYGEN_CANISTERS + " left.";
        return true;
    }

    public int backupCellsRemaining() {
        return backupCells;
    }

    public boolean useBackupPower() {
        if (phase != Phase.ACTIVE || backupCells == 0 || battery >= 100) return false;

        battery = Math.min(100, battery + 10);
        backupCells--;
        decisions++;
        lastEvent = "Backup cell transferred up to 10 energy units. " + backupCells + " cells remain; this is stored energy, not new solar power.";
        return true;
    }

    public boolean cycleAirlock() {
        if (phase == Phase.READY) return true;
        if (phase != Phase.ACTIVE || oxygen <= 2) return false;

        oxygen -= 2;
        lowestOxygen = Math.min(lowestOxygen, oxygen);
        decisions++;
        lastEvent = "Airlock cycled: 2 shared oxygen reserve units used. Plan trips together to conserve air.";
        return true;
    }

    public boolean transferVehicleCharge() {
        if (phase == Phase.READY) return true;
        if (phase != Phase.ACTIVE || battery < 5) return false;

        battery -= 5;
        lowestBattery = Math.min(lowestBattery, battery);
        decisions++;
        lastEvent = "Rover charging used 5 shared energy units. Stored driving energy competes with habitat equipment.";
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(phase, elapsedSeconds, battery, oxygen, oxygenOnline, researchOnline,
                DustStormProfile.generation(storm, elapsedSeconds), 0.15 + (oxygenOnline ? 0.45 : 0) + (researchOnline ? 0.65 : 0),
                decisions, researchSeconds, lastEvent, lowestBattery, lowestOxygen, firstResearchCutSecond, canistersLeft, storm, radiationDose);
    }

    public record Snapshot(Phase phase, int elapsedSeconds, double battery, double oxygen,
                           boolean oxygenOnline, boolean researchOnline, double generation, double demand,
                           int decisions, int researchSeconds, String lastEvent,
                           double lowestBattery, double lowestOxygen, int firstResearchCutSecond, int canistersLeft,
                           DustStorm dustStorm, double radiationDose) {
        public boolean storm() {
            return elapsedSeconds >= STORM_START_SECONDS && elapsedSeconds < STORM_END_SECONDS;
        }

        public String objective() {
            return switch (phase) {
                case READY -> "Choose a role and prepare your crew";
                case PAUSED -> "Mission paused by Mission Control";
                case SUCCESS -> "Mission complete / crew safe";
                case PARTIAL -> "Crew safe / research incomplete";
                case FAILED -> "Mission failed / review and retry";
                case ABORTED -> "Mission aborted / crew recalled";
                case ACTIVE -> storm() ? "Preserve power. Keep oxygen above zero." : "Keep the habitat running until extraction";
            };
        }
    }
}
