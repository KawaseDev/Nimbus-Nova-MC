package cc.nimbusnova.mc.world;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.GZIPOutputStream;

public final class ImportedMarsTerrainTest {
    private ImportedMarsTerrainTest() {
        /* w */
    }

    public static void main(final String[] arguments) throws IOException {
        final Path directory = Files.createTempDirectory("nimbus-import-test-");
        final String previous = System.getProperty("nimbusnova.marsHeightmap");
        try {
            final short[] samples = new short[48 * 48];
            for (int z = 0; z < 48; z++) {
                for (int x = 0; x < 48; x++)
                    samples[z * 48 + x] = (short) (100 + x * 2 + z);
            }

            final Path valid = directory.resolve("valid.bin.gz");
            write(valid, 0x4E4D5331, 48, 48, samples, false);
            final ImportedMarsTerrain imported = ImportedMarsTerrain.load(valid);
            assert imported.normalizedHeight(0, 0) == 64 : "The crop center must align with the outpost height";
            assert imported.normalizedHeight(7, -3) == 75 : "Relative source heights and row/column order must survive normalization";
            assert imported.influence(0, 0) == 1 : "The crop interior must preserve the source heights";
            assert imported.influence(23, 0) == 0 && imported.influence(-24, 0) == 0 : "The crop edges must blend to procedural terrain";
            assert imported.influence(20, 0) > 0 && imported.influence(20, 0) < 1 : "The outer sixteen cells need a smooth transition";
            assert imported.influence(Integer.MAX_VALUE, Integer.MIN_VALUE) == 0 : "Far coordinates must not overflow into the crop";

            System.clearProperty("nimbusnova.marsHeightmap");
            assert ImportedMarsTerrain.loadConfigured() == null;
            System.setProperty("nimbusnova.marsHeightmap", valid.toString());
            final ImportedMarsTerrain configured = ImportedMarsTerrain.loadConfigured();
            assert configured != null : "An explicitly configured fixture must load";
            assert configured.normalizedHeight(7, -3) == 75;

            final Path badMagic = directory.resolve("magic.bin.gz");
            write(badMagic, 0x42414421, 48, 48, samples, false);
            expectInvalid(badMagic);
            final Path oversized = directory.resolve("size.bin.gz");
            write(oversized, 0x4E4D5331, 1025, 48, new short[0], false);
            expectInvalid(oversized);
            final Path truncated = directory.resolve("truncated.bin.gz");
            write(truncated, 0x4E4D5331, 48, 48, new short[4], false);
            expectInvalid(truncated);
            final Path trailing = directory.resolve("trailing.bin.gz");
            write(trailing, 0x4E4D5331, 48, 48, samples, true);
            expectInvalid(trailing);
            final Path unsafe = directory.resolve("unsafe.bin.gz");
            write(unsafe, 0x4E4D5331, 2, 2, new short[] { Short.MIN_VALUE, 0, 0, Short.MAX_VALUE }, false);
            expectInvalid(unsafe);
            final Path canyon = directory.resolve("canyon.bin.gz");
            write(canyon, 0x4E4D5331, 2, 2, new short[] { 59, 230, 100, 209 }, false);
            final ImportedMarsTerrain canyonMap = ImportedMarsTerrain.load(canyon);
            assert canyonMap.verticalOffset() == -121 : "A deep crop needs a safe constant offset, not clipping";
            assert canyonMap.normalizedHeight(0, 0) == 88 : "The source center must move when necessary to preserve the canyon";
            assert canyonMap.normalizedHeight(-1, -1) == -62 && canyonMap.normalizedHeight(0, -1) == 109;

            final short[] larger = new short[320 * 320];
            for (int z = 0; z < 320; z++) {
                for (int x = 0; x < 320; x++)
                    larger[z * 320 + x] = (short) (100 + x / 8 + z / 16);
            }

            final Path largerMap = directory.resolve("integration.bin.gz");
            write(largerMap, 0x4E4D5331, 320, 320, larger, false);
            System.setProperty("nimbusnova.marsHeightmap", largerMap.toString());
            assert MarsTerrain.usesImportedHeightmap() : "The configured local crop must reach the terrain sampler";
            assert MarsTerrain.surfaceHeight(0, 0) == 64 && MarsTerrain.surfaceHeight(80, 0) == 64 : "The import must preserve the protected pad";
            assert MarsTerrain.surfaceHeight(140, 0) == 81 : "The crop interior must preserve source relief outside the pad blend";
            assert MarsTerrain.terrainDescription().contains("Local imported");
            assert Math.abs(MarsTerrain.surfaceHeight(159, 0) - MarsTerrain.surfaceHeight(160, 0)) <= 4 : "The finite crop edge must join procedural fallback";

            System.setProperty("nimbusnova.marsHeightmap", directory.resolve("missing.bin.gz").toString());
            boolean rejected = false;
            try {
                ImportedMarsTerrain.loadConfigured();
            } catch (final IllegalStateException expected) {
                rejected = true;
            }

            assert rejected : "An explicit missing file must fail instead of silently using procedural terrain";
            System.out.println("Imported Mars terrain check passed: format validation, relative heights, bounded sampling and edge blending.");
        } finally {
            if (previous == null)
                System.clearProperty("nimbusnova.marsHeightmap");
            else
                System.setProperty("nimbusnova.marsHeightmap", previous);

            try (final var files = Files.walk(directory)) {
                for (final Path file : files.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(file);
            }
        }
    }

    private static void write(final Path path, final int magic, final int width, final int height,
                              final short[] samples, final boolean trailing) throws IOException {
        try (final DataOutputStream output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            output.writeInt(magic);
            output.writeShort(width);
            output.writeShort(height);
            for (final short sample : samples)
                output.writeShort(sample);

            if (trailing)
                output.writeByte(1);
        }
    }

    private static void expectInvalid(final Path path) {
        boolean rejected = false;
        try {
            ImportedMarsTerrain.load(path);
        } catch (final IOException | IllegalArgumentException expected) {
            rejected = true;
        }

        assert rejected : "Invalid imported map must be rejected: " + path;
    }
}
