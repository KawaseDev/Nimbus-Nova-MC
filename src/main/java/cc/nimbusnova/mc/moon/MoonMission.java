package cc.nimbusnova.mc.moon;

import java.util.EnumSet;

/** Initial server-side sequencing for the Earth–Moon mission. Resource values are game units. */
public final class MoonMission {
    public enum Variant { EXPLORATION, RESCUE }
    public enum Role { POWER, LIFE_SUPPORT, SUPPLIES, MAINTENANCE, PILOT }
    public enum Stage { PREPARATION, DOCKING, NAVIGATION, ORBIT, DESCENT, SURFACE, EMERGENCY, FILTER, RESTART, RETURN, COMPLETE }
    public enum Incident { DOCKING, DESCENT, EMERGENCY, FILTER, RESTART }

    private final Variant variant;
    private final EnumSet<Role> confirmations = EnumSet.noneOf(Role.class);
    private Stage stage = Stage.PREPARATION;
    private Incident incident;
    private boolean acknowledged, paused;
    private int battery = 100, oxygen = 100, water = 100, fuel = 100, parts = 6;

    public MoonMission(Variant variant) {
        this.variant = java.util.Objects.requireNonNull(variant);
    }

    public void confirm(Role role) {
        if (paused || stage == Stage.COMPLETE) throw new IllegalStateException("The mission cannot accept reports now.");
        confirmations.add(java.util.Objects.requireNonNull(role));
    }

    public void advance() {
        if (paused || stage == Stage.COMPLETE) throw new IllegalStateException("The mission cannot advance now.");
        if (!confirmations.containsAll(required(stage))) throw new IllegalStateException("Required crew reports are missing.");
        confirmations.clear();
        // shortcut: fixed stage costs until choices and equipment simulation are integrated.
        switch (stage) {
            case PREPARATION -> { stage = Stage.DOCKING; announce(Incident.DOCKING); }
            case DOCKING -> { fuel -= 5; stage = Stage.NAVIGATION; }
            case NAVIGATION -> { fuel -= 5; stage = variant == Variant.EXPLORATION ? Stage.ORBIT : Stage.EMERGENCY; if (stage == Stage.EMERGENCY) announce(Incident.EMERGENCY); }
            case ORBIT -> { fuel -= 10; stage = Stage.DESCENT; announce(Incident.DESCENT); }
            case DESCENT -> { fuel -= 15; stage = Stage.SURFACE; }
            case SURFACE -> { fuel -= 15; stage = Stage.RETURN; }
            case EMERGENCY -> { battery -= 20; oxygen -= 10; stage = Stage.FILTER; announce(Incident.FILTER); }
            case FILTER -> { parts--; battery -= 5; stage = Stage.RESTART; announce(Incident.RESTART); }
            case RESTART -> { battery -= 10; water -= 5; stage = Stage.RETURN; }
            case RETURN -> { fuel -= 10; stage = Stage.COMPLETE; }
            default -> throw new IllegalStateException("Mission already complete.");
        }
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
    public EnumSet<Role> confirmations() { return confirmations.clone(); }
    public int battery() { return battery; }
    public int oxygen() { return oxygen; }
    public int water() { return water; }
    public int fuel() { return fuel; }
    public int parts() { return parts; }
}
