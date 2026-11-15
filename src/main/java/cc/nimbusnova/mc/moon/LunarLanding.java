package cc.nimbusnova.mc.moon;

/** Guided, fictional lunar approach. Velocities and fuel are authored teaching units. */
public final class LunarLanding {
    public enum Burn { INSUFFICIENT, PLANNED, EXCESSIVE }
    public enum Site { PRIMARY, ALTERNATE }
    public enum Phase { ORBIT_APPROACH, DESCENT, SURFACE, RENDEZVOUS, RETURN, ABORTED }

    private Phase phase = Phase.ORBIT_APPROACH;
    private int orbitalFuel = 20, descentFuel = 30, ascentFuel = 15;
    private int verticalSpeed = 3, lateralSpeed = 2;
    private boolean guidanceChecked, siteSelected, suitsChecked, sampleCollected;

    /** Predictions are reversible; only the approved burn changes the vehicle. */
    public String preview(Burn burn) {
        return switch (burn) {
            case INSUFFICIENT -> "The spacecraft would pass the Moon without entering the planned orbit.";
            case PLANNED -> "The spacecraft would enter the planned lunar orbit.";
            case EXCESSIVE -> "The predicted orbit would approach too close to the surface.";
        };
    }

    public void capture(Burn burn) {
        require(phase == Phase.ORBIT_APPROACH, "Orbital capture is not available.");
        require(burn == Burn.PLANNED, "Review the prediction before approving the burn.");
        orbitalFuel -= 10;
        phase = Phase.DESCENT;
    }

    public void checkGuidance(boolean radarValid, boolean guidanceValid) {
        require(phase == Phase.DESCENT, "Guidance checks are only available during descent.");
        require(radarValid && guidanceValid, "Check the radar and guidance before continuing.");
        guidanceChecked = true;
    }

    public void chooseSite(Site site) {
        require(phase == Phase.DESCENT && guidanceChecked, "Check the guidance before selecting a site.");
        require(site == Site.ALTERNATE, "The primary site contains boulders; choose an accessible site.");
        siteSelected = true;
    }

    /** Lateral braking tilts thrust away from vertical; vertical speed increases in this model. */
    public void brakeLateral() {
        requireDescent();
        require(lateralSpeed > 0 && descentFuel > 0, "No lateral correction is available.");
        lateralSpeed--;
        verticalSpeed++;
        descentFuel--;
    }

    public void brakeVertical() {
        requireDescent();
        require(verticalSpeed > 0 && descentFuel > 0, "No vertical correction is available.");
        verticalSpeed--;
        descentFuel--;
    }

    public void land() {
        requireDescent();
        require(verticalSpeed <= 1 && lateralSpeed <= 1, "Both landing speeds must be inside the model limits.");
        phase = Phase.SURFACE;
    }

    public void checkSuits(boolean communications, boolean reserves) {
        require(phase == Phase.SURFACE, "Suit checks require a stable landing.");
        require(communications && reserves, "Confirm communications and reserves before opening the hatch.");
        suitsChecked = true;
    }

    public void collectSample() {
        require(phase == Phase.SURFACE && suitsChecked, "Check suits and communications before collecting a sample.");
        require(!sampleCollected, "The sample has already been collected.");
        sampleCollected = true;
    }

    public void launch() {
        require(phase == Phase.SURFACE && suitsChecked, "The crew and lander must be ready before ascent.");
        require(ascentFuel >= 10, "The ascent reserve is insufficient.");
        ascentFuel -= 10;
        phase = Phase.RENDEZVOUS;
    }

    public void rendezvous(boolean alignment, boolean mechanicalLock) {
        require(phase == Phase.RENDEZVOUS, "Rendezvous is not available.");
        require(alignment && mechanicalLock, "Check alignment and the mechanical lock before transfer.");
        phase = Phase.RETURN;
    }

    public void abort() {
        require(phase == Phase.ORBIT_APPROACH || phase == Phase.DESCENT, "This stage cannot be aborted to orbit.");
        phase = Phase.ABORTED;
    }

    private void requireDescent() {
        require(phase == Phase.DESCENT && guidanceChecked && siteSelected, "Verify guidance and select a safe site first.");
    }
    private static void require(boolean allowed, String message) { if (!allowed) throw new IllegalStateException(message); }
    public Phase phase() { return phase; }
    public int orbitalFuel() { return orbitalFuel; }
    public int descentFuel() { return descentFuel; }
    public int ascentFuel() { return ascentFuel; }
    public int verticalSpeed() { return verticalSpeed; }
    public int lateralSpeed() { return lateralSpeed; }
    public boolean guidanceChecked() { return guidanceChecked; }
    public boolean siteSelected() { return siteSelected; }
    public boolean suitsChecked() { return suitsChecked; }
    public boolean sampleCollected() { return sampleCollected; }
}
