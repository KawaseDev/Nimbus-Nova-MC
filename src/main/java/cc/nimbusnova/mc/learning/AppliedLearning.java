package cc.nimbusnova.mc.learning;

import java.util.List;

/** Actions are inferred from authored mission readings, not from a reading-time quota. */
public final class AppliedLearning {
    public enum Case {
        SOLAR("Exposure route", "Particle alert: incoming. Outside route: exposed. Shelter route: shielded.",
                "Choose the route that changes the crew's exposure.",
                List.of("Inspect the outside solar panels", "Move together into the shelter", "Increase research power"), 1,
                "The shelter changes exposure. Extra power or oxygen does not shield the crew from energetic particles."),
        DUST("Read the power budget", "Assume battery storage has room. Compare the current supply and load, not the later forecast.",
                "What battery trend do these displayed readings predict RIGHT NOW?",
                List.of("Charge: supply exceeds load", "Drain: load exceeds supply", "Stay steady: supply equals load"), 2,
                "Subtract the current electrical load from the current supply to predict the storage trend."),
        RELAY("Fault isolation", "Research uses power but its output has stopped. Oxygen equipment still works.",
                "Choose the repair supported by both readings.",
                List.of("Add oxygen to the habitat", "Restart every electrical load", "Isolate and repair the research relay"), 2,
                "Draw without the expected output identifies the faulty circuit. Recheck output after repairing it."),
        DOCKING("Contact versus lock", "Contact sensor: YES. Mechanical lock: UNCONFIRMED.",
                "Which next observation would make crew transfer safer?",
                List.of("Begin transfer because contact is enough", "Verify the latch and its power", "Spend fuel on a blind correction"), 1,
                "Contact and a secure mechanical connection are different observations. Verify the lock before transferring."),
        DESCENT("Cross-check guidance", "Guidance alarm: ACTIVE. Radar: AVAILABLE. Terrain: obstructed.",
                "Choose a response that uses the remaining evidence.",
                List.of("Ignore both guidance and radar", "Keep burning until fuel is empty", "Compare radar and guidance; choose clear terrain"), 2,
                "An alarm does not prove every instrument failed. Cross-check valid readings and preserve a fuel reserve."),
        EMERGENCY("Essential loads", "Command-module power is falling. The crew still needs breathable air.",
                "Choose what the backup system must preserve first.",
                List.of("Move essential life support to backup power", "Power the science lab before life support", "Open the cabin to remove the warning"), 0,
                "Protect essential life support and preserve return energy. A power fault is not solved by opening the cabin."),
        FILTER("Oxygen is not CO2 removal", "Stored oxygen: AVAILABLE. CO2 trend: RISING. Filter connection: FAULTY.",
                "Which physical change addresses the dangerous trend?",
                List.of("Add oxygen without changing the filter", "Warm the laboratory", "Seal the adapter and restore filtered airflow"), 2,
                "Adding oxygen does not remove CO2. A sealed adapter must direct cabin air through the working filter."),
        SURFACE("Before opening the airlock", "Landing: COMPLETE. Suit seals and communications: NOT YET VERIFIED.",
                "Which checks support a safe surface excursion?",
                List.of("Leave because touchdown succeeded", "Verify suits, communications and the return reserve", "Use the entire reserve for instruments"), 1,
                "Touchdown does not confirm EVA readiness. Verify protective equipment, communication and a reserved route back first."),
        RESTART("Heat and return reserve", "Cabin: COLD. Condensation: PRESENT. Return energy: LIMITED.",
                "Choose the restart plan that keeps the return possible.",
                List.of("Warm essential circuits and inspect before restart", "Heat every room before checking the reserve", "Switch all wet circuits on at once"), 0,
                "Prioritize essential heating and inspect circuits. Warming every load can consume the return reserve.");

        public final String title, observation, prompt, explanation;
        public final List<String> choices;
        private final int answer;

        Case(final String title, final String observation, final String prompt, final List<String> choices,
             final int answer, final String explanation) {
            this.title = title;
            this.observation = observation;
            this.prompt = prompt;
            this.choices = choices;
            this.answer = answer;
            this.explanation = explanation;
        }
    }

    private final Case situation;
    private final int supportedChoice;
    private final String explanation;
    private boolean solved;
    private String feedback = "Compare both readings before committing the crew's next action.";

    public AppliedLearning(final Case situation) {
        this(situation, 0, 0);
    }

    public AppliedLearning(final Case situation, final int first, final int second) {
        this.situation = situation;
        if (situation == Case.DUST) {
            supportedChoice = first > second ? 0 : first < second ? 1 : 2;
            explanation = "Supply " + first + " minus load " + second + " gives " + (first - (long) second)
                    + " model units. " + (first > second
                    ? "That surplus can charge available storage; a full battery cannot store more."
                    : first < second ? "The battery supplies the deficit and drains." : "There is no net transfer, so charge stays steady.")
                    + " A future dust forecast does not change what these current readings show.";
            feedback = "Compare current supply and load before predicting the battery trend.";
        } else {
            supportedChoice = situation.answer;
            explanation = situation.explanation;
        }
    }

    public boolean answer(final int choice) {
        if (choice < 0 || choice >= situation.choices.size() || solved) return false;
        solved = choice == supportedChoice;
        feedback = (solved ? "Prediction supported. " : "That action does not address these readings. ") + explanation;
        return true;
    }

    public boolean solved() {
        return solved;
    }

    public String feedback() {
        return feedback;
    }
}
