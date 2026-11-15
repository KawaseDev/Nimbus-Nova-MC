package cc.nimbusnova.mc.scenario;

/** Authored classroom indices and compressed timing, not measured habitat performance. */
public final class ScenarioMission {
    public enum Phase { READY, ACTIVE, PAUSED, SUCCESS, PARTIAL, FAILED, ABORTED }
    public final String id;
    public Phase phase = Phase.READY;
    public int revision, elapsed, scans, shields, signal, valves, harvest;
    public int loads = 15;
    public double battery = 100, oxygen = 100, water = 100, temperature = 65, dose;
    public final double[] moisture = { 30, 55, 80 };
    public final double[] rootAir = { 80, 80, 80 };
    public boolean cableBroken, pipeBroken, repaired;
    public String feedback = "Prepare your crew. No hazard time passes before launch.";

    public ScenarioMission(final String id) {
        if (!id.equals("shelter") && !id.equals("long_night") && !id.equals("greenhouse"))
            throw new IllegalArgumentException("Unknown outpost mission.");
        this.id = id;
    }

    public boolean ended() {
        return phase == Phase.SUCCESS || phase == Phase.PARTIAL || phase == Phase.FAILED || phase == Phase.ABORTED;
    }

    public void start() {
        if (phase != Phase.READY) return;
        if (id.equals("shelter") && (scans != 7 || shields != 7))
            throw new IllegalStateException("Survey all three bearings and install three shielding blocks first.");
        if (id.equals("long_night") && (loads & 3) != 3)
            throw new IllegalStateException("Keep life support and habitat heat supplied before starting.");
        phase = Phase.ACTIVE;
        revision++;
        feedback = "Mission running. Physical repairs and resource decisions affect the outcome.";
    }

    public void pause() {
        if (phase != Phase.ACTIVE) return;
        phase = Phase.PAUSED;
        revision++;
    }

    public void resume() {
        if (phase != Phase.PAUSED) return;
        phase = Phase.ACTIVE;
        revision++;
    }

    public boolean action(final int station, final int value, final int expectedRevision) {
        if (revision != expectedRevision || ended() || phase == Phase.PAUSED || station < 0 || station > 2) return false;
        if (id.equals("shelter")) {
            if (station == 0 && value >= 0 && value < 3) {
                if ((scans & 1 << value) != 0) return false;
                scans |= 1 << value;
                feedback = "Survey recorded: exposed terrain has little mass above it; the covered shelter provides passive shielding.";
            } else if (station == 2 && phase == Phase.ACTIVE && value >= 0 && value <= 1) {
                if (signal >= 9) return false;
                final int expected = new int[] { 0, 0, 0, 1, 1, 1, 0, 0, 0 }[signal];
                signal = signal < 9 && value == expected ? signal + 1 : 0;
                feedback = signal == 9 ? "SOS beacon verified. Keep the crew protected through the event." : "Transmit SOS: three short, three long, three short pulses.";
            } else return false;
        } else if (id.equals("long_night")) {
            if (station == 0 && value >= 0 && value < 4) {
                loads ^= 1 << value;
                feedback = "Budget heat and breathing first. Laboratory and beacon loads also drain the battery.";
            } else if (station == 2 && phase == Phase.ACTIVE && value >= 0 && value <= 1 && (loads & 8) != 0 && !cableBroken) {
                if (signal >= 9) return false;
                final int expected = new int[] { 0, 0, 0, 1, 1, 1, 0, 0, 0 }[signal];
                signal = value == expected ? signal + 1 : 0;
                battery = Math.max(0, battery - 0.3);
                feedback = signal == 9 ? "Rescue beacon locked. Protect the remaining battery until sunrise." : "Beacon needs SOS. More transmitted pulses use stored energy.";
            } else return false;
        } else {
            if (station == 1 && value >= 0 && value < 3) {
                valves ^= 1 << value;
                feedback = "Open channels water roots but consume reserve. Excess water reduces root aeration.";
            } else if (station == 2 && value >= 0 && value < 3 && phase == Phase.ACTIVE && elapsed >= 80) {
                if ((harvest & 1 << value) != 0) return false;
                if (moisture[value] < 40 || moisture[value] > 70 || rootAir[value] < 60 || pipeBroken) {
                    feedback = "Wait for this bed to reach moisture 40-70 and root air at least 60. Repair the pipe before sampling.";
                    revision++;
                    return true;
                }
                harvest |= 1 << value;
                feedback = "Healthy crop observation archived. This is a compressed care lesson, not instant real food growth.";
            } else return false;
        }
        revision++;
        return true;
    }

    public void repair() {
        if (ended() || phase != Phase.ACTIVE || !cableBroken && !pipeBroken) return;
        cableBroken = false;
        pipeBroken = false;
        repaired = true;
        revision++;
        feedback = "Physical repair verified. Stored resources still have to last until the end.";
    }

    public void tick(final boolean protectedCrew) {
        if (phase != Phase.ACTIVE) return;
        elapsed++;
        if (id.equals("shelter")) {
            if (elapsed >= 45 && elapsed <= 100 && (!protectedCrew || shields != 7)) dose += 3;
            if (elapsed == 25) feedback = "Particle-event warning. Finish outside work and enter the covered shelter before 00:45.";
            if (elapsed == 45) feedback = "Particle event active. Stay under the covered shielding until 01:40.";
            if (elapsed == 101) feedback = "All-clear. The passive shield reduced exposure without requiring power.";
        } else if (id.equals("long_night")) {
            if (elapsed == 45) { cableBroken = true; feedback = "Cable fault: collect a wire kit from storage and replace the failed connection at the power rack."; }
            final boolean heat = !cableBroken && (loads & 1) != 0, air = !cableBroken && (loads & 2) != 0;
            temperature = Math.clamp(temperature + (heat ? 0.15 : -1), 0, 100);
            oxygen = Math.clamp(oxygen + (air ? 0.1 : -0.8), 0, 100);
            battery = Math.max(0, battery - (cableBroken ? 0.05 : 0.08 + (heat ? 0.12 : 0) + (air ? 0.12 : 0)
                    + ((loads & 4) != 0 ? 0.55 : 0) + ((loads & 8) != 0 ? 0.18 : 0)));
        } else {
            if (elapsed == 40) { pipeBroken = true; feedback = "The feed pipe burst. Take a repair plate from storage and physically replace the missing pipe block."; }
            battery = Math.max(0, battery - 0.12);
            if (pipeBroken) water = Math.max(0, water - 0.7);
            for (int bed = 0; bed < 3; bed++) {
                final boolean watering = !pipeBroken && (valves & 1 << bed) != 0;
                moisture[bed] = Math.clamp(moisture[bed] + (watering ? 3 : -1), 0, 100);
                rootAir[bed] = Math.clamp(rootAir[bed] + (moisture[bed] > 80 ? -1.2 : 0.5), 0, 100);
                if (watering) water = Math.max(0, water - 0.25);
            }
        }
        if (dose >= 100 || battery <= 0 || oxygen <= 0 || temperature <= 0 || water <= 0) {
            phase = Phase.FAILED;
            feedback = dose >= 100 ? "Exposure limit reached. Compare the crew's timing and shield placement." : "An essential reserve reached zero. Review loads, repairs and resource choices.";
        } else if (elapsed >= 150) {
            final boolean objective = id.equals("shelter") ? shields == 7 && signal == 9
                    : id.equals("long_night") ? repaired && signal == 9 : repaired && harvest == 7;
            phase = objective ? Phase.SUCCESS : Phase.PARTIAL;
            feedback = (objective ? "Mission complete. " : "Crew survived; objective incomplete. ") + switch (id) {
                case "shelter" -> "Exposure " + Math.round(dose) + "/100; shield modules " + Integer.bitCount(shields)
                        + "/3; beacon " + signal + "/9. Mass shielding preserved the crew without drawing power.";
                case "long_night" -> "Battery " + Math.round(battery) + "/100; heat " + Math.round(temperature)
                        + "; oxygen " + Math.round(oxygen) + ". Cable repaired: " + repaired + "; beacon " + signal + "/9.";
                default -> "Healthy bed records " + Integer.bitCount(harvest) + "/3; water " + Math.round(water)
                        + "/100; pipe repaired: " + repaired + ". Compare irrigation, root air and remaining reserve.";
            };
        }
    }

    public String title() {
        return switch (id) { case "shelter" -> "Shelter Before the Event"; case "long_night" -> "The Long Night"; default -> "Greenhouse Emergency"; };
    }

    public String objective() {
        if (ended()) return feedback;
        return switch (id) {
            case "shelter" -> phase == Phase.READY ? "Survey three bearings, collect shielding blocks and fill the three marked shield slots."
                    : elapsed < 45 ? "Transmit SOS and move the crew inside the covered shelter." : elapsed <= 100 ? "Remain in the protected shelter." : "Verify SOS before rescue.";
            case "long_night" -> cableBroken ? "Repair the physical power junction with a wire kit from storage."
                    : "Keep heat and life support supplied, shed optional loads and transmit the rescue beacon.";
            default -> pipeBroken ? "Replace the missing feed-pipe block using a plate from storage."
                    : elapsed < 80 ? "Balance irrigation and root aeration while preserving water." : "Collect healthy observations from all three beds (moisture40-70, rootair60+).";
        };
    }

    public String evidence() {
        return switch (id) {
            case "shelter" -> "NASA measured a solar-particle radiation increase on Mars in 2017. This lunar-inspired exercise uses an authored shelter, warning and exposure index; it is not that location or a dose calculation.";
            case "long_night" -> "NASA describes a typical roughly 14-day lunar night and challenging polar illumination. This 150-second site, loads and reserves are authored teaching values.";
            default -> "NASA plant-water studies investigate hydration and oxygen movement around roots in reduced gravity. Our moisture, aeration, repair and crop-observation values are authored and do not accelerate real crop growth.";
        };
    }

    public String source() {
        return switch (id) {
            case "shelter" -> "https://www.jpl.nasa.gov/news/large-solar-storm-sparks-global-aurora-and-doubles-radiation-levels-on-the-martian-surface/";
            case "long_night" -> "https://www.nasa.gov/lunar-surface-technology/";
            default -> "https://science.nasa.gov/science-research/science-enabling-technology/technology-highlights/how-do-you-water-plants-in-space/";
        };
    }
}
