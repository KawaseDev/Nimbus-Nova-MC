package cc.nimbusnova.mc.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@NullMarked
public final class MarsTerrainGenerator extends ChunkGenerator {
    public static final MapCodec<MarsTerrainGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
            .xmap(MarsTerrainGenerator::new, generator -> generator.biomeSource);

    public MarsTerrainGenerator(final BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(final HolderLookup<StructureSet> structures, final RandomState random,
                                                    final long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, ChunkPos.ZERO, biomeSource, Stream.empty());
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(final ChunkAccess chunk, final Blender blender, final RandomState random,
                                                       final StructureManager structures, final BiomeManager biomes,
                                                       final @Nullable WorldGenRegion region, final Set<Holder<Biome>> possibleBiomes) {
        final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        final Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        final Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                final int x = chunk.getPos().getMinBlockX() + localX, z = chunk.getPos().getMinBlockZ() + localZ;
                final int top = Math.min(MarsTerrain.surfaceHeight(x, z), chunk.getMaxY() - 1);
                for (int y = chunk.getMinY(); y <= top; y++) {
                    final BlockState state = layer(y, top);
                    chunk.setBlockState(position.set(x, y, z), state);
                    ocean.update(localX, y, localZ, state);
                    surface.update(localX, y, localZ, state);
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    private static BlockState layer(final int y, final int top) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y >= top - 1) return Blocks.RED_SAND.defaultBlockState();
        if (Math.floorMod(y, 13) < 3) return Blocks.DYED_TERRACOTTA.red().defaultBlockState();

        return Blocks.RED_SANDSTONE.defaultBlockState();
    }

    @Override
    public int getBaseHeight(final int x, final int z, final Heightmap.Types type, final LevelHeightAccessor height,
                             final RandomState random) {
        return Math.clamp(MarsTerrain.surfaceHeight(x, z) + 1, height.getMinY(), height.getMaxY());
    }

    @Override
    public NoiseColumn getBaseColumn(final int x, final int z, final LevelHeightAccessor height, final RandomState random) {
        final BlockState[] states = new BlockState[height.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        final int top = Math.min(MarsTerrain.surfaceHeight(x, z), height.getMaxY() - 1);
        for (int y = height.getMinY(); y <= top; y++)
            states[y - height.getMinY()] = layer(y, top);

        return new NoiseColumn(height.getMinY(), states);
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }

    @Override
    public int getSpawnHeight(final LevelHeightAccessor height) {
        return 65;
    }

    @Override
    public void spawnOriginalMobs(final WorldGenRegion region) {
        /* w */
    }

    @Override
    public void addDebugScreenInfo(final List<String> text, final RandomState random, final BlockPos position,
                                   final SamplerContext context) {
        text.add("Nimbus Nova: authored Mars ridges and craters");
    }
}
