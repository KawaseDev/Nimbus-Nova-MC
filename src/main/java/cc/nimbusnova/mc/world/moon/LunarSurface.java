package cc.nimbusnova.mc.world.moon;

import cc.nimbusnova.mc.content.ScifiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authored training site in a dedicated dimension; it is not a reconstruction of a real landing site. */
public final class LunarSurface {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.parse("nimbus_nova:lunar_surface"));
    public static final double GRAVITY_RATIO = 1.0 / 6.0;
    public static final BlockPos ARRIVAL = new BlockPos(0, 63, -7);
    public static final BlockPos SAMPLE = new BlockPos(10, 63, -8);
    private static final int LEGACY_RADIUS = 28, RADIUS = 46;
    private static final int[][] CRATERS = { { -14, 10, 6, 3 }, { 22, -22, 8, 4 }, { -24, -18, 5, 2 }, { 20, 14, 4, 2 }, { 15, -13, 3, 1 } };

    private LunarSurface() {
        /* w */
    }

    public static void create(final MinecraftServer server) throws IOException {
        final ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) throw new IOException("The lunar surface dimension is unavailable. Restart with the updated mod.");

        final Path legacy = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/lunar-surface-v1.txt");
        final Path marker = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/lunar-surface-v2.txt");
        if (Files.exists(marker)) {
            if (!Files.readString(marker).equals("complete"))
                throw new IOException("Lunar surface construction was interrupted. Restore a backup or use a fresh world.");
            return;
        }

        // A finished first-generation site is replaced block by block; anything a crew built there stays where it is.
        final boolean upgrade = Files.exists(legacy) && Files.readString(legacy).equals("complete");
        final Map<BlockPos, BlockState> plan = plan();
        if (upgrade) {
            for (final var placement : legacyPlan().entrySet()) {
                if (level.getBlockState(placement.getKey()).is(placement.getValue().getBlock()))
                    level.setBlock(placement.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        } else {
            for (final BlockPos position : plan.keySet())
                if (!level.getBlockState(position).isAir())
                    throw new IOException("Lunar site contains existing blocks at " + position.toShortString() + "; they were preserved.");
        }

        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "building", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        for (final var placement : plan.entrySet()) {
            if (upgrade && !level.getBlockState(placement.getKey()).isAir()) continue;
            level.setBlock(placement.getKey(), placement.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        Files.writeString(marker, "complete", StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static int hash(final int x, final int z, final int salt) {
        int value = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        value ^= value >>> 13;
        value *= 0x5bd1e995;
        return (value ^ value >>> 15) & 0x7fffffff;
    }

    /** Regolith height: a level landing plain, bowl craters with raised rims, and highlands toward the boundary. */
    public static int surfaceHeight(final int x, final int z) {
        final int outside = Math.max(Math.max(Math.abs(x - 4) - 14, Math.abs(z + 5) - 11), 0);
        final double radius = Math.hypot(x, z);
        double relief = (Math.sin(x * 0.23) * 1.2 + Math.cos(z * 0.19) * 1.2 + Math.sin((x - z) * 0.09) * 1.5) * Math.min(1, outside / 6.0);
        if (radius > 32)
            relief += Math.pow(radius - 32, 1.5) * 0.38;

        for (final int[] crater : CRATERS) {
            final double distance = Math.hypot(x - crater[0], z - crater[1]);
            if (distance < crater[2])
                relief -= (1 - distance * distance / (crater[2] * crater[2])) * crater[3] + 0.5;
            else if (distance < crater[2] + 1.5)
                relief += 1;
        }

        return Math.clamp(62 + (int) Math.round(relief), 56, 88);
    }

    private static Map<BlockPos, BlockState> plan() {
        final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        final Block[] regolith = { Blocks.TUFF, Blocks.TUFF, Blocks.TUFF, Blocks.ANDESITE, Blocks.STONE, Blocks.CLAY };
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                final int height = surfaceHeight(x, z);
                Block surface = regolith[hash(x, z, 1) % regolith.length];
                for (final int[] crater : CRATERS) {
                    final double distance = Math.hypot(x - crater[0], z - crater[1]);
                    if (distance < crater[2] - 1)
                        surface = hash(x, z, 3) % 3 == 0 ? Blocks.SMOOTH_BASALT : Blocks.DEEPSLATE;
                    else if (distance < crater[2] + 1.5)
                        surface = hash(x, z, 4) % 2 == 0 ? Blocks.CALCITE : Blocks.DIORITE;
                }

                for (int y = Math.max(55, height - 4); y < height; y++)
                    plan.put(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());

                plan.put(new BlockPos(x, height, z), surface.defaultBlockState());
                if (Math.abs(x) == RADIUS || Math.abs(z) == RADIUS) {
                    for (int y = height + 1; y <= 96; y++)
                        plan.put(new BlockPos(x, y, z), Blocks.BARRIER.defaultBlockState());
                } else if (Math.max(Math.abs(x - 4), Math.abs(z + 5)) > 16 && hash(x, z, 7) % 53 == 0) {
                    plan.put(new BlockPos(x, height + 1, z), Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                    if (hash(x, z, 9) % 3 == 0)
                        plan.put(new BlockPos(x, height + 2, z), Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
            }
        }

        lander(plan);
        for (int step = 0; step < 4; step++)
            put(plan, 2 + step * 2, 62, -8, ScifiBlocks.FLOOR_LIGHT);

        put(plan, SAMPLE.getX(), 63, SAMPLE.getZ(), Blocks.BASALT);
        put(plan, SAMPLE.getX(), 62, SAMPLE.getZ(), Blocks.SEA_LANTERN);
        put(plan, SAMPLE.getX() + 1, 63, SAMPLE.getZ() + 1, Blocks.SMOOTH_BASALT);
        put(plan, SAMPLE.getX() + 1, 63, SAMPLE.getZ() - 1, Blocks.COBBLED_DEEPSLATE);

        put(plan, -4, 63, -9, Blocks.IRON_BARS);
        put(plan, -4, 64, -9, Blocks.IRON_BARS);
        put(plan, -4, 65, -9, Blocks.IRON_BARS);
        put(plan, -3, 65, -9, Blocks.WOOL.white());
        put(plan, -2, 65, -9, Blocks.WOOL.lightBlue());

        for (int panel = 0; panel < 2; panel++) {
            put(plan, 6, 63, 1 + panel * 3, ScifiBlocks.SUPPORT_BEAM);
            for (int x = 5; x <= 7; x++)
                put(plan, x, 64, 1 + panel * 3, Blocks.DAYLIGHT_DETECTOR);
        }
        put(plan, -6, 63, -3, ScifiBlocks.BATTERY_BANK);
        put(plan, -6, 63, -2, Blocks.BARREL);
        put(plan, -7, 63, -3, ScifiBlocks.AIR_TANK);
        for (int y = 63; y <= 69; y++)
            put(plan, -8, y, 2, y < 65 ? ScifiBlocks.SUPPORT_BEAM : Blocks.IRON_BARS);
        put(plan, -8, 70, 2, Blocks.END_ROD);
        put(plan, -7, 68, 2, Blocks.CONCRETE.white());
        put(plan, -7, 69, 2, Blocks.CONCRETE.white());
        return plan;
    }

    /** A fictional two-stage lander on four legs; the crew steps out of the hatch facing the sample site. */
    private static void lander(final Map<BlockPos, BlockState> plan) {
        for (final int x : new int[] { -3, 3 }) {
            for (final int z : new int[] { -3, 3 }) {
                put(plan, x, 63, z, ScifiBlocks.SUPPORT_BEAM);
                put(plan, x, 64, z, ScifiBlocks.SUPPORT_BEAM);
                put(plan, x, 62, z, Blocks.GOLD_BLOCK);
            }
        }

        for (final BlockPos position : BlockPos.betweenClosed(-3, 63, -3, 3, 68, 3)) {
            final int x = position.getX(), y = position.getY(), z = position.getZ();
            final boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
            final boolean shell = Math.abs(x) == 3 || Math.abs(z) == 3 || y == 68;
            if (!shell || corner || z == -3 && x == 0 && y <= 64) continue;

            final boolean window = y == 66 && (Math.abs(x) <= 1 || Math.abs(z) <= 1);
            final Block block = y == 68 ? ScifiBlocks.REINFORCED_HULL : window ? ScifiBlocks.DOME_GLASS
                    : y <= 64 ? Blocks.GOLD_BLOCK : ScifiBlocks.HULL_PANEL;
            plan.put(position.immutable(), block.defaultBlockState());
        }

        for (final BlockPos position : BlockPos.betweenClosed(-2, 69, -2, 2, 70, 2))
            plan.put(position.immutable(), (position.getY() == 70 ? ScifiBlocks.REINFORCED_HULL : ScifiBlocks.HULL_PANEL).defaultBlockState());

        put(plan, 0, 67, 0, Blocks.SEA_LANTERN);
        put(plan, 0, 71, 0, Blocks.END_ROD);
        put(plan, 2, 71, 2, ScifiBlocks.FLOOR_LIGHT);
        put(plan, -1, 65, -4, ScifiBlocks.FLOOR_LIGHT);
        put(plan, 1, 65, -4, ScifiBlocks.FLOOR_LIGHT);
        for (final int x : new int[] { -1, 0, 1 })
            put(plan, x, 62, -4, Blocks.POLISHED_DEEPSLATE);
    }

    private static void put(final Map<BlockPos, BlockState> plan, final int x, final int y, final int z, final Block block) {
        plan.put(new BlockPos(x, y, z), block.defaultBlockState());
    }

    private static Map<BlockPos, BlockState> legacyPlan() {
        final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        for (int x = -LEGACY_RADIUS; x <= LEGACY_RADIUS; x++) {
            for (int z = -LEGACY_RADIUS; z <= LEGACY_RADIUS; z++) {
                final int height = (x + 14) * (x + 14) + (z - 10) * (z - 10) < 36 ? 61 : 62;
                for (int y = 60; y <= height; y++)
                    put(plan, x, y, z, y == height ? Math.abs(x * 31 + z * 17) % 7 == 0 ? Blocks.ANDESITE : Blocks.TUFF : Blocks.STONE);

                if (Math.abs(x) == LEGACY_RADIUS || Math.abs(z) == LEGACY_RADIUS) {
                    for (int y = 63; y <= 78; y++)
                        put(plan, x, y, z, Blocks.BARRIER);
                }
            }
        }

        for (final BlockPos position : BlockPos.betweenClosed(-3, 63, -3, 3, 68, 3)) {
            if (Math.abs(position.getX()) == 3 || Math.abs(position.getZ()) == 3 || position.getY() == 68)
                plan.put(position.immutable(), ScifiBlocks.REINFORCED_HULL.defaultBlockState());
        }

        put(plan, 0, 67, 0, Blocks.SEA_LANTERN);
        put(plan, SAMPLE.getX(), 63, SAMPLE.getZ(), Blocks.BASALT);
        put(plan, SAMPLE.getX(), 62, SAMPLE.getZ(), Blocks.SEA_LANTERN);
        for (final int x : new int[] { -20, 20 }) {
            for (final int z : new int[] { -20, 20 })
                put(plan, x, 62, z, Blocks.SEA_LANTERN);
        }

        return plan;
    }
}
