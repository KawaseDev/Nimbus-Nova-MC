package cc.nimbusnova.mc.world;

public final class MarsTerrain {
    private MarsTerrain() {
        /* w */
    }

    public static int surfaceHeight(final int x, final int z) {
        final double distance = Math.hypot(x, z);
        if (distance <= 80) return 64;

        final double blend = Math.clamp((distance - 80) / 48, 0, 1);
        final double smoothBlend = blend * blend * (3 - 2 * blend);
        final double ridge = Math.pow(1 - Math.abs(noise(x / 92.0, z / 92.0)), 3);
        final double height = 65 + noise(x / 180.0, z / 180.0) * 28 + ridge * 34
                + noise(x / 31.0, z / 31.0) * 5 + crater(x, z);
        return (int) Math.round(Math.clamp(64 + (height - 64) * smoothBlend, 32, 145));
    }

    private static double crater(final int x, final int z) {
        final int cellX = Math.floorDiv(x, 256), cellZ = Math.floorDiv(z, 256);
        final double centerX = cellX * 256.0 + 128 + hash(cellX, cellZ, 17) * 35;
        final double centerZ = cellZ * 256.0 + 128 + hash(cellX, cellZ, 29) * 35;
        final double radius = 48 + hash(cellX, cellZ, 43) * 15;
        final double relative = Math.hypot(x - centerX, z - centerZ) / radius;
        if (relative > 1.35) return 0;

        final double bowl = relative < 1 ? -20 * Math.pow(1 - relative * relative, 2) : 0;
        final double rim = 11 * Math.exp(-Math.pow((relative - 1) / 0.13, 2));
        return bowl + rim;
    }

    private static double noise(final double x, final double z) {
        final int gridX = (int) Math.floor(x), gridZ = (int) Math.floor(z);
        final double fractionX = x - gridX, fractionZ = z - gridZ;
        final double smoothX = fractionX * fractionX * (3 - 2 * fractionX);
        final double smoothZ = fractionZ * fractionZ * (3 - 2 * fractionZ);
        final double lower = hash(gridX, gridZ, 7) * (1 - smoothX) + hash(gridX + 1, gridZ, 7) * smoothX;
        final double upper = hash(gridX, gridZ + 1, 7) * (1 - smoothX) + hash(gridX + 1, gridZ + 1, 7) * smoothX;
        return lower * (1 - smoothZ) + upper * smoothZ;
    }

    private static double hash(final int x, final int z, final int salt) {
        long value = x * 341873128712L + z * 132897987541L + salt * 42317861L;
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return ((value ^ value >>> 31) >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
