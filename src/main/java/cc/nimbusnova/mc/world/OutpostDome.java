package cc.nimbusnova.mc.world;

import cc.nimbusnova.mc.content.ScifiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Fictional architecture only: radiation shelter checks remain in MarsTrainingMap. */
public final class OutpostDome {
    public static final int HEIGHT = 28;
    public static final int RADIUS = 36;
    private static final List<Panel> PANELS = createPanels();

    private OutpostDome() {
        /* w */
    }

    public static List<Panel> panels() {
        return PANELS;
    }

    public static boolean portal(final int x, final int y, final int z) {
        if (y < 1 || y > 5) return false;
        return (Math.abs(x) <= 4 && z < -30)
                || (x < -29 && Math.abs(z) <= 3)
                || (x > 28 && Math.abs(z + 14) <= 3)
                || (x >= 22 && x <= 33 && z >= 20 && z <= 29);
    }

    private static boolean inside(final int x, final int y, final int z) {
        return (x - 2.0) * (x - 2.0) / (RADIUS * RADIUS)
                + (double) z * z / (RADIUS * RADIUS) + (double) y * y / (HEIGHT * HEIGHT) <= 1;
    }

    private static List<Panel> createPanels() {
        final List<Panel> panels = new ArrayList<>();
        for (int x = 2 - RADIUS; x <= 2 + RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                for (int y = 0; y <= HEIGHT; y++) {
                    if (!inside(x, y, z) || portal(x, y, z)) continue;
                    if (inside(x - 1, y, z) && inside(x + 1, y, z) && inside(x, y + 1, z)
                            && inside(x, y, z - 1) && inside(x, y, z + 1)) continue;

                    final double angle = Math.atan2(z, x - 2.0);
                    final double meridianDistance = Math.abs(Math.sin(angle * 6)) * Math.hypot(x - 2, z) / 6;
                    final Kind kind;
                    if (y == 0)
                        kind = Math.floorMod(x + z, 6) == 0 ? Kind.LIGHT : Kind.RIM;
                    else if (meridianDistance < 0.42 || y == 15 || y == 24)
                        kind = y % 8 == 0 ? Kind.LIGHT : Kind.RIB;
                    else
                        kind = Kind.GLASS;

                    panels.add(new Panel(x, y, z, kind));
                }
            }
        }

        return List.copyOf(panels);
    }

    public static BlockPos findConflict(final ServerLevel level) {
        for (final Panel panel : PANELS) {
            final BlockPos position = new BlockPos(panel.x, 65 + panel.y, panel.z);
            final var state = level.getBlockState(position);
            if (state.isAir() || state.is(ScifiBlocks.DOME_GLASS) || state.is(ScifiBlocks.SUPPORT_BEAM)
                    || state.is(ScifiBlocks.FLOOR_LIGHT) || state.is(ScifiBlocks.REINFORCED_HULL)) continue;
            return position;
        }

        return null;
    }

    public static void build(final ServerLevel level) {
        for (final Panel panel : PANELS) {
            final Block material = switch (panel.kind) {
                case GLASS -> ScifiBlocks.DOME_GLASS;
                case RIB -> ScifiBlocks.SUPPORT_BEAM;
                case RIM -> ScifiBlocks.REINFORCED_HULL;
                case LIGHT -> ScifiBlocks.FLOOR_LIGHT;
            };
            level.setBlock(new BlockPos(panel.x, 65 + panel.y, panel.z), material.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public enum Kind {
        GLASS, RIB, RIM, LIGHT
    }

    public record Panel(int x, int y, int z, Kind kind) {
        /* w */
    }
}
