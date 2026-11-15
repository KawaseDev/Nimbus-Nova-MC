package cc.nimbusnova.mc.moon;

import java.util.EnumSet;

/** Initial server-side sequencing for the Earth–Moon mission. Resource values are game units. */
public final class MoonMission {
    public enum Variant { EXPLORATION, RESCUE }
    public enum Role { POWER, LIFE_SUPPORT, SUPPLIES, MAINTENANCE, PILOT }
    public enum Stage { PREPARATION, DOCKING, NAVIGATION, ORBIT, DESCENT, SURFACE, EMERGENCY, FILTER, RESTART, RETURN, COMPLETE }
    public enum Incident {
        DOCKING("Docking contact", "Contact is detected, but the mechanical lock is not confirmed.",
                "Contact and secure docking are separate events. Sensors can confirm the latch and power.",
                "An unsecured connection can make transfer unsafe. A retry also uses propellant.",
                "Which readings distinguish contact from a secure lock?",
                "Apollo 14 needed multiple attempts to dock. This spacecraft and its readings are simulated.",
                "https://www.nasa.gov/missions/apollo/apollo-14-mission-details/"),
        DESCENT("Guidance alarm", "A guidance alarm sounds while the planned landing area appears rocky.",
                "A computer alarm does not automatically mean every guidance instrument has failed.",
                "Invalid guidance or an inaccessible site can make descent unsafe and consume fuel.",
                "Which guidance and radar readings should the crew check before continuing?",
                "Apollo 11 experienced guidance alarms and avoided a boulder field. The displays and limits are simulated.",
                "https://www.nasa.gov/wp-content/uploads/static/history/alsj/a11/a11.summary.html"),
        EMERGENCY("Power and oxygen loss", "A tank is damaged and power from the command module falls.",
                "Stored oxygen, cabin pressure and electrical power are related systems but are different measurements.",
                "The crew must protect essential functions and preserve power for the return.",
                "Which functions must move to the lunar module, and what power must be saved?",
                "Apollo 13 used the lunar module as a lifeboat. This scenario and its resource values are simulated.",
                "https://www.nasa.gov/missions/apollo/apollo-13-mission-details/"),
        FILTER("Carbon dioxide rising", "The CO2 trend rises although oxygen remains available.",
                "Adding oxygen does not remove carbon dioxide. Cabin air needs to pass through a suitable CO2 filter.",
                "Without adequate removal, rising CO2 can endanger the crew; oxygen and pressure alone do not show this.",
                "How can the crew connect the available filter and verify that it works?",
                "Apollo 13 used an improvised adapter for incompatible cartridges. The repair in this game is simplified.",
                "https://www.nasa.gov/ochmo-tb-004-carbon-dioxide-2/"),
        SURFACE("Working on the Moon", "The lander has reached the surface.",
                "Lunar gravity is about one sixth of Earth's. The Moon's extremely thin exosphere cannot provide useful parachute braking.",
                "The crew still needs suits, communication and enough reserves to return to the lander.",
                "What should the crew check before leaving the lander, and why could a parachute not slow its descent?",
                "NASA describes the Moon's gravity and exosphere. This landing site and its resource values are simulated.",
                "https://science.nasa.gov/moon/facts/"),
        RESTART("Cold cabin and condensation", "The cabin has cooled and moisture has condensed before restart.",
                "Cooling can cause moisture to condense on surfaces. Essential heating also needs electrical power.",
                "Condensation can threaten equipment, while excessive heating can consume the re-entry reserve.",
                "Which systems need heat first, and what should be checked before powering circuits?",
                "Apollo 13 experienced a cold, damp cabin. Temperatures and energy costs here are simulated.",
                "https://www.nasa.gov/missions/apollo/apollo-13-mission-details/");

        public final String title, observation, explanation, hazard, question, evidence, source;
        Incident(String title, String observation, String explanation, String hazard, String question, String evidence, String source) {
            this.title = title; this.observation = observation; this.explanation = explanation;
            this.hazard = hazard; this.question = question; this.evidence = evidence; this.source = source;
        }
    }

    private final Variant variant;
    private final EnumSet<Role> confirmations = EnumSet.noneOf(Role.class);
    private Stage stage = Stage.PREPARATION;
    private Incident incident;
    private boolean acknowledged, paused, decisionMade;
    private int battery = 100, oxygen = 100, water = 100, fuel = 100, parts = 6;
    private String feedback = "Check every station and keep a return reserve.";

    public MoonMission(Variant variant) {
        this.variant = java.util.Objects.requireNonNull(variant);
    }

    public void confirm(Role role) {
        if (paused || stage == Stage.COMPLETE) throw new IllegalStateException("The mission cannot accept reports now.");
        confirmations.add(java.util.Objects.requireNonNull(role));
    }
    public void removeConfirmation(Role role) { confirmations.remove(role); }

    public void advance() {
        if (paused || stage == Stage.COMPLETE) throw new IllegalStateException("The mission cannot advance now.");
        if (!confirmations.containsAll(required(stage))) throw new IllegalStateException("Required crew reports are missing.");
        if (!decisionMade) throw new IllegalStateException("Complete the crew decision first.");
        int fuelCost = switch (stage) {
            case DOCKING, NAVIGATION -> 5;
            case ORBIT, RETURN -> 10;
            case DESCENT, SURFACE -> 15;
            default -> 0;
        };
        int batteryCost = switch (stage) { case EMERGENCY -> 20; case FILTER -> 5; case RESTART -> 10; default -> 0; };
        if (fuel < fuelCost || battery < batteryCost || stage == Stage.EMERGENCY && oxygen < 10
                || stage == Stage.FILTER && parts < 1 || stage == Stage.RESTART && water < 5)
            throw new IllegalStateException("Insufficient reserves for this stage. Ask Mission Control to restart.");
        confirmations.clear();
        decisionMade = false;
        // shortcut: fixed stage costs until choices and equipment simulation are integrated.
        switch (stage) {
            case PREPARATION -> { stage = Stage.DOCKING; announce(Incident.DOCKING); }
            case DOCKING -> { fuel -= 5; stage = Stage.NAVIGATION; }
            case NAVIGATION -> { fuel -= 5; stage = variant == Variant.EXPLORATION ? Stage.ORBIT : Stage.EMERGENCY; if (stage == Stage.EMERGENCY) announce(Incident.EMERGENCY); }
            case ORBIT -> { fuel -= 10; stage = Stage.DESCENT; announce(Incident.DESCENT); }
            case DESCENT -> { fuel -= 15; stage = Stage.SURFACE; announce(Incident.SURFACE); }
            case SURFACE -> { fuel -= 15; stage = Stage.RETURN; }
            case EMERGENCY -> { battery -= 20; oxygen -= 10; stage = Stage.FILTER; announce(Incident.FILTER); }
            case FILTER -> { parts--; battery -= 5; stage = Stage.RESTART; announce(Incident.RESTART); }
            case RESTART -> { battery -= 10; water -= 5; stage = Stage.RETURN; }
            case RETURN -> { fuel -= 10; stage = Stage.COMPLETE; }
            default -> throw new IllegalStateException("Mission already complete.");
        }
        feedback = stage == Stage.COMPLETE ? "Crew recovered. Review the decisions and remaining reserves." : decision(stage).prompt;
    }

    public record Decision(Role role, String prompt, String safe, String alternative, String safeResult, String alternativeResult) { }

    public void abortDescent() {
        if (variant != Variant.EXPLORATION || stage != Stage.DESCENT || paused || fuel < 5)
            throw new IllegalStateException("Descent cannot be aborted now.");
        fuel -= 5;
        confirmations.clear();
        decisionMade = false;
        stage = Stage.RETURN;
        feedback = "Descent canceled. Prepare the return to Earth.";
    }

    public static Decision decision(Stage stage) {
        return switch (stage) {
            case PREPARATION -> new Decision(Role.SUPPLIES, "Choose cargo while keeping a return reserve.", "Carry spare parts", "Extra instrument", "Spare parts secured.", "The instrument takes space; fewer repair parts remain.");
            case DOCKING -> new Decision(Role.MAINTENANCE, "Contact is detected, but the lock is unconfirmed.", "Check lock sensors", "Retry immediately", "Electrical and mechanical locks confirmed.", "The retry consumes fuel; contact alone does not prove a lock.");
            case NAVIGATION -> new Decision(Role.PILOT, "Compare the predicted route with the observed position.", "Validate correction", "Repeat correction", "The route and return reserve are confirmed.", "An unnecessary correction uses fuel.");
            case ORBIT -> new Decision(Role.PILOT, "Select the braking burn after checking speed and orientation.", "Planned burn", "Excessive burn", "The spacecraft enters the planned orbit.", "The excessive burn consumes extra fuel.");
            case DESCENT -> new Decision(Role.MAINTENANCE, "A guidance alarm appears near rocky terrain.", "Check guidance", "Ignore alarm", "Guidance and radar readings are valid; select the accessible site.", "The crew loses time checking an unverified continuation.");
            case SURFACE -> new Decision(Role.LIFE_SUPPORT, "Choose a sample after checking suits and return reserves.", "Nearby sample", "Distant sample", "The crew returns with a nearby sample.", "The longer trip uses more oxygen and water.");
            case EMERGENCY -> new Decision(Role.POWER, "A damaged tank and lost power cancel the landing.", "Use lunar module", "Keep all systems on", "Essential loads move to the lunar module; re-entry power is protected.", "Nonessential loads consume power without restoring the tank.");
            case FILTER -> new Decision(Role.LIFE_SUPPORT, "CO2 rises even though oxygen remains available.", "Adapt air filter", "Add oxygen", "Air flows through the adapted filter; the simulated CO2 trend falls.", "Adding oxygen does not remove CO2 and uses stored oxygen.");
            case RESTART -> new Decision(Role.POWER, "The cold cabin has condensation before restart.", "Prioritize heating", "Heat everything", "Essential heating and dry circuits are checked before restart.", "Heating everything uses energy needed for re-entry.");
            case RETURN -> new Decision(Role.PILOT, "Check the heat shield, parachutes and entry predictions.", "Approved corridor", "Steep entry", "The approved profile is selected and recovery is notified.", "The steep prediction exceeds the simulated vehicle limit; review it.");
            case COMPLETE -> throw new IllegalStateException("The mission is complete.");
        };
    }

    public void choose(Role role, boolean safe) {
        if (paused || stage == Stage.COMPLETE || decisionMade) throw new IllegalStateException("No decision is available.");
        Decision choice = decision(stage);
        if (role != choice.role) throw new IllegalStateException("This decision belongs to " + choice.role + ".");
        if (safe) {
            decisionMade = true;
            feedback = choice.safeResult;
            return;
        }
        switch (stage) {
            case PREPARATION -> { if (parts > 0) parts--; decisionMade = true; }
            case DOCKING, NAVIGATION, ORBIT -> fuel = Math.max(0, fuel - 3);
            case DESCENT -> battery = Math.max(0, battery - 2);
            case SURFACE -> { oxygen = Math.max(0, oxygen - 5); water = Math.max(0, water - 3); decisionMade = true; }
            case EMERGENCY, RESTART -> battery = Math.max(0, battery - 5);
            case FILTER -> oxygen = Math.max(0, oxygen - 5);
            case RETURN -> fuel = Math.max(0, fuel - 2);
            default -> throw new IllegalStateException("No decision is available.");
        }
        feedback = choice.alternativeResult;
    }

    private void announce(Incident next) { incident = next; acknowledged = false; paused = true; }
    public void pause() {
        if (paused || stage == Stage.COMPLETE) throw new IllegalStateException("Cannot pause now.");
        paused = true;
    }
    public void acknowledge() {
        if (!paused || incident == null) throw new IllegalStateException("There is no incident to confirm.");
        acknowledged = true;
    }
    public void resume() {
        if (!paused || incident != null && !acknowledged) throw new IllegalStateException("Confirm the incident before resuming.");
        paused = false;
        incident = null;
        acknowledged = false;
    }
    public static EnumSet<Role> required(Stage stage) {
        return switch (stage) {
            case PREPARATION, RETURN -> EnumSet.allOf(Role.class);
            case DOCKING, DESCENT -> EnumSet.of(Role.PILOT, Role.MAINTENANCE, Role.POWER);
            case NAVIGATION, ORBIT -> EnumSet.of(Role.PILOT, Role.POWER);
            case SURFACE -> EnumSet.of(Role.PILOT, Role.LIFE_SUPPORT, Role.SUPPLIES);
            case EMERGENCY -> EnumSet.of(Role.POWER, Role.LIFE_SUPPORT, Role.PILOT);
            case FILTER -> EnumSet.of(Role.LIFE_SUPPORT, Role.MAINTENANCE, Role.SUPPLIES);
            case RESTART -> EnumSet.of(Role.POWER, Role.LIFE_SUPPORT, Role.MAINTENANCE);
            case COMPLETE -> EnumSet.noneOf(Role.class);
        };
    }
    public Variant variant() { return variant; }
    public Stage stage() { return stage; }
    public Incident incident() { return incident; }
    public boolean paused() { return paused; }
    public boolean acknowledged() { return acknowledged; }
    public boolean decisionMade() { return decisionMade; }
    public String feedback() { return feedback; }
    public EnumSet<Role> confirmations() { return confirmations.clone(); }
    public int battery() { return battery; }
    public int oxygen() { return oxygen; }
    public int water() { return water; }
    public int fuel() { return fuel; }
    public int parts() { return parts; }
}
