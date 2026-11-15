package cc.nimbusnova.mc.world.ship;

import java.util.Random;

/** A stylized backdrop, not a geographic Earth dataset or navigation instrument. */
public final class OrbitGeometry {
    private OrbitGeometry() {
        /* w */
    }

    public static Mesh planet() {
        final float[] positions = new float[24 * 48 * 4 * 3];
        final int[] colors = new int[positions.length / 3];
        int vertex = 0;
        for (int latitude = 0; latitude < 24; latitude++) {
            for (int longitude = 0; longitude < 48; longitude++) {
                for (final int[] corner : new int[][] { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 } }) {
                    final double angle = (latitude + corner[1]) * Math.PI / 24 - Math.PI / 2;
                    final double bearing = (longitude + corner[0]) * Math.PI * 2 / 48;
                    final float x = (float) (Math.cos(angle) * Math.cos(bearing)), y = (float) Math.sin(angle),
                            z = (float) (Math.cos(angle) * Math.sin(bearing));
                    positions[vertex * 3] = x;
                    positions[vertex * 3 + 1] = y;
                    positions[vertex * 3 + 2] = z;
                    final double continents = Math.sin(bearing * 3 + Math.sin(angle * 2)) + Math.cos(bearing * 5 - angle * 4) * 0.55;
                    final boolean land = continents > 0.55 && Math.abs(y) < 0.92;
                    final boolean cloud = Math.sin(bearing * 11 + angle * 17) + Math.cos(bearing * 7 - angle * 13) > 1.55;
                    final boolean ice = Math.abs(y) > 0.92;
                    final double light = 0.18 + 0.82 * Math.max(0, x * -0.45 + y * 0.35 - z * 0.82);
                    final int red = (int) ((ice || cloud ? 222 : land ? 46 : 19) * light);
                    final int green = (int) ((ice || cloud ? 232 : land ? 123 : 77) * light);
                    final int blue = (int) ((ice || cloud ? 245 : land ? 79 : 166) * light);
                    colors[vertex++] = 0xff000000 | red << 16 | green << 8 | blue;
                }
            }
        }
        return new Mesh(positions, colors);
    }

    public static Mesh stars() {
        final Random random = new Random(20261115);
        final float[] positions = new float[600 * 4 * 3];
        final int[] colors = new int[600 * 4];
        for (int star = 0; star < 600; star++) {
            final double vertical = random.nextDouble() * 2 - 1, bearing = random.nextDouble() * Math.PI * 2;
            final double radial = Math.sqrt(1 - vertical * vertical);
            final double x = radial * Math.cos(bearing), z = radial * Math.sin(bearing);
            final double size = 0.12 + random.nextDouble() * 0.3;
            final double horizontalX = -Math.sin(bearing) * size, horizontalZ = Math.cos(bearing) * size;
            final double verticalX = -vertical * Math.cos(bearing) * size, verticalY = radial * size,
                    verticalZ = -vertical * Math.sin(bearing) * size;
            final int color = random.nextInt(5) == 0 ? 0xffffe6bf : 0xffdceaff;
            int corner = 0;
            for (final int[] sign : new int[][] { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } }) {
                final int vertex = star * 4 + corner++;
                positions[vertex * 3] = (float) (x * 180 + sign[0] * horizontalX + sign[1] * verticalX);
                positions[vertex * 3 + 1] = (float) (vertical * 180 + sign[1] * verticalY);
                positions[vertex * 3 + 2] = (float) (z * 180 + sign[0] * horizontalZ + sign[1] * verticalZ);
                colors[vertex] = color;
            }
        }
        return new Mesh(positions, colors);
    }

    public record Mesh(float[] positions, int[] colors) {
        /* w */
    }
}
