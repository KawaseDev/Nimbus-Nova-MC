package cc.nimbusnova.mc.world;

import java.util.ArrayDeque;
import java.util.HashSet;

public final class DomeGeometryTest {
    private DomeGeometryTest() {
        /* w */
    }

    public static void main(final String[] ignoredArguments) {
        final var occupied = new HashSet<String>();
        final var disconnectedStructure = new HashSet<Cell>();
        int glass = 0, structure = 0;
        for (final var panel : OutpostDome.panels()) {
            if (!occupied.add(panel.x() + "," + panel.y() + "," + panel.z()))
                throw new AssertionError("Duplicate shell voxel");
            assert panel.y() >= 0 && panel.y() <= OutpostDome.HEIGHT;
            assert Math.abs(panel.x() - 2) <= OutpostDome.RADIUS && Math.abs(panel.z()) <= OutpostDome.RADIUS;
            assert !OutpostDome.portal(panel.x(), panel.y(), panel.z()) : "An exit was blocked";
            if (panel.kind() == OutpostDome.Kind.GLASS)
                glass++;
            else {
                structure++;
                disconnectedStructure.add(new Cell(panel.x(), panel.y(), panel.z()));
            }
        }

        final var pending = new ArrayDeque<Cell>();
        pending.add(new Cell(-34, 0, 0));
        disconnectedStructure.remove(pending.peek());
        while (!pending.isEmpty()) {
            final Cell cell = pending.remove();
            for (final int[] offset : new int[][] { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } }) {
                final Cell neighbor = new Cell(cell.x + offset[0], cell.y + offset[1], cell.z + offset[2]);
                if (disconnectedStructure.remove(neighbor))
                    pending.add(neighbor);
            }
        }

        assert disconnectedStructure.isEmpty() : "Dome scaffolding has floating or corner-only connections";
        assert occupied.contains("2,28,0") : "Missing dome crown";
        assert occupied.contains("-34,0,0") && occupied.contains("38,0,0") : "Missing supported rim";
        assert glass > structure * 2 : "Structure overwhelms transparent shell";
        assert OutpostDome.portal(-33, 2, 0) : "Power cable / outdoor array exit";
        assert OutpostDome.portal(34, 2, -14) : "Sample route exit";
        assert OutpostDome.portal(28, 3, 25) : "Rover exit clearance";
        assert OutpostDome.portal(0, 3, -35) : "Landing pad exit";
        assert !OutpostDome.portal(28, 6, 25) : "Rover portal needs overhead structure";
        assert Math.hypot(40 - 2, -16) > OutpostDome.RADIUS : "Sample must remain outside the habitat";
        System.out.println("Dome geometry: transparent shell, continuous scaffolding, supported rim and all mission exits passed.");
    }

    private record Cell(int x, int y, int z) {
        /* w */
    }
}
