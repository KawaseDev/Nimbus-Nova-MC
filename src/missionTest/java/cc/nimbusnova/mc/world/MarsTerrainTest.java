package cc.nimbusnova.mc.world;

public final class MarsTerrainTest {
    private MarsTerrainTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        for (int x = -48; x <= 48; x++) {
            for (int z = -48; z <= 48; z++)
                assert MarsTerrain.surfaceHeight(x, z) == 64 : "The whole protected outpost pad must remain flat";
        }

        int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
        for (int x = -1024; x <= 1024; x += 7) {
            for (int z = -1024; z <= 1024; z += 7) {
                final int height = MarsTerrain.surfaceHeight(x, z);
                assert height >= 32 && height <= 145 : "Terrain escaped its vertical bounds";
                assert height == MarsTerrain.surfaceHeight(x, z) : "Terrain must be deterministic across chunk generation";
                assert Math.abs(height - MarsTerrain.surfaceHeight(x + 1, z)) <= 4 : "Terrain has a discontinuity at an X boundary";
                assert Math.abs(height - MarsTerrain.surfaceHeight(x, z + 1)) <= 4 : "Terrain has a discontinuity at a Z boundary";
                minimum = Math.min(minimum, height);
                maximum = Math.max(maximum, height);
            }
        }

        assert maximum - minimum >= 40 : "Distant Mars terrain must have substantial relief";
        assert MarsTerrain.surfaceHeight(-1, 0) == MarsTerrain.surfaceHeight(0, -1) : "The protected pad must span negative chunks";
        System.out.println("Mars terrain check passed: protected pad, deterministic hills/craters, continuity and " + (maximum - minimum) + " blocks of relief.");
    }
}
