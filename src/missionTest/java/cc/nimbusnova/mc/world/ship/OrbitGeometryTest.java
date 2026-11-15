package cc.nimbusnova.mc.world.ship;

import java.util.Arrays;

public final class OrbitGeometryTest {
    private OrbitGeometryTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final OrbitGeometry.Mesh planet = OrbitGeometry.planet(), stars = OrbitGeometry.stars();
        assert planet.colors().length == 4608 && stars.colors().length == 2400;
        for (final OrbitGeometry.Mesh mesh : new OrbitGeometry.Mesh[] { planet, stars }) {
            assert mesh.positions().length == mesh.colors().length * 3;
            assert mesh.colors().length % 4 == 0;
            for (int index = 0; index < mesh.colors().length; index++) {
                final float x = mesh.positions()[index * 3], y = mesh.positions()[index * 3 + 1], z = mesh.positions()[index * 3 + 2];
                final double radius = Math.sqrt(x * x + y * y + z * z);
                assert Double.isFinite(radius);
                assert mesh == planet ? Math.abs(radius - 1) < 0.00001 : radius >= 180 && radius < 180.01;
                assert (mesh.colors()[index] >>> 24) == 255;
            }
        }
        int outward = 0;
        for (int quad = 0; quad < planet.colors().length; quad += 4) {
            final int offset = quad * 3;
            final double edgeX = planet.positions()[offset + 3] - planet.positions()[offset],
                    edgeY = planet.positions()[offset + 4] - planet.positions()[offset + 1],
                    edgeZ = planet.positions()[offset + 5] - planet.positions()[offset + 2];
            final double nextX = planet.positions()[offset + 6] - planet.positions()[offset],
                    nextY = planet.positions()[offset + 7] - planet.positions()[offset + 1],
                    nextZ = planet.positions()[offset + 8] - planet.positions()[offset + 2];
            final double facing = (edgeY * nextZ - edgeZ * nextY) * planet.positions()[offset]
                    + (edgeZ * nextX - edgeX * nextZ) * planet.positions()[offset + 1]
                    + (edgeX * nextY - edgeY * nextX) * planet.positions()[offset + 2];
            assert facing >= -0.000001 : "Planet quad faces inward and would disappear with backface culling";
            if (facing > 0.000001) outward++;
        }
        assert outward > 1000;
        assert Arrays.equals(stars.positions(), OrbitGeometry.stars().positions()) : "Backdrop changed between frames";
        assert Arrays.stream(planet.colors()).distinct().count() > 100 : "Planet lost its ocean/land/light variation";
        System.out.println("Orbit geometry: bounded deterministic star mesh and shaded planet passed.");
    }
}
