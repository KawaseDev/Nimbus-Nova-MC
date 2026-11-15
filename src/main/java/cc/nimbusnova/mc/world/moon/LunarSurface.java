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
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Authored training site in a dedicated dimension; it is not a reconstruction of a real landing site. */
public final class LunarSurface {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.parse("nimbus_nova:lunar_surface"));
    public static final double GRAVITY_RATIO = 1.0 / 6.0;
    public static final BlockPos ARRIVAL = new BlockPos(0, 63, -7);
    public static final BlockPos SAMPLE = new BlockPos(10, 63, -8);
    private static final int RADIUS = 28;

    private LunarSurface() { }

    public static void create(MinecraftServer server) throws IOException {
        final ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) throw new IOException("The lunar surface dimension is unavailable. Restart with the updated mod.");
        final Path marker = server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/lunar-surface-v1.txt");
        if (Files.exists(marker)) {
            if (!Files.readString(marker).equals("complete")) throw new IOException("Lunar surface construction was interrupted. Restore a backup or use a fresh world.");
            return;
        }
        for (final BlockPos pos : BlockPos.betweenClosed(-RADIUS, 60, -RADIUS, RADIUS, 80, RADIUS))
            if (!level.getBlockState(pos).isAir()) throw new IOException("Lunar site contains existing blocks at " + pos + "; they were preserved.");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "building", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
            final int height = surfaceHeight(x, z);
            for (int y = 60; y <= height; y++) {
                final Block material = y == height ? ((Math.abs(x * 31 + z * 17) % 7 == 0) ? Blocks.ANDESITE : Blocks.TUFF) : Blocks.STONE;
                level.setBlock(new BlockPos(x, y, z), material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            if (Math.abs(x) == RADIUS || Math.abs(z) == RADIUS)
                for (int y = 63; y <= 78; y++) level.setBlock(new BlockPos(x, y, z), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        // Small, fictional lander: the crew arrives outside only after their suit checks.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = 63; y <= 68; y++) {
            final boolean shell = Math.abs(x) == 3 || Math.abs(z) == 3 || y == 68;
            final boolean doorway = z == -3 && x == 0 && y <= 64;
            if (shell && !doorway) level.setBlock(new BlockPos(x, y, z), ScifiBlocks.REINFORCED_HULL.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(new BlockPos(0, 67, 0), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(SAMPLE, Blocks.BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(SAMPLE.getX(), 62, SAMPLE.getZ()), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (final int x : new int[] { -20, 20 }) for (final int z : new int[] { -20, 20 })
            level.setBlock(new BlockPos(x, 62, z), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        Files.writeString(marker, "complete", StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public static int surfaceHeight(int x, int z) {
        int dx = x + 14, dz = z - 10;
        return dx * dx + dz * dz < 36 ? 61 : 62;
    }
}
