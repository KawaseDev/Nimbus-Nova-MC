package cc.nimbusnova.mc.mission;

public final class MissionState {
    public static final int DURATION_SECONDS = 240;
    public static final int STORM_START_SECONDS = 75;
    public static final int STORM_END_SECONDS = 195;
    public static final int RESEARCH_TARGET_SECONDS = 90;

    public enum Phase {
        READY, ACTIVE, PAUSED, SUCCESS, PARTIAL, FAILED, ABORTED
    }

    public enum Equipment {
        OXYGEN, RESEARCH
    }

    private Phase phase = Phase.READY;
    private int elapsedSeconds;
    private double battery = 80, oxygen = 75;
    private boolean oxygenOnline = true, researchOnline = true;
    private int decisions;
    private int researchSeconds;
    private String lastEvent = "Choose your role. Mission Control starts the simulation.";

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

        if (equipment == Equipment.OXYGEN) {
            if (oxygenOnline == online) return;
            oxygenOnline = online;
        } else {
            if (researchOnline == online) return;
            researchOnline = online;
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
        final double generation = DustStormProfile.generation(elapsedSeconds);
        final double demand = 0.15 + (oxygenOnline ? 0.45 : 0) + (researchOnline ? 0.65 : 0);
        battery = Math.clamp(battery + generation - demand, 0, 100);
        oxygen = Math.clamp(oxygen + (oxygenOnline && battery > 0 ? 0.55 : 0) - 0.4, 0, 100);

        if (researchOnline && battery > 0)
            researchSeconds++;

        if (elapsedSeconds == STORM_START_SECONDS - 20)
            lastEvent = "Dust front approaching. Solar output will drop in 20 seconds.";

        if (elapsedSeconds == STORM_START_SECONDS)
            lastEvent = "Dust front arrived. The sky is darkening and solar output is falling. Rebalance power with your crew.";

        if (elapsedSeconds == DustStormProfile.PEAK_START_SECONDS)
            lastEvent = "Storm peak: the sun gives only 3% of its usual energy, like Opportunity's 21 Wh on June 10, 2018.";

        if (elapsedSeconds == STORM_END_SECONDS)
            lastEvent = "The dust is thinning and solar output is slowly recovering. Opportunity never woke up after its storm.";

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

    public boolean replenishOxygen() {
        if (phase != Phase.ACTIVE || oxygen >= 100) return false;

        oxygen = Math.min(100, oxygen + 25);
        decisions++;
        lastEvent = "An oxygen canister restored up to 25 reserve units. Supplies are limited.";
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(phase, elapsedSeconds, battery, oxygen, oxygenOnline, researchOnline,
                DustStormProfile.generation(elapsedSeconds), 0.15 + (oxygenOnline ? 0.45 : 0) + (researchOnline ? 0.65 : 0),
                decisions, researchSeconds, lastEvent);
    }

    public record Snapshot(Phase phase, int elapsedSeconds, double battery, double oxygen,
                           boolean oxygenOnline, boolean researchOnline, double generation, double demand,
                           int decisions, int researchSeconds, String lastEvent) {
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
