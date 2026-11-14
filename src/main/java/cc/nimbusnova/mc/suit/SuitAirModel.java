package cc.nimbusnova.mc.suit;

/** Authored classroom reserves and zones, not a physiological or pressure calculation. */
public final class SuitAirModel {
    public double air = 100;

    public void tick(final boolean pressurized, final boolean sealedSuit, final boolean frozen) {
        if (frozen) return;

        air = Math.clamp(air + (pressurized ? 10 : sealedSuit ? -0.5 : -5), 0, 100);
    }

    public static boolean insideDome(final int x, final int y, final int z) {
        return y >= 66 && y <= 92 && (x - 2.0) * (x - 2.0) / (36 * 36)
                + (double) z * z / (36 * 36) + (y - 65.0) * (y - 65.0) / (28 * 28) < 1;
    }

    public static boolean insideRoom(final int x, final int y, final int z) {
        if (y < 66 || y > 69) return false;
        return x >= -9 && x <= 9 && z >= -7 && z <= 7
                || x >= -1 && x <= 1 && z >= -11 && z <= -8
                || x >= -7 && x <= 7 && z >= 21 && z <= 31
                || x >= 21 && x <= 33 && z >= -7 && z <= 7;
    }
}
