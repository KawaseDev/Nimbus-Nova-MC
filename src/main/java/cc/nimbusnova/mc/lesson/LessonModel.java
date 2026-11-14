package cc.nimbusnova.mc.lesson;

/** Deliberately authored classroom indices, not measured pressures or radiation doses. */
public final class LessonModel {
    public int pressure = 100;
    public boolean isolated;
    public int layers;
    public int revision;
    public int target;

    public boolean adjustPressure(final int direction) {
        if (!isolated || direction != -1 && direction != 1) return false;
        final int next = Math.clamp(pressure + direction * 20, 0, 100);
        if (next == pressure) return false;
        pressure = next;
        return true;
    }

    public boolean canCycle() {
        return isolated && pressure == target;
    }

    public void changeLayer(final int slot) {
        if (slot < 0 || slot > 2) return;
        final int shift = slot * 2;
        layers = layers & ~(3 << shift) | ((layers >> shift & 3) + 1 & 3) << shift;
    }

    public static int detectorIndex(final int layers) {
        int index = 100;
        for (int slot = 0; slot < 3; slot++)
            index = index * switch (layers >> slot * 2 & 3) { case 1 -> 90; case 2 -> 70; case 3 -> 55; default -> 100; } / 100;
        return index;
    }

    public static int mass(final int layers) {
        int mass = 0;
        for (int slot = 0; slot < 3; slot++)
            mass += switch (layers >> slot * 2 & 3) { case 1 -> 1; case 2 -> 3; case 3 -> 5; default -> 0; };
        return mass;
    }
}
