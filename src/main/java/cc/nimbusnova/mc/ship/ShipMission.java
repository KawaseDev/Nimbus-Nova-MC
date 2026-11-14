package cc.nimbusnova.mc.ship;

import cc.nimbusnova.mc.mission.MissionPreparation;
import cc.nimbusnova.mc.moon.MoonMission;

import java.util.ArrayList;
import java.util.List;

/** Physical tasks wrap the shared Moon rescue sequence; reserves and timing are authored teaching values. */
public final class ShipMission {
    public static final int DURATION_SECONDS = 360;

    public enum Phase {
        READY, ACTIVE, PAUSED, SUCCESS, PARTIAL, FAILED, ABORTED;

        public boolean ended() {
            return this == SUCCESS || this == PARTIAL || this == FAILED || this == ABORTED;
        }
    }

    private final MoonMission moon = new MoonMission(MoonMission.Variant.RESCUE);
    private final List<String> journal = new ArrayList<>();
    private Phase phase = Phase.READY;
    private int revision, elapsed, stageComplete, optionalComplete, completedTasks, decisions, meetings;
    private int engineBoard = 1 | 1 << 2 | 1 << 4 | 1 << 6 | 2 << 8 | 1 << 10 | 2 << 12 | 3 << 14 | 1 << 16;
    private int pilotBoard, commsBoard, valves, stableSeconds, heaterMask, exerciseRepetitions;
    private int loadMask = 7, parts = 6;
    private double battery = 100, oxygen = 100, water = 100, fuel = 100, co2 = 12, readiness = 70;
    private boolean cargoSelected, extraInstrument, filterOnline, heaterCommitted;
    private String feedback = "Prepare the power circuit and choose cargo before Mission Control starts the clock.";

    public MoonMission underlying() {
        return moon;
    }

    public int revision() {
        return revision;
    }

    public boolean ready() {
        return completed(ShipTask.ENGINE) && cargoSelected;
    }

    public void start() {
        if (phase != Phase.READY || !ready())
            throw new IllegalStateException("Repair the power circuit and load cargo before launch.");

        phase = Phase.ACTIVE;
        advance(!extraInstrument);
        revision++;
    }

    public void pause() {
        if (phase != Phase.ACTIVE)
            throw new IllegalStateException("Only an active ship mission can pause.");

        moon.pause();
        phase = Phase.PAUSED;
        feedback = "Mission Control paused the ship. Discuss your next decision; reserves are frozen.";
        revision++;
    }

    public void acknowledge() {
        if (phase != Phase.PAUSED || moon.incident() == null)
            throw new IllegalStateException("There is no ship incident to acknowledge.");

        moon.acknowledge();
        feedback = "Evidence acknowledged. Mission Control may resume when the crew understands the hazard.";
        revision++;
    }

    public void resume() {
        if (phase != Phase.PAUSED)
            throw new IllegalStateException("The ship mission is not paused.");

        moon.resume();
        phase = Phase.ACTIVE;
        feedback = instruction();
        revision++;
    }

    public void abort() {
        if (phase.ended())
            throw new IllegalStateException("This ship attempt has ended.");

        phase = Phase.ABORTED;
        feedback = "Mission Control ended the attempt. Review the crew's choices before trying again.";
        revision++;
    }

    public boolean action(final ShipTask task, final int expectedRevision, final int action) {
        if (task == null || expectedRevision != revision || phase == Phase.PAUSED || phase.ended() || !activeTask(task)) return false;
        final boolean accepted = switch (task) {
            case ENGINE -> engine(action);
            case PILOT, COMMS -> alignment(task, action);
            case MEDICAL -> moon.stage() == MoonMission.Stage.FILTER ? filter(action) : optional(task, action);
            case GALLEY -> moon.stage() == MoonMission.Stage.RESTART ? heating(action) : optional(task, action);
            case STORAGE -> cargo(action);
            case MEETING -> meeting(action);
            case SCIENCE, PLANTS, EXERCISE -> optional(task, action);
        };
        if (!accepted) return false;

        revision++;
        checkFailure();
        return true;
    }

    private boolean engine(final int action) {
        if (moon.stage() == MoonMission.Stage.EMERGENCY) {
            if (action >= 0 && action < 3) {
                loadMask ^= 1 << action;
                feedback = "Preserve life support. Science and comfort heating can be isolated to reduce demand.";
                return true;
            }

            if (action != 3) return false;
            if (loadMask != 1) {
                feedback = "Keep essential life support ON. Switch the science and comfort-heating loads OFF before isolation.";
                return true;
            }

            record("Isolated nonessential loads while preserving life support.");
            readiness -= 12;
            advance(true);
            return true;
        }

        if (action < 0 || action > 8 || completed(ShipTask.ENGINE)) return false;
        final int shift = action * 2;
        engineBoard = engineBoard & ~(3 << shift) | (((engineBoard >> shift & 3) + 1) & 3) << shift;
        if (MissionPreparation.powerConnected(engineBoard)) {
            complete(ShipTask.ENGINE);
            feedback = moon.stage() == MoonMission.Stage.PREPARATION
                    ? "Power path verified. Cargo must also be ready before launch."
                    : "Restart circuit verified. Allocate essential heat at the galley thermal controls.";
        } else
            feedback = "Rotate cable tiles to connect the left supply to the right output. Connected ports must face each other.";
        return true;
    }

    private boolean alignment(final ShipTask task, final int action) {
        if (completed(task) || action < 0 || action > 6) return false;
        final int board = task == ShipTask.PILOT ? pilotBoard : commsBoard;
        if (action == 6) {
            if (board != target(task)) {
                if (task == ShipTask.PILOT)
                    fuel = Math.max(0, fuel - 1);
                else
                    battery = Math.max(0, battery - 0.5);
                feedback = "The observed readings do not match the planned target. An unnecessary correction used a small reserve; compare all three axes.";
                return true;
            }

            complete(task);
            feedback = task == ShipTask.PILOT ? "Guidance matches the planned corridor." : "Communication lock confirmed on all three channels.";
            if (completed(ShipTask.PILOT) && (moon.stage() == MoonMission.Stage.NAVIGATION || completed(ShipTask.COMMS)))
                advance(true);
            return true;
        }

        final int axis = action / 2, value = dial(board, axis), next = value + (action % 2 == 0 ? 1 : -1);
        if (next < 0 || next > 4) return false;
        final int changed = board & ~(7 << axis * 3) | next << axis * 3;
        if (task == ShipTask.PILOT)
            pilotBoard = changed;
        else
            commsBoard = changed;
        feedback = "Match all three observed readings to the target, then validate. Previewing a change does not consume propellant.";
        return true;
    }

    private boolean filter(final int action) {
        if (action < 0 || action > 6) return false;
        if (action == 6) {
            if (stableSeconds < 3) {
                feedback = "Seal the adapter and hold balanced flow for three seconds before starting the filter.";
                return true;
            }

            filterOnline = true;
            complete(ShipTask.MEDICAL);
            record("Restored CO2 removal with a balanced adapter; adding oxygen alone would not remove CO2.");
            advance(true);
            return true;
        }

        final int valve = action / 2, value = dial(valves, valve), next = value + (action % 2 == 0 ? 1 : -1);
        if (next < 0 || next > 4) return false;
        valves = valves & ~(7 << valve * 3) | next << valve * 3;
        stableSeconds = 0;
        feedback = "Balance intake, filter pump and bypass. Pressure must be 55-65 and flow 35-45 model units.";
        return true;
    }

    private boolean heating(final int action) {
        if (heaterCommitted || action < 0 || action > 3) return false;
        if (action < 3) {
            heaterMask ^= 1 << action;
            feedback = "Warm navigation and the air loop first. Warming the science lab uses additional re-entry reserves.";
            return true;
        }

        if (!completed(ShipTask.ENGINE) || (heaterMask & 3) != 3) {
            feedback = "Repair the engine restart circuit, then select navigation and air-loop heating before committing.";
            return true;
        }

        final int cost = (heaterMask & 4) != 0 ? 20 : 8;
        if (battery < cost || water < 3) {
            feedback = "This heat plan exceeds the remaining reserves. Reduce nonessential heating.";
            return true;
        }

        battery -= cost;
        water -= 3;
        heaterCommitted = true;
        complete(ShipTask.GALLEY);
        record("Committed " + cost + " energy units to " + ((heaterMask & 4) != 0 ? "all heating zones." : "essential heating only."));
        advance(true);
        return true;
    }

    private boolean cargo(final int action) {
        if (cargoSelected || action < 0 || action > 1) return false;
        cargoSelected = true;
        extraInstrument = action == 1;
        if (!extraInstrument)
            parts += 2;
        feedback = extraInstrument ? "Extra science instrument loaded; one fewer repair part will be available after launch."
                : "Two spare repair parts loaded. The crew is keeping capacity for repairs.";
        record(feedback);
        return true;
    }

    private boolean meeting(final int action) {
        if (action != 0) return false;
        if (phase == Phase.READY) {
            feedback = "Check power and cargo first. During flight, the intercom can request up to two paused crew discussions.";
            return true;
        }

        if (meetings >= 2) {
            feedback = "Both scheduled crew discussion breaks were used. Mission Control can still pause the lesson.";
            return true;
        }

        meetings++;
        moon.pause();
        phase = Phase.PAUSED;
        record("Requested crew discussion break " + meetings + " of 2.");
        feedback = "Crew meeting requested. Reserves are frozen until Mission Control resumes.";
        return true;
    }

    private boolean optional(final ShipTask task, final int action) {
        if (action != 0 || (optionalComplete & 1 << task.ordinal()) != 0) return false;
        final double waterCost = switch (task) { case GALLEY -> 8; case PLANTS -> 6; case EXERCISE -> 2; default -> 0; };
        if (water < waterCost || task == ShipTask.MEDICAL && parts < 1 || task == ShipTask.SCIENCE && battery < 4) return false;
        if (task == ShipTask.EXERCISE && ++exerciseRepetitions < 5) {
            feedback = "Controlled exercise set: " + exerciseRepetitions + " / 5. Finish the set while a crewmate watches the systems.";
            return true;
        }

        water -= waterCost;
        if (task == ShipTask.MEDICAL)
            parts--;
        if (task == ShipTask.SCIENCE)
            battery -= 4;
        readiness = Math.min(100, readiness + switch (task) { case GALLEY, MEDICAL -> 8; case PLANTS -> 6; case EXERCISE -> 5; default -> 0; });
        optionalComplete |= 1 << task.ordinal();
        feedback = switch (task) {
            case GALLEY -> "Crew ration prepared: readiness +8, water -8. Food preparation also needs a water budget.";
            case MEDICAL -> "Crew check completed: readiness +8, one kit part used. These are game values, not medical treatment.";
            case PLANTS -> "Plant tray serviced: readiness +6, water -6. Food growth is slower than this emergency lesson.";
            case EXERCISE -> "Exercise set complete: readiness +5, water -2. Routine crew health still needs resources.";
            case SCIENCE -> "Science observations archived: energy -4. The archive can be preserved without keeping every laboratory load on.";
            default -> throw new IllegalArgumentException("This station does not have an optional action.");
        };
        record(feedback);
        return true;
    }

    private void complete(final ShipTask task) {
        stageComplete |= 1 << task.ordinal();
        completedTasks++;
        record(task.label + " verified during " + moon.stage().name().toLowerCase().replace('_', ' ') + ".");
    }

    private void record(final String decision) {
        decisions++;
        if (journal.size() < 32)
            journal.add(decision);
    }

    private void advance(final boolean safe) {
        final MoonMission.Stage stage = moon.stage();
        final int fuelCost = switch (stage) { case DOCKING, NAVIGATION -> 5; case RETURN -> 10; default -> 0; };
        final int batteryCost = switch (stage) { case EMERGENCY -> 20; case FILTER -> 5; case RESTART -> 10; default -> 0; };
        if (battery < batteryCost || fuel < fuelCost || stage == MoonMission.Stage.EMERGENCY && oxygen <= 10
                || stage == MoonMission.Stage.FILTER && parts < 1 || stage == MoonMission.Stage.RESTART && water < 5) {
            phase = Phase.FAILED;
            feedback = "The verified maneuver requires more reserves than remain. Preserve a return budget before committing loads.";
            record(feedback);
            return;
        }

        final int previousBattery = moon.battery(), previousOxygen = moon.oxygen(), previousWater = moon.water(),
                previousFuel = moon.fuel(), previousParts = moon.parts();
        moon.choose(MoonMission.decision(moon.stage()).role(), safe);
        for (final MoonMission.Role role : MoonMission.required(moon.stage()))
            moon.confirm(role);
        moon.advance();
        battery -= previousBattery - moon.battery();
        oxygen -= previousOxygen - moon.oxygen();
        water -= previousWater - moon.water();
        fuel -= previousFuel - moon.fuel();
        parts -= previousParts - moon.parts();
        stageComplete = 0;
        pilotBoard = 0;
        commsBoard = 0;
        if (moon.stage() == MoonMission.Stage.RESTART)
            engineBoard = 2 | 3 << 2 | 1 << 4 | 2 << 6 | 2 << 10 | 1 << 12 | 1 << 14 | 3 << 16;
        if (moon.stage() == MoonMission.Stage.COMPLETE) {
            phase = battery >= 20 && oxygen >= 25 && fuel >= 25 && readiness >= 60 ? Phase.SUCCESS : Phase.PARTIAL;
            feedback = phase == Phase.SUCCESS ? "Crew recovered inside the approved return corridor with essential reserves intact."
                    : "Crew recovered, but reserve or readiness targets were missed. Compare the crew's resource choices.";
            return;
        }

        phase = moon.paused() ? Phase.PAUSED : Phase.ACTIVE;
        feedback = moon.incident() == null ? instruction() : moon.incident().observation;
        checkFailure();
    }

    public void tickSecond() {
        if (phase != Phase.ACTIVE) return;
        elapsed++;
        final double extraLoad = moon.stage() == MoonMission.Stage.EMERGENCY
                ? ((loadMask & 2) != 0 ? 0.08 : 0) + ((loadMask & 4) != 0 ? 0.06 : 0) : 0;
        battery = Math.max(0, battery - 0.05 - extraLoad - (heaterCommitted ? (heaterMask & 4) != 0 ? 0.12 : 0.04 : 0));
        oxygen = Math.max(0, oxygen - 0.035 - (moon.stage() == MoonMission.Stage.EMERGENCY && (loadMask & 1) == 0 ? 0.6 : 0));
        if (moon.stage() == MoonMission.Stage.EMERGENCY || moon.stage() == MoonMission.Stage.FILTER || filterOnline)
            co2 = Math.clamp(co2 + (filterOnline ? -1 : 0.6), 0, 100);
        if (co2 > 60)
            readiness = Math.max(0, readiness - 0.2);
        if (moon.stage() == MoonMission.Stage.FILTER)
            stableSeconds = MissionPreparation.oxygenBalanced(valves) ? Math.min(3, stableSeconds + 1) : 0;
        checkFailure();
    }

    private void checkFailure() {
        if (phase.ended() || phase == Phase.READY) return;
        if (battery > 0 && oxygen > 0 && co2 < 100 && elapsed < DURATION_SECONDS) return;
        phase = Phase.FAILED;
        feedback = battery <= 0 ? "Electrical reserve exhausted before safe return."
                : oxygen <= 0 ? "Oxygen reserve exhausted before safe return."
                : co2 >= 100 ? "CO2 reached the simulated limit. Stored oxygen did not replace a working filter."
                : "The return window closed before the departure checklist was complete.";
        record(feedback);
    }

    public boolean activeTask(final ShipTask task) {
        if (phase.ended()) return false;
        return switch (task) {
            case ENGINE -> moon.stage() == MoonMission.Stage.PREPARATION || moon.stage() == MoonMission.Stage.EMERGENCY || moon.stage() == MoonMission.Stage.RESTART;
            case STORAGE -> moon.stage() == MoonMission.Stage.PREPARATION && !cargoSelected;
            case PILOT -> moon.stage() == MoonMission.Stage.DOCKING || moon.stage() == MoonMission.Stage.NAVIGATION || moon.stage() == MoonMission.Stage.RETURN;
            case COMMS -> moon.stage() == MoonMission.Stage.DOCKING || moon.stage() == MoonMission.Stage.RETURN;
            case GALLEY -> moon.stage() == MoonMission.Stage.RESTART || (optionalComplete & 1 << task.ordinal()) == 0;
            case MEDICAL -> moon.stage() == MoonMission.Stage.FILTER || (optionalComplete & 1 << task.ordinal()) == 0;
            case MEETING -> true;
            case SCIENCE, PLANTS, EXERCISE -> (optionalComplete & 1 << task.ordinal()) == 0;
        };
    }

    public boolean completed(final ShipTask task) {
        if (task == ShipTask.STORAGE) return cargoSelected;
        if (task == ShipTask.GALLEY && moon.stage() == MoonMission.Stage.RESTART) return heaterCommitted;
        if (task == ShipTask.MEDICAL && moon.stage() == MoonMission.Stage.FILTER) return filterOnline;
        return (stageComplete & 1 << task.ordinal()) != 0 || (optionalComplete & 1 << task.ordinal()) != 0;
    }

    public int board(final ShipTask task) {
        return switch (task) {
            case ENGINE -> moon.stage() == MoonMission.Stage.EMERGENCY ? loadMask : engineBoard;
            case PILOT -> pilotBoard;
            case COMMS -> commsBoard;
            case MEDICAL -> moon.stage() == MoonMission.Stage.FILTER ? valves : (optionalComplete & 1 << task.ordinal()) != 0 ? 1 : 0;
            case GALLEY -> moon.stage() == MoonMission.Stage.RESTART ? heaterMask : (optionalComplete & 1 << task.ordinal()) != 0 ? 1 : 0;
            case STORAGE -> cargoSelected ? extraInstrument ? 2 : 1 : 0;
            case EXERCISE -> exerciseRepetitions;
            default -> completed(task) ? 1 : 0;
        };
    }

    public int aux(final ShipTask task) {
        return switch (task) {
            case ENGINE -> moon.stage() == MoonMission.Stage.EMERGENCY ? 1 : MissionPreparation.energizedCells(engineBoard);
            case PILOT, COMMS -> target(task);
            case MEDICAL -> moon.stage() == MoonMission.Stage.FILTER
                    ? MissionPreparation.oxygenPressure(valves) | MissionPreparation.oxygenFlow(valves) << 8 | stableSeconds << 16 : 0;
            default -> 0;
        };
    }

    public int target(final ShipTask task) {
        if (task == ShipTask.COMMS)
            return moon.stage() == MoonMission.Stage.RETURN ? 1 | 4 << 3 | 2 << 6 : 3 | 1 << 3 | 2 << 6;
        return switch (moon.stage()) {
            case NAVIGATION -> 4 | 1 << 3 | 3 << 6;
            case RETURN -> 3 | 2 << 3 | 4 << 6;
            default -> 2 | 3 << 3 | 1 << 6;
        };
    }

    public static int dial(final int board, final int axis) {
        return axis < 0 || axis > 2 ? 0 : board >> axis * 3 & 7;
    }

    public ShipTask objectiveTask(final MoonMission.Role role) {
        if (role == null) return ShipTask.MEETING;
        final ShipTask owned = switch (role) {
            case POWER -> ShipTask.ENGINE;
            case LIFE_SUPPORT -> moon.stage() == MoonMission.Stage.FILTER ? ShipTask.MEDICAL : ShipTask.PLANTS;
            case SUPPLIES -> moon.stage() == MoonMission.Stage.PREPARATION ? ShipTask.STORAGE : ShipTask.GALLEY;
            case MAINTENANCE -> activeTask(ShipTask.COMMS) ? ShipTask.COMMS : ShipTask.SCIENCE;
            case PILOT -> ShipTask.PILOT;
        };
        return activeTask(owned) && !completed(owned) ? owned : ShipTask.MEETING;
    }

    public String instruction() {
        return switch (moon.stage()) {
            case PREPARATION -> "Route startup power at the engines and choose the cargo load at storage. Preparation has no timer.";
            case DOCKING -> "Pilot: match the docking alignment. Communications: tune all three channels to verify the lock.";
            case NAVIGATION -> "Pilot: compare the new predicted route with the three observed guidance readings.";
            case EMERGENCY -> "Power engineer: isolate science and comfort heating while keeping life support on.";
            case FILTER -> "Life-support engineer: balance the CO2 filter adapter and hold stable flow before activation.";
            case RESTART -> "Power engineer: repair the restart circuit. Supplies: allocate heat to navigation and the air loop.";
            case RETURN -> "Pilot: align the approved return corridor. Communications: confirm the recovery link.";
            case COMPLETE -> feedback;
            default -> throw new IllegalStateException("Unexpected rescue stage " + moon.stage());
        };
    }

    public List<String> debrief() {
        return List.copyOf(journal);
    }

    public Snapshot snapshot() {
        return new Snapshot(phase, moon.stage(), revision, battery, oxygen, water, fuel, parts, co2, readiness,
                elapsed, Math.max(0, DURATION_SECONDS - elapsed), completedTasks, decisions, feedback,
                phase.ended() ? feedback : "", moon.incident());
    }

    public record Snapshot(Phase phase, MoonMission.Stage stage, int revision, double battery, double oxygen,
                           double water, double fuel, int parts, double co2, double readiness, int elapsed,
                           int remaining, int completedTasks, int decisions, String feedback, String outcome,
                           MoonMission.Incident incident) {
        /* w */
    }
}
